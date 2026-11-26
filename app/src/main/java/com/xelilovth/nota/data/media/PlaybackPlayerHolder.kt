package com.xelilovth.nota.data.media

import androidx.annotation.OptIn
import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory

@OptIn(UnstableApi::class)
object PlaybackPlayerHolder {
    @Volatile private var instance: ExoPlayer? = null

    fun get(context: Context): ExoPlayer = instance ?: synchronized(this) {
        instance ?: run {
            val appContext = context.applicationContext
            val extractorsFactory = DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
            ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(appContext, extractorsFactory))
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
                .apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(),
                        true,
                    )
                    setHandleAudioBecomingNoisy(true)
                    setSeekParameters(SeekParameters.EXACT)
                }.also { instance = it }
        }
    }

    fun release() {
        synchronized(this) {
            instance?.release()
            instance = null
        }
    }
}
