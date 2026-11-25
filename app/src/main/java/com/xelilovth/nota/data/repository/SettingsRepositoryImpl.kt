package com.xelilovth.nota.data.repository

import android.content.Context
import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    override fun setHideShortTracks(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_SHORT_TRACKS, hide).apply()
        _settings.value = _settings.value.copy(hideShortTracks = hide)
    }

    private fun loadSettings(): AppSettings {
        val themeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM
        val hideShort = prefs.getBoolean(KEY_HIDE_SHORT_TRACKS, false)
        return AppSettings(themeMode = themeMode, hideShortTracks = hideShort)
    }

    private companion object {
        const val PREFS_NAME = "nota_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_HIDE_SHORT_TRACKS = "hide_short_tracks"
    }
}
