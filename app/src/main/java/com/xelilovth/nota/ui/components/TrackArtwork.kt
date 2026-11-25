package com.xelilovth.nota.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.xelilovth.nota.ui.theme.AppDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ArtworkCache {
    private const val MAX_ENTRIES = 60
    private val cache = LruCache<Long, ImageBitmap>(MAX_ENTRIES)
    private val misses = mutableSetOf<Long>()

    fun get(trackId: Long): ImageBitmap? = cache.get(trackId)
    fun isMiss(trackId: Long): Boolean = synchronized(misses) { trackId in misses }
    fun put(trackId: Long, bitmap: ImageBitmap?) {
        if (bitmap != null) {
            cache.put(trackId, bitmap)
        } else {
            synchronized(misses) { misses.add(trackId) }
        }
    }
}

@Composable
fun TrackArtwork(
    trackId: Long?,
    trackUri: Uri?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    iconPadding: Dp = AppDimens.ElementSpacing,
) {
    val context = LocalContext.current
    var bitmap by remember(trackId) {
        mutableStateOf(if (trackId != null) ArtworkCache.get(trackId) else null)
    }

    LaunchedEffect(trackId, trackUri) {
        if (trackId == null || trackUri == null) {
            bitmap = null
            return@LaunchedEffect
        }
        val cached = ArtworkCache.get(trackId)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        if (ArtworkCache.isMiss(trackId)) {
            bitmap = null
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            loadEmbeddedArtwork(context, trackUri)?.asImageBitmap()
        }
        ArtworkCache.put(trackId, loaded)
        bitmap = loaded
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.padding(iconPadding),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun loadEmbeddedArtwork(context: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            return context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
        } catch (_: Exception) {
            // Fall through to MediaMetadataRetriever if MediaStore thumbnail is unavailable
        }
    }
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val art = retriever.embeddedPicture ?: return null
        BitmapFactory.decodeByteArray(art, 0, art.size)
    } catch (_: Exception) {
        null
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
            // Ignore release errors
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
