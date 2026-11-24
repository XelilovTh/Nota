package com.xelilovth.nota.di

import com.xelilovth.nota.data.media.AudioMediaStore
import com.xelilovth.nota.data.media.AudioScanner
import com.xelilovth.nota.data.media.ContentResolverAudioMediaStore
import com.xelilovth.nota.data.media.MediaStoreScanner
import com.xelilovth.nota.data.repository.LibraryRepositoryImpl
import com.xelilovth.nota.domain.repository.LibraryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {
    @Binds
    @Singleton
    abstract fun bindAudioMediaStore(implementation: ContentResolverAudioMediaStore): AudioMediaStore

    @Binds
    @Singleton
    abstract fun bindAudioScanner(implementation: MediaStoreScanner): AudioScanner

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(implementation: LibraryRepositoryImpl): LibraryRepository
}
