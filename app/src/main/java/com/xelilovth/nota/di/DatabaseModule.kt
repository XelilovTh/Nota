package com.xelilovth.nota.di

import android.content.Context
import com.xelilovth.nota.data.local.FavoriteDao
import com.xelilovth.nota.data.local.HistoryDao
import com.xelilovth.nota.data.local.NotaDatabase
import com.xelilovth.nota.data.local.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideNotaDatabase(
        @ApplicationContext context: Context,
    ): NotaDatabase {
        return NotaDatabase.create(context)
    }

    @Provides
    @Singleton
    fun providePlaylistDao(database: NotaDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    @Singleton
    fun provideFavoriteDao(database: NotaDatabase): FavoriteDao {
        return database.favoriteDao()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: NotaDatabase): HistoryDao {
        return database.historyDao()
    }
}
