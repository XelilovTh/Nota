package com.xelilovth.nota.di

import com.xelilovth.nota.data.lyrics.LyricsRepositoryImpl
import com.xelilovth.nota.domain.repository.LyricsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LyricsModule {
    @Binds
    @Singleton
    abstract fun bindLyricsRepository(implementation: LyricsRepositoryImpl): LyricsRepository
}
