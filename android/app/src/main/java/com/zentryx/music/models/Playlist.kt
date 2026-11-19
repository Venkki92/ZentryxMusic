package com.zentryx.music.models

/**
 * Domain model representing a custom user playlist (synced via .m3u8 on Nextcloud).
 */
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val songCount: Int = 0
)
