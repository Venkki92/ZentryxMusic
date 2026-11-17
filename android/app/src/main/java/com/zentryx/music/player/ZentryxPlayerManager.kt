package com.zentryx.music.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.zentryx.music.dsp.AndroidDspAudioEngine
import com.zentryx.music.models.PlaybackState
import com.zentryx.music.models.RepeatMode
import com.zentryx.music.models.Song
import com.zentryx.music.repository.MusicRepository
import com.zentryx.music.visualizer.AudioFftCapturer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Core Media3 ExoPlayer playback engine for Zentryx Music.
 * Manages audio focus ducking, gapless transitions, hardware DSP + FFT visualizer session binding,
 * GPS SDVC gain scaling, -20dB talk attenuation, playback speed, sleep timer fade-out, and soft-start resume.
 */
@OptIn(UnstableApi::class)
@Singleton
class ZentryxPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheDataSourceFactory: CacheDataSourceFactory,
    private val playerPreferences: PlayerPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val speedVolumeController: SpeedVolumeController,
    private val engineStandbyController: EngineStandbyController,
    private val dspAudioEngine: AndroidDspAudioEngine,
    private val fftCapturer: AudioFftCapturer
) {
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionUpdateJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var softStartJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _isAttenuated = MutableStateFlow(false)
    val isAttenuated: StateFlow<Boolean> = _isAttenuated.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private val _sleepTimerRemainingSec = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSec: StateFlow<Int?> = _sleepTimerRemainingSec.asStateFlow()

    private var activeQueue = mutableListOf<Song>()
    private var currentSpeedMultiplier = 1.0f
    private var currentSoftStartGain = 1.0f
    private var currentSleepFadeGain = 1.0f
    private var currentPlaybackRate = 1.0f

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    // Tuned LoadControl: preserves RAM on AutoChips AC8259P headunits
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(5000, 15000, 1500, 3000)
        .setBackBuffer(10000, true)
        .build()

    val exoPlayer: ExoPlayer by lazy {
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory.createDataSourceFactory())

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                addListener(PlayerEventListener())
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0) {
                    dspAudioEngine.attachAudioSession(audioSessionId)
                    fftCapturer.attachAudioSession(audioSessionId, isPlaying)
                }
            }
    }

    init {
        startPositionTicker()
        engineStandbyController.attachPlayer(exoPlayer)

        playerScope.launch {
            speedVolumeController.volumeMultiplier.collectLatest { multiplier ->
                currentSpeedMultiplier = multiplier.coerceIn(0f, 1.5f)
                syncOutputGain()
            }
        }

        playerScope.launch {
            settingsPreferences.playbackSpeedFlow.collectLatest { speed ->
                currentPlaybackRate = speed
                exoPlayer.playbackParameters = PlaybackParameters(speed)
            }
        }

        playerScope.launch {
            settingsPreferences.duckingEnabledFlow.collectLatest { duckEnabled ->
                exoPlayer.setAudioAttributes(audioAttributes, duckEnabled)
            }
        }
    }

    private fun syncOutputGain() {
        var gain = min(1.0f, currentSpeedMultiplier)
        if (_isAttenuated.value) {
            gain *= 0.1f // -20 dB cockpit talk attenuation
        }
        gain *= currentSoftStartGain * currentSleepFadeGain
        exoPlayer.volume = gain.coerceIn(0f, 1.0f)
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        activeQueue = queue.toMutableList()
        val index = activeQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        val mediaItems = activeQueue.map { buildMediaItem(it) }

        currentSoftStartGain = 1.0f
        syncOutputGain()

        exoPlayer.setMediaItems(mediaItems, index, 0L)
        exoPlayer.playbackParameters = PlaybackParameters(currentPlaybackRate)
        exoPlayer.prepare()
        exoPlayer.play()

        attachAudioEffectsIfReady()

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
        attachAudioEffectsIfReady()
    }

    fun pause() {
        exoPlayer.pause()
        saveCurrentPosition()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) pause() else play()
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

    fun toggleAttenuation() {
        _isAttenuated.value = !_isAttenuated.value
        syncOutputGain()
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        if (minutes <= 0) {
            _sleepTimerRemainingSec.value = null
            currentSleepFadeGain = 1.0f
            syncOutputGain()
            return
        }

        val totalSeconds = minutes * 60
        _sleepTimerRemainingSec.value = totalSeconds
        sleepTimerJob = playerScope.launch {
            var remaining = totalSeconds
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _sleepTimerRemainingSec.value = remaining
                currentSleepFadeGain = if (remaining <= 10) {
                    (remaining / 10f).coerceIn(0f, 1f)
                } else {
                    1.0f
                }
                syncOutputGain()
            }
            pause()
            _sleepTimerMinutes.value = 0
            _sleepTimerRemainingSec.value = null
            currentSleepFadeGain = 1.0f
            syncOutputGain()
        }
    }

    /**
     * Restores last played track and position on ignition / app boot.
     * Respects autoResumeOnBoot and autoResumeSoftRamp preferences.
     */
    suspend fun restoreLastPlaybackState(musicRepository: MusicRepository) {
        val autoResumeEnabled = settingsPreferences.autoResumeOnBootFlow.firstOrNull() ?: true
        if (!autoResumeEnabled) return

        val (lastSongId, lastPos) = playerPreferences.getLastPlaybackState()
        if (lastSongId != null) {
            val song = musicRepository.getSongById(lastSongId)
            if (song != null) {
                activeQueue = mutableListOf(song)
                val softRamp = settingsPreferences.autoResumeSoftRampFlow.firstOrNull() ?: true
                if (softRamp) {
                    triggerSoftStartRamp()
                }

                val mediaItem = buildMediaItem(song)
                exoPlayer.setMediaItem(mediaItem, lastPos)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                attachAudioEffectsIfReady()

                _playbackState.update {
                    it.copy(
                        currentSong = song,
                        currentPositionMs = lastPos,
                        durationMs = song.durationMs,
                        queue = listOf(song),
                        currentQueueIndex = 0,
                        isPlaying = true
                    )
                }
            }
        }
    }

    private fun triggerSoftStartRamp() {
        softStartJob?.cancel()
        currentSoftStartGain = 0f
        syncOutputGain()
        softStartJob = playerScope.launch {
            val steps = 28
            for (i in 1..steps) {
                delay(50L)
                currentSoftStartGain = (i.toFloat() / steps).coerceIn(0f, 1f)
                syncOutputGain()
            }
            currentSoftStartGain = 1.0f
            syncOutputGain()
        }
    }

    fun onVisualizerPermissionUpdated() {
        attachAudioEffectsIfReady()
    }

    private fun attachAudioEffectsIfReady() {
        val sessionId = exoPlayer.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0) {
            dspAudioEngine.attachAudioSession(sessionId)
            fftCapturer.attachAudioSession(sessionId, exoPlayer.isPlaying)
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
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0) {
                dspAudioEngine.attachAudioSession(audioSessionId)
                fftCapturer.attachAudioSession(audioSessionId, exoPlayer.isPlaying)
            }
        }

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
            fftCapturer.setCaptureActive(isPlaying)
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
        sleepTimerJob?.cancel()
        softStartJob?.cancel()
        fftCapturer.release()
        dspAudioEngine.releaseEffects()
        exoPlayer.release()
    }
}
