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
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val translationMutex = Mutex()
    private val lastRemoteTranslateAtMs = AtomicLong(0L)
    private val inFlightTranslations = ConcurrentHashMap<String, CompletableDeferred<TrackLyrics?>>()

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

        val result = fetchOnlineLyricsInternal(track)
        if (result != null) {
            writeDiskCache(track.id, result)
        }
        cache[track.id] = CachedLyrics(result, now)
        result
    }

    override suspend fun fetchOnlineLyrics(track: Track): TrackLyrics? = withContext(ioDispatcher) {
        cache.remove(track.id)
        runCatching { File(cacheDir, "${track.id}.json").delete() }
        val result = fetchOnlineLyricsInternal(track)
        if (result != null) {
            writeDiskCache(track.id, result)
        }
        cache[track.id] = CachedLyrics(result, System.currentTimeMillis())
        result
    }

    private suspend fun fetchOnlineLyricsInternal(track: Track): TrackLyrics? {
        val cleanTrack = normalizedSearchTrack(track)
        val candidates = if (cleanTrack != track) listOf(cleanTrack, track) else listOf(track)
        var plainFallback: TrackLyrics? = null

        for (candidate in candidates) {
            val betterLyrics = try {
                fetchFromBetterLyrics(candidate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (betterLyrics != null) {
                if (betterLyrics.instrumental || betterLyrics.syncType != LyricsSyncType.PLAIN) {
                    return betterLyrics
                }
                if (plainFallback == null) plainFallback = betterLyrics
            }

            val lrclib = try {
                fetchFromLrclib(candidate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (lrclib != null) {
                if (lrclib.instrumental || lrclib.syncType != LyricsSyncType.PLAIN) {
                    return lrclib
                }
                if (plainFallback == null) plainFallback = lrclib
            }
        }

        return plainFallback ?: candidates.firstNotNullOfOrNull { candidate ->
            try {
                fetchFromLyricsOvh(candidate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun normalizedSearchTrack(track: Track): Track {
        val cleanAlbum = if (
            track.album.equals("Online", ignoreCase = true) ||
            track.album.equals("Unknown album", ignoreCase = true)
        ) {
            ""
        } else {
            track.album
        }
        val cleanArtist = track.artist
            .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
            .trim()
        var cleanTitle = track.title
            .replace(
                Regex(
                    "\\s*[\\(\\[]\\s*(?:official\\s*(?:music\\s*)?(?:video|audio|lyric(?:s)?)|lyric(?:s)?\\s*video|music\\s*from[^\\)\\]]*|sped\\s*up|slowed[^\\)\\]]*|audio|hd|4k)\\s*[\\)\\]]",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(Regex("\\s+"), " ")
            .trim()
        val dashIdx = cleanTitle.indexOf(" - ")
        if (dashIdx > 0 && dashIdx < cleanTitle.length - 3) {
            val left = cleanTitle.substring(0, dashIdx).trim()
            val right = cleanTitle.substring(dashIdx + 3).trim()
            if (cleanArtist.contains(left, ignoreCase = true) || left.contains(cleanArtist, ignoreCase = true)) {
                cleanTitle = right
            }
        }
        return track.copy(
            title = cleanTitle.ifBlank { track.title },
            artist = cleanArtist.ifBlank { track.artist },
            album = cleanAlbum,
        )
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
        if (lyrics.lines.isEmpty()) return@withContext null

        resolveCachedTranslation(lyrics, targetLanguage)?.let { cached ->
            cache[track.id] = CachedLyrics(cached, System.currentTimeMillis())
            return@withContext cached
        }

        val flightKey = "${track.id}:$targetLanguage"
        val newDeferred = CompletableDeferred<TrackLyrics?>()
        val existingDeferred = inFlightTranslations.putIfAbsent(flightKey, newDeferred)
        if (existingDeferred != null) {
            return@withContext existingDeferred.await()
        }

        try {
            val result = translationMutex.withLock {
                val latestLyrics = cache[track.id]?.value ?: lyrics
                resolveCachedTranslation(latestLyrics, targetLanguage)?.let { cached ->
                    return@withLock cached
                }

                enforceTranslationThrottle()

                val translationsByIndex = translateWithOnlineFallbacks(latestLyrics, targetLanguage)

                if (translationsByIndex.isNullOrEmpty()) {
                    return@withLock null
                }

                val updatedLines = latestLyrics.lines.mapIndexed { index, line ->
                    val translation = translationsByIndex[index]
                    if (translation != null) {
                        line.copy(
                            translatedText = translation,
                            translations = line.translations + (targetLanguage to translation),
                        )
                    } else {
                        line
                    }
                }
                val updated = latestLyrics.copy(lines = updatedLines, translatedLanguage = targetLanguage)
                writeDiskCache(track.id, updated)
                cache[track.id] = CachedLyrics(updated, System.currentTimeMillis())
                updated
            }
            newDeferred.complete(result)
            result
        } catch (cancelled: CancellationException) {
            newDeferred.cancel(cancelled)
            throw cancelled
        } catch (t: Throwable) {
            newDeferred.complete(null)
            throw t
        } finally {
            inFlightTranslations.remove(flightKey, newDeferred)
        }
    }

    private fun resolveCachedTranslation(lyrics: TrackLyrics, targetLanguage: String): TrackLyrics? {
        val hasMatch = lyrics.lines.any { !it.translations[targetLanguage].isNullOrBlank() } ||
            (lyrics.translatedLanguage == targetLanguage && lyrics.lines.any { !it.translatedText.isNullOrBlank() })
        if (!hasMatch) return null
        val cachedLines = lyrics.lines.map { line ->
            val cachedText = line.translations[targetLanguage]
                ?: line.translatedText.takeIf { lyrics.translatedLanguage == targetLanguage }
            val merged = if (cachedText != null) line.translations + (targetLanguage to cachedText) else line.translations
            line.copy(translatedText = cachedText, translations = merged)
        }
        return lyrics.copy(lines = cachedLines, translatedLanguage = targetLanguage)
    }

    private suspend fun enforceTranslationThrottle() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRemoteTranslateAtMs.get()
        if (elapsed in 0 until TRANSLATION_MIN_INTERVAL_MS) {
            delay(TRANSLATION_MIN_INTERVAL_MS - elapsed)
        }
        lastRemoteTranslateAtMs.set(System.currentTimeMillis())
    }

    private suspend fun translateWithOnlineFallbacks(
        lyrics: TrackLyrics,
        targetLanguage: String,
    ): Map<Int, String>? {
        val targetCode = toIsoLanguageCode(targetLanguage)
        val indexedNonBlank = lyrics.lines.mapIndexedNotNull { index, line ->
            line.text.trim().takeIf { it.isNotBlank() }?.let { index to it }
        }
        if (indexedNonBlank.isEmpty()) return null

        val chunks = mutableListOf<List<Pair<Int, String>>>()
        var currentChunk = mutableListOf<Pair<Int, String>>()
        var currentLength = 0
        for (entry in indexedNonBlank) {
            val lineLen = entry.second.length + 1
            if (currentChunk.isNotEmpty() && currentLength + lineLen > TRANSLATION_CHUNK_MAX_CHARS) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
                currentLength = 0
            }
            currentChunk.add(entry)
            currentLength += lineLen
        }
        if (currentChunk.isNotEmpty()) chunks.add(currentChunk)

        val result = mutableMapOf<Int, String>()
        for ((chunkIndex, chunk) in chunks.withIndex()) {
            if (chunkIndex > 0) delay(TRANSLATION_CHUNK_DELAY_MS)
            val translatedChunk = translateChunkWithFallbacks(chunk.map { it.second }, targetCode)
            if (translatedChunk != null && translatedChunk.size == chunk.size) {
                chunk.forEachIndexed { idx, (lineIndex, _) ->
                    val translated = translatedChunk[idx].trim()
                    if (translated.isNotBlank()) {
                        result[lineIndex] = translated
                    }
                }
            } else {
                for ((itemIdx, pair) in chunk.withIndex()) {
                    if (itemIdx > 0) delay(TRANSLATION_SINGLE_LINE_DELAY_MS)
                    val single = translateSingleWithFallbacks(pair.second, targetCode)
                    if (!single.isNullOrBlank()) {
                        result[pair.first] = single.trim()
                    }
                }
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun translateChunkWithFallbacks(lines: List<String>, targetCode: String): List<String>? {
        val joined = lines.joinToString("\n")
        val gtx = translateViaGoogleGtx(joined, targetCode)?.split('\n')
        if (gtx != null && gtx.size == lines.size) return gtx

        val lingva = translateViaLingva(joined, targetCode)?.split('\n')
        if (lingva != null && lingva.size == lines.size) return lingva

        return null
    }

    private fun translateSingleWithFallbacks(text: String, targetCode: String): String? {
        return translateViaGoogleGtx(text, targetCode)
            ?: translateViaLingva(text, targetCode)
            ?: translateViaMyMemory(text, targetCode)
    }

    private fun translateViaGoogleGtx(text: String, targetCode: String): String? {
        val uri = Uri.parse("https://translate.googleapis.com/translate_a/single").buildUpon()
            .appendQueryParameter("client", "gtx")
            .appendQueryParameter("sl", "auto")
            .appendQueryParameter("tl", targetCode)
            .appendQueryParameter("dt", "t")
            .appendQueryParameter("q", text)
            .build()
        val array = try {
            requestArray(uri.toString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return null
        val segments = array.optJSONArray(0) ?: return null
        val builder = StringBuilder()
        for (i in 0 until segments.length()) {
            val part = segments.optJSONArray(i)?.optString(0) ?: continue
            builder.append(part)
        }
        return builder.toString().trim().takeIf { it.isNotBlank() }
    }

    private fun translateViaLingva(text: String, targetCode: String): String? {
        val encodedText = Uri.encode(text)
        for (baseUrl in LINGVA_INSTANCES) {
            val url = "$baseUrl/api/v1/auto/$targetCode/$encodedText"
            val json = try {
                requestJson(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: continue
            val translated = json.optString("translation").trim()
            if (translated.isNotBlank()) return translated
        }
        return null
    }

    private fun translateViaMyMemory(text: String, targetCode: String): String? {
        val sourceCode = detectSourceLangCode(text, targetCode)
        val uri = Uri.parse("https://api.mymemory.translated.net/get").buildUpon()
            .appendQueryParameter("q", text)
            .appendQueryParameter("langpair", "$sourceCode|$targetCode")
            .build()
        val json = try {
            requestJson(uri.toString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return null
        val responseData = json.optJSONObject("responseData") ?: return null
        val translated = responseData.optString("translatedText").trim()
        return translated.takeIf { it.isNotBlank() }
    }

    private fun toIsoLanguageCode(language: String): String = when (language.trim().lowercase()) {
        "english", "en" -> "en"
        "russian", "ru" -> "ru"
        "azerbaijani", "az" -> "az"
        "turkish", "tr" -> "tr"
        else -> "az"
    }

    private fun detectSourceLangCode(text: String, targetCode: String): String {
        if (text.any { it in '\u0400'..'\u04FF' }) return "ru"
        if (text.any { it == 'ə' || it == 'Ə' }) return "az"
        if (text.any { it in "ğĞşŞıİöÖüÜçÇ" }) {
            return if (targetCode == "tr") "az" else "tr"
        }
        return if (targetCode == "en") "az" else "en"
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
                            if (line.translations.isNotEmpty()) {
                                put(
                                    "translations",
                                    JSONObject().apply {
                                        line.translations.forEach { (lang, value) ->
                                            if (value.isNotBlank()) put(lang, value)
                                        }
                                    },
                                )
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
            val translationsMap = mutableMapOf<String, String>()
            lineObj.optJSONObject("translations")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val lang = keys.next()
                    val value = obj.optString(lang).takeUnless { it.isBlank() }
                    if (value != null) translationsMap[lang] = value
                }
            }
            if (translatedLanguage != null && translatedText != null && !translationsMap.containsKey(translatedLanguage)) {
                translationsMap[translatedLanguage] = translatedText
            }
            if (words.isEmpty()) {
                LyricsParser.parseInlineWordLine(text, startMs, endMs)?.let { repaired ->
                    return@mapNotNull repaired.copy(
                        translatedText = translatedText,
                        translations = translationsMap,
                    )
                }
            }
            LyricLine(
                text = text,
                startMs = startMs,
                endMs = endMs,
                words = words,
                translatedText = translatedText,
                translations = translationsMap,
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
                track.album.takeIf {
                    it.isNotBlank() &&
                        !it.equals("Unknown album", true) &&
                        !it.equals("Online", true)
                }?.let { appendQueryParameter("al", it) }
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
                track.album.takeIf {
                    it.isNotBlank() &&
                        !it.equals("Unknown album", true) &&
                        !it.equals("Online", true)
                }?.let { appendQueryParameter("album_name", it) }
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
                track.album.takeIf {
                    it.isNotBlank() &&
                        !it.equals("Unknown album", true) &&
                        !it.equals("Online", true)
                }?.let { appendQueryParameter("album_name", it) }
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
        const val TRANSLATION_MIN_INTERVAL_MS = 600L
        const val TRANSLATION_CHUNK_MAX_CHARS = 900
        const val TRANSLATION_CHUNK_DELAY_MS = 200L
        const val TRANSLATION_SINGLE_LINE_DELAY_MS = 120L
        val LINGVA_INSTANCES = listOf("https://lingva.ml", "https://lingva.lunar.icu")
        const val USER_AGENT = "Nota/0.3.0 (https://github.com/XelilovTh/Nota)"
    }
}
