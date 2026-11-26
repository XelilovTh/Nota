package com.xelilovth.nota.di

import com.xelilovth.nota.data.online.OnlineMusicRepositoryImpl
import com.xelilovth.nota.domain.repository.OnlineMusicRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OnlineModule {
    @Binds
    @Singleton
    abstract fun bindOnlineMusicRepository(impl: OnlineMusicRepositoryImpl): OnlineMusicRepository
}
