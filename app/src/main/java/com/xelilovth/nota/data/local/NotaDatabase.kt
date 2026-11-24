package com.xelilovth.nota.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        FavoriteEntity::class,
        PlayHistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class NotaDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val DATABASE_NAME = "nota.db"

        fun create(context: Context): NotaDatabase {
            return Room.databaseBuilder(
                context,
                NotaDatabase::class.java,
                DATABASE_NAME,
            ).fallbackToDestructiveMigration().build()
        }
    }
}
