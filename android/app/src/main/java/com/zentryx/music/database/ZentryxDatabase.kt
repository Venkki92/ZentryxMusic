package com.zentryx.music.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zentryx.music.database.dao.FolderSyncDao
import com.zentryx.music.database.dao.SongDao
import com.zentryx.music.database.entities.FolderSyncEntity
import com.zentryx.music.database.entities.SongEntity
import com.zentryx.music.database.entities.SongFtsEntity

/**
 * Main Room database for Zentryx Music.
 * Contains the songs table, FTS4 full-text search table, and folder sync cache.
 */
@Database(
    entities = [
        SongEntity::class,
        SongFtsEntity::class,
        FolderSyncEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ZentryxDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun folderSyncDao(): FolderSyncDao

    companion object {
        const val DATABASE_NAME = "zentryx_music_catalog.db"
    }
}
