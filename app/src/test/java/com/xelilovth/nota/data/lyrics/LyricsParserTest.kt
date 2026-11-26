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
        assertEquals(115, exact)
    }

    @Test
    fun stripsLeadingPlusMarkersInLrcAndPlainLyrics() {
        val lrcLines = LyricsParser.fromLrc(
            """[00:05.00] + First line
                |[00:10.00]+   Second line
                |[00:12.00] + 
                |[00:15.00] +1 keeps plus without space""".trimMargin(),
            durationMs = 20_000L,
        )
        val plainLines = LyricsParser.fromPlain(
            """+ Plain one
                |  + Plain two
                |Normal line""".trimMargin(),
        )

        assertEquals(
            listOf("First line", "Second line", "+1 keeps plus without space"),
            lrcLines.map { it.text },
        )
        assertEquals(
            listOf("Plain one", "Plain two", "Normal line"),
            plainLines.map { it.text },
        )
    }

    @Test
    fun prefersExactDurationOverMismatchedDuration() {
        val exactDuration = LyricsParser.matchScore(
            title = "Böyle İyi",
            artist = "No.1",
            album = "Siyah Bayrak",
            durationSeconds = 210,
            targetTitle = "Böyle İyi",
            targetArtist = "No.1",
            targetAlbum = "Siyah Bayrak",
            targetDurationSeconds = 211,
        )
        val mismatchedDuration = LyricsParser.matchScore(
            title = "Böyle İyi",
            artist = "No.1",
            album = "Siyah Bayrak",
            durationSeconds = 226,
            targetTitle = "Böyle İyi",
            targetArtist = "No.1",
            targetAlbum = "Siyah Bayrak",
            targetDurationSeconds = 211,
        )

        assertTrue(exactDuration > mismatchedDuration)
        assertEquals(115, exactDuration)
        assertEquals(80, mismatchedDuration)
    }

    @Test
    fun parsesWordSyncedTtml() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:01.000" end="00:03.500">
                    <span begin="00:01.000" end="00:01.500">Hello</span>
                    <span begin="00:01.600" end="00:02.200">world</span>
                    <span ttm:role="x-bg" begin="00:02.300" end="00:03.000">(ooh)</span>
                  </p>
                  <p begin="00:04.000" end="00:06.000">
                    <span begin="00:04.000" end="00:04.800">+</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val result = LyricsParser.fromTtml(ttml, durationMs = 10_000L)!!

        assertEquals(LyricsSource.BETTER_LYRICS, result.source)
        assertEquals(LyricsSyncType.WORD, result.syncType)
        assertEquals(1, result.lines.size)
        val line = result.lines.single()
        assertEquals("Hello world", line.text)
        assertEquals(1_000L, line.startMs)
        assertEquals(3_500L, line.endMs)
        assertEquals(2, line.words.size)
        assertEquals("Hello", line.words[0].text)
        assertEquals(1_000L, line.words[0].startMs)
        assertEquals(1_500L, line.words[0].endMs)
        assertEquals("world", line.words[1].text)
        assertEquals(1_600L, line.words[1].startMs)
        assertEquals(2_200L, line.words[1].endMs)
    }

    @Test
    fun parsesLineSyncedTtmlWithVariousTimestampFormats() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="1.5s" end="3.25s">First line</p>
                  <p begin="00:04.500">Second line</p>
                  <p begin="00:00:06.000">Third line</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val result = LyricsParser.fromTtml(ttml, durationMs = 10_000L)!!

        assertEquals(LyricsSource.BETTER_LYRICS, result.source)
        assertEquals(LyricsSyncType.LINE, result.syncType)
        assertEquals(3, result.lines.size)
        assertEquals("First line", result.lines[0].text)
        assertEquals(1_500L, result.lines[0].startMs)
        assertEquals(3_250L, result.lines[0].endMs)
        assertTrue(result.lines[0].words.isEmpty())

        assertEquals("Second line", result.lines[1].text)
        assertEquals(4_500L, result.lines[1].startMs)
        assertEquals(6_000L, result.lines[1].endMs)

        assertEquals("Third line", result.lines[2].text)
        assertEquals(6_000L, result.lines[2].startMs)
        assertEquals(10_000L, result.lines[2].endMs)
    }
}
