package com.xelilovth.nota.di

import com.xelilovth.nota.data.repository.CustomPlaylistRepositoryImpl
import com.xelilovth.nota.domain.repository.CustomPlaylistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaylistModule {
    @Binds
    @Singleton
    abstract fun bindCustomPlaylistRepository(
        implementation: CustomPlaylistRepositoryImpl,
    ): CustomPlaylistRepository
}
