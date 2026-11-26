package com.xelilovth.nota.domain.model

enum class LyricsSource(val label: String) {
    LRCLIB("LRCLIB"),
    LYRICS_OVH("Lyrics.ovh"),
    LOCAL_FILE("Local .lrc"),
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
)

data class TrackLyrics(
    val lines: List<LyricLine>,
    val source: LyricsSource,
    val syncType: LyricsSyncType,
    val instrumental: Boolean = false,
)
