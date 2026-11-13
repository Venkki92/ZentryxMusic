package com.zentryx.music.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks WebDAV folder state for incremental synchronization.
 * Stores the last known ETag and modification timestamp to avoid redundant PROPFIND traversals.
 */
@Entity(tableName = "folder_sync")
data class FolderSyncEntity(
    @PrimaryKey
    val folderPath: String,            // Normalized relative folder path (e.g. "/Music/Rock")
    val eTag: String?,                 // WebDAV getetag of the folder
    val lastModified: Long = 0L,       // Timestamp from getlastmodified
    val lastScannedAt: Long = System.currentTimeMillis(),
    val fileCount: Int = 0             // Number of music files discovered inside
)
