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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    val playerManager: ZentryxPlayerManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

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

    fun playSong(song: Song) {
        playerManager.playSong(song, searchResults.value.ifEmpty { listOf(song) })
    }
}
