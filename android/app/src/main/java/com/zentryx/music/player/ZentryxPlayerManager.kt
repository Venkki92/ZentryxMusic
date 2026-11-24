package com.zentryx.music.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.zentryx.music.download.DownloadManager
import com.zentryx.music.dsp.AndroidDspAudioEngine
import com.zentryx.music.dsp.AudioOutputRoute
import com.zentryx.music.dsp.CarDspAudioProcessor
import com.zentryx.music.dsp.matchPresetForTrack
import com.zentryx.music.models.AudioFormat
import com.zentryx.music.models.PlaybackState
import com.zentryx.music.models.RepeatMode
import com.zentryx.music.models.Song
import com.zentryx.music.repository.MusicRepository
import com.zentryx.music.visualizer.AudioFftCapturer
import com.zentryx.music.widget.ZentryxWidgetProvider
import dagger.Lazy
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
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Core Media3 ExoPlayer playback engine for Zentryx Music.
 * Routes PCM through CarDspAudioProcessor (real Butterworth HPF, L/R ms Time Alignment, Mid/Side Stereo Width),
 * attaches hardware 16-Band DynamicsProcessing + GALA SDVC boost dB stage + FFT visualizer,
 * enforces Offline Mode, supports Tunnel Guard predictive pre-caching, and handles CAN-Bus SWC commands.
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
    private val carDspAudioProcessor: CarDspAudioProcessor,
    private val fftCapturer: AudioFftCapturer,
    private val musicRepositoryLazy: Lazy<MusicRepository>,
    private val downloadManagerLazy: Lazy<DownloadManager>
) {
    companion object {
        private val EXT_TO_MIME: Map<String, String> = mapOf(
            "mp3" to MimeTypes.AUDIO_MPEG,
            "flac" to MimeTypes.AUDIO_FLAC,
            "m4a" to MimeTypes.AUDIO_MP4,
            "mp4" to MimeTypes.AUDIO_MP4,
            "aac" to MimeTypes.AUDIO_AAC,
            "ogg" to MimeTypes.AUDIO_OGG,
            "oga" to MimeTypes.AUDIO_OGG,
            "opus" to MimeTypes.AUDIO_OPUS,
            "wav" to MimeTypes.AUDIO_WAV,
            "webm" to MimeTypes.AUDIO_WEBM,
            "wma" to "audio/x-ms-wma"
        )

        /**
         * Safely resolves a local filesystem path or percent-encoded `file://` URI
         * (handling spaces, parentheses, and non-ASCII characters).
         */
        fun resolveLocalFile(pathOrUri: String): File {
            val trimmed = pathOrUri.trim()
            if (!trimmed.startsWith("file://", ignoreCase = true)) {
                return File(trimmed)
            }
            val fromJavaUri = runCatching { File(URI(trimmed)) }.getOrNull()
            if (fromJavaUri != null && fromJavaUri.exists()) {
                return fromJavaUri
            }
            val stripped = trimmed.removePrefix("file://").removePrefix("FILE://")
            val rawCandidate = File(stripped)
            if (rawCandidate.exists()) {
                return rawCandidate
            }
            val decodedCandidate = runCatching {
                File(URLDecoder.decode(stripped, "UTF-8"))
            }.getOrNull()
            return decodedCandidate ?: fromJavaUri ?: rawCandidate
        }

        /**
         * Resolves a concrete audio MIME type for Media3 ExoPlayer (never returns wildcard MIME types).
         */
        fun resolveConcreteMimeType(song: Song): String {
            for (candidate in listOf(song.localFilePath.orEmpty(), song.relativePath, song.fileUrl)) {
                if (candidate.isBlank()) continue
                val clean = candidate.substringBefore('?').substringBefore('#')
                val dot = clean.lastIndexOf('.')
                val slash = clean.lastIndexOf('/')
                if (dot > slash && dot in 0 until clean.lastIndex) {
                    val ext = clean.substring(dot + 1).lowercase(Locale.US)
                    EXT_TO_MIME[ext]?.let { return it }
                }
            }

            val cleanMime = song.mimeType.substringBefore(';').trim().lowercase(Locale.US)
            if (cleanMime.isNotEmpty() && cleanMime != "audio/*" && cleanMime != "*/*" && cleanMime != "application/octet-stream") {
                return when (cleanMime) {
                    "audio/mp3" -> MimeTypes.AUDIO_MPEG
                    "audio/x-flac" -> MimeTypes.AUDIO_FLAC
                    "audio/x-m4a", "audio/m4a" -> MimeTypes.AUDIO_MP4
                    "audio/x-wav" -> MimeTypes.AUDIO_WAV
                    else -> if (cleanMime.startsWith("audio/") || cleanMime == "application/ogg") cleanMime else MimeTypes.AUDIO_MPEG
                }
            }

            return when (song.format) {
                AudioFormat.MP3 -> MimeTypes.AUDIO_MPEG
                AudioFormat.FLAC -> MimeTypes.AUDIO_FLAC
                AudioFormat.AAC -> MimeTypes.AUDIO_AAC
                AudioFormat.M4A -> MimeTypes.AUDIO_MP4
                AudioFormat.OGG -> MimeTypes.AUDIO_OGG
                AudioFormat.OPUS -> MimeTypes.AUDIO_OPUS
                AudioFormat.WAV -> MimeTypes.AUDIO_WAV
            }
        }

        fun replayGainDbToLinear(db: Float?): Float {
            if (db == null || !db.isFinite()) return 1.0f
            val clamped = db.coerceIn(-15f, 6f)
            return 10f.pow(clamped / 20f).coerceIn(0.18f, 2.0f)
        }

        fun buildSignalPathBadge(
            song: Song?,
            activeSampleRateHz: Int,
            replayGainAppliedDb: Float?
        ): String? {
            if (song == null) return null
            val parts = mutableListOf(song.format.name)
            val effectiveRate = song.sampleRateHz ?: activeSampleRateHz.takeIf { it > 0 }
            val khz = effectiveRate?.let { hz ->
                if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(Locale.US, "%.1f kHz", hz / 1000f)
            }
            when {
                song.bitsPerSample != null && khz != null -> parts.add("${song.bitsPerSample}-bit/$khz")
                khz != null -> parts.add(khz)
            }
            song.bitrateKbps?.takeIf { it > 0 }?.let { parts.add("$it kbps") }
            if (song.sourceType == "LOCAL") {
                parts.add("USB/LOCAL")
            }
            if (replayGainAppliedDb != null) {
                parts.add(String.format(Locale.US, "RG %+.1fdB", replayGainAppliedDb))
            }
            return parts.joinToString(" · ")
        }
    }

    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionUpdateJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var softStartJob: Job? = null
    private var crossfadeInJob: Job? = null
    private var gapPauseJob: Job? = null
    private var prefetchJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _isAttenuated = MutableStateFlow(false)
    val isAttenuated: StateFlow<Boolean> = _isAttenuated.asStateFlow()

    private val _isReverseDucked = MutableStateFlow(false)
    val isReverseDucked: StateFlow<Boolean> = _isReverseDucked.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private val _sleepTimerRemainingSec = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSec: StateFlow<Int?> = _sleepTimerRemainingSec.asStateFlow()

    val activeOutputRoute: StateFlow<AudioOutputRoute> = dspAudioEngine.activeOutputRoute
    val currentSpeedKmH: StateFlow<Float> = speedVolumeController.currentSpeedKmH

    private var activeQueue = mutableListOf<Song>()
    private var manualPresetOverrideSongId: String? = null
    private var currentSpeedMultiplier = 1.0f
    private var currentSoftStartGain = 1.0f
    private var currentSleepFadeGain = 1.0f
    private var currentTransitionDipGain = 1.0f
    private var currentCrossfadeGain = 1.0f
    private var currentPlaybackRate = 1.0f
    private var isGaplessEnabled = true
    private var isReplayGainEnabled = true
    private var crossfadeSeconds = 0
    private var isOfflineOnly = false
    private var bufferMode = "RAM_SAVER"

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
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(cacheDataSourceFactory.createDataSourceFactory())

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(carDspAudioProcessor))
                    .build()
            }
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                // Guarantee Media3 always decodes to PCM through CarDspAudioProcessor
                // instead of offloading compressed streams directly to OEM audio DSPs.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setAudioOffloadPreferences(
                        TrackSelectionParameters.AudioOffloadPreferences.Builder()
                            .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                            .build()
                    )
                    .build()
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
                currentSpeedMultiplier = multiplier.coerceIn(0.5f, 1.8f)
                syncOutputGain()
            }
        }

        playerScope.launch {
            speedVolumeController.currentSpeedKmH.collectLatest { speedKmH ->
                dspAudioEngine.updateVehicleSpeedKmH(speedKmH)
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

        playerScope.launch {
            settingsPreferences.gaplessEnabledFlow.collectLatest { gapless ->
                isGaplessEnabled = gapless
            }
        }

        playerScope.launch {
            settingsPreferences.offlineOnlyFlow.collectLatest { offline ->
                isOfflineOnly = offline
            }
        }

        playerScope.launch {
            settingsPreferences.bufferModeFlow.collectLatest { mode ->
                bufferMode = mode
            }
        }

        playerScope.launch {
            settingsPreferences.replayGainEnabledFlow.collectLatest { rgEnabled ->
                isReplayGainEnabled = rgEnabled
                applyTrackDspAndSignalPath(_playbackState.value.currentSong)
            }
        }

        playerScope.launch {
            settingsPreferences.crossfadeSecondsFlow.collectLatest { sec ->
                crossfadeSeconds = sec.coerceIn(0, 8)
                if (crossfadeSeconds == 0) {
                    crossfadeInJob?.cancel()
                    currentCrossfadeGain = 1.0f
                    syncOutputGain()
                }
            }
        }

        playerScope.launch {
            downloadManagerLazy.get().downloadStatusUpdates.collect { update ->
                var changed = false
                activeQueue = activeQueue.map { item ->
                    if (item.id == update.songId) {
                        changed = true
                        item.copy(
                            isDownloaded = update.isDownloaded,
                            localFilePath = update.localFilePath
                        )
                    } else {
                        item
                    }
                }.toMutableList()
                val cur = _playbackState.value.currentSong
                if (changed || cur?.id == update.songId) {
                    _playbackState.update { state ->
                        val updatedCurrent = if (state.currentSong?.id == update.songId) {
                            state.currentSong.copy(
                                isDownloaded = update.isDownloaded,
                                localFilePath = update.localFilePath
                            )
                        } else {
                            state.currentSong
                        }
                        state.copy(
                            currentSong = updatedCurrent,
                            queue = activeQueue.toList()
                        )
                    }
                }
            }
        }
    }

    fun markManualPresetOverrideForCurrentTrack() {
        manualPresetOverrideSongId = _playbackState.value.currentSong?.id
    }

    private fun applyAutoGenreAndRecordPlay(song: Song?) {
        if (song == null) return
        playerScope.launch {
            runCatching { musicRepositoryLazy.get().recordTrackPlay(song.id) }
        }
        val dsp = dspAudioEngine.dspSettings.value
        if (dsp.autoGenrePresetEnabled && song.id != manualPresetOverrideSongId) {
            val matchedPresetId = matchPresetForTrack(song.genre, song.relativePath, song.title)
            if (matchedPresetId != null && matchedPresetId != dsp.selectedPresetId) {
                dspAudioEngine.applyPresetById(matchedPresetId)
            }
        }
    }

    private fun applyTrackDspAndSignalPath(song: Song?) {
        val appliedDb = if (isReplayGainEnabled) song?.replayGainDb else null
        carDspAudioProcessor.setReplayGainLinear(replayGainDbToLinear(appliedDb))
        val badge = buildSignalPathBadge(song, carDspAudioProcessor.activeSampleRateHz, appliedDb)
        _playbackState.update {
            it.copy(
                signalPathBadge = badge,
                replayGainAppliedDb = appliedDb
            )
        }
        ZentryxWidgetProvider.updateWidgetFromState(context, _playbackState.value, _isAttenuated.value)
    }

    private fun syncOutputGain() {
        // Apply GALA / SDVC speed boost (> 1.0) safely via hardware DSP preamp + brickwall limiter
        val sdvcBoostDb = if (currentSpeedMultiplier > 1.0f) {
            (20f * log10(currentSpeedMultiplier)).coerceIn(0f, 5.5f)
        } else {
            0f
        }
        dspAudioEngine.setDynamicSdvcBoostDb(sdvcBoostDb)

        var gain = 1.0f
        if (_isMuted.value) {
            gain = 0f
        } else if (_isAttenuated.value || _isReverseDucked.value) {
            gain *= 0.1f // -20 dB cockpit talk / reverse-gear parking sensor attenuation
        }
        gain *= currentSoftStartGain * currentSleepFadeGain * currentTransitionDipGain * currentCrossfadeGain
        exoPlayer.volume = gain.coerceIn(0f, 1.0f)
    }

    private fun isLocallyAvailable(song: Song): Boolean {
        if (!song.isDownloaded || song.localFilePath.isNullOrBlank()) return false
        val path = song.localFilePath
        return if (path.startsWith("content://", ignoreCase = true)) {
            try {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(path), "r")?.use { true } ?: false
            } catch (_: Throwable) {
                false
            }
        } else {
            val resolvedFile = resolveLocalFile(path)
            resolvedFile.exists() && resolvedFile.isFile && resolvedFile.canRead()
        }
    }

    fun isSongPlayable(song: Song): Boolean {
        return !isOfflineOnly || isLocallyAvailable(song)
    }

    fun syncExternalQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        playerScope.launch {
            val safeIdx = startIndex.coerceIn(0, songs.lastIndex)
            val activeSong = activeQueue.getOrNull(safeIdx) ?: songs.getOrNull(safeIdx)
            activeQueue = songs.toMutableList()
            currentSoftStartGain = 1.0f
            currentTransitionDipGain = 1.0f
            currentCrossfadeGain = 1.0f
            syncOutputGain()
            attachAudioEffectsIfReady()
            scheduleTunnelPrefetch(safeIdx)
            _playbackState.update {
                it.copy(
                    currentSong = activeSong,
                    queue = activeQueue,
                    currentQueueIndex = safeIdx,
                    errorMessage = null
                )
            }
            applyTrackDspAndSignalPath(activeSong)
        }
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song), startPositionMs: Long = 0L) {
        if (isOfflineOnly && !isLocallyAvailable(song)) {
            _playbackState.update {
                it.copy(errorMessage = "Offline Mode active: download \"${song.title}\" first to play without internet.")
            }
            return
        }

        val filteredQueue = if (isOfflineOnly) {
            queue.filter { isLocallyAvailable(it) }.ifEmpty { listOf(song) }
        } else {
            queue.ifEmpty { listOf(song) }
        }

        if (manualPresetOverrideSongId != song.id) {
            manualPresetOverrideSongId = null
        }

        activeQueue = filteredQueue.toMutableList()
        val index = activeQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        val mediaItems = activeQueue.map { buildMediaItem(it) }
        val safeStartPos = startPositionMs.coerceAtLeast(0L)

        crossfadeInJob?.cancel()
        currentSoftStartGain = 1.0f
        currentTransitionDipGain = 1.0f
        currentCrossfadeGain = 1.0f
        syncOutputGain()

        exoPlayer.setMediaItems(mediaItems, index, safeStartPos)
        exoPlayer.playbackParameters = PlaybackParameters(currentPlaybackRate)
        exoPlayer.prepare()
        exoPlayer.play()

        attachAudioEffectsIfReady()
        scheduleTunnelPrefetch(index)

        _playbackState.update {
            it.copy(
                currentSong = song,
                queue = activeQueue,
                currentQueueIndex = index,
                currentPositionMs = safeStartPos,
                isPlaying = true,
                errorMessage = null
            )
        }
        applyTrackDspAndSignalPath(song)
        applyAutoGenreAndRecordPlay(song)
    }

    fun resumeSong(song: Song, queue: List<Song> = listOf(song)) {
        val resumePos = if (
            song.lastPositionMs > 5_000L &&
            (song.durationMs <= 0L || song.lastPositionMs < song.durationMs - 10_000L)
        ) {
            song.lastPositionMs
        } else {
            0L
        }
        playSong(song, queue, startPositionMs = resumePos)
    }

    fun playNext(song: Song): Boolean {
        if (isOfflineOnly && !isLocallyAvailable(song)) {
            _playbackState.update {
                it.copy(errorMessage = "Offline Mode active: download \"${song.title}\" first.")
            }
            return false
        }
        if (activeQueue.isEmpty() || _playbackState.value.currentSong == null) {
            playSong(song)
            return true
        }
        val currentIdx = exoPlayer.currentMediaItemIndex.coerceIn(0, activeQueue.lastIndex)
        val insertIdx = (currentIdx + 1).coerceAtMost(activeQueue.size)
        activeQueue.add(insertIdx, song)
        exoPlayer.addMediaItem(insertIdx, buildMediaItem(song))
        _playbackState.update {
            it.copy(
                queue = activeQueue.toList(),
                currentQueueIndex = exoPlayer.currentMediaItemIndex.coerceIn(0, activeQueue.lastIndex)
            )
        }
        scheduleTunnelPrefetch(exoPlayer.currentMediaItemIndex)
        return true
    }

    fun addToQueue(song: Song): Boolean {
        if (isOfflineOnly && !isLocallyAvailable(song)) {
            _playbackState.update {
                it.copy(errorMessage = "Offline Mode active: download \"${song.title}\" first.")
            }
            return false
        }
        if (activeQueue.isEmpty() || _playbackState.value.currentSong == null) {
            playSong(song)
            return true
        }
        activeQueue.add(song)
        exoPlayer.addMediaItem(buildMediaItem(song))
        _playbackState.update { it.copy(queue = activeQueue.toList()) }
        return true
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in activeQueue.indices || toIndex !in activeQueue.indices || fromIndex == toIndex) return
        val moved = activeQueue.removeAt(fromIndex)
        activeQueue.add(toIndex, moved)
        exoPlayer.moveMediaItem(fromIndex, toIndex)
        val newIdx = exoPlayer.currentMediaItemIndex.takeIf { it in activeQueue.indices }
            ?: activeQueue.indexOfFirst { it.id == _playbackState.value.currentSong?.id }.coerceAtLeast(0)
        _playbackState.update {
            it.copy(
                queue = activeQueue.toList(),
                currentQueueIndex = newIdx,
                currentSong = activeQueue.getOrNull(newIdx) ?: it.currentSong
            )
        }
        scheduleTunnelPrefetch(newIdx)
    }

    fun removeQueueItem(index: Int) {
        if (index !in activeQueue.indices) return
        if (activeQueue.size <= 1) {
            clearQueue(keepCurrent = false)
            return
        }
        val curIdx = exoPlayer.currentMediaItemIndex
        activeQueue.removeAt(index)
        exoPlayer.removeMediaItem(index)
        val nextIdx = exoPlayer.currentMediaItemIndex.coerceIn(0, activeQueue.lastIndex)
        val nextSong = activeQueue.getOrNull(nextIdx)
        _playbackState.update {
            it.copy(
                queue = activeQueue.toList(),
                currentQueueIndex = nextIdx,
                currentSong = nextSong
            )
        }
        if (index == curIdx) {
            applyTrackDspAndSignalPath(nextSong)
        }
        scheduleTunnelPrefetch(nextIdx)
    }

    fun clearQueue(keepCurrent: Boolean = true) {
        if (activeQueue.isEmpty()) return
        if (keepCurrent) {
            val curIdx = exoPlayer.currentMediaItemIndex.coerceIn(0, activeQueue.lastIndex)
            val curSong = activeQueue.getOrNull(curIdx) ?: return
            if (curIdx + 1 < exoPlayer.mediaItemCount) {
                exoPlayer.removeMediaItems(curIdx + 1, exoPlayer.mediaItemCount)
            }
            if (curIdx > 0) {
                exoPlayer.removeMediaItems(0, curIdx)
            }
            activeQueue = mutableListOf(curSong)
            _playbackState.update {
                it.copy(
                    queue = activeQueue.toList(),
                    currentQueueIndex = 0,
                    currentSong = curSong
                )
            }
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            activeQueue.clear()
            _playbackState.update {
                it.copy(
                    currentSong = null,
                    queue = emptyList(),
                    currentQueueIndex = 0,
                    isPlaying = false,
                    currentPositionMs = 0L,
                    durationMs = 0L,
                    signalPathBadge = null
                )
            }
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
        val safe = positionMs.coerceAtLeast(0L)
        exoPlayer.seekTo(safe)
        _playbackState.update { it.copy(currentPositionMs = safe) }
        saveCurrentPosition()
    }

    fun seekBy(deltaMs: Long) {
        val cur = exoPlayer.currentPosition.coerceAtLeast(0L)
        val dur = exoPlayer.duration.takeIf { it > 0L }
            ?: _playbackState.value.durationMs.takeIf { it > 0L }
            ?: Long.MAX_VALUE
        seekTo((cur + deltaMs).coerceIn(0L, dur))
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

    /**
     * Jumps cleanly to the first track of the next album (either later in current queue or in Room DB).
     */
    fun skipToNextAlbum() {
        val currentIdx = exoPlayer.currentMediaItemIndex
        val currentAlbum = activeQueue.getOrNull(currentIdx)?.album
        if (currentIdx >= 0 && activeQueue.size > 1) {
            for (i in (currentIdx + 1) until activeQueue.size) {
                if (activeQueue[i].album != currentAlbum) {
                    exoPlayer.seekTo(i, 0L)
                    return
                }
            }
        }

        playerScope.launch {
            val repo = musicRepositoryLazy.get()
            val albums = repo.getAlbums().firstOrNull().orEmpty()
            if (albums.isEmpty()) {
                skipToNext()
                return@launch
            }
            val curAlbumIdx = albums.indexOfFirst { it.name == currentAlbum }
            val nextAlbum = albums[(curAlbumIdx + 1) % albums.size]
            val albumSongs = repo.getSongsByAlbum(nextAlbum.name, nextAlbum.artist).firstOrNull().orEmpty()
            val playableSongs = if (isOfflineOnly) albumSongs.filter { isLocallyAvailable(it) } else albumSongs
            if (playableSongs.isNotEmpty()) {
                playSong(playableSongs.first(), playableSongs)
            } else {
                skipToNext()
            }
        }
    }

    /**
     * Plays a Nextcloud folder or album group immediately from the split-screen rail or Quick-Swap drawer.
     */
    fun playSwapGroup(group: SwapMediaGroup): Boolean {
        val playable = if (isOfflineOnly) {
            group.songs.filter { isLocallyAvailable(it) }
        } else {
            group.songs
        }
        if (playable.isEmpty()) {
            _playbackState.update {
                it.copy(errorMessage = "No playable tracks in \"${group.displayName}\" (Offline Mode active)")
            }
            return false
        }
        playSong(playable.first(), playable)
        return true
    }

    /**
     * Jumps cleanly to the previous (-1) or next (+1) Nextcloud folder or album on the go.
     */
    suspend fun swapToAdjacentGroup(
        delta: Int,
        mode: SwapGroupMode,
        pinnedFolders: Set<String>
    ): Pair<SwapMediaGroup, Int>? {
        val allSongs = musicRepositoryLazy.get().getAllSongs().firstOrNull().orEmpty()
        val playablePool = if (isOfflineOnly) allSongs.filter { isLocallyAvailable(it) } else allSongs
        if (playablePool.isEmpty()) return null
        val groups = when (mode) {
            SwapGroupMode.FOLDERS -> buildFolderSwapGroups(playablePool, pinnedFolders)
            SwapGroupMode.ALBUMS -> buildAlbumSwapGroups(playablePool, pinnedFolders)
        }
        val nextPair = findAdjacentSwapGroup(groups, _playbackState.value.currentSong, mode, delta) ?: return null
        playSong(nextPair.first.songs.first(), nextPair.first.songs)
        return nextPair
    }

    /**
     * Toggles the Heart / Favorite state of the currently playing track from Steering Wheel / UI.
     */
    fun toggleFavoriteCurrentSong() {
        val current = _playbackState.value.currentSong ?: return
        val nextFav = !current.isFavorite
        playerScope.launch {
            musicRepositoryLazy.get().setFavorite(current.id, nextFav)
            val updatedSong = current.copy(isFavorite = nextFav)
            activeQueue = activeQueue.map { if (it.id == current.id) updatedSong else it }.toMutableList()
            _playbackState.update {
                it.copy(
                    currentSong = if (it.currentSong?.id == current.id) updatedSong else it.currentSong,
                    queue = activeQueue
                )
            }
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        syncOutputGain()
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
        ZentryxWidgetProvider.updateWidgetFromState(context, _playbackState.value, _isAttenuated.value)
    }

    fun setReverseGearDucking(active: Boolean) {
        if (_isReverseDucked.value == active) return
        _isReverseDucked.value = active
        syncOutputGain()
    }

    fun cycleSurroundMode() = dspAudioEngine.cycleSurroundMode()

    fun cycleEqPreset() = dspAudioEngine.cycleEqPreset()

    fun startCruisingMix(reshuffle: Boolean = true, sortByGenre: Boolean? = null) {
        playerScope.launch {
            val effectiveSortByGenre = sortByGenre
                ?: settingsPreferences.cruisingSortByGenreFlow.firstOrNull()
                ?: true
            playSmartMixInternal(
                mixType = SmartMixType.CRUISING,
                reshuffle = reshuffle,
                sortByGenre = effectiveSortByGenre,
                fullLibrary = true
            )
        }
    }

    suspend fun playSmartMix(
        mixType: SmartMixType,
        reshuffle: Boolean = false,
        sortByGenre: Boolean = true,
        fullLibrary: Boolean = (mixType == SmartMixType.CRUISING)
    ): Pair<Int, Int> {
        return playSmartMixInternal(
            mixType = mixType,
            reshuffle = reshuffle,
            sortByGenre = sortByGenre,
            fullLibrary = fullLibrary
        )
    }

    private suspend fun playSmartMixInternal(
        mixType: SmartMixType,
        reshuffle: Boolean,
        sortByGenre: Boolean,
        fullLibrary: Boolean
    ): Pair<Int, Int> {
        val all = musicRepositoryLazy.get().getAllSongs().firstOrNull().orEmpty()
            .filter { isSongPlayable(it) }
        if (all.isEmpty()) return 0 to 0

        val genreAware = mixType == SmartMixType.CRUISING ||
            mixType == SmartMixType.DEEP_CUTS ||
            mixType == SmartMixType.FAVORITES_RADIO

        val limit = when {
            mixType == SmartMixType.CRUISING && fullLibrary -> all.size
            mixType == SmartMixType.FAVORITES_RADIO -> maxOf(all.size, 40)
            else -> 40
        }

        val mix = SmartMixEngine.buildSmartMixSongs(
            songs = all,
            mixType = mixType,
            limit = limit,
            reshuffle = reshuffle || mixType == SmartMixType.DEEP_CUTS || mixType == SmartMixType.FAVORITES_RADIO,
            sortByGenre = if (genreAware) sortByGenre else false
        )

        if (mix.isEmpty()) return 0 to 0

        // Keep sequential playback when genre-sorted so tracks flow cleanly genre-by-genre
        if (genreAware && sortByGenre) {
            setShuffleMode(false)
        }
        playSong(mix.first(), mix)

        val genreCount = if (genreAware && sortByGenre) {
            SmartMixEngine.summarizeMixGenres(mix).genreCount
        } else {
            0
        }
        return mix.size to genreCount
    }

    fun resumeOrStartOnBluetooth() {
        playerScope.launch {
            if (exoPlayer.isPlaying) return@launch
            if (exoPlayer.mediaItemCount > 0) {
                play()
            } else {
                restoreLastPlaybackState(musicRepositoryLazy.get())
            }
        }
    }

    fun clearErrorMessage() {
        _playbackState.update { it.copy(errorMessage = null) }
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

    suspend fun restoreLastPlaybackState(musicRepository: MusicRepository) {
        val autoResumeEnabled = settingsPreferences.autoResumeOnBootFlow.firstOrNull() ?: true
        if (!autoResumeEnabled) return

        val (lastSongId, lastPos) = playerPreferences.getLastPlaybackState()
        if (lastSongId != null) {
            val song = musicRepository.getSongById(lastSongId)
            if (song != null && (!isOfflineOnly || isLocallyAvailable(song))) {
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
                applyTrackDspAndSignalPath(song)
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

    private fun triggerNonGaplessSpacing() {
        gapPauseJob?.cancel()
        currentTransitionDipGain = 0f
        syncOutputGain()
        gapPauseJob = playerScope.launch {
            delay(350L)
            currentTransitionDipGain = 1.0f
            syncOutputGain()
        }
    }

    private fun triggerCrossfadeIn() {
        crossfadeInJob?.cancel()
        val fadeInMs = (crossfadeSeconds.coerceIn(1, 8).coerceAtMost(4) * 500L)
        val steps = 16
        val stepMs = (fadeInMs / steps).coerceAtLeast(30L)
        currentCrossfadeGain = 0.12f
        syncOutputGain()
        crossfadeInJob = playerScope.launch {
            for (i in 1..steps) {
                delay(stepMs)
                val p = i.toFloat() / steps
                currentCrossfadeGain = sqrt(p).coerceIn(0.12f, 1.0f)
                syncOutputGain()
            }
            currentCrossfadeGain = 1.0f
            syncOutputGain()
        }
    }

    private fun scheduleTunnelPrefetch(currentIndex: Int) {
        prefetchJob?.cancel()
        if (bufferMode != "TUNNEL_GUARD" || activeQueue.size <= 1) return

        prefetchJob = playerScope.launch(Dispatchers.IO) {
            val lookAheadCount = 2
            for (offset in 1..lookAheadCount) {
                val targetIdx = currentIndex + offset
                if (targetIdx >= activeQueue.size) break
                val nextSong = activeQueue[targetIdx]
                if (isLocallyAvailable(nextSong)) continue
                try {
                    val cacheDs = cacheDataSourceFactory.createCacheDataSource()
                    val dataSpec = DataSpec.Builder()
                        .setUri(Uri.parse(nextSong.fileUrl))
                        .setPosition(0L)
                        .setLength(4L * 1024L * 1024L) // 4 MiB tunnel head-buffer
                        .build()
                    CacheWriter(cacheDs, dataSpec, null, null).cache()
                } catch (_: Throwable) {
                    // Best-effort prefetch
                }
            }
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

    fun buildMediaItem(song: Song): MediaItem {
        val localPath = song.localFilePath?.takeIf { isLocallyAvailable(song) && it.isNotBlank() }
        val uri: Uri = when {
            localPath == null -> Uri.parse(song.fileUrl)
            localPath.startsWith("content://", ignoreCase = true) -> Uri.parse(localPath)
            localPath.startsWith("file://", ignoreCase = true) -> Uri.fromFile(resolveLocalFile(localPath))
            else -> Uri.fromFile(File(localPath))
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)

        if (!song.artworkPath.isNullOrBlank()) {
            metadataBuilder.setArtworkUri(Uri.parse(song.artworkPath))
        }

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMimeType(resolveConcreteMimeType(song))
            .setCustomCacheKey(song.id)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun saveCurrentPosition() {
        val currentSong = _playbackState.value.currentSong ?: return
        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
        playerScope.launch {
            playerPreferences.savePlaybackState(currentSong.id, pos)
            runCatching { musicRepositoryLazy.get().updateResumePosition(currentSong.id, pos) }
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

                    // Equal-power DJ Crossfade fade-out ramp during final N seconds before track transition
                    val cfWindowMs = crossfadeSeconds * 1000L
                    if (crossfadeSeconds > 0 && dur > cfWindowMs + 1500L && exoPlayer.hasNextMediaItem()) {
                        val remainingMs = dur - pos
                        if (remainingMs in 1..cfWindowMs) {
                            val ratio = (remainingMs.toFloat() / cfWindowMs.toFloat()).coerceIn(0.04f, 1.0f)
                            currentCrossfadeGain = sqrt(ratio)
                            syncOutputGain()
                        } else if (crossfadeInJob?.isActive != true && currentCrossfadeGain != 1.0f) {
                            currentCrossfadeGain = 1.0f
                            syncOutputGain()
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private inner class PlayerEventListener : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0) {
                dspAudioEngine.attachAudioSession(audioSessionId)
                fftCapturer.attachAudioSession(audioSessionId, exoPlayer.isPlaying)
                applyTrackDspAndSignalPath(_playbackState.value.currentSong)
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
            ZentryxWidgetProvider.updateWidgetFromState(context, _playbackState.value, _isAttenuated.value)
            if (!isPlaying) {
                saveCurrentPosition()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) {
                val index = exoPlayer.currentMediaItemIndex
                val currentSong = activeQueue.getOrNull(index)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    if (crossfadeSeconds > 0) {
                        triggerCrossfadeIn()
                    } else if (!isGaplessEnabled) {
                        triggerNonGaplessSpacing()
                    }
                } else {
                    crossfadeInJob?.cancel()
                    currentCrossfadeGain = 1.0f
                    syncOutputGain()
                }
                scheduleTunnelPrefetch(index)
                if (manualPresetOverrideSongId != currentSong?.id) {
                    manualPresetOverrideSongId = null
                }
                _playbackState.update {
                    it.copy(
                        currentSong = currentSong,
                        currentQueueIndex = index,
                        durationMs = exoPlayer.duration.coerceAtLeast(0L),
                        currentPositionMs = 0L
                    )
                }
                applyTrackDspAndSignalPath(currentSong)
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    applyAutoGenreAndRecordPlay(currentSong)
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
            ZentryxWidgetProvider.updateWidgetFromState(context, _playbackState.value, _isAttenuated.value)
        }
    }

    fun release() {
        positionUpdateJob?.cancel()
        sleepTimerJob?.cancel()
        softStartJob?.cancel()
        crossfadeInJob?.cancel()
        gapPauseJob?.cancel()
        prefetchJob?.cancel()
        fftCapturer.release()
        dspAudioEngine.releaseEffects()
        exoPlayer.release()
    }
}
