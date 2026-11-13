package com.zentryx.music.database.entities

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Full-Text Search (FTS4) virtual table indexing songs, albums, and artists.
 * Enables instant prefix matching (< 10ms queries) over thousands of tracks
 * without causing UI hitching or lagging on the AutoChips AC8259P headunit.
 */
@Entity(tableName = "songs_fts")
@Fts4(contentEntity = SongEntity::class)
data class SongFtsEntity(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String?
)
