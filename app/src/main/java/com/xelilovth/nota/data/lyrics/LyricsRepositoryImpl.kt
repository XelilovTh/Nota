package com.xelilovth.nota.data.lyrics

import android.content.Context
import android.net.Uri
import com.xelilovth.nota.di.IoDispatcher
import com.xelilovth.nota.domain.model.LyricLine
import com.xelilovth.nota.domain.model.LyricWord
import com.xelilovth.nota.domain.model.LyricsSource
import com.xelilovth.nota.domain.model.LyricsSyncType
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.model.TrackLyrics
import com.xelilovth.nota.domain.repository.LyricsRepository
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LyricsRepository {
    private data class CachedLyrics(val value: TrackLyrics?, val savedAtMs: Long)

    private val cache = ConcurrentHashMap<Long, CachedLyrics>()
    private val cacheDir by lazy { File(context.filesDir, LYRICS_CACHE_DIR) }
    private val offsetsPrefs by lazy {
        context.getSharedPreferences(OFFSETS_PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun getLyrics(track: Track): TrackLyrics? = withContext(ioDispatcher) {
        val cached = cache[track.id]
        val now = System.currentTimeMillis()
        val cacheTtl = if (cached?.value == null) MISS_CACHE_TTL_MS else CACHE_TTL_MS
        if (cached != null && now - cached.savedAtMs < cacheTtl) return@withContext cached.value

        val diskCached = readDiskCache(track.id)
        if (diskCached != null) {
            cache[track.id] = CachedLyrics(diskCached, now)
            return@withContext diskCached
        }

        val result = try {
            fetchFromLrclib(track) ?: fetchFromLyricsOvh(track)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            runCatching { fetchFromLyricsOvh(track) }.getOrNull()
        }
        if (result != null) {
            writeDiskCache(track.id, result)
        }
        cache[track.id] = CachedLyrics(result, now)
        result
    }

    override suspend fun importLocalLyrics(track: Track, uriString: String): TrackLyrics? =
        withContext(ioDispatcher) {
            val rawText = runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriString))
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@withContext null

            val parsed = LyricsParser.buildTrackLyrics(
                plainLyrics = rawText,
                syncedLyrics = rawText,
                lyricsFile = rawText,
                hasWordSync = rawText.contains("lines:") && rawText.contains("words:"),
                durationMs = track.durationMs,
                source = LyricsSource.LOCAL_FILE,
            ) ?: return@withContext null

            writeDiskCache(track.id, parsed)
            cache[track.id] = CachedLyrics(parsed, System.currentTimeMillis())
            parsed
        }

    override suspend fun getSyncOffsetMs(trackId: Long): Long = withContext(ioDispatcher) {
        offsetsPrefs.getLong(trackId.toString(), 0L)
    }

    override suspend fun setSyncOffsetMs(trackId: Long, offsetMs: Long) {
        withContext(ioDispatcher) {
            offsetsPrefs.edit().putLong(trackId.toString(), offsetMs).apply()
        }
    }

    private fun readDiskCache(trackId: Long): TrackLyrics? {
        val file = File(cacheDir, "$trackId.json")
        if (!file.isFile) return null
        return runCatching {
            deserializeLyrics(JSONObject(file.readText(Charsets.UTF_8)))
        }.getOrNull()
    }

    private fun writeDiskCache(trackId: Long, lyrics: TrackLyrics) {
        runCatching {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            File(cacheDir, "$trackId.json").writeText(
                serializeLyrics(lyrics).toString(),
                Charsets.UTF_8,
            )
        }
    }

    private fun serializeLyrics(lyrics: TrackLyrics): JSONObject = JSONObject().apply {
        put("source", lyrics.source.name)
        put("syncType", lyrics.syncType.name)
        put("instrumental", lyrics.instrumental)
        put(
            "lines",
            JSONArray().apply {
                lyrics.lines.forEach { line ->
                    put(
                        JSONObject().apply {
                            put("text", line.text)
                            if (line.startMs != null) put("startMs", line.startMs)
                            if (line.endMs != null) put("endMs", line.endMs)
                            put(
                                "words",
                                JSONArray().apply {
                                    line.words.forEach { word ->
                                        put(
                                            JSONObject().apply {
                                                put("text", word.text)
                                                put("startMs", word.startMs)
                                                if (word.endMs != null) put("endMs", word.endMs)
                                            },
                                        )
                                    }
                                },
                            )
                        },
                    )
                }
            },
        )
    }

    private fun deserializeLyrics(json: JSONObject): TrackLyrics? {
        val source = LyricsSource.valueOf(json.getString("source"))
        val syncType = LyricsSyncType.valueOf(json.getString("syncType"))
        val instrumental = json.optBoolean("instrumental", false)
        val linesArray = json.optJSONArray("lines") ?: JSONArray()
        val lines = (0 until linesArray.length()).mapNotNull { lineIndex ->
            val lineObj = linesArray.optJSONObject(lineIndex) ?: return@mapNotNull null
            val text = lineObj.optString("text")
            if (text.isBlank()) return@mapNotNull null
            val startMs = if (lineObj.has("startMs") && !lineObj.isNull("startMs")) {
                lineObj.getLong("startMs")
            } else {
                null
            }
            val endMs = if (lineObj.has("endMs") && !lineObj.isNull("endMs")) {
                lineObj.getLong("endMs")
            } else {
                null
            }
            val wordsArray = lineObj.optJSONArray("words") ?: JSONArray()
            val words = (0 until wordsArray.length()).mapNotNull { wordIndex ->
                val wordObj = wordsArray.optJSONObject(wordIndex) ?: return@mapNotNull null
                val wordText = wordObj.optString("text")
                if (!wordObj.has("startMs") || wordObj.isNull("startMs")) return@mapNotNull null
                val wordStartMs = wordObj.getLong("startMs")
                val wordEndMs = if (wordObj.has("endMs") && !wordObj.isNull("endMs")) {
                    wordObj.getLong("endMs")
                } else {
                    null
                }
                LyricWord(text = wordText, startMs = wordStartMs, endMs = wordEndMs)
            }
            LyricLine(text = text, startMs = startMs, endMs = endMs, words = words)
        }
        if (!instrumental && lines.isEmpty()) return null
        return TrackLyrics(
            lines = lines,
            source = source,
            syncType = syncType,
            instrumental = instrumental,
        )
    }

    private fun fetchFromLrclib(track: Track): TrackLyrics? {
        if (track.title.isBlank() || track.artist.isBlank()) return null
        val durationSeconds = (track.durationMs / 1_000L).toInt().takeIf { it in 1..3_600 }
        val getUri = Uri.parse("https://lrclib.net/api/get").buildUpon()
            .appendQueryParameter("track_name", track.title)
            .appendQueryParameter("artist_name", track.artist)
            .apply {
                track.album.takeIf { it.isNotBlank() && !it.equals("Unknown album", true) }
                    ?.let { appendQueryParameter("album_name", it) }
                durationSeconds?.let { appendQueryParameter("duration", it.toString()) }
            }
            .build()
        var plainFallback: TrackLyrics? = null
        val exact = requestJson(getUri.toString())
        if (exact != null) {
            val exactLyrics = parseLrclibRecord(exact, track)
            if (exactLyrics?.instrumental == true) return exactLyrics
            if (exactLyrics != null) {
                if (exactLyrics.syncType != LyricsSyncType.PLAIN) return exactLyrics
                plainFallback = exactLyrics
            }
        }

        val searchUri = Uri.parse("https://lrclib.net/api/search").buildUpon()
            .appendQueryParameter("track_name", track.title)
            .appendQueryParameter("artist_name", track.artist)
            .apply {
                track.album.takeIf { it.isNotBlank() && !it.equals("Unknown album", true) }
                    ?.let { appendQueryParameter("album_name", it) }
            }
            .build()
        val candidates = requestArray(searchUri.toString()) ?: return plainFallback
        val targetDuration = durationSeconds ?: 0
        val ranked = (0 until candidates.length()).mapNotNull { index ->
            candidates.optJSONObject(index)?.let { candidate ->
                val score = LyricsParser.matchScore(
                    title = candidate.optString("trackName"),
                    artist = candidate.optString("artistName"),
                    album = candidate.optString("albumName"),
                    durationSeconds = candidate.optInt("duration"),
                    targetTitle = track.title,
                    targetArtist = track.artist,
                    targetAlbum = track.album,
                    targetDurationSeconds = targetDuration,
                )
                score to candidate
            }
        }.filter { it.first >= MIN_MATCH_SCORE }
            .sortedByDescending { it.first }

        var searchPlainFallback: TrackLyrics? = null
        for ((_, candidate) in ranked) {
            val candidateLyrics = parseLrclibRecord(candidate, track) ?: continue
            if (candidateLyrics.instrumental) return candidateLyrics
            if (candidateLyrics.syncType != LyricsSyncType.PLAIN) return candidateLyrics
            if (searchPlainFallback == null) searchPlainFallback = candidateLyrics
        }
        return searchPlainFallback ?: plainFallback
    }

    private fun parseLrclibRecord(json: JSONObject, track: Track): TrackLyrics? {
        if (json.optBoolean("instrumental")) {
            return TrackLyrics(emptyList(), LyricsSource.LRCLIB, LyricsSyncType.PLAIN, instrumental = true)
        }
        return LyricsParser.buildTrackLyrics(
            plainLyrics = json.optString("plainLyrics").takeUnless { it == "null" || it.isBlank() },
            syncedLyrics = json.optString("syncedLyrics").takeUnless { it == "null" || it.isBlank() },
            lyricsFile = json.optString("lyricsfile").takeUnless { it == "null" || it.isBlank() },
            hasWordSync = json.optBoolean("hasWordSync"),
            durationMs = track.durationMs,
            source = LyricsSource.LRCLIB,
        )
    }

    private fun fetchFromLyricsOvh(track: Track): TrackLyrics? {
        val artist = Uri.encode(track.artist)
        val title = Uri.encode(track.title)
        val response = requestJson("https://api.lyrics.ovh/v1/$artist/$title") ?: return null
        val lyrics = response.optString("lyrics").takeUnless { it == "null" || it.isBlank() } ?: return null
        val lines = LyricsParser.fromPlain(lyrics)
        return lines.takeIf { it.isNotEmpty() }?.let {
            TrackLyrics(it, LyricsSource.LYRICS_OVH, LyricsSyncType.PLAIN)
        }
    }

    private fun requestJson(url: String): JSONObject? {
        val response = request(url) ?: return null
        return runCatching { JSONObject(response) }.getOrNull()
    }

    private fun requestArray(url: String): JSONArray? {
        val response = request(url) ?: return null
        return runCatching { JSONArray(response) }.getOrNull()
    }

    private fun request(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (status == 429) return null
            if (status !in 200..299) throw IOException("Lyrics service returned HTTP $status")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val LYRICS_CACHE_DIR = "lyrics_cache"
        const val OFFSETS_PREFS_NAME = "nota_lyrics_offsets"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 7_000
        const val CACHE_TTL_MS = 6 * 60 * 60 * 1_000L
        const val MISS_CACHE_TTL_MS = 10 * 60 * 1_000L
        const val MIN_MATCH_SCORE = 75
        const val USER_AGENT = "Nota/0.3.0 (https://github.com/XelilovTh/Nota)"
    }
}
