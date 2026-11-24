package com.xelilovth.nota.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xelilovth.nota.domain.model.RepeatMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nota_user_preferences",
)

data class UserPreferences(
    val lastTrackId: Long? = null,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore: DataStore<Preferences> = context.userPreferencesDataStore

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val lastTrackId = preferences[KEY_LAST_TRACK_ID]
            val positionMs = preferences[KEY_POSITION_MS] ?: 0L
            val shuffle = preferences[KEY_SHUFFLE] ?: false
            val repeatModeName = preferences[KEY_REPEAT_MODE] ?: RepeatMode.OFF.name
            val repeatMode = runCatching { RepeatMode.valueOf(repeatModeName) }.getOrDefault(RepeatMode.OFF)

            UserPreferences(
                lastTrackId = lastTrackId,
                positionMs = positionMs,
                shuffle = shuffle,
                repeatMode = repeatMode,
            )
        }

    val lastTrackIdFlow: Flow<Long?> = userPreferencesFlow.map { it.lastTrackId }
    val positionMsFlow: Flow<Long> = userPreferencesFlow.map { it.positionMs }
    val shuffleFlow: Flow<Boolean> = userPreferencesFlow.map { it.shuffle }
    val repeatModeFlow: Flow<RepeatMode> = userPreferencesFlow.map { it.repeatMode }

    suspend fun setLastTrackId(trackId: Long?) {
        dataStore.edit { preferences ->
            if (trackId != null) {
                preferences[KEY_LAST_TRACK_ID] = trackId
            } else {
                preferences.remove(KEY_LAST_TRACK_ID)
            }
        }
    }

    suspend fun setPositionMs(positionMs: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_POSITION_MS] = positionMs
        }
    }

    suspend fun setShuffle(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SHUFFLE] = enabled
        }
    }

    suspend fun setRepeatMode(repeatMode: RepeatMode) {
        dataStore.edit { preferences ->
            preferences[KEY_REPEAT_MODE] = repeatMode.name
        }
    }

    suspend fun savePlaybackState(
        trackId: Long?,
        positionMs: Long,
        shuffle: Boolean,
        repeatMode: RepeatMode,
    ) {
        dataStore.edit { preferences ->
            if (trackId != null) {
                preferences[KEY_LAST_TRACK_ID] = trackId
            } else {
                preferences.remove(KEY_LAST_TRACK_ID)
            }
            preferences[KEY_POSITION_MS] = positionMs
            preferences[KEY_SHUFFLE] = shuffle
            preferences[KEY_REPEAT_MODE] = repeatMode.name
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    companion object {
        val KEY_LAST_TRACK_ID = longPreferencesKey("last_track_id")
        val KEY_POSITION_MS = longPreferencesKey("position_ms")
        val KEY_SHUFFLE = booleanPreferencesKey("shuffle")
        val KEY_REPEAT_MODE = stringPreferencesKey("repeat_mode")
    }
}
