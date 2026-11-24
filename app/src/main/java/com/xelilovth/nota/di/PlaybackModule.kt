package com.xelilovth.nota.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xelilovth.nota.data.repository.PlaybackRepositoryImpl
import com.xelilovth.nota.domain.repository.PlaybackRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object PlaybackPlayerModule {
    @Provides
    @Singleton
    @UnstableApi
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
}

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(implementation: PlaybackRepositoryImpl): PlaybackRepository
}
