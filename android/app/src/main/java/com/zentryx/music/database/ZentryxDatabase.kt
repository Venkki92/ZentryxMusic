package com.zentryx.music.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zentryx.music.database.dao.FolderSyncDao
import com.zentryx.music.database.dao.PlaylistDao
import com.zentryx.music.database.dao.SongDao
import com.zentryx.music.database.entities.FolderSyncEntity
import com.zentryx.music.database.entities.PlaylistEntity
import com.zentryx.music.database.entities.PlaylistSongCrossRef
import com.zentryx.music.database.entities.SongEntity
import com.zentryx.music.database.entities.SongFtsEntity

/**
 * Main Room database for Zentryx Music (v3: adds Smart Shelf play history & resume cursor columns).
 */
@Database(
    entities = [
        SongEntity::class,
        SongFtsEntity::class,
        FolderSyncEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ZentryxDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun folderSyncDao(): FolderSyncDao

    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val DATABASE_NAME = "zentryx_music_catalog.db"
    }
}
