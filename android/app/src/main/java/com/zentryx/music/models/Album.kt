package com.zentryx.music.models

/**
 * Domain model representing a music album.
 */
data class Album(
    val name: String,
    val artist: String,
    val songCount: Int,
    val totalDurationMs: Long,
    val year: Int? = null,
    val artworkPath: String? = null
)
