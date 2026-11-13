package com.zentryx.music.database.entities

import com.zentryx.music.models.Album
import com.zentryx.music.models.Artist

data class AlbumSummary(
    val album: String,
    val artist: String,
    val songCount: Int,
    val totalDurationMs: Long,
    val year: Int?,
    val artworkPath: String?
)

fun AlbumSummary.toDomainModel(): Album = Album(
    name = album,
    artist = artist,
    songCount = songCount,
    totalDurationMs = totalDurationMs,
    year = year,
    artworkPath = artworkPath
)

data class ArtistSummary(
    val artist: String,
    val albumCount: Int,
    val songCount: Int
)

fun ArtistSummary.toDomainModel(): Artist = Artist(
    name = artist,
    albumCount = albumCount,
    songCount = songCount
)
