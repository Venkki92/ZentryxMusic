package com.zentryx.music.models

/**
 * Domain model representing a musical artist.
 */
data class Artist(
    val name: String,
    val albumCount: Int,
    val songCount: Int
)
