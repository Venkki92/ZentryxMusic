package com.zentryx.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.zentryx.music.download.DownloadManager
import com.zentryx.music.models.Album
import com.zentryx.music.models.Artist
import com.zentryx.music.models.ScanProgress
import com.zentryx.music.models.Song
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.player.ZentryxPlayerManager
import com.zentryx.music.repository.MusicRepository
import com.zentryx.music.security.EncryptedDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab {
    SONGS, ALBUMS, ARTISTS, FAVORITES, OFFLINE
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    val playerManager: ZentryxPlayerManager,
    private val encryptedDataStore: EncryptedDataStore,
    private val downloadManager: DownloadManager,
    private val settingsPreferences: SettingsPreferences
) : ViewModel() {

    val pagedSongs: Flow<PagingData<Song>> = musicRepository.getPagedSongs()
        .cachedIn(viewModelScope)

    val albums: StateFlow<List<Album>> = musicRepository.getAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = musicRepository.getArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> = musicRepository.getFavoriteSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedSongs: StateFlow<List<Song>> = musicRepository.getDownloadedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isOfflineOnly: StateFlow<Boolean> = settingsPreferences.offlineOnlyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _selectedTab = MutableStateFlow(LibraryTab.SONGS)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    private val _scanProgress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    init {
        viewModelScope.launch {
            encryptedDataStore.getServerUrl()?.let {
                _serverUrl.value = it
            }
            playerManager.restoreLastPlaybackState(musicRepository)
        }
    }

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun toggleOfflineOnly() {
        viewModelScope.launch {
            val current = isOfflineOnly.value
            settingsPreferences.setOfflineOnly(!current)
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            musicRepository.setFavorite(song.id, !song.isFavorite)
        }
    }

    fun downloadSong(song: Song) {
        if (song.isDownloaded) {
            downloadManager.removeDownload(song)
        } else {
            downloadManager.downloadSong(song)
        }
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        playerManager.playSong(song, queue)
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            musicRepository.getSongsByAlbum(album.name, album.artist).collect { songs ->
                if (songs.isNotEmpty()) {
                    playerManager.playSong(songs.first(), songs)
                }
            }
        }
    }

    fun startScan() {
        viewModelScope.launch {
            musicRepository.startScan().collect { progress ->
                _scanProgress.value = progress
            }
        }
    }

    fun cancelScan() {
        viewModelScope.launch {
            musicRepository.cancelScan()
            _scanProgress.value = ScanProgress.Idle
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            playerManager.pause()
            encryptedDataStore.clearAll()
            onLoggedOut()
        }
    }
}
