package com.xelilovth.nota.data.repository

import android.content.Context
import com.xelilovth.nota.domain.model.CustomPlaylist
import com.xelilovth.nota.domain.model.PlaylistCollectionType
import com.xelilovth.nota.domain.repository.CustomPlaylistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomPlaylistRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : CustomPlaylistRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _playlists = MutableStateFlow(loadPlaylists())
    override val playlists: StateFlow<List<CustomPlaylist>> = _playlists.asStateFlow()
    private val _hiddenAutoGroupIds = MutableStateFlow(loadHiddenAutoGroups())
    override val hiddenAutoGroupIds: StateFlow<Set<String>> = _hiddenAutoGroupIds.asStateFlow()

    override fun createPlaylist(name: String): CustomPlaylist =
        createCollection(name, PlaylistCollectionType.PLAYLIST)

    override fun createCollection(
        name: String,
        type: PlaylistCollectionType,
        trackIds: List<Long>,
    ): CustomPlaylist {
        val cleanName = name.trim().ifBlank { "Untitled ${type.name.lowercase()}" }
        val created = CustomPlaylist(
            id = "custom:${type.name.lowercase()}:${UUID.randomUUID()}",
            name = cleanName,
            trackIds = trackIds.distinct(),
            type = type,
        )
        updateAndSave(_playlists.value + created)
        return created
    }

    override fun hideAutoGroup(groupId: String) {
        val updated = _hiddenAutoGroupIds.value + groupId
        _hiddenAutoGroupIds.value = updated
        prefs.edit().putString(KEY_HIDDEN_GROUPS, JSONArray(updated.toList()).toString()).apply()
    }

    override fun renamePlaylist(playlistId: String, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        updateAndSave(
            _playlists.value.map { playlist ->
                if (playlist.id == playlistId) playlist.copy(name = cleanName) else playlist
            },
        )
    }

    override fun deletePlaylist(playlistId: String) {
        updateAndSave(_playlists.value.filterNot { it.id == playlistId })
    }

    override fun addTrack(playlistId: String, trackId: Long) {
        updateAndSave(
            _playlists.value.map { playlist ->
                if (playlist.id == playlistId && trackId !in playlist.trackIds) {
                    playlist.copy(trackIds = playlist.trackIds + trackId)
                } else {
                    playlist
                }
            },
        )
    }

    override fun removeTrack(playlistId: String, trackId: Long) {
        updateAndSave(
            _playlists.value.map { playlist ->
                if (playlist.id == playlistId) {
                    playlist.copy(trackIds = playlist.trackIds.filterNot { it == trackId })
                } else {
                    playlist
                }
            },
        )
    }

    private fun updateAndSave(updated: List<CustomPlaylist>) {
        _playlists.value = updated
        val array = JSONArray()
        updated.forEach { playlist ->
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            val idsArray = JSONArray()
            playlist.trackIds.forEach { idsArray.put(it) }
            obj.put("trackIds", idsArray)
            obj.put("type", playlist.type.name)
            array.put(obj)
        }
        prefs.edit().putString(KEY_PLAYLISTS, array.toString()).apply()
    }

    private fun loadPlaylists(): List<CustomPlaylist> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val idsJson = obj.optJSONArray("trackIds") ?: JSONArray()
                    val trackIds = buildList {
                        for (j in 0 until idsJson.length()) {
                            add(idsJson.getLong(j))
                        }
                    }
                    add(
                        CustomPlaylist(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            trackIds = trackIds,
                            type = runCatching { PlaylistCollectionType.valueOf(obj.optString("type", "PLAYLIST")) }
                                .getOrDefault(PlaylistCollectionType.PLAYLIST),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadHiddenAutoGroups(): Set<String> {
        val raw = prefs.getString(KEY_HIDDEN_GROUPS, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet { for (i in 0 until array.length()) add(array.getString(i)) }
        }.getOrDefault(emptySet())
    }

    private companion object {
        const val PREFS_NAME = "nota_custom_playlists"
        const val KEY_PLAYLISTS = "playlists_json"
        const val KEY_HIDDEN_GROUPS = "hidden_auto_groups"
    }
}
