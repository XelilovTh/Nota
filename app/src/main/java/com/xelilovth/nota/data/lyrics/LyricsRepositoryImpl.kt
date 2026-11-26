package com.xelilovth.nota.data.lyrics

import android.content.Context
import android.net.Uri
import android.util.Base64
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

    private sealed interface DiskCacheEntry {
        data object Deleted : DiskCacheEntry
        data class Found(val lyrics: TrackLyrics) : DiskCacheEntry
    }

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

        when (val diskCached = readDiskCache(track.id)) {
            DiskCacheEntry.Deleted -> {
                cache[track.id] = CachedLyrics(null, now)
                return@withContext null
            }
            is DiskCacheEntry.Found -> {
                cache[track.id] = CachedLyrics(diskCached.lyrics, now)
                return@withContext diskCached.lyrics
            }
            null -> Unit
        }

        val betterLyrics = try {
            fetchFromBetterLyrics(track)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

        val result = if (
            betterLyrics != null &&
            (betterLyrics.instrumental || betterLyrics.syncType != LyricsSyncType.PLAIN)
        ) {
            betterLyrics
        } else {
            val lrclib = try {
                fetchFromLrclib(track)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            lrclib ?: betterLyrics ?: try {
                fetchFromLyricsOvh(track)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
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

    override suspend fun generateLyricsWithGemini(track: Track, apiKey: String): TrackLyrics? =
        withContext(ioDispatcher) {
            val trimmedKey = apiKey.trim()
            if (trimmedKey.isBlank()) return@withContext null

            val trackUri = Uri.parse(track.uri)
            val audioBytes = runCatching {
                context.contentResolver.openInputStream(trackUri)?.use { it.readBytes() }
            }.getOrNull() ?: return@withContext null
            if (audioBytes.isEmpty() || audioBytes.size > MAX_GEMINI_INLINE_BYTES) {
                return@withContext null
            }

            val mimeType = context.contentResolver.getType(trackUri) ?: "audio/mpeg"
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            val prompt = "Listen carefully to this audio track and transcribe every sung word accurately in Enhanced LRC format: [mm:ss.xx] <mm:ss.xx>word1 <mm:ss.xx>word2 ... Give each line a [mm:ss.xx] start timestamp and each word inside the line its own <mm:ss.xx> timestamp matching when that word is sung. Output ONLY valid Enhanced LRC lines without markdown code blocks or explanations. If the track is purely instrumental, output exactly [INSTRUMENTAL]."
            val requestBody = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put(
                                "parts",
                                JSONArray()
                                    .put(
                                        JSONObject().apply {
                                            put(
                                                "inline_data",
                                                JSONObject().apply {
                                                    put("mime_type", mimeType)
                                                    put("data", base64Audio)
                                                },
                                            )
                                        },
                                    )
                                    .put(
                                        JSONObject().apply {
                                            put("text", prompt)
                                        },
                                    ),
                            )
                        },
                    ),
                )
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${Uri.encode(trimmedKey)}"
            val responseJson = try {
                postJson(url, requestBody)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return@withContext null

            val rawText = responseJson
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext null

            val cleaned = stripMarkdownFences(rawText).trim()
            if (cleaned.isBlank()) return@withContext null

            val parsed = if (cleaned.equals("[INSTRUMENTAL]", ignoreCase = true)) {
                TrackLyrics(
                    lines = emptyList(),
                    source = LyricsSource.GEMINI,
                    syncType = LyricsSyncType.PLAIN,
                    instrumental = true,
                )
            } else {
                val lrcLines = LyricsParser.fromLrc(cleaned, track.durationMs)
                if (lrcLines.isNotEmpty()) {
                    TrackLyrics(
                        lines = lrcLines,
                        source = LyricsSource.GEMINI,
                        syncType = if (lrcLines.any { it.words.isNotEmpty() }) {
                            LyricsSyncType.WORD
                        } else {
                            LyricsSyncType.LINE
                        },
                    )
                } else {
                    LyricsParser.fromPlain(cleaned).takeIf { it.isNotEmpty() }?.let { plainLines ->
                        TrackLyrics(
                            lines = plainLines,
                            source = LyricsSource.GEMINI,
                            syncType = LyricsSyncType.PLAIN,
                        )
                    }
                }
            } ?: return@withContext null

            writeDiskCache(track.id, parsed)
            cache[track.id] = CachedLyrics(parsed, System.currentTimeMillis())
            parsed
        }

    override suspend fun translateLyrics(
        track: Track,
        lyrics: TrackLyrics,
        apiKey: String,
        targetLanguage: String,
    ): TrackLyrics? = withContext(ioDispatcher) {
        val trimmedKey = apiKey.trim()
        if (lyrics.lines.isEmpty() || trimmedKey.isBlank()) return@withContext null
        if (
            lyrics.translatedLanguage == targetLanguage &&
            lyrics.lines.any { !it.translatedText.isNullOrBlank() }
        ) {
            return@withContext lyrics
        }

        val numberedLines = lyrics.lines
            .mapIndexed { index, line -> "$index|${line.text}" }
            .joinToString("\n")
        val prompt = buildString {
            append("Translate each numbered lyric line naturally into $targetLanguage.\n")
            append("Keep the exact index|translated text format on each line, preserving the index number and | separator.\n")
            append("Do not include any markdown formatting, code fences, or explanations.\n\n")
            append(numberedLines)
        }
        val requestBody = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put(
                            "parts",
                            JSONArray().put(
                                JSONObject().apply {
                                    put("text", prompt)
                                },
                            ),
                        )
                    },
                ),
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${Uri.encode(trimmedKey)}"
        val responseJson = try {
            postJson(url, requestBody)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return@withContext null

        val rawText = responseJson
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext null

        val cleaned = stripMarkdownFences(rawText).trim()
        if (cleaned.isBlank()) return@withContext null

        val translationsByIndex = mutableMapOf<Int, String>()
        cleaned.lines().forEach { rawLine ->
            val sep = rawLine.indexOf('|')
            if (sep <= 0) return@forEach
            val index = rawLine.substring(0, sep).trim().toIntOrNull() ?: return@forEach
            val translation = rawLine.substring(sep + 1).trim()
            if (index in lyrics.lines.indices && translation.isNotBlank()) {
                translationsByIndex[index] = translation
            }
        }
        if (translationsByIndex.isEmpty()) return@withContext null

        val updatedLines = lyrics.lines.mapIndexed { index, line ->
            val translation = translationsByIndex[index]
            if (translation != null) line.copy(translatedText = translation) else line
        }
        val updated = lyrics.copy(lines = updatedLines, translatedLanguage = targetLanguage)
        writeDiskCache(track.id, updated)
        cache[track.id] = CachedLyrics(updated, System.currentTimeMillis())
        updated
    }

    override suspend fun deleteLyrics(trackId: Long) {
        withContext(ioDispatcher) {
            cache.remove(trackId)
            runCatching {
                if (!cacheDir.exists()) cacheDir.mkdirs()
                File(cacheDir, "$trackId.json").writeText(
                    JSONObject().put("deleted", true).toString(),
                    Charsets.UTF_8,
                )
            }
        }
    }

    override suspend fun getSyncOffsetMs(trackId: Long): Long = withContext(ioDispatcher) {
        offsetsPrefs.getLong(trackId.toString(), 0L)
    }

    override suspend fun setSyncOffsetMs(trackId: Long, offsetMs: Long) {
        withContext(ioDispatcher) {
            offsetsPrefs.edit().putLong(trackId.toString(), offsetMs).apply()
        }
    }

    private fun readDiskCache(trackId: Long): DiskCacheEntry? {
        val file = File(cacheDir, "$trackId.json")
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            if (json.optBoolean("deleted", false)) {
                DiskCacheEntry.Deleted
            } else {
                deserializeLyrics(json)?.let { DiskCacheEntry.Found(it) }
            }
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
        if (!lyrics.translatedLanguage.isNullOrBlank()) {
            put("translatedLanguage", lyrics.translatedLanguage)
        }
        put(
            "lines",
            JSONArray().apply {
                lyrics.lines.forEach { line ->
                    put(
                        JSONObject().apply {
                            put("text", line.text)
                            if (line.startMs != null) put("startMs", line.startMs)
                            if (line.endMs != null) put("endMs", line.endMs)
                            if (!line.translatedText.isNullOrBlank()) {
                                put("translatedText", line.translatedText)
                            }
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
        val translatedLanguage = json.optString("translatedLanguage").takeUnless { it.isBlank() }
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
            val translatedText = lineObj.optString("translatedText").takeUnless { it.isBlank() }
            if (words.isEmpty()) {
                LyricsParser.parseInlineWordLine(text, startMs, endMs)?.let { repaired ->
                    return@mapNotNull repaired.copy(translatedText = translatedText)
                }
            }
            LyricLine(
                text = text,
                startMs = startMs,
                endMs = endMs,
                words = words,
                translatedText = translatedText,
            )
        }
        if (!instrumental && lines.isEmpty()) return null
        val resolvedSyncType = if (lines.any { it.words.isNotEmpty() }) {
            LyricsSyncType.WORD
        } else {
            syncType
        }
        return TrackLyrics(
            lines = lines,
            source = source,
            syncType = resolvedSyncType,
            instrumental = instrumental,
            translatedLanguage = translatedLanguage,
        )
    }

    private fun fetchFromBetterLyrics(track: Track): TrackLyrics? {
        if (track.title.isBlank() || track.artist.isBlank()) return null
        val durationSeconds = (track.durationMs / 1_000L).toInt().takeIf { it in 1..3_600 }
        val uri = Uri.parse("https://lyrics-api.boidu.dev/getLyrics").buildUpon()
            .appendQueryParameter("s", track.title)
            .appendQueryParameter("a", track.artist)
            .apply {
                durationSeconds?.let { appendQueryParameter("d", it.toString()) }
                track.album.takeIf { it.isNotBlank() && !it.equals("Unknown album", true) }
                    ?.let { appendQueryParameter("al", it) }
            }
            .build()
        val json = requestJson(uri.toString()) ?: return null
        if (json.optBoolean("error")) return null
        if (json.optBoolean("instrumental")) {
            return TrackLyrics(emptyList(), LyricsSource.BETTER_LYRICS, LyricsSyncType.PLAIN, instrumental = true)
        }
        val ttml = json.optString("ttml").takeUnless { it == "null" || it.isBlank() } ?: return null
        return LyricsParser.fromTtml(ttml, track.durationMs)
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

    private fun stripMarkdownFences(text: String): String {
        val lines = text.lines()
        val withoutStart = if (lines.firstOrNull()?.trim()?.startsWith("```") == true) {
            lines.drop(1)
        } else {
            lines
        }
        val withoutEnd = if (withoutStart.lastOrNull()?.trim() == "```") {
            withoutStart.dropLast(1)
        } else {
            withoutStart
        }
        return withoutEnd.joinToString("\n")
    }

    private fun postJson(url: String, body: JSONObject): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = GEMINI_CONNECT_TIMEOUT_MS
            readTimeout = GEMINI_READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
            }
            val status = connection.responseCode
            if (status !in 200..299) return null
            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            runCatching { JSONObject(responseText) }.getOrNull()
        } finally {
            connection.disconnect()
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
        const val GEMINI_CONNECT_TIMEOUT_MS = 15_000
        const val GEMINI_READ_TIMEOUT_MS = 90_000
        const val MAX_GEMINI_INLINE_BYTES = 15 * 1024 * 1024
        const val CACHE_TTL_MS = 6 * 60 * 60 * 1_000L
        const val MISS_CACHE_TTL_MS = 10 * 60 * 1_000L
        const val MIN_MATCH_SCORE = 75
        const val USER_AGENT = "Nota/0.3.0 (https://github.com/XelilovTh/Nota)"
    }
}
