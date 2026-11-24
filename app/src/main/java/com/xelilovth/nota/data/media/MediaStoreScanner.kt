package com.xelilovth.nota.data.media

import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.xelilovth.nota.di.IoDispatcher
import com.xelilovth.nota.domain.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AudioScanner {
    suspend fun scan(): List<Track>
}

class MediaStoreScanner @Inject constructor(
    private val audioMediaStore: AudioMediaStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AudioScanner {
    override suspend fun scan(): List<Track> = withContext(ioDispatcher) {
        try {
            val cursor = audioMediaStore.queryAudio() ?: return@withContext emptyList()
            cursor.use { result ->
                if (!result.moveToFirst()) return@use emptyList()

                val idColumn = result.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleColumn = result.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistColumn = result.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumColumn = result.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = result.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = result.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    result.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    -1
                }
                @Suppress("DEPRECATION")
                val dataColumn = result.getColumnIndex(MediaStore.Audio.Media.DATA)

                if (idColumn < 0) return@use emptyList()

                buildList {
                    do {
                        val id = result.getLong(idColumn)
                        val contentUri: Uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )
                        val albumId = if (albumIdColumn >= 0 && !result.isNull(albumIdColumn)) {
                            result.getLong(albumIdColumn)
                        } else {
                            null
                        }
                        val artworkUri = albumId?.let {
                            ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                it,
                            )
                        }
                        val pathFolder = if (dataColumn >= 0) {
                            result.getString(dataColumn)?.substringBeforeLast('/', "")
                                ?.substringAfterLast('/', "")
                        } else {
                            null
                        }
                        val folder = (if (relativePathColumn >= 0) {
                            result.getString(relativePathColumn)?.trimEnd('/')
                        } else {
                            pathFolder
                        }).takeUnless { it.isNullOrBlank() } ?: "Unknown folder"

                        add(
                            Track(
                                id = id,
                                uri = contentUri,
                                title = result.stringOrFallback(titleColumn, "Unknown title"),
                                artist = result.stringOrFallback(artistColumn, "Unknown artist"),
                                album = result.stringOrFallback(albumColumn, "Unknown album"),
                                durationMs = if (durationColumn >= 0) result.getLong(durationColumn) else 0L,
                                folder = folder,
                                albumId = albumId,
                                artworkUri = artworkUri,
                            ),
                        )
                    } while (result.moveToNext())
                }
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun Cursor.stringOrFallback(columnIndex: Int, fallback: String): String =
        if (columnIndex >= 0) getString(columnIndex)?.takeIf(String::isNotBlank) ?: fallback else fallback
}
