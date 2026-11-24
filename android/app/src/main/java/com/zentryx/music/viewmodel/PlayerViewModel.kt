package com.zentryx.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zentryx.music.download.DownloadFeedbackEvent
import com.zentryx.music.download.DownloadManager
import com.zentryx.music.download.WifiAutoCacheManager
import com.zentryx.music.download.WifiAutoCacheState
import com.zentryx.music.dsp.AndroidDspAudioEngine
import com.zentryx.music.dsp.AudioOutputRoute
import com.zentryx.music.dsp.DspSettings
import com.zentryx.music.dsp.SurroundMode
import com.zentryx.music.lyrics.LyricsParser
import com.zentryx.music.lyrics.ParsedLyrics
import com.zentryx.music.models.PlaybackState
import com.zentryx.music.models.Playlist
import com.zentryx.music.models.RepeatMode
import com.zentryx.music.player.CockpitThemeMode
import com.zentryx.music.player.MbuxAccentColor
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.player.SpeedVolumeController
import com.zentryx.music.player.SwapGroupMode
import com.zentryx.music.player.SwapMediaGroup
import com.zentryx.music.player.ZentryxPlayerManager
import com.zentryx.music.player.buildAlbumSwapGroups
import com.zentryx.music.player.buildFolderSwapGroups
import com.zentryx.music.repository.MusicRepository
import com.zentryx.music.visualizer.AudioFftCapturer
import com.zentryx.music.visualizer.VisualizerColorway
import com.zentryx.music.visualizer.VisualizerFrame
import com.zentryx.music.visualizer.VisualizerMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: ZentryxPlayerManager,
    private val musicRepository: MusicRepository,
    private val downloadManager: DownloadManager,
    private val wifiAutoCacheManager: WifiAutoCacheManager,
    private val speedVolumeController: SpeedVolumeController,
    private val settingsPreferences: SettingsPreferences,
    private val dspAudioEngine: AndroidDspAudioEngine,
    private val fftCapturer: AudioFftCapturer
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val currentSpeedKmH: StateFlow<Float> = speedVolumeController.currentSpeedKmH
    val volumeMultiplier: StateFlow<Float> = speedVolumeController.volumeMultiplier
    val isAttenuated: StateFlow<Boolean> = playerManager.isAttenuated
    val sleepTimerMinutes: StateFlow<Int> = playerManager.sleepTimerMinutes
    val sleepTimerRemainingSec: StateFlow<Int?> = playerManager.sleepTimerRemainingSec

    val dspSettings: StateFlow<DspSettings> = dspAudioEngine.dspSettings
    val activeOutputRoute: StateFlow<AudioOutputRoute> = dspAudioEngine.activeOutputRoute
    val isMuted: StateFlow<Boolean> = playerManager.isMuted
    val visualizerFrame: StateFlow<VisualizerFrame> = fftCapturer.frame
    val activeDownloads: StateFlow<Map<String, Int>> = downloadManager.activeDownloads
    val downloadFeedback: StateFlow<DownloadFeedbackEvent?> = downloadManager.downloadFeedback

    val accentColor: StateFlow<MbuxAccentColor> = settingsPreferences.accentColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MbuxAccentColor.LIME)

    val playbackRate: StateFlow<Float> = settingsPreferences.playbackSpeedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val swipeGesturesEnabled: StateFlow<Boolean> = settingsPreferences.swipeGesturesEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val idleScreensaverSeconds: StateFlow<Int> = settingsPreferences.idleScreensaverSecondsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val driveModeAutoSpeedKmH: StateFlow<Int> = settingsPreferences.driveModeAutoSpeedKmHFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val visualizerMode: StateFlow<VisualizerMode> = settingsPreferences.visualizerModeFlow
        .map { VisualizerMode.fromId(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VisualizerMode.GAUGE)

    val visualizerColorway: StateFlow<VisualizerColorway> = settingsPreferences.visualizerColorwayFlow
        .map { VisualizerColorway.fromId(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VisualizerColorway.AMBER)

    val playlists: StateFlow<List<Playlist>> = musicRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cockpitThemeMode: StateFlow<CockpitThemeMode> = settingsPreferences.cockpitThemeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CockpitThemeMode.AUTO)

    val swapGroupMode: StateFlow<SwapGroupMode> = settingsPreferences.swapGroupModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SwapGroupMode.FOLDERS)

    val splitScreenRailEnabled: StateFlow<Boolean> = settingsPreferences.splitScreenRailFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val pinnedCacheFolders: StateFlow<Set<String>> = settingsPreferences.pinnedCacheFoldersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val wifiAutoCacheState: StateFlow<WifiAutoCacheState> = wifiAutoCacheManager.state

    val folderSwapGroups: StateFlow<List<SwapMediaGroup>> = combine(
        musicRepository.getAllSongs(),
        pinnedCacheFolders
    ) { songs, pins ->
        buildFolderSwapGroups(songs, pins)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albumSwapGroups: StateFlow<List<SwapMediaGroup>> = combine(
        musicRepository.getAllSongs(),
        pinnedCacheFolders
    ) { songs, pins ->
        buildAlbumSwapGroups(songs, pins)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLyricsVisible = MutableStateFlow(false)
    val isLyricsVisible: StateFlow<Boolean> = _isLyricsVisible.asStateFlow()

    private val _lyrics = MutableStateFlow(ParsedLyrics())
    val lyrics: StateFlow<ParsedLyrics> = _lyrics.asStateFlow()

    init {
        viewModelScope.launch {
            playbackState.collectLatest { state ->
                val song = state.currentSong
                _lyrics.value = if (song?.lyricsText != null) {
                    LyricsParser.parse(song.lyricsText)
                } else {
                    ParsedLyrics()
                }
            }
        }
    }

    fun toggleLyrics() {
        _isLyricsVisible.value = !_isLyricsVisible.value
    }

    fun toggleFavorite() {
        playerManager.toggleFavoriteCurrentSong()
    }

    fun downloadCurrentSong() {
        val current = playbackState.value.currentSong ?: return
        if (current.isDownloaded) {
            downloadManager.removeDownload(current)
        } else {
            downloadManager.downloadSong(current)
        }
    }

    fun addCurrentSongToPlaylist(playlistId: String) {
        val current = playbackState.value.currentSong ?: return
        viewModelScope.launch {
            musicRepository.addSongToPlaylist(playlistId, current.id)
        }
    }

    fun createPlaylistAndAddCurrent(name: String) {
        val current = playbackState.value.currentSong ?: return
        viewModelScope.launch {
            val created = musicRepository.createPlaylist(name)
            musicRepository.addSongToPlaylist(created.id, current.id)
        }
    }

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun play() = playerManager.play()
    fun pause() = playerManager.pause()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun seekBy(deltaMs: Long) = playerManager.seekBy(deltaMs)
    fun skipToNext() = playerManager.skipToNext()
    fun skipToPrevious() = playerManager.skipToPrevious()
    fun toggleAttenuation() = playerManager.toggleAttenuation()
    fun toggleMute() = playerManager.toggleMute()
    fun setSleepTimer(minutes: Int) = playerManager.setSleepTimer(minutes)
    fun clearError() = playerManager.clearErrorMessage()
    fun clearDownloadFeedback() = downloadManager.clearFeedback()
    fun cycleSurroundMode(): SurroundMode = dspAudioEngine.cycleSurroundMode()
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = playerManager.moveQueueItem(fromIndex, toIndex)
    fun removeQueueItem(index: Int) = playerManager.removeQueueItem(index)
    fun clearQueue() = playerManager.clearQueue(keepCurrent = true)
    fun selectOutputRouteManually(route: AudioOutputRoute) = dspAudioEngine.selectOutputRouteManually(route)

    fun setPlaybackRate(speed: Float) {
        viewModelScope.launch {
            settingsPreferences.setPlaybackSpeed(speed)
        }
    }

    fun setVisualizerMode(mode: VisualizerMode) {
        viewModelScope.launch {
            settingsPreferences.setVisualizerMode(mode.id)
        }
    }

    fun setVisualizerColorway(colorway: VisualizerColorway) {
        viewModelScope.launch {
            settingsPreferences.setVisualizerColorway(colorway.id)
        }
    }

    fun toggleShuffle() {
        val current = playbackState.value.shuffleEnabled
        playerManager.setShuffleMode(!current)
    }

    fun cycleRepeatMode() {
        val nextMode = when (playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playerManager.setRepeatMode(nextMode)
    }

    fun playQueueIndex(index: Int) {
        val queue = playbackState.value.queue
        val song = queue.getOrNull(index) ?: return
        playerManager.playSong(song, queue)
    }

    fun setSwapGroupMode(mode: SwapGroupMode) {
        viewModelScope.launch {
            settingsPreferences.setSwapGroupMode(mode)
        }
    }

    fun toggleSplitScreenRail() {
        val next = !splitScreenRailEnabled.value
        viewModelScope.launch {
            settingsPreferences.setSplitScreenRailEnabled(next)
        }
    }

    fun playSwapGroup(group: SwapMediaGroup): Boolean {
        return playerManager.playSwapGroup(group)
    }

    fun swapToAdjacentGroup(delta: Int, onSwapped: (String) -> Unit) {
        viewModelScope.launch {
            val mode = swapGroupMode.value
            val pins = pinnedCacheFolders.value
            val result = playerManager.swapToAdjacentGroup(delta, mode, pins)
            if (result != null) {
                val (group, idx) = result
                val total = if (mode == SwapGroupMode.FOLDERS) folderSwapGroups.value.size else albumSwapGroups.value.size
                val kind = if (mode == SwapGroupMode.FOLDERS) "Folder" else "Album"
                onSwapped("$kind ${idx + 1}/${total.coerceAtLeast(1)} · ${group.displayName}")
            } else {
                onSwapped("No other ${if (mode == SwapGroupMode.FOLDERS) "folders" else "albums"} available")
            }
        }
    }

    fun togglePinFolderForWifiCache(folderKey: String) {
        viewModelScope.launch {
            settingsPreferences.togglePinnedCacheFolder(folderKey)
        }
    }

    fun syncPinnedFoldersNow() {
        wifiAutoCacheManager.syncPinnedFoldersNow(forceEvenWithoutWifi = true)
    }

    fun cycleCockpitThemeMode(): CockpitThemeMode {
        val modes = CockpitThemeMode.entries
        val cur = cockpitThemeMode.value
        val next = modes[(modes.indexOf(cur) + 1) % modes.size]
        viewModelScope.launch {
            settingsPreferences.setCockpitThemeMode(next)
        }
        return next
    }
}
