package com.xelilovth.nota.data.online

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.xelilovth.nota.di.IoDispatcher
import com.xelilovth.nota.domain.model.OfflineStorageLocation
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.OnlineMusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Singleton
class OnlineMusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : OnlineMusicRepository {

    private data class CachedStreamUrl(val url: String, val savedAtMs: Long)

    private val offlineDir by lazy { File(context.filesDir, OFFLINE_DIR_NAME).apply { mkdirs() } }
    private val offlineIndexFile by lazy { File(context.filesDir, OFFLINE_INDEX_FILE) }
    private val onlineMetaFile by lazy { File(context.filesDir, ONLINE_META_FILE) }

    private val streamUrlCache = ConcurrentHashMap<String, CachedStreamUrl>()
    private val onlineMetaMap = ConcurrentHashMap<Long, Track>()

    private val _offlineTracks = MutableStateFlow<List<Track>>(emptyList())
    override val offlineTracks: StateFlow<List<Track>> = _offlineTracks.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<Long, Int>>(emptyMap())
    override val downloadProgress: StateFlow<Map<Long, Int>> = _downloadProgress.asStateFlow()

    private val _downloadedOnlineIds = MutableStateFlow<Set<String>>(emptySet())
    override val downloadedOnlineIds: StateFlow<Set<String>> = _downloadedOnlineIds.asStateFlow()

    init {
        loadOnlineMetaFromDisk()
        loadOfflineTracksFromDisk()
    }

    override suspend fun searchOnline(query: String): Result<List<Track>> = withContext(ioDispatcher) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Result.success(emptyList())

        try {
            val newPipeResults = runCatching { searchViaNewPipe(trimmed) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }

            val tracks = newPipeResults ?: searchViaPiped(trimmed)
            tracks.forEach(::cacheOnlineTrackInternal)
            persistOnlineMeta()
            Result.success(tracks)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun resolveStreamUrl(onlineId: String): String? = withContext(ioDispatcher) {
        val cleanId = onlineId.trim()
        if (cleanId.isBlank()) return@withContext null

        _offlineTracks.value.firstOrNull { it.onlineId == cleanId }?.let { local ->
            if (localUriExists(local.uri)) return@withContext local.uri
        }

        val now = System.currentTimeMillis()
        val cached = streamUrlCache[cleanId]
        if (cached != null && now - cached.savedAtMs < STREAM_URL_TTL_MS) {
            return@withContext cached.url
        }

        val resolved = runCatching { resolveViaNewPipe(cleanId) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
            ?: resolveViaPiped(cleanId)

        if (!resolved.isNullOrBlank()) {
            streamUrlCache[cleanId] = CachedStreamUrl(resolved, now)
        }
        resolved
    }

    override suspend fun downloadForOffline(
        track: Track,
        location: OfflineStorageLocation,
    ): Result<Track> = withContext(ioDispatcher) {
        val onlineId = track.onlineId ?: extractOnlineId(track.uri)
            ?: return@withContext Result.failure(IllegalArgumentException("Track has no online ID"))

        _offlineTracks.value.firstOrNull { it.onlineId == onlineId }?.let { existing ->
            if (localUriExists(existing.uri)) return@withContext Result.success(existing)
        }

        if (_downloadProgress.value.containsKey(track.id)) {
            return@withContext Result.failure(IllegalStateException("Download already in progress"))
        }

        _downloadProgress.value = _downloadProgress.value + (track.id to 0)
        try {
            val streamUrl = resolveStreamUrl(onlineId)
                ?: throw IOException("Could not resolve audio stream")

            val savedUri = when (location) {
                OfflineStorageLocation.APP_INTERNAL -> downloadToInternal(track, onlineId, streamUrl)
                OfflineStorageLocation.PUBLIC_MUSIC -> downloadToPublicMusic(track, onlineId, streamUrl)
            }

            val savedTrack = track.copy(
                uri = savedUri,
                folder = if (location == OfflineStorageLocation.PUBLIC_MUSIC) PUBLIC_FOLDER_NAME else OFFLINE_FOLDER_NAME,
                isOnline = false,
                onlineId = onlineId,
            )
            val updated = (_offlineTracks.value.filterNot { it.onlineId == onlineId } + savedTrack)
            _offlineTracks.value = updated
            _downloadedOnlineIds.value = updated.mapNotNull { it.onlineId }.toSet()
            persistOfflineTracks(updated)
            Result.success(savedTrack)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            _downloadProgress.value = _downloadProgress.value - track.id
        }
    }

    override suspend fun removeOfflineTrack(track: Track): Unit = withContext(ioDispatcher) {
        val onlineId = track.onlineId
        val target = _offlineTracks.value.firstOrNull {
            it.id == track.id || (onlineId != null && it.onlineId == onlineId)
        } ?: track

        runCatching {
            val parsed = Uri.parse(target.uri)
            when (parsed.scheme) {
                "file" -> parsed.path?.let { File(it).delete() }
                "content" -> context.contentResolver.delete(parsed, null, null)
                else -> Unit
            }
        }

        val updated = _offlineTracks.value.filterNot {
            it.id == target.id || (onlineId != null && it.onlineId == onlineId)
        }
        _offlineTracks.value = updated
        _downloadedOnlineIds.value = updated.mapNotNull { it.onlineId }.toSet()
        persistOfflineTracks(updated)
    }

    override fun getCachedOnlineTracks(): List<Track> = onlineMetaMap.values.toList()

    override fun cacheOnlineTrack(track: Track) {
        if (!track.isOnline) return
        cacheOnlineTrackInternal(track)
        persistOnlineMeta()
    }

    private fun searchViaNewPipe(query: String): List<Track> {
        ensureNewPipeInitialized()
        val extractor = runCatching {
            ServiceList.YouTube.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                "",
            ).apply { fetchPage() }
        }.getOrElse {
            ServiceList.YouTube.getSearchExtractor(query).apply { fetchPage() }
        }

        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val videoId = extractVideoIdFromUrl(item.url) ?: return@mapNotNull null
                val durationMs = if (item.duration > 0L) item.duration * 1_000L else 0L
                val thumbUrl = item.thumbnails.maxByOrNull { it.width }?.url
                Track(
                    id = stableOnlineTrackId(videoId),
                    uri = "$ONLINE_URI_PREFIX$videoId",
                    title = item.name.orEmpty().ifBlank { "Unknown track" },
                    artist = item.uploaderName.orEmpty().ifBlank { "Unknown artist" },
                    album = "Online",
                    durationMs = durationMs,
                    folder = "Online",
                    isOnline = true,
                    onlineId = videoId,
                    artworkUrl = thumbUrl,
                )
            }
            .distinctBy { it.onlineId }
            .take(MAX_SEARCH_RESULTS)
    }

    private fun searchViaPiped(query: String): List<Track> {
        val encoded = Uri.encode(query)
        for (baseUrl in PIPED_INSTANCES) {
            val url = "$baseUrl/search?q=$encoded&filter=music_songs"
            val json = runCatching { requestJson(url) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull() ?: continue
            val items = json.optJSONArray("items") ?: continue
            val parsed = (0 until items.length()).mapNotNull { index ->
                val obj = items.optJSONObject(index) ?: return@mapNotNull null
                val rawUrl = obj.optString("url")
                val videoId = extractVideoIdFromUrl(rawUrl) ?: return@mapNotNull null
                val durationSec = obj.optLong("duration", 0L)
                Track(
                    id = stableOnlineTrackId(videoId),
                    uri = "$ONLINE_URI_PREFIX$videoId",
                    title = obj.optString("title").ifBlank { "Unknown track" },
                    artist = obj.optString("uploaderName").ifBlank { "Unknown artist" },
                    album = "Online",
                    durationMs = if (durationSec > 0L) durationSec * 1_000L else 0L,
                    folder = "Online",
                    isOnline = true,
                    onlineId = videoId,
                    artworkUrl = obj.optString("thumbnail").takeIf { it.isNotBlank() },
                )
            }
            if (parsed.isNotEmpty()) return parsed.distinctBy { it.onlineId }.take(MAX_SEARCH_RESULTS)
        }
        return emptyList()
    }

    private fun resolveViaNewPipe(onlineId: String): String? {
        ensureNewPipeInitialized()
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$onlineId")
        return pickBestAudioStream(info.audioStreams)?.content
    }

    private fun pickBestAudioStream(streams: List<AudioStream>): AudioStream? {
        val valid = streams.filter { !it.content.isNullOrBlank() }
        if (valid.isEmpty()) return null
        val m4a = valid.filter {
            it.format?.suffix.equals("m4a", ignoreCase = true) ||
                it.format?.mimeType?.contains("mp4", ignoreCase = true) == true
        }
        val pool = if (m4a.isNotEmpty()) m4a else valid
        return pool.filter { it.averageBitrate in 64..192 }.maxByOrNull { it.averageBitrate }
            ?: pool.maxByOrNull { it.averageBitrate }
    }

    private fun resolveViaPiped(onlineId: String): String? {
        for (baseUrl in PIPED_INSTANCES) {
            val json = runCatching { requestJson("$baseUrl/streams/$onlineId") }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull() ?: continue
            val audioStreams = json.optJSONArray("audioStreams") ?: continue
            var fallbackUrl: String? = null
            var bestBitrate = -1
            var bestM4aUrl: String? = null
            var bestM4aBitrate = -1
            for (i in 0 until audioStreams.length()) {
                val stream = audioStreams.optJSONObject(i) ?: continue
                val url = stream.optString("url").takeIf { it.isNotBlank() } ?: continue
                val mime = stream.optString("mimeType")
                val bitrate = stream.optInt("bitrate", 0)
                if (mime.contains("mp4", ignoreCase = true) && bitrate > bestM4aBitrate) {
                    bestM4aBitrate = bitrate
                    bestM4aUrl = url
                }
                if (bitrate > bestBitrate) {
                    bestBitrate = bitrate
                    fallbackUrl = url
                }
            }
            val picked = bestM4aUrl ?: fallbackUrl
            if (!picked.isNullOrBlank()) return picked
        }
        return null
    }

    private fun downloadToInternal(track: Track, onlineId: String, streamUrl: String): String {
        if (!offlineDir.exists()) offlineDir.mkdirs()
        val targetFile = File(offlineDir, "$onlineId.m4a")
        val tempFile = File(offlineDir, "$onlineId.m4a.part")
        try {
            downloadStreamTo(track.id, streamUrl) { input, contentLength ->
                tempFile.outputStream().use { output ->
                    copyWithProgress(input, output, contentLength, track.id)
                }
            }
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            return Uri.fromFile(targetFile).toString()
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
    }

    private fun downloadToPublicMusic(track: Track, onlineId: String, streamUrl: String): String {
        val safeTitle = track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(50).trim().ifBlank { onlineId }
        val fileName = "${safeTitle}_$onlineId.m4a"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.TITLE, track.title)
                put(MediaStore.Audio.Media.ARTIST, track.artist)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$PUBLIC_FOLDER_NAME")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val insertedUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Failed to create MediaStore entry")
            try {
                downloadStreamTo(track.id, streamUrl) { input, contentLength ->
                    resolver.openOutputStream(insertedUri)?.use { output ->
                        copyWithProgress(input, output, contentLength, track.id)
                    } ?: throw IOException("Failed to open MediaStore output stream")
                }
                val doneValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                resolver.update(insertedUri, doneValues, null, null)
                return insertedUri.toString()
            } catch (t: Throwable) {
                resolver.delete(insertedUri, null, null)
                throw t
            }
        } else {
            @Suppress("DEPRECATION")
            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                PUBLIC_FOLDER_NAME,
            ).apply { mkdirs() }
            val targetFile = File(musicDir, fileName)
            downloadStreamTo(track.id, streamUrl) { input, contentLength ->
                targetFile.outputStream().use { output ->
                    copyWithProgress(input, output, contentLength, track.id)
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf("audio/mp4"), null)
            return Uri.fromFile(targetFile).toString()
        }
    }

    private inline fun downloadStreamTo(
        trackId: Long,
        streamUrl: String,
        writer: (java.io.InputStream, Long) -> Unit,
    ) {
        val connection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", DOWNLOAD_USER_AGENT)
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("Audio download failed with HTTP $status")
            }
            val contentLength = connection.contentLengthLong
            connection.inputStream.use { input ->
                writer(input, contentLength)
            }
            _downloadProgress.value = _downloadProgress.value + (trackId to 100)
        } finally {
            connection.disconnect()
        }
    }

    private fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        contentLength: Long,
        trackId: Long,
    ) {
        val buffer = ByteArray(16 * 1024)
        var downloaded = 0L
        var lastPercent = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            downloaded += read
            if (contentLength > 0L) {
                val percent = ((downloaded * 100L) / contentLength).toInt().coerceIn(0, 99)
                if (percent != lastPercent) {
                    lastPercent = percent
                    _downloadProgress.value = _downloadProgress.value + (trackId to percent)
                }
            }
        }
    }

    private fun localUriExists(uriString: String): Boolean {
        val parsed = Uri.parse(uriString)
        return when (parsed.scheme) {
            "file" -> parsed.path?.let { File(it).exists() } == true
            "content" -> runCatching {
                context.contentResolver.openAssetFileDescriptor(parsed, "r")?.use { true } ?: false
            }.getOrDefault(false)
            else -> false
        }
    }

    private fun cacheOnlineTrackInternal(track: Track) {
        onlineMetaMap[track.id] = track
    }

    private fun persistOnlineMeta() {
        runCatching {
            val array = JSONArray()
            onlineMetaMap.values.take(MAX_ONLINE_META_ENTRIES).forEach { track ->
                array.put(trackToJson(track))
            }
            onlineMetaFile.writeText(array.toString(), Charsets.UTF_8)
        }
    }

    private fun loadOnlineMetaFromDisk() {
        runCatching {
            if (!onlineMetaFile.exists()) return
            val array = JSONArray(onlineMetaFile.readText(Charsets.UTF_8))
            for (i in 0 until array.length()) {
                val track = jsonToTrack(array.optJSONObject(i) ?: continue) ?: continue
                onlineMetaMap[track.id] = track
            }
        }
    }

    private fun persistOfflineTracks(tracks: List<Track>) {
        runCatching {
            val array = JSONArray()
            tracks.forEach { array.put(trackToJson(it)) }
            offlineIndexFile.writeText(array.toString(), Charsets.UTF_8)
        }
    }

    private fun loadOfflineTracksFromDisk() {
        runCatching {
            if (!offlineIndexFile.exists()) return
            val array = JSONArray(offlineIndexFile.readText(Charsets.UTF_8))
            val loaded = (0 until array.length()).mapNotNull { index ->
                val track = jsonToTrack(array.optJSONObject(index) ?: return@mapNotNull null)
                track?.takeIf { localUriExists(it.uri) }
            }
            _offlineTracks.value = loaded
            _downloadedOnlineIds.value = loaded.mapNotNull { it.onlineId }.toSet()
        }
    }

    private fun trackToJson(track: Track): JSONObject = JSONObject().apply {
        put("id", track.id)
        put("uri", track.uri)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        put("durationMs", track.durationMs)
        put("folder", track.folder)
        put("isOnline", track.isOnline)
        if (track.onlineId != null) put("onlineId", track.onlineId)
        if (track.artworkUrl != null) put("artworkUrl", track.artworkUrl)
    }

    private fun jsonToTrack(obj: JSONObject): Track? {
        val uri = obj.optString("uri").takeIf { it.isNotBlank() } ?: return null
        return Track(
            id = obj.optLong("id"),
            uri = uri,
            title = obj.optString("title"),
            artist = obj.optString("artist"),
            album = obj.optString("album"),
            durationMs = obj.optLong("durationMs"),
            folder = obj.optString("folder"),
            isOnline = obj.optBoolean("isOnline", false),
            onlineId = obj.optString("onlineId").takeIf { it.isNotBlank() },
            artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
        )
    }

    private fun requestJson(url: String): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", DOWNLOAD_USER_AGENT)
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            runCatching { JSONObject(body) }.getOrNull()
        } finally {
            connection.disconnect()
        }
    }

    private fun ensureNewPipeInitialized() {
        if (newPipeInitialized) return
        synchronized(NewPipeLock) {
            if (!newPipeInitialized) {
                NewPipe.init(NewPipeDownloader, Localization.DEFAULT)
                newPipeInitialized = true
            }
        }
    }

    companion object {
        const val ONLINE_URI_PREFIX = "nota://online/"
        private const val OFFLINE_DIR_NAME = "offline_tracks"
        private const val OFFLINE_INDEX_FILE = "offline_tracks.json"
        private const val ONLINE_META_FILE = "online_tracks_meta.json"
        private const val OFFLINE_FOLDER_NAME = "Offline"
        private const val PUBLIC_FOLDER_NAME = "Nota"
        private const val STREAM_URL_TTL_MS = 2 * 60 * 60 * 1_000L
        private const val MAX_SEARCH_RESULTS = 25
        private const val MAX_ONLINE_META_ENTRIES = 100
        private const val DOWNLOAD_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private val PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.tokhmi.xyz",
            "https://api.piped.private.coffee",
        )
        private val NewPipeLock = Any()

        @Volatile
        private var newPipeInitialized = false

        fun extractOnlineId(uri: String): String? =
            uri.removePrefix(ONLINE_URI_PREFIX).takeIf { uri.startsWith(ONLINE_URI_PREFIX) && it.isNotBlank() }

        fun extractVideoIdFromUrl(url: String?): String? {
            if (url.isNullOrBlank()) return null
            val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return null
            parsed.getQueryParameter("v")?.takeIf { it.isNotBlank() }?.let { return it }
            val segments = parsed.pathSegments
            if (parsed.host?.contains("youtu.be") == true && segments.isNotEmpty()) {
                return segments.first().takeIf { it.isNotBlank() }
            }
            if (segments.size >= 2 && (segments[0] == "shorts" || segments[0] == "embed" || segments[0] == "watch")) {
                return segments[1].takeIf { it.isNotBlank() }
            }
            return null
        }

        fun stableOnlineTrackId(videoId: String): Long {
            var hash = 1125899906842597L
            for (ch in videoId) {
                hash = 31L * hash + ch.code.toLong()
            }
            val positive = hash and 0x3FFFFFFFFFFFFFFFL
            return -(positive + 10_000L)
        }
    }
}
