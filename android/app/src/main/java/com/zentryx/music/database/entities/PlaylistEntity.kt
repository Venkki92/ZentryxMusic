package com.zentryx.music.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zentryx.music.models.Playlist

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["songId"])
    ]
)
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String,
    val position: Int = 0
)

data class PlaylistSummaryTuple(
    val id: String,
    val name: String,
    val createdAt: Long,
    val songCount: Int
)

fun PlaylistSummaryTuple.toDomainModel(): Playlist = Playlist(
    id = id,
    name = name,
    createdAt = createdAt,
    songCount = songCount
)
