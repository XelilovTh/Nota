package com.xelilovth.nota.data.local

import androidx.room.Database
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
    }
}
