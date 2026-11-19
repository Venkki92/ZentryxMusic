package com.zentryx.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zentryx.music.models.Song
import com.zentryx.music.player.ZentryxPlayerManager
import com.zentryx.music.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    val playerManager: ZentryxPlayerManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _queueFeedback = MutableStateFlow<String?>(null)
    val queueFeedback: StateFlow<String?> = _queueFeedback.asStateFlow()

    val searchResults: StateFlow<List<Song>> = _query
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.trim().isEmpty()) {
                flowOf(emptyList())
            } else {
                musicRepository.searchSongs(q.trim())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun clearQueueFeedback() {
        _queueFeedback.value = null
    }

    fun playSong(song: Song) {
        playerManager.playSong(song, searchResults.value.ifEmpty { listOf(song) })
    }

    fun playNext(song: Song) {
        if (playerManager.playNext(song)) {
            _queueFeedback.value = "Playing next: ${song.title}"
        }
    }

    fun addToQueue(song: Song) {
        if (playerManager.addToQueue(song)) {
            _queueFeedback.value = "Added to queue: ${song.title}"
        }
    }

    fun handleVoiceSearchResult(spokenText: String) {
        val raw = spokenText.trim()
        if (raw.isEmpty()) return
        val wantsAutoPlay = raw.startsWith("play ", ignoreCase = true)
        val cleanQuery = if (wantsAutoPlay) raw.substring(5).trim() else raw
        _query.value = cleanQuery
        if (wantsAutoPlay && cleanQuery.isNotEmpty()) {
            viewModelScope.launch {
                val results = musicRepository.searchSongs(cleanQuery).firstOrNull().orEmpty()
                if (results.isNotEmpty()) {
                    playerManager.playSong(results.first(), results)
                    _queueFeedback.value = "Voice playing: ${results.first().title}"
                } else {
                    _queueFeedback.value = "No track matching \"$cleanQuery\""
                }
            }
        }
    }
}
