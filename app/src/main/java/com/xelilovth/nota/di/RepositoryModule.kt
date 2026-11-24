package com.xelilovth.nota.di

import com.xelilovth.nota.data.repository.FavoritesRepositoryImpl
import com.xelilovth.nota.data.repository.HistoryRepositoryImpl
import com.xelilovth.nota.data.repository.PlaylistRepositoryImpl
import com.xelilovth.nota.domain.repository.FavoritesRepository
import com.xelilovth.nota.domain.repository.HistoryRepository
import com.xelilovth.nota.domain.repository.PlaylistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(implementation: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(implementation: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(implementation: HistoryRepositoryImpl): HistoryRepository
}
