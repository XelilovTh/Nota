package com.xelilovth.nota.data.lyrics

import com.xelilovth.nota.domain.model.LyricLine
import com.xelilovth.nota.domain.model.LyricWord
import com.xelilovth.nota.domain.model.LyricsSource
import com.xelilovth.nota.domain.model.LyricsSyncType
import com.xelilovth.nota.domain.model.TrackLyrics
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

internal object LyricsParser {
    private val lrcTimestamp = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]\s*(.*)""")
    private val inlineWordTimestamp = Regex("""[<\[](\d{1,2}):(\d{2})(?:\.(\d{1,3}))?[>\]]""")
    private val leadingPlusMarker = Regex("""^\+(?:\s+|$)""")

    private fun cleanLineText(raw: String): String =
        raw.replace(inlineWordTimestamp, " ")
            .trimStart()
            .replace(leadingPlusMarker, "")
            .trim()

    internal fun parseInlineWordLine(
        body: String,
        lineStartMs: Long?,
        lineEndMs: Long?,
    ): LyricLine? {
        val matches = inlineWordTimestamp.findAll(body).toList()
        if (matches.isEmpty()) return null

        val rawWords = mutableListOf<LyricWord>()
        val prefix = cleanLineText(body.substring(0, matches.first().range.first))
        if (prefix.isNotBlank() && lineStartMs != null) {
            rawWords.add(LyricWord(text = prefix, startMs = lineStartMs))
        }

        for (i in matches.indices) {
            val match = matches[i]
            val timestampMs = parseTimestampMs(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3],
            ) ?: continue
            val segmentEnd = matches.getOrNull(i + 1)?.range?.first ?: body.length
            val wordText = cleanLineText(body.substring(match.range.last + 1, segmentEnd))
            if (wordText.isNotBlank()) {
                rawWords.add(LyricWord(text = wordText, startMs = timestampMs))
            }
        }

        if (rawWords.isEmpty()) return null

        val words = rawWords.mapIndexed { index, word ->
            val wordEndMs = (rawWords.getOrNull(index + 1)?.startMs ?: lineEndMs)
                ?.takeIf { it > word.startMs }
            word.copy(endMs = wordEndMs)
        }
        val text = words.joinToString(" ") { it.text }
        if (text.isBlank()) return null

        return LyricLine(
            text = text,
            startMs = lineStartMs ?: words.firstOrNull()?.startMs,
            endMs = lineEndMs,
            words = words,
        )
    }

    private fun parseTimestampMs(minutesStr: String, secondsStr: String, fractionStr: String): Long? {
        val minutes = minutesStr.toLongOrNull() ?: return null
        val seconds = secondsStr.toLongOrNull() ?: return null
        val millis = fractionStr.takeIf(String::isNotEmpty)
            ?.padEnd(3, '0')
            ?.take(3)
            ?.toLongOrNull() ?: 0L
        return minutes * 60_000L + seconds * 1_000L + millis
    }

    fun fromLrc(value: String, durationMs: Long): List<LyricLine> {
        val parsed = value.lineSequence().mapNotNull { raw ->
            val match = lrcTimestamp.matchEntire(raw.trim()) ?: return@mapNotNull null
            val startMs = parseTimestampMs(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3],
            ) ?: return@mapNotNull null
            val rawBody = match.groupValues[4]
            parseInlineWordLine(rawBody, startMs, null) ?: run {
                val text = cleanLineText(rawBody)
                if (text.isBlank()) return@mapNotNull null
                LyricLine(
                    text = text,
                    startMs = startMs,
                )
            }
        }.sortedBy { it.startMs }.toList()
        return parsed.mapIndexed { index, line ->
            val lineEndMs = parsed.getOrNull(index + 1)?.startMs ?: durationMs.takeIf { it > 0L }
            val normalizedWords = line.words.mapIndexed { wordIndex, word ->
                val wordEndMs = (line.words.getOrNull(wordIndex + 1)?.startMs ?: lineEndMs)
                    ?.takeIf { it > word.startMs }
                word.copy(endMs = wordEndMs)
            }
            line.copy(endMs = lineEndMs, words = normalizedWords)
        }
    }

    fun fromPlain(value: String): List<LyricLine> = value
        .lineSequence()
        .map(::cleanLineText)
        .filter(String::isNotBlank)
        .map { LyricLine(it) }
        .toList()

    fun fromLyricsFile(value: String, durationMs: Long = 0L): List<LyricLine> {
        val options = LoaderOptions().apply {
            maxAliasesForCollections = 20
            codePointLimit = 1_000_000
        }
        val root = Yaml(SafeConstructor(options)).load<Any?>(value) as? Map<*, *> ?: return emptyList()
        val rawLines = root["lines"] as? List<*> ?: return emptyList()
        val parsed = rawLines.mapNotNull { rawLine ->
            val line = rawLine as? Map<*, *> ?: return@mapNotNull null
            val text = (line["text"] as? String)?.trimEnd() ?: return@mapNotNull null
            if (text.isBlank()) return@mapNotNull null
            val rawWords = (line["words"] as? List<*>).orEmpty()
            val parsedWords = rawWords.mapNotNull { rawWord ->
                val word = rawWord as? Map<*, *> ?: return@mapNotNull null
                val wordText = word["text"] as? String ?: return@mapNotNull null
                val startMs = (word["start_ms"] as? Number)?.toLong()?.takeIf { it >= 0L }
                    ?: return@mapNotNull null
                LyricWord(
                    text = wordText,
                    startMs = startMs,
                    endMs = (word["end_ms"] as? Number)?.toLong()?.takeIf { it > startMs },
                )
            }
            // Keep the lyric's original word order and full text; malformed timing falls back to line sync.
            val words = parsedWords.takeIf { list ->
                list.size == rawWords.size &&
                    list.zipWithNext().all { (current, next) -> next.startMs >= current.startMs }
            }.orEmpty()
            val lineStart = (line["start_ms"] as? Number)?.toLong()?.takeIf { it >= 0L }
                ?: words.firstOrNull()?.startMs
            LyricLine(
                text = text,
                startMs = lineStart,
                endMs = (line["end_ms"] as? Number)?.toLong()?.takeIf { lineStart == null || it > lineStart },
                words = words,
            )
        }.sortedBy { it.startMs ?: Long.MAX_VALUE }

        return parsed.mapIndexed { index, line ->
            val nextLineStart = parsed.getOrNull(index + 1)?.startMs
            val lineEndCandidates = listOfNotNull(
                line.endMs,
                nextLineStart,
                durationMs.takeIf { it > 0L },
            ).filter { line.startMs == null || it > line.startMs }
            val lineEnd = lineEndCandidates.minOrNull()
            val normalizedWords = line.words.mapIndexed { wordIndex, word ->
                val nextWordStart = line.words.getOrNull(wordIndex + 1)?.startMs
                val wordEndCandidates = listOfNotNull(word.endMs, nextWordStart, lineEnd)
                    .filter { it > word.startMs }
                word.copy(endMs = wordEndCandidates.minOrNull())
            }
            line.copy(endMs = lineEnd?.takeIf { line.startMs == null || it > line.startMs }, words = normalizedWords)
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
        if (targetDurationSeconds > 0 && durationSeconds > 0) {
            val diff = kotlin.math.abs(durationSeconds - targetDurationSeconds)
            score += when {
                diff <= 1 -> 20
                diff <= 2 -> 14
                diff <= 4 -> 8
                diff <= 8 -> 2
                diff > 12 -> -15
                else -> 0
            }
        }
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
            val wordLines = runCatching { fromLyricsFile(lyricsFile, durationMs) }.getOrDefault(emptyList())
            if (wordLines.any { it.words.isNotEmpty() }) {
                return TrackLyrics(wordLines, source, LyricsSyncType.WORD)
            }
        }
        if (!syncedLyrics.isNullOrBlank()) {
            val syncedLines = fromLrc(syncedLyrics, durationMs)
            if (syncedLines.isNotEmpty()) {
                val syncType = if (syncedLines.any { it.words.isNotEmpty() }) {
                    LyricsSyncType.WORD
                } else {
                    LyricsSyncType.LINE
                }
                return TrackLyrics(syncedLines, source, syncType)
            }
        }
        if (!plainLyrics.isNullOrBlank()) {
            val plainLines = fromPlain(plainLyrics)
            if (plainLines.isNotEmpty()) return TrackLyrics(plainLines, source, LyricsSyncType.PLAIN)
        }
        return null
    }

    fun fromTtml(ttml: String, durationMs: Long = 0L): TrackLyrics? {
        if (ttml.isBlank()) return null
        val document = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                isNamespaceAware = false
                isExpandEntityReferences = false
            }
            factory.newDocumentBuilder().parse(InputSource(StringReader(ttml)))
        }.getOrNull() ?: return null

        val root = document.documentElement ?: return null
        val body = findFirstElementByLocalName(root, "body") ?: root
        val pElements = collectElementsByLocalName(body, "p")

        val parsed = pElements.mapNotNull { p ->
            val pStart = parseTtmlTimeMs(p.getAttribute("begin"))
            val pEnd = parseTtmlTimeMs(p.getAttribute("end"))

            val spanElements = mutableListOf<Element>()
            collectTimedSpans(p, spanElements)
            val words = spanElements.mapNotNull { span ->
                val wStart = parseTtmlTimeMs(span.getAttribute("begin")) ?: return@mapNotNull null
                val wEnd = parseTtmlTimeMs(span.getAttribute("end"))?.takeIf { it > wStart }
                val cleanWord = span.textContent.trim()
                if (cleanWord.isBlank()) return@mapNotNull null
                LyricWord(text = cleanWord, startMs = wStart, endMs = wEnd)
            }

            val rawLineText = if (words.isNotEmpty()) {
                words.joinToString(" ") { it.text.trim() }
            } else {
                p.textContent.trim()
            }
            val lineText = cleanLineText(rawLineText)
            if (lineText.isBlank()) return@mapNotNull null

            val lineStart = pStart ?: words.firstOrNull()?.startMs
            val lineEnd = (pEnd ?: words.lastOrNull()?.endMs)
                ?.takeIf { lineStart == null || it > lineStart }

            LyricLine(
                text = lineText,
                startMs = lineStart,
                endMs = lineEnd,
                words = words,
            )
        }.sortedBy { it.startMs ?: Long.MAX_VALUE }

        if (parsed.isEmpty()) return null

        val lines = parsed.mapIndexed { index, line ->
            val nextLineStart = parsed.getOrNull(index + 1)?.startMs
            val inferredEnd = line.endMs
                ?: nextLineStart?.takeIf { line.startMs == null || it > line.startMs }
                ?: durationMs.takeIf { it > 0L && (line.startMs == null || it > line.startMs) }
            line.copy(endMs = inferredEnd)
        }

        val syncType = when {
            lines.any { it.words.isNotEmpty() } -> LyricsSyncType.WORD
            lines.any { it.startMs != null } -> LyricsSyncType.LINE
            else -> LyricsSyncType.PLAIN
        }
        return TrackLyrics(lines, LyricsSource.BETTER_LYRICS, syncType)
    }

    internal fun parseTtmlTimeMs(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if (value.endsWith("s", ignoreCase = true) && !value.endsWith("ms", ignoreCase = true)) {
            val seconds = value.dropLast(1).toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
            return (seconds * 1_000.0).roundToLong()
        }
        if (':' in value) {
            val parts = value.split(':')
            return when (parts.size) {
                2 -> {
                    val minutes = parts[0].toLongOrNull()?.takeIf { it >= 0L } ?: return null
                    val seconds = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
                    minutes * 60_000L + (seconds * 1_000.0).roundToLong()
                }
                3 -> {
                    val hours = parts[0].toLongOrNull()?.takeIf { it >= 0L } ?: return null
                    val minutes = parts[1].toLongOrNull()?.takeIf { it >= 0L } ?: return null
                    val seconds = parts[2].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
                    (hours * 3_600L + minutes * 60L) * 1_000L + (seconds * 1_000.0).roundToLong()
                }
                else -> null
            }
        }
        val seconds = value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        return (seconds * 1_000.0).roundToLong()
    }

    private fun localTagName(element: Element): String =
        element.localName ?: element.tagName.substringAfter(':')

    private fun findFirstElementByLocalName(root: Element, target: String): Element? {
        if (localTagName(root) == target) return root
        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            findFirstElementByLocalName(child, target)?.let { return it }
        }
        return null
    }

    private fun collectElementsByLocalName(root: Element, target: String): List<Element> {
        val result = mutableListOf<Element>()
        fun visit(node: Node) {
            val children = node.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                if (localTagName(child) == target) {
                    result.add(child)
                } else {
                    visit(child)
                }
            }
        }
        visit(root)
        return result
    }

    private fun collectTimedSpans(parent: Node, out: MutableList<Element>) {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            val role = child.getAttribute("ttm:role").ifBlank { child.getAttribute("role") }
            if (role.contains("bg", ignoreCase = true) || role.contains("background", ignoreCase = true)) {
                continue
            }
            if (localTagName(child) == "span" && child.hasAttribute("begin")) {
                out.add(child)
            } else {
                collectTimedSpans(child, out)
            }
        }
    }
}
