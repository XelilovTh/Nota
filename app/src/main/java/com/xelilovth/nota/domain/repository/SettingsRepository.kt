package com.xelilovth.nota.domain.repository

import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.domain.model.TrackMetadataOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    val recentTrackIds: StateFlow<List<Long>>
    val geminiApiKey: StateFlow<String> get() = MutableStateFlow("")
    val favoriteTrackIds: StateFlow<Set<Long>> get() = MutableStateFlow(emptySet())
    val playCounts: StateFlow<Map<Long, Int>> get() = MutableStateFlow(emptyMap())
    val excludedFolders: StateFlow<Set<String>> get() = MutableStateFlow(emptySet())
    val metadataOverrides: StateFlow<Map<Long, TrackMetadataOverride>> get() = MutableStateFlow(emptyMap())

    fun setThemeMode(mode: ThemeMode)
    fun setHideShortTracks(hide: Boolean)
    fun setCrossfadeSeconds(seconds: Int) = Unit
    fun addRecentTrack(trackId: Long)
    fun setGeminiApiKey(key: String) = Unit
    fun toggleFavorite(trackId: Long) = Unit
    fun incrementPlayCount(trackId: Long) = Unit
    fun setFolderExcluded(folder: String, excluded: Boolean) = Unit
    fun setTrackMetadataOverride(trackId: Long, override: TrackMetadataOverride?) = Unit
}
