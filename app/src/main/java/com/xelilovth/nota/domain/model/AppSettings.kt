package com.xelilovth.nota.domain.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hideShortTracks: Boolean = false,
)
