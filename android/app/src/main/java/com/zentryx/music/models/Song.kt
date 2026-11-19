package com.zentryx.music.models

import java.util.Locale

/**
 * Domain model representing an audio track (Nextcloud WebDAV or Local USB/Device storage).
 * Decoupled from Room Entity to adhere to Clean Architecture principles.
 */
data class Song(
    val id: String,                    // Hash or unique identifier derived from relative path or local URI
    val title: String,                 // Track title extracted from metadata or fallback filename
    val artist: String,                // Artist name or "Unknown Artist"
    val album: String,                 // Album name or "Unknown Album"
    val durationMs: Long,              // Duration in milliseconds (0 if unparsed yet)
    val fileUrl: String,               // Absolute WebDAV URL or content:// / file:// URI
    val relativePath: String,          // Relative path under Nextcloud music root or "Local/<folder>/..."
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
    val lyricsText: String? = null,
    val sourceType: String = "WEBDAV", // "WEBDAV" or "LOCAL"
    val sampleRateHz: Int? = null,     // e.g. 44100, 48000, 96000
    val bitsPerSample: Int? = null,    // e.g. 16, 24
    val bitrateKbps: Int? = null,      // e.g. 320
    val replayGainDb: Float? = null,   // Track ReplayGain adjustment in dB
    val lastPlayedAt: Long = 0L,       // Timestamp in ms when last played
    val playCount: Int = 0,            // Total play count for Most Played smart shelf
    val lastPositionMs: Long = 0L      // Saved playback cursor for Resume Mixes
) {
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val remainingSeconds = totalSeconds % 60
            return "%d:%02d".format(minutes, remainingSeconds)
        }

    val format: AudioFormat
        get() = AudioFormat.fromExtension(relativePath.substringAfterLast('.', ""))
            ?: AudioFormat.entries.firstOrNull { mimeType.contains(it.extension, ignoreCase = true) }
            ?: AudioFormat.MP3

    val isHighRes: Boolean
        get() = (bitsPerSample != null && bitsPerSample >= 24) ||
            (sampleRateHz != null && sampleRateHz >= 88200) ||
            format == AudioFormat.FLAC

    val signalPathSummary: String
        get() = buildString {
            append(format.name)
            if (bitsPerSample != null && sampleRateHz != null && sampleRateHz > 0) {
                val khz = if (sampleRateHz % 1000 == 0) {
                    "${sampleRateHz / 1000}"
                } else {
                    String.format(Locale.US, "%.1f", sampleRateHz / 1000f)
                }
                append(" · ${bitsPerSample}-bit / $khz kHz")
            } else if (sampleRateHz != null && sampleRateHz > 0) {
                val khz = if (sampleRateHz % 1000 == 0) {
                    "${sampleRateHz / 1000}"
                } else {
                    String.format(Locale.US, "%.1f", sampleRateHz / 1000f)
                }
                append(" · $khz kHz")
            }
            if (bitrateKbps != null && bitrateKbps > 0) {
                append(" · $bitrateKbps kbps")
            }
        }
}
