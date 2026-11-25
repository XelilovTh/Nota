package com.xelilovth.nota.presentation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AppRoute {
    const val HOME = "home"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val SLEEP_TIMER = "sleep_timer"
    const val LYRICS = "lyrics"
    const val NOW_PLAYING = "now_playing"

    val bottomTabs = listOf(HOME, PLAYLISTS, SEARCH, LIBRARY)

    fun playlistDetail(playlistId: String): String {
        val encoded = URLEncoder.encode(playlistId, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "playlist/$encoded"
    }

    fun homeActionRoute(action: String): String? = when (action) {
        "search" -> SEARCH
        "settings" -> SETTINGS
        else -> null
    }
}
