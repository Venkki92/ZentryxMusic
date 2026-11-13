package com.zentryx.music.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zentryx.music.models.Song

/**
 * Primary SQLite entity representing an audio track discovered on Nextcloud.
 * Indexed for fast lookups by album, artist, and relative path.
 * Artwork is stored strictly as a cached file path (String), NEVER as an image blob.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["album"]),
        Index(value = ["artist"]),
        Index(value = ["relativePath"], unique = true),
        Index(value = ["title"])
    ]
)
data class SongEntity(
    @PrimaryKey
    val id: String,                    // Hash derived from relative WebDAV path
    val title: String,                 // Track title
    val artist: String,                // Artist name
    val album: String,                 // Album name
    val durationMs: Long,              // Duration in milliseconds
    val fileUrl: String,               // Absolute WebDAV URL
    val relativePath: String,          // Relative path under Nextcloud music root
    val mimeType: String,              // MIME type for Media3
    val trackNumber: Int? = null,      // Track number in album
    val discNumber: Int? = null,       // Disc number
    val year: Int? = null,             // Release year
    val genre: String? = null,         // Genre
    val fileSize: Long = 0L,           // Size in bytes
    val eTag: String? = null,          // WebDAV getetag for incremental invalidation
    val lastModified: Long = 0L,       // Timestamp in milliseconds
    val artworkPath: String? = null,   // Local disk cached artwork path (never blob!)
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val lyricsText: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

fun SongEntity.toDomainModel(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    fileUrl = fileUrl,
    relativePath = relativePath,
    mimeType = mimeType,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    genre = genre,
    fileSize = fileSize,
    eTag = eTag,
    lastModified = lastModified,
    artworkPath = artworkPath,
    isFavorite = isFavorite,
    isDownloaded = isDownloaded,
    localFilePath = localFilePath,
    lyricsText = lyricsText
)

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    fileUrl = fileUrl,
    relativePath = relativePath,
    mimeType = mimeType,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    genre = genre,
    fileSize = fileSize,
    eTag = eTag,
    lastModified = lastModified,
    artworkPath = artworkPath,
    isFavorite = isFavorite,
    isDownloaded = isDownloaded,
    localFilePath = localFilePath,
    lyricsText = lyricsText
)
