package com.xelilovth.nota.data.lyrics

import com.xelilovth.nota.domain.model.LyricsSource
import com.xelilovth.nota.domain.model.LyricsSyncType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {
    @Test
    fun parsesLrcTimestampsAndInfersLineEndTimes() {
        val lines = LyricsParser.fromLrc(
            """[00:01.23]First line
                |[00:02.345]Second line""".trimMargin(),
            durationMs = 5_000L,
        )

        assertEquals(2, lines.size)
        assertEquals("First line", lines[0].text)
        assertEquals(1_230L, lines[0].startMs)
        assertEquals(2_345L, lines[0].endMs)
        assertEquals(5_000L, lines[1].endMs)
    }

    @Test
    fun parsesWordTimedLyricsFile() {
        val lyricsFile = """version: '1.0'
            |metadata:
            |  title: Sample
            |lines:
            |  - text: Hello world
            |    start_ms: 1000
            |    end_ms: 2200
            |    words:
            |      - text: 'Hello '
            |        start_ms: 1000
            |        end_ms: 1500
            |      - text: world
            |        start_ms: 1500
            |        end_ms: 2200
            |plain: |
            |  Hello world
            |""".trimMargin()

        val lines = LyricsParser.fromLyricsFile(lyricsFile)

        assertEquals(1, lines.size)
        assertEquals("Hello world", lines.single().text)
        assertEquals(2, lines.single().words.size)
        assertEquals(1_500L, lines.single().words[1].startMs)
    }

    @Test
    fun sortsLyricsFileLinesAndInfersEndTimesWithoutChangingWordOrder() {
        val lyricsFile = """version: '1.0'
            |lines:
            |  - text: Second line
            |    start_ms: 2000
            |    words:
            |      - text: 'Hello '
            |        start_ms: 2000
            |      - text: world
            |        start_ms: 2400
            |  - text: First line
            |    start_ms: 1000
            |    words:
            |      - text: 'First '
            |        start_ms: 1000
            |      - text: line
            |        start_ms: 1500
            |""".trimMargin()

        val lines = LyricsParser.fromLyricsFile(lyricsFile, durationMs = 3_000L)

        assertEquals(listOf("First line", "Second line"), lines.map { it.text })
        assertEquals(1_000L, lines[0].words[0].startMs)
        assertEquals(1_500L, lines[0].words[0].endMs)
        assertEquals(2_000L, lines[0].endMs)
        assertEquals(2_400L, lines[1].words[0].endMs)
        assertEquals(3_000L, lines[1].words[1].endMs)
    }

    @Test
    fun fallsBackToLineTextWhenAnyWordTimestampIsInvalid() {
        val lyricsFile = """version: '1.0'
            |lines:
            |  - text: Keep this line
            |    start_ms: 1000
            |    words:
            |      - text: 'Keep '
            |        start_ms: -1
            |      - text: 'this '
            |        start_ms: 1100
            |      - text: line
            |        start_ms: 1200
            |""".trimMargin()

        val lines = LyricsParser.fromLyricsFile(lyricsFile, durationMs = 2_000L)

        assertEquals(1, lines.size)
        assertEquals("Keep this line", lines.single().text)
        assertTrue(lines.single().words.isEmpty())
    }

    @Test
    fun fallsBackToLineTextWhenWordTimestampsAreOutOfOrder() {
        val lyricsFile = """version: '1.0'
            |lines:
            |  - text: Keep the original word order
            |    start_ms: 1000
            |    words:
            |      - text: 'Keep '
            |        start_ms: 1200
            |      - text: 'the '
            |        start_ms: 1100
            |      - text: original word order
            |        start_ms: 1300
            |""".trimMargin()

        val line = LyricsParser.fromLyricsFile(lyricsFile).single()

        assertEquals("Keep the original word order", line.text)
        assertTrue(line.words.isEmpty())
    }

    @Test
    fun prefersWordSyncThenLineSyncThenPlainText() {
        val lyricsFile = """version: '1.0'
            |lines:
            |  - text: Hello
            |    start_ms: 1000
            |    words:
            |      - text: Hello
            |        start_ms: 1000
            |""".trimMargin()

        val wordSynced = LyricsParser.buildTrackLyrics(
            plainLyrics = "Hello",
            syncedLyrics = "[00:01.00] Hello",
            lyricsFile = lyricsFile,
            hasWordSync = true,
            durationMs = 3_000L,
            source = LyricsSource.LRCLIB,
        )
        val lineSynced = LyricsParser.buildTrackLyrics(
            plainLyrics = "Hello",
            syncedLyrics = "[00:01.00] Hello",
            lyricsFile = null,
            hasWordSync = false,
            durationMs = 3_000L,
            source = LyricsSource.LRCLIB,
        )
        val plain = LyricsParser.buildTrackLyrics(
            plainLyrics = "Hello",
            syncedLyrics = null,
            lyricsFile = null,
            hasWordSync = false,
            durationMs = 3_000L,
            source = LyricsSource.LRCLIB,
        )

        assertEquals(LyricsSyncType.WORD, wordSynced?.syncType)
        assertEquals(LyricsSyncType.LINE, lineSynced?.syncType)
        assertEquals(LyricsSyncType.PLAIN, plain?.syncType)
    }

    @Test
    fun ranksExactTitleAndArtistAboveWeakMatches() {
        val exact = LyricsParser.matchScore(
            "2001 (feat. Ceza & Sahtiyan)", "Silahsız Kuvvet", "İhtiyar Heyeti", 233,
            "2001 (feat. Ceza & Sahtiyan)", "Silahsız Kuvvet", "İhtiyar Heyeti", 233,
        )
        val weak = LyricsParser.matchScore(
            "2001 Remix", "Another Artist", "Other Album", 180,
            "2001 (feat. Ceza & Sahtiyan)", "Silahsız Kuvvet", "İhtiyar Heyeti", 233,
        )

        assertTrue(exact > weak)
        assertEquals(100, exact)
    }
}
