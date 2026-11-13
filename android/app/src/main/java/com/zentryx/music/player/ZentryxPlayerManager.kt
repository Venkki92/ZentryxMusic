package com.zentryx.music.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.zentryx.music.models.PlaybackState
import com.zentryx.music.models.RepeatMode
import com.zentryx.music.models.Song
import com.zentryx.music.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core Media3 ExoPlayer playback engine for Zentryx Music.
 * Handles audio focus ducking (essential for Mercedes S203 reverse radar and navigation prompts),
 * memory-conserving buffer control (< 150MB RAM budget), gapless transitions, and position restoration.
 */
@OptIn(UnstableApi::class)
@Singleton
class ZentryxPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheDataSourceFactory: CacheDataSourceFactory,
    private val playerPreferences: PlayerPreferences,
    private val speedVolumeController: SpeedVolumeController,
    private val engineStandbyController: EngineStandbyController
) {
    private val playerScope = CoroutineScope(Dispatchers.Main + Job())
    private var positionUpdateJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var activeQueue = mutableListOf<Song>()

    // Automotive audio attributes: ducks media when parking sensors or GPS navigation voice prompts chime
    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    // Tuned LoadControl: avoids hoarding hundreds of megabytes of RAM on AutoChips AC8259P
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            5000,   // Min buffer: 5s
            15000,  // Max buffer: 15s (preserves RAM)
            1500,   // Buffer for playback start: 1.5s
            3000    // Buffer after rebuffer: 3s
        )
        .setBackBuffer(10000, /* retainBackBufferFromKeyframe = */ true)
        .build()

    val exoPlayer: ExoPlayer by lazy {
        val mediaSourceFactory = DefaultMediaSourceFactory(
            context,
            cacheDataSourceFactory.createDataSourceFactory()
        )

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true) // Pauses automatically when headset/Bluetooth disconnects
            .build().apply {
                addListener(PlayerEventListener())
            }
    }

    init {
        startPositionTicker()
        engineStandbyController.attachPlayer(exoPlayer)
        playerScope.launch {
            speedVolumeController.volumeMultiplier.collect { multiplier ->
                exoPlayer.volume = multiplier.coerceIn(0f, 2.0f)
            }
        }
    }

    /**
     * Plays a specific song with an active playlist queue.
     * Media3 handles gapless playback across sequential tracks in the playlist.
     */
    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        activeQueue = queue.toMutableList()
        val index = activeQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        val mediaItems = activeQueue.map { s ->
            buildMediaItem(s)
        }

        exoPlayer.setMediaItems(mediaItems, index, 0L)
        exoPlayer.prepare()
        exoPlayer.play()

        _playbackState.update {
            it.copy(
                currentSong = song,
                queue = activeQueue,
                currentQueueIndex = index,
                isPlaying = true,
                errorMessage = null
            )
        }
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
        saveCurrentPosition()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
        saveCurrentPosition()
    }

    fun skipToNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL && activeQueue.isNotEmpty()) {
            exoPlayer.seekTo(0, 0L)
        }
    }

    fun skipToPrevious() {
        if (exoPlayer.currentPosition > 3000) {
            // Seek to track start if more than 3 seconds in
            exoPlayer.seekTo(0L)
        } else if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
        }
    }

    fun setShuffleMode(enabled: Boolean) {
        exoPlayer.shuffleModeEnabled = enabled
        _playbackState.update { it.copy(shuffleEnabled = enabled) }
        playerScope.launch {
            playerPreferences.savePlayerModes(_playbackState.value.repeatMode.name, enabled)
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        val exoMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        exoPlayer.repeatMode = exoMode
        _playbackState.update { it.copy(repeatMode = mode) }
        playerScope.launch {
            playerPreferences.savePlayerModes(mode.name, _playbackState.value.shuffleEnabled)
        }
    }

    /**
     * Restores last played track and position on app restart.
     */
    suspend fun restoreLastPlaybackState(musicRepository: MusicRepository) {
        val (lastSongId, lastPos) = playerPreferences.getLastPlaybackState()
        if (lastSongId != null) {
            val song = musicRepository.getSongById(lastSongId)
            if (song != null) {
                activeQueue = mutableListOf(song)
                val mediaItem = buildMediaItem(song)
                exoPlayer.setMediaItem(mediaItem, lastPos)
                exoPlayer.prepare()

                _playbackState.update {
                    it.copy(
                        currentSong = song,
                        currentPositionMs = lastPos,
                        durationMs = song.durationMs,
                        queue = listOf(song),
                        currentQueueIndex = 0,
                        isPlaying = false
                    )
                }
            }
        }
    }

    private fun buildMediaItem(song: Song): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)

        if (song.artworkPath != null) {
            metadataBuilder.setArtworkUri(Uri.parse(song.artworkPath))
        }

        val playbackUri = if (song.isDownloaded && song.localFilePath != null && java.io.File(song.localFilePath).exists()) {
            Uri.fromFile(java.io.File(song.localFilePath))
        } else {
            Uri.parse(song.fileUrl)
        }

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(playbackUri)
            .setMimeType(song.mimeType)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun saveCurrentPosition() {
        val currentSong = _playbackState.value.currentSong ?: return
        val pos = exoPlayer.currentPosition
        playerScope.launch {
            playerPreferences.savePlaybackState(currentSong.id, pos)
        }
    }

    private fun startPositionTicker() {
        positionUpdateJob?.cancel()
        positionUpdateJob = playerScope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    _playbackState.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = if (dur > 0) dur else it.durationMs
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private inner class PlayerEventListener : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val isBuffering = state == Player.STATE_BUFFERING
            val duration = exoPlayer.duration.coerceAtLeast(0L)

            _playbackState.update {
                it.copy(
                    isBuffering = isBuffering,
                    durationMs = if (duration > 0) duration else it.durationMs
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (!isPlaying) {
                saveCurrentPosition()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) {
                val index = exoPlayer.currentMediaItemIndex
                val currentSong = activeQueue.getOrNull(index)
                _playbackState.update {
                    it.copy(
                        currentSong = currentSong,
                        currentQueueIndex = index,
                        durationMs = exoPlayer.duration.coerceAtLeast(0L),
                        currentPositionMs = 0L
                    )
                }
                saveCurrentPosition()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "Playback error: ${error.localizedMessage}"
                )
            }
        }
    }

    fun release() {
        positionUpdateJob?.cancel()
        exoPlayer.release()
    }
}
