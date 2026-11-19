package com.zentryx.music.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zentryx.music.models.Song

/**
 * Primary SQLite entity representing an audio track (Nextcloud WebDAV or Local USB/Device storage).
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
    val id: String,                    // Hash derived from relative path or local URI
    val title: String,                 // Track title
    val artist: String,                // Artist name
    val album: String,                 // Album name
    val durationMs: Long,              // Duration in milliseconds
    val fileUrl: String,               // Absolute WebDAV URL or local file/content URI
    val relativePath: String,          // Relative path under Nextcloud music root or "Local/<folder>/..."
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
    val addedAt: Long = System.currentTimeMillis(),
    val sourceType: String = "WEBDAV",
    val sampleRateHz: Int? = null,
    val bitsPerSample: Int? = null,
    val bitrateKbps: Int? = null,
    val replayGainDb: Float? = null,
    val lastPlayedAt: Long = 0L,
    val playCount: Int = 0,
    val lastPositionMs: Long = 0L
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
    lyricsText = lyricsText,
    sourceType = sourceType,
    sampleRateHz = sampleRateHz,
    bitsPerSample = bitsPerSample,
    bitrateKbps = bitrateKbps,
    replayGainDb = replayGainDb,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount,
    lastPositionMs = lastPositionMs
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
    lyricsText = lyricsText,
    sourceType = sourceType,
    sampleRateHz = sampleRateHz,
    bitsPerSample = bitsPerSample,
    bitrateKbps = bitrateKbps,
    replayGainDb = replayGainDb,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount,
    lastPositionMs = lastPositionMs
)
