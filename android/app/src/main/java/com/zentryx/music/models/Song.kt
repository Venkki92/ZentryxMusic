package com.zentryx.music.models

/**
 * Domain model representing an audio track discovered on Nextcloud.
 * Decoupled from Room Entity to adhere to Clean Architecture principles.
 */
data class Song(
    val id: String,                    // Hash or unique identifier derived from relative WebDAV path
    val title: String,                 // Track title extracted from metadata or fallback filename
    val artist: String,                // Artist name or "Unknown Artist"
    val album: String,                 // Album name or "Unknown Album"
    val durationMs: Long,              // Duration in milliseconds (0 if unparsed yet)
    val fileUrl: String,               // Absolute WebDAV URL used by Media3 ExoPlayer
    val relativePath: String,          // Relative path under Nextcloud music root
    val mimeType: String,              // MIME type for ExoPlayer MediaItem configuration
    val trackNumber: Int? = null,      // Optional track position in album
    val discNumber: Int? = null,       // Optional disc number
    val year: Int? = null,             // Release year
    val genre: String? = null,         // Music genre
    val fileSize: Long = 0L,           // Size in bytes
    val eTag: String? = null,          // WebDAV getetag for incremental sync invalidation
    val lastModified: Long = 0L,       // Timestamp in milliseconds
    val artworkPath: String? = null,   // Local cached artwork path or remote cover art URL
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val lyricsText: String? = null
) {
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val remainingSeconds = totalSeconds % 60
            return "%d:%02d".format(minutes, remainingSeconds)
        }
}
