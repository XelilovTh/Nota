package com.xelilovth.nota.domain.model

enum class LyricsSource(val label: String) {
    BETTER_LYRICS("Better Lyrics"),
    LRCLIB("LRCLIB"),
    LYRICS_OVH("Lyrics.ovh"),
    LOCAL_FILE("Local .lrc"),
    GEMINI("Gemini AI"),
}

enum class LyricsSyncType {
    WORD,
    LINE,
    PLAIN,
}

data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long? = null,
)

data class LyricLine(
    val text: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val words: List<LyricWord> = emptyList(),
    val translatedText: String? = null,
)

data class TrackLyrics(
    val lines: List<LyricLine>,
    val source: LyricsSource,
    val syncType: LyricsSyncType,
    val instrumental: Boolean = false,
)
