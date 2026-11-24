@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.xelilovth.nota.data.media

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

object PlaybackPlayerHolder {
    @Volatile private var instance: ExoPlayer? = null

    fun get(context: Context): ExoPlayer = instance ?: synchronized(this) {
        instance ?: ExoPlayer.Builder(context.applicationContext).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
        }.also { instance = it }
    }

    fun release() {
        synchronized(this) {
            instance?.release()
            instance = null
        }
    }
}
