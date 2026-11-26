package com.xelilovth.nota.data.repository

import android.content.Context
import com.xelilovth.nota.domain.model.AppSettings
import com.xelilovth.nota.domain.model.OfflineStorageLocation
import com.xelilovth.nota.domain.model.ThemeMode
import com.xelilovth.nota.domain.model.TrackMetadataOverride
import com.xelilovth.nota.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    private val _recentTrackIds = MutableStateFlow(loadRecentTrackIds())
    override val recentTrackIds: StateFlow<List<Long>> = _recentTrackIds.asStateFlow()
    private val _geminiApiKey = MutableStateFlow(prefs.getString(KEY_GEMINI_API_KEY, "").orEmpty())
    override val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()
    private val _favoriteTrackIds = MutableStateFlow(loadFavoriteTrackIds())
    override val favoriteTrackIds: StateFlow<Set<Long>> = _favoriteTrackIds.asStateFlow()
    private val _playCounts = MutableStateFlow(loadPlayCounts())
    override val playCounts: StateFlow<Map<Long, Int>> = _playCounts.asStateFlow()
    private val _listenDurationsMs = MutableStateFlow(loadListenDurationsMs())
    override val listenDurationsMs: StateFlow<Map<Long, Long>> = _listenDurationsMs.asStateFlow()
    private val _lyricsTranslationLanguage = MutableStateFlow(
        prefs.getString(KEY_LYRICS_TRANSLATION_LANGUAGE, DEFAULT_LYRICS_TRANSLATION_LANGUAGE)
            ?.takeUnless { it.isBlank() }
            ?: DEFAULT_LYRICS_TRANSLATION_LANGUAGE,
    )
    override val lyricsTranslationLanguage: StateFlow<String> = _lyricsTranslationLanguage.asStateFlow()
    private val _excludedFolders = MutableStateFlow(loadExcludedFolders())
    override val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()
    private val _metadataOverrides = MutableStateFlow(loadMetadataOverrides())
    override val metadataOverrides: StateFlow<Map<Long, TrackMetadataOverride>> = _metadataOverrides.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    override fun setHideShortTracks(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_SHORT_TRACKS, hide).apply()
        _settings.value = _settings.value.copy(hideShortTracks = hide)
    }

    override fun setCrossfadeSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_CROSSFADE_SECONDS, seconds).apply()
        _settings.value = _settings.value.copy(crossfadeSeconds = seconds)
    }

    override fun setOfflineStorageLocation(location: OfflineStorageLocation) {
        prefs.edit().putString(KEY_OFFLINE_STORAGE_LOCATION, location.name).apply()
        _settings.value = _settings.value.copy(offlineStorageLocation = location)
    }

    override fun setStreamCacheSizeMb(sizeMb: Int) {
        val coerced = sizeMb.coerceIn(100, 2048)
        prefs.edit().putInt(KEY_STREAM_CACHE_SIZE_MB, coerced).apply()
        _settings.value = _settings.value.copy(streamCacheSizeMb = coerced)
    }

    override fun addRecentTrack(trackId: Long) {
        val updated = (listOf(trackId) + _recentTrackIds.value.filterNot { it == trackId }).take(10)
        prefs.edit().putString(KEY_RECENT_TRACK_IDS, updated.joinToString(",")).apply()
        _recentTrackIds.value = updated
    }

    override fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key).apply()
        _geminiApiKey.value = key
    }

    override fun toggleFavorite(trackId: Long) {
        val current = _favoriteTrackIds.value
        val updated = if (trackId in current) current - trackId else current + trackId
        prefs.edit().putString(KEY_FAVORITE_TRACK_IDS, updated.joinToString(",")).apply()
        _favoriteTrackIds.value = updated
    }

    override fun incrementPlayCount(trackId: Long) {
        val current = _playCounts.value
        val updated = current + (trackId to ((current[trackId] ?: 0) + 1))
        val json = JSONObject()
        updated.forEach { (id, count) -> json.put(id.toString(), count) }
        prefs.edit().putString(KEY_PLAY_COUNTS, json.toString()).apply()
        _playCounts.value = updated
    }

    override fun addListenDuration(trackId: Long, deltaMs: Long) {
        if (deltaMs <= 0L) return
        val current = _listenDurationsMs.value
        val updated = current + (trackId to ((current[trackId] ?: 0L) + deltaMs))
        val json = JSONObject()
        updated.forEach { (id, totalMs) -> json.put(id.toString(), totalMs) }
        prefs.edit().putString(KEY_LISTEN_DURATIONS_MS, json.toString()).apply()
        _listenDurationsMs.value = updated
    }

    override fun setLyricsTranslationLanguage(language: String) {
        val normalized = language.trim().ifBlank { DEFAULT_LYRICS_TRANSLATION_LANGUAGE }
        prefs.edit().putString(KEY_LYRICS_TRANSLATION_LANGUAGE, normalized).apply()
        _lyricsTranslationLanguage.value = normalized
    }

    override fun setFolderExcluded(folder: String, excluded: Boolean) {
        val current = _excludedFolders.value
        val updated = if (excluded) current + folder else current - folder
        prefs.edit().putStringSet(KEY_EXCLUDED_FOLDERS, updated).apply()
        _excludedFolders.value = updated
    }

    override fun setTrackMetadataOverride(trackId: Long, override: TrackMetadataOverride?) {
        val current = _metadataOverrides.value
        val updated = if (override == null) current - trackId else current + (trackId to override)
        val json = JSONObject()
        updated.forEach { (id, item) ->
            json.put(
                id.toString(),
                JSONObject().apply {
                    put("title", item.title)
                    put("artist", item.artist)
                    put("album", item.album)
                },
            )
        }
        prefs.edit().putString(KEY_METADATA_OVERRIDES, json.toString()).apply()
        _metadataOverrides.value = updated
    }

    private fun loadSettings(): AppSettings {
        val themeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM
        val hideShort = prefs.getBoolean(KEY_HIDE_SHORT_TRACKS, true)
        val crossfadeSeconds = prefs.getInt(KEY_CROSSFADE_SECONDS, 0)
        val storageName = prefs.getString(KEY_OFFLINE_STORAGE_LOCATION, OfflineStorageLocation.APP_INTERNAL.name)
        val storageLocation = OfflineStorageLocation.entries.firstOrNull { it.name == storageName }
            ?: OfflineStorageLocation.APP_INTERNAL
        val streamCacheSizeMb = prefs.getInt(KEY_STREAM_CACHE_SIZE_MB, 500)
        return AppSettings(
            themeMode = themeMode,
            hideShortTracks = hideShort,
            crossfadeSeconds = crossfadeSeconds,
            offlineStorageLocation = storageLocation,
            streamCacheSizeMb = streamCacheSizeMb,
        )
    }

    private fun loadRecentTrackIds(): List<Long> {
        val raw = prefs.getString(KEY_RECENT_TRACK_IDS, null) ?: return emptyList()
        return raw.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .distinct()
            .take(10)
    }

    private fun loadFavoriteTrackIds(): Set<Long> {
        val raw = prefs.getString(KEY_FAVORITE_TRACK_IDS, null) ?: return emptySet()
        return raw.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
    }

    private fun loadPlayCounts(): Map<Long, Int> {
        val raw = prefs.getString(KEY_PLAY_COUNTS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val id = key.toLongOrNull() ?: continue
                    val count = obj.optInt(key, 0)
                    if (count > 0) put(id, count)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun loadListenDurationsMs(): Map<Long, Long> {
        val raw = prefs.getString(KEY_LISTEN_DURATIONS_MS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val id = key.toLongOrNull() ?: continue
                    val totalMs = obj.optLong(key, 0L)
                    if (totalMs > 0L) put(id, totalMs)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun loadExcludedFolders(): Set<String> {
        return prefs.getStringSet(KEY_EXCLUDED_FOLDERS, emptySet())?.toSet() ?: emptySet()
    }

    private fun loadMetadataOverrides(): Map<Long, TrackMetadataOverride> {
        val raw = prefs.getString(KEY_METADATA_OVERRIDES, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val id = key.toLongOrNull() ?: continue
                    val item = obj.optJSONObject(key) ?: continue
                    put(
                        id,
                        TrackMetadataOverride(
                            title = item.optString("title", ""),
                            artist = item.optString("artist", ""),
                            album = item.optString("album", ""),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val PREFS_NAME = "nota_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_HIDE_SHORT_TRACKS = "hide_short_tracks"
        const val KEY_CROSSFADE_SECONDS = "crossfade_seconds"
        const val KEY_RECENT_TRACK_IDS = "recent_track_ids"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_FAVORITE_TRACK_IDS = "favorite_track_ids"
        const val KEY_PLAY_COUNTS = "play_counts"
        const val KEY_LISTEN_DURATIONS_MS = "listen_durations_ms"
        const val KEY_LYRICS_TRANSLATION_LANGUAGE = "lyrics_translation_language"
        const val DEFAULT_LYRICS_TRANSLATION_LANGUAGE = "Azerbaijani"
        const val KEY_EXCLUDED_FOLDERS = "excluded_folders"
        const val KEY_METADATA_OVERRIDES = "metadata_overrides"
        const val KEY_OFFLINE_STORAGE_LOCATION = "offline_storage_location"
        const val KEY_STREAM_CACHE_SIZE_MB = "stream_cache_size_mb"
    }
}
