package com.zentryx.music.models

/**
 * State snapshot of the Media3 audio player.
 */
data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Song> = emptyList(),
    val currentQueueIndex: Int = -1,
    val errorMessage: String? = null,
    val signalPathBadge: String? = null,
    val replayGainAppliedDb: Float? = null
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}
