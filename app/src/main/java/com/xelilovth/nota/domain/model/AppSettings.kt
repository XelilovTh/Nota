package com.xelilovth.nota.domain.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class OfflineStorageLocation {
    APP_INTERNAL,
    PUBLIC_MUSIC,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hideShortTracks: Boolean = true,
    val crossfadeSeconds: Int = 0,
    val offlineStorageLocation: OfflineStorageLocation = OfflineStorageLocation.APP_INTERNAL,
    val streamCacheSizeMb: Int = 500,
)
