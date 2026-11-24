package com.xelilovth.nota.presentation

object AppRoute {
    const val HOME = "home"
    const val PLAYLISTS = "playlists"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val EQUALIZER = "equalizer"
    const val SLEEP_TIMER = "sleep_timer"
    const val LYRICS = "lyrics"
    const val NOW_PLAYING = "now_playing"

    val bottomTabs = listOf(HOME, PLAYLISTS, SEARCH, LIBRARY)

    fun homeActionRoute(action: String): String? = when (action) {
        "search" -> SEARCH
        "settings" -> SETTINGS
        else -> null
    }
}
