package com.xelilovth.nota.data.lyrics

import com.xelilovth.nota.domain.model.LyricLine
import com.xelilovth.nota.domain.model.LyricWord
import com.xelilovth.nota.domain.model.LyricsSource
import com.xelilovth.nota.domain.model.LyricsSyncType
import com.xelilovth.nota.domain.model.TrackLyrics
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

internal object LyricsParser {
    private val lrcTimestamp = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]\s*(.*)""")

    fun fromLrc(value: String, durationMs: Long): List<LyricLine> {
        val parsed = value.lineSequence().mapNotNull { raw ->
            val match = lrcTimestamp.matchEntire(raw.trim()) ?: return@mapNotNull null
            val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val fraction = match.groupValues[3]
            val millis = fraction.takeIf(String::isNotEmpty)
                ?.padEnd(3, '0')
                ?.take(3)
                ?.toLongOrNull() ?: 0L
            val text = match.groupValues[4].trim()
            if (text.isBlank()) return@mapNotNull null
            LyricLine(
                text = text,
                startMs = minutes * 60_000L + seconds * 1_000L + millis,
            )
        }.sortedBy { it.startMs }
        return parsed.mapIndexed { index, line ->
            line.copy(endMs = parsed.getOrNull(index + 1)?.startMs ?: durationMs.takeIf { it > 0L })
        }
    }

    fun fromPlain(value: String): List<LyricLine> = value
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .map(::LyricLine)
        .toList()

    fun fromLyricsFile(value: String): List<LyricLine> {
        val options = LoaderOptions().apply {
            maxAliasesForCollections = 20
            codePointLimit = 1_000_000
        }
        val root = Yaml(SafeConstructor(options)).load<Any?>(value) as? Map<*, *> ?: return emptyList()
        val rawLines = root["lines"] as? List<*> ?: return emptyList()
        return rawLines.mapNotNull { rawLine ->
            val line = rawLine as? Map<*, *> ?: return@mapNotNull null
            val text = line["text"] as? String ?: return@mapNotNull null
            val words = (line["words"] as? List<*>).orEmpty().mapNotNull { rawWord ->
                val word = rawWord as? Map<*, *> ?: return@mapNotNull null
                val wordText = word["text"] as? String ?: return@mapNotNull null
                val startMs = (word["start_ms"] as? Number)?.toLong() ?: return@mapNotNull null
                LyricWord(
                    text = wordText,
                    startMs = startMs,
                    endMs = (word["end_ms"] as? Number)?.toLong(),
                )
            }
            LyricLine(
                text = text,
                startMs = (line["start_ms"] as? Number)?.toLong(),
                endMs = (line["end_ms"] as? Number)?.toLong(),
                words = words,
            )
        }
    }

    fun normalize(value: String): String = value
        .lowercase()
        .filter(Char::isLetterOrDigit)

    fun matchScore(
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int,
        targetTitle: String,
        targetArtist: String,
        targetAlbum: String,
        targetDurationSeconds: Int,
    ): Int {
        val candidateTitle = normalize(title)
        val candidateArtist = normalize(artist)
        val candidateAlbum = normalize(album)
        val expectedTitle = normalize(targetTitle)
        val expectedArtist = normalize(targetArtist)
        val expectedAlbum = normalize(targetAlbum)
        if (candidateTitle.isBlank() || expectedTitle.isBlank() || candidateArtist.isBlank() || expectedArtist.isBlank()) {
            return 0
        }
        var score = 0
        score += when {
            candidateTitle == expectedTitle -> 55
            candidateTitle.contains(expectedTitle) || expectedTitle.contains(candidateTitle) -> 35
            else -> 0
        }
        score += when {
            candidateArtist == expectedArtist -> 35
            candidateArtist.contains(expectedArtist) || expectedArtist.contains(candidateArtist) -> 20
            else -> 0
        }
        if (expectedAlbum.isNotBlank() && candidateAlbum == expectedAlbum) score += 5
        if (targetDurationSeconds > 0 && kotlin.math.abs(durationSeconds - targetDurationSeconds) <= 3) score += 5
        return score
    }

    fun buildTrackLyrics(
        plainLyrics: String?,
        syncedLyrics: String?,
        lyricsFile: String?,
        hasWordSync: Boolean,
        durationMs: Long,
        source: LyricsSource,
    ): TrackLyrics? {
        if (hasWordSync && !lyricsFile.isNullOrBlank()) {
            val wordLines = runCatching { fromLyricsFile(lyricsFile) }.getOrDefault(emptyList())
            if (wordLines.any { it.words.isNotEmpty() }) {
                return TrackLyrics(wordLines, source, LyricsSyncType.WORD)
            }
        }
        if (!syncedLyrics.isNullOrBlank()) {
            val syncedLines = fromLrc(syncedLyrics, durationMs)
            if (syncedLines.isNotEmpty()) return TrackLyrics(syncedLines, source, LyricsSyncType.LINE)
        }
        if (!plainLyrics.isNullOrBlank()) {
            val plainLines = fromPlain(plainLyrics)
            if (plainLines.isNotEmpty()) return TrackLyrics(plainLines, source, LyricsSyncType.PLAIN)
        }
        return null
    }
}
