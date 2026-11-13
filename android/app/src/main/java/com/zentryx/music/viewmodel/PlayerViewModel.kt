package com.zentryx.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zentryx.music.download.DownloadManager
import com.zentryx.music.lyrics.LyricsParser
import com.zentryx.music.lyrics.ParsedLyrics
import com.zentryx.music.models.PlaybackState
import com.zentryx.music.models.RepeatMode
import com.zentryx.music.models.Song
import com.zentryx.music.player.SpeedVolumeController
import com.zentryx.music.player.ZentryxPlayerManager
import com.zentryx.music.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: ZentryxPlayerManager,
    private val musicRepository: MusicRepository,
    private val downloadManager: DownloadManager,
    private val speedVolumeController: SpeedVolumeController
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val currentSpeedKmH: StateFlow<Float> = speedVolumeController.currentSpeedKmH
    val volumeMultiplier: StateFlow<Float> = speedVolumeController.volumeMultiplier

    private val _isLyricsVisible = MutableStateFlow(false)
    val isLyricsVisible: StateFlow<Boolean> = _isLyricsVisible.asStateFlow()

    private val _lyrics = MutableStateFlow(ParsedLyrics())
    val lyrics: StateFlow<ParsedLyrics> = _lyrics.asStateFlow()

    init {
        viewModelScope.launch {
            playbackState.collectLatest { state ->
                val song = state.currentSong
                if (song != null) {
                    if (song.lyricsText != null) {
                        _lyrics.value = LyricsParser.parse(song.lyricsText)
                    } else {
                        _lyrics.value = ParsedLyrics()
                    }
                }
            }
        }
    }

    fun toggleLyrics() {
        _isLyricsVisible.value = !_isLyricsVisible.value
    }

    fun toggleFavorite() {
        val current = playbackState.value.currentSong ?: return
        viewModelScope.launch {
            val newFav = !current.isFavorite
            musicRepository.setFavorite(current.id, newFav)
        }
    }

    fun downloadCurrentSong() {
        val current = playbackState.value.currentSong ?: return
        if (current.isDownloaded) {
            downloadManager.removeDownload(current)
        } else {
            downloadManager.downloadSong(current)
        }
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun play() {
        playerManager.play()
    }

    fun pause() {
        playerManager.pause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun skipToNext() {
        playerManager.skipToNext()
    }

    fun skipToPrevious() {
        playerManager.skipToPrevious()
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
}
