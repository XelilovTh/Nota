package com.xelilovth.nota.data.repository

import android.app.RecoverableSecurityException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.xelilovth.nota.data.media.AudioScanner
import com.xelilovth.nota.domain.model.DeleteTrackResult
import com.xelilovth.nota.domain.model.Track
import com.xelilovth.nota.domain.repository.LibraryRepository
import com.xelilovth.nota.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val scanner: AudioScanner,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : LibraryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var rawTracks: List<Track> = emptyList()
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    override val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _allDiscoveredFolders = MutableStateFlow<List<String>>(emptyList())
    override val allDiscoveredFolders: StateFlow<List<String>> = _allDiscoveredFolders.asStateFlow()

    init {
        scope.launch {
            combine(
                settingsRepository.settings,
                settingsRepository.excludedFolders,
                settingsRepository.metadataOverrides,
            ) { _, _, _ -> }.collect {
                publishFilteredTracks()
            }
        }
    }

    override suspend fun scan(): List<Track> {
        rawTracks = scanner.scan()
        _allDiscoveredFolders.value = rawTracks.map(Track::folder).distinct().sorted()
        return publishFilteredTracks()
    }

    override suspend fun deleteTrack(trackId: Long): DeleteTrackResult {
        val track = rawTracks.firstOrNull { it.id == trackId } ?: return DeleteTrackResult.Failed
        val uri = Uri.parse(track.uri)
        return try {
            val deletedRows = context.contentResolver.delete(uri, null, null)
            if (deletedRows > 0) {
                onTrackDeletedFromDevice(trackId)
                DeleteTrackResult.Deleted
            } else {
                DeleteTrackResult.Failed
            }
        } catch (e: SecurityException) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val intentSender = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(uri),
                    ).intentSender
                    DeleteTrackResult.RequiresPermission(intentSender)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException -> {
                    DeleteTrackResult.RequiresPermission(e.userAction.actionIntent.intentSender)
                }
                else -> DeleteTrackResult.Failed
            }
        } catch (_: Exception) {
            DeleteTrackResult.Failed
        }
    }

    override fun onTrackDeletedFromDevice(trackId: Long) {
        rawTracks = rawTracks.filterNot { it.id == trackId }
        _allDiscoveredFolders.value = rawTracks.map(Track::folder).distinct().sorted()
        publishFilteredTracks()
    }

    private fun publishFilteredTracks(): List<Track> {
        val settings = settingsRepository.settings.value
        val excludedFolders = settingsRepository.excludedFolders.value
        val overrides = settingsRepository.metadataOverrides.value

        val filtered = rawTracks
            .let { list ->
                if (settings.hideShortTracks) {
                    list.filter { it.durationMs >= SHORT_TRACK_THRESHOLD_MS }
                } else {
                    list
                }
            }
            .filterNot { it.folder in excludedFolders }
            .map { track ->
                val override = overrides[track.id]
                if (override != null) {
                    track.copy(
                        title = override.title,
                        artist = override.artist,
                        album = override.album,
                    )
                } else {
                    track
                }
            }
        _tracks.value = filtered
        return filtered
    }

    private companion object {
        const val SHORT_TRACK_THRESHOLD_MS = 30_000L
    }
}
