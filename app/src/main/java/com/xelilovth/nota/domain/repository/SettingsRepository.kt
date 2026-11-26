package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    val recentTrackIds: StateFlow<List<Long>>
    val geminiApiKey: StateFlow<String> get() = MutableStateFlow("")
    fun setThemeMode(mode: ThemeMode)
    fun setHideShortTracks(hide: Boolean)
    fun addRecentTrack(trackId: Long)
    fun setGeminiApiKey(key: String) = Unit
}
