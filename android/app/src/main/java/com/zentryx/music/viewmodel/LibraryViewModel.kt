package com.zentryx.music.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.zentryx.music.download.DownloadFeedbackEvent
import com.zentryx.music.download.DownloadManager
import com.zentryx.music.dsp.AndroidDspAudioEngine
import com.zentryx.music.dsp.AudioOutputRoute
import com.zentryx.music.dsp.DspSettings
import com.zentryx.music.models.Album
import com.zentryx.music.models.Artist
import com.zentryx.music.models.AudioFormat
import com.zentryx.music.models.Playlist
import com.zentryx.music.models.Resource
import com.zentryx.music.models.ScanProgress
import com.zentryx.music.models.ScanStatus
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.models.Song
import com.zentryx.music.network.webdav.ScanDiagnosticsState
import com.zentryx.music.player.MixGenreSummary
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.player.SmartMixEngine
import com.zentryx.music.player.SmartMixType
import com.zentryx.music.player.ZentryxPlayerManager
import com.zentryx.music.repository.MusicRepository
import com.zentryx.music.security.EncryptedDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab {
    SONGS, ALBUMS, ARTISTS, FOLDERS, PLAYLISTS, RECENT, MOST_PLAYED, RESUME, FAVORITES, OFFLINE
}

data class RemoteFolderEntry(
    val name: String,
    val relativePath: String
)

data class RemoteFolderBrowserState(
    val isOpen: Boolean = false,
    val currentPath: String = "",
    val folders: List<RemoteFolderEntry> = emptyList(),
    val audioFileCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    val playerManager: ZentryxPlayerManager,
    private val encryptedDataStore: EncryptedDataStore,
    private val downloadManager: DownloadManager,
    private val settingsPreferences: SettingsPreferences,
    private val dspAudioEngine: AndroidDspAudioEngine
) : ViewModel() {

    private val _libraryQuery = MutableStateFlow("")
    val libraryQuery: StateFlow<String> = _libraryQuery.asStateFlow()

    val pagedSongs: Flow<PagingData<Song>> = musicRepository.getPagedSongs()
        .cachedIn(viewModelScope)

    val allSongs: StateFlow<List<Song>> = musicRepository.getAllSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredSongs: StateFlow<List<Song>> = combine(allSongs, _libraryQuery) { songs, q ->
        filterSongsByQuery(songs, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allAlbums: StateFlow<List<Album>> = musicRepository.getAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = combine(allAlbums, _libraryQuery) { list, q ->
        val needle = q.trim()
        if (needle.isEmpty()) list
        else list.filter {
            it.name.contains(needle, ignoreCase = true) ||
                it.artist.contains(needle, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allArtists: StateFlow<List<Artist>> = musicRepository.getArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = combine(allArtists, _libraryQuery) { list, q ->
        val needle = q.trim()
        if (needle.isEmpty()) list
        else list.filter { it.name.contains(needle, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allFavoriteSongs: StateFlow<List<Song>> = musicRepository.getFavoriteSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> = combine(allFavoriteSongs, _libraryQuery) { songs, q ->
        if (q.isBlank()) songs else filterSongsByQuery(songs, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allDownloadedSongs: StateFlow<List<Song>> = musicRepository.getDownloadedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedSongs: StateFlow<List<Song>> = combine(allDownloadedSongs, _libraryQuery) { songs, q ->
        if (q.isBlank()) songs else filterSongsByQuery(songs, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allRecentSongs: StateFlow<List<Song>> = musicRepository.getRecentlyPlayedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSongs: StateFlow<List<Song>> = combine(allRecentSongs, _libraryQuery) { songs, q ->
        if (q.isBlank()) songs else filterSongsByQuery(songs, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allMostPlayedSongs: StateFlow<List<Song>> = musicRepository.getMostPlayedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedSongs: StateFlow<List<Song>> = combine(allMostPlayedSongs, _libraryQuery) { songs, q ->
        if (q.isBlank()) songs else filterSongsByQuery(songs, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allResumableSongs: StateFlow<List<Song>> = musicRepository.getResumableSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val resumableSongs: StateFlow<List<Song>> = combine(allResumableSongs, _libraryQuery) { songs, q ->
        if (q.isBlank()) songs else filterSongsByQuery(songs, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Hierarchical Folder Browser (WebDAV + Local USB folders)
    private val _folderBrowserPath = MutableStateFlow("")
    val folderBrowserPath: StateFlow<String> = _folderBrowserPath.asStateFlow()

    val subFoldersInPath: StateFlow<List<String>> = combine(allSongs, _folderBrowserPath, _libraryQuery) { songs, path, q ->
        val prefix = if (path.isBlank()) "" else "${path.trim('/')}/"
        val folders = mutableSetOf<String>()
        for (song in songs) {
            val clean = song.relativePath.trimStart('/')
            val parentDir = clean.substringBeforeLast('/', "")
            if (parentDir.isEmpty()) continue
            if (prefix.isEmpty()) {
                val top = clean.substringBefore('/', "")
                if (top.isNotEmpty() && clean.contains('/')) {
                    folders.add(top)
                }
            } else if (clean.startsWith(prefix, ignoreCase = true)) {
                val remainder = clean.substring(prefix.length)
                if (remainder.contains('/')) {
                    folders.add(remainder.substringBefore('/'))
                }
            }
        }
        val needle = q.trim()
        val sorted = folders.sortedWith(String.CASE_INSENSITIVE_ORDER)
        if (needle.isEmpty()) sorted else sorted.filter { it.contains(needle, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val songsInFolderPath: StateFlow<List<Song>> = combine(allSongs, _folderBrowserPath, _libraryQuery) { songs, path, q ->
        val prefix = if (path.isBlank()) "" else "${path.trim('/')}/"
        val direct = songs.filter { song ->
            val clean = song.relativePath.trimStart('/')
            if (prefix.isEmpty()) {
                !clean.contains('/')
            } else {
                clean.startsWith(prefix, ignoreCase = true) && !clean.substring(prefix.length).contains('/')
            }
        }
        if (q.isBlank()) direct else filterSongsByQuery(direct, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Playlists & .m3u8 Sync
    val playlists: StateFlow<List<Playlist>> = musicRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    val selectedPlaylistSongs: StateFlow<List<Song>> = _selectedPlaylist
        .flatMapLatest { pl ->
            if (pl == null) flowOf(emptyList()) else musicRepository.getPlaylistSongs(pl.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batchProgress: StateFlow<Pair<Int, Int>?> = downloadManager.batchProgress
    val activeDownloads: StateFlow<Map<String, Int>> = downloadManager.activeDownloads
    val downloadFeedback: StateFlow<DownloadFeedbackEvent?> = downloadManager.downloadFeedback

    private val _isLocalScanning = MutableStateFlow(false)
    val isLocalScanning: StateFlow<Boolean> = _isLocalScanning.asStateFlow()

    private val _playlistSyncMessage = MutableStateFlow<String?>(null)
    val playlistSyncMessage: StateFlow<String?> = _playlistSyncMessage.asStateFlow()

    val isOfflineOnly: StateFlow<Boolean> = settingsPreferences.offlineOnlyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val dualPaneCockpit: StateFlow<Boolean> = settingsPreferences.dualPaneCockpitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val driveModeAutoSpeedKmH: StateFlow<Int> = settingsPreferences.driveModeAutoSpeedKmHFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dspSettings: StateFlow<DspSettings> = dspAudioEngine.dspSettings
    val activeOutputRoute: StateFlow<AudioOutputRoute> = dspAudioEngine.activeOutputRoute
    val currentSpeedKmH: StateFlow<Float> = playerManager.currentSpeedKmH
    val isAttenuated: StateFlow<Boolean> = playerManager.isAttenuated
    val isMuted: StateFlow<Boolean> = playerManager.isMuted

    // Smart Mixes & Genre-Sorted Cruising Shuffle
    val cruisingSortByGenre: StateFlow<Boolean> = settingsPreferences.cruisingSortByGenreFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _activeSmartMix = MutableStateFlow<SmartMixType?>(null)
    val activeSmartMix: StateFlow<SmartMixType?> = _activeSmartMix.asStateFlow()

    private val _cruisingReshuffleCount = MutableStateFlow(0)
    val cruisingReshuffleCount: StateFlow<Int> = _cruisingReshuffleCount.asStateFlow()

    val mixGenreSummary: StateFlow<MixGenreSummary> = allSongs
        .map { songs -> SmartMixEngine.summarizeMixGenres(songs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MixGenreSummary(0, emptyList()))

    val deepCutsCount: StateFlow<Int> = allSongs
        .map { songs ->
            val unheard = songs.count { it.playCount == 0 }
            if (unheard > 0) minOf(songs.size, maxOf(unheard, minOf(songs.size, 40))) else songs.size
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _selectedTab = MutableStateFlow(LibraryTab.SONGS)
    val selectedTab: StateFlow<LibraryTab> = _selectedTab.asStateFlow()

    private val _scanProgress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _diagnosticsState = MutableStateFlow(ScanDiagnosticsState())
    val diagnosticsState: StateFlow<ScanDiagnosticsState> = _diagnosticsState.asStateFlow()

    private val _showDiagnosticsDialog = MutableStateFlow(false)
    val showDiagnosticsDialog: StateFlow<Boolean> = _showDiagnosticsDialog.asStateFlow()

    private val _remoteFolderBrowserState = MutableStateFlow(RemoteFolderBrowserState())
    val remoteFolderBrowserState: StateFlow<RemoteFolderBrowserState> = _remoteFolderBrowserState.asStateFlow()

    val extraWebDavFolders: StateFlow<List<String>> = settingsPreferences.extraWebDavFoldersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private var scanJob: kotlinx.coroutines.Job? = null
    private var diagnosticsJob: kotlinx.coroutines.Job? = null
    private var autoScanTriggered = false

    init {
        viewModelScope.launch {
            encryptedDataStore.getServerUrl()?.let {
                _serverUrl.value = it
            }
            playerManager.restoreLastPlaybackState(musicRepository)
            runAutoScanIfLibraryEmpty()
        }
    }

    private fun runAutoScanIfLibraryEmpty() {
        if (autoScanTriggered) return
        autoScanTriggered = true
        viewModelScope.launch {
            val existingCount = runCatching { musicRepository.getSongCount() }.getOrDefault(0)
            if (existingCount > 0) return@launch

            // Scan any already-permitted local/USB storage silently first
            val localCount = runCatching { musicRepository.scanLocalLibrary(null) }.getOrDefault(0)
            if (localCount > 0) {
                _playlistSyncMessage.value = "Auto-indexed $localCount local/USB tracks"
            }

            // Automatically sync Nextcloud WebDAV library when credentials exist
            if (encryptedDataStore.hasCredentials()) {
                startScan()
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun updateLibraryQuery(query: String) {
        _libraryQuery.value = query
    }

    fun clearLibraryQuery() {
        _libraryQuery.value = ""
    }

    private fun filterSongsByQuery(songs: List<Song>, query: String): List<Song> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return songs.filter { song ->
            song.title.contains(needle, ignoreCase = true) ||
                song.artist.contains(needle, ignoreCase = true) ||
                song.album.contains(needle, ignoreCase = true)
        }
    }

    fun toggleOfflineOnly() {
        viewModelScope.launch {
            val next = !isOfflineOnly.value
            settingsPreferences.setOfflineOnly(next)
            if (next && (_selectedTab.value == LibraryTab.SONGS || _selectedTab.value == LibraryTab.ALBUMS)) {
                _selectedTab.value = LibraryTab.OFFLINE
            }
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

    fun clearDownloadFeedback() {
        downloadManager.clearFeedback()
    }

    fun downloadAlbum(album: Album) {
        viewModelScope.launch {
            val albumSongs = musicRepository.getSongsByAlbum(album.name, album.artist).firstOrNull().orEmpty()
            downloadManager.downloadBatch(albumSongs)
        }
    }

    fun pinAllFavorites() {
        viewModelScope.launch {
            val favs = allFavoriteSongs.value
            downloadManager.downloadBatch(favs)
        }
    }

    fun scanLocalAudio(treeUri: Uri? = null, additive: Boolean = false) {
        viewModelScope.launch {
            _isLocalScanning.value = true
            try {
                val count = musicRepository.scanLocalLibrary(treeUri, additive = additive)
                _playlistSyncMessage.value = if (count > 0) {
                    if (additive) {
                        "Added $count local/USB track(s) to your library"
                    } else {
                        "Scanned $count local USB/device audio tracks"
                    }
                } else {
                    if (additive) {
                        "0 audio files found in that folder — existing library untouched"
                    } else {
                        "0 local tracks found — tap 'Add Folder' or 'USB Folder' to grant drive access"
                    }
                }
            } catch (t: Throwable) {
                _playlistSyncMessage.value = "Local scan error: ${t.localizedMessage ?: "Permission or I/O failure"}"
            } finally {
                _isLocalScanning.value = false
            }
        }
    }

    fun setDualPaneCockpit(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setDualPaneCockpit(enabled)
        }
    }

    fun setCruisingSortByGenre(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setCruisingSortByGenre(enabled)
        }
    }

    fun playSmartMix(
        mixType: SmartMixType,
        reshuffle: Boolean = false,
        sortByGenre: Boolean? = null,
        fullLibrary: Boolean = (mixType == SmartMixType.CRUISING),
        onStarted: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val effectiveSort = sortByGenre ?: cruisingSortByGenre.value
            val (trackCount, genreCount) = playerManager.playSmartMix(
                mixType = mixType,
                reshuffle = reshuffle,
                sortByGenre = effectiveSort,
                fullLibrary = fullLibrary
            )
            if (trackCount == 0) {
                _playlistSyncMessage.value = when (mixType) {
                    SmartMixType.RECENT -> "No recently played tracks yet. Start listening to build history!"
                    SmartMixType.MOST_PLAYED -> "No play counts recorded yet."
                    SmartMixType.FAVORITES_RADIO -> "No favorite tracks yet. Tap the heart icon on songs first!"
                    else -> "No playable tracks available for ${mixType.label}."
                }
                return@launch
            }
            _activeSmartMix.value = mixType
            if (mixType == SmartMixType.CRUISING && reshuffle) {
                _cruisingReshuffleCount.value += 1
                _playlistSyncMessage.value = if (effectiveSort && genreCount > 0) {
                    "Cruising Mix reshuffled ($trackCount tracks · sorted across $genreCount genres)"
                } else {
                    "Cruising Mix reshuffled ($trackCount tracks · full random)"
                }
            } else {
                _playlistSyncMessage.value = if (effectiveSort && genreCount > 0) {
                    "${mixType.label} started ($trackCount tracks · $genreCount genres A–Z)"
                } else {
                    "${mixType.label} started ($trackCount tracks)"
                }
            }
            onStarted()
        }
    }

    fun pinFolderOffline(folderPath: String) {
        val clean = folderPath.trim('/')
        val prefix = if (clean.isEmpty()) "" else "$clean/"
        val matching = allSongs.value.filter { song ->
            val rel = song.relativePath.trimStart('/')
            (prefix.isEmpty() || rel.startsWith(prefix, ignoreCase = true)) &&
                song.sourceType == "WEBDAV" &&
                !song.isDownloaded
        }
        if (matching.isEmpty()) {
            _playlistSyncMessage.value = "All Nextcloud tracks in ${clean.ifEmpty { "Music Root" }} are already offline"
            return
        }
        downloadManager.downloadBatch(matching)
        _playlistSyncMessage.value = "Pinning ${matching.size} track(s) from /${clean.ifEmpty { "Root" }} for offline use"
    }

    fun playAllInFolder(folderPath: String) {
        val clean = folderPath.trim('/')
        val prefix = if (clean.isEmpty()) "" else "$clean/"
        val matching = allSongs.value.filter { song ->
            val rel = song.relativePath.trimStart('/')
            prefix.isEmpty() || rel.startsWith(prefix, ignoreCase = true)
        }
        if (matching.isNotEmpty()) {
            playerManager.playSong(matching.first(), matching)
        }
    }

    fun openFolder(folderName: String) {
        val cur = _folderBrowserPath.value.trim('/')
        _folderBrowserPath.value = if (cur.isEmpty()) folderName else "$cur/$folderName"
    }

    fun navigateFolderUp() {
        val cur = _folderBrowserPath.value.trim('/')
        _folderBrowserPath.value = if (cur.contains('/')) cur.substringBeforeLast('/') else ""
    }

    fun resetFolderRoot() {
        _folderBrowserPath.value = ""
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val created = musicRepository.createPlaylist(name)
            _selectedPlaylist.value = created
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlistId)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = null
            }
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            musicRepository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            musicRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun playPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val songs = musicRepository.getPlaylistSongs(playlist.id).firstOrNull().orEmpty()
            if (songs.isNotEmpty()) {
                playerManager.playSong(songs.first(), songs)
            }
        }
    }

    fun syncPlaylistsWithNextcloud() {
        viewModelScope.launch {
            val res = musicRepository.syncPlaylistsWithNextcloud()
            _playlistSyncMessage.value = res.fold(
                onSuccess = { count -> "Synced $count .m3u8 playlist(s) with Nextcloud /Playlists" },
                onFailure = { err -> err.message ?: "Playlist sync failed" }
            )
        }
    }

    fun clearPlaylistSyncMessage() {
        _playlistSyncMessage.value = null
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        playerManager.playSong(song, queue)
    }

    fun resumeSong(song: Song, queue: List<Song> = listOf(song)) {
        playerManager.resumeSong(song, queue)
    }

    fun playNext(song: Song) {
        if (playerManager.playNext(song)) {
            _playlistSyncMessage.value = "Playing next: ${song.title}"
        }
    }

    fun addToQueue(song: Song) {
        if (playerManager.addToQueue(song)) {
            _playlistSyncMessage.value = "Added to queue: ${song.title}"
        }
    }

    fun toggleMute() = playerManager.toggleMute()
    fun toggleAttenuation() = playerManager.toggleAttenuation()
    fun seekBy(deltaMs: Long) = playerManager.seekBy(deltaMs)
    fun toggleFavoriteCurrentSong() = playerManager.toggleFavoriteCurrentSong()

    fun handleVoiceSearchResult(spokenText: String) {
        val raw = spokenText.trim()
        if (raw.isEmpty()) return
        val wantsAutoPlay = raw.startsWith("play ", ignoreCase = true)
        val cleanQuery = if (wantsAutoPlay) raw.substring(5).trim() else raw
        _libraryQuery.value = cleanQuery
        if (wantsAutoPlay && cleanQuery.isNotEmpty()) {
            val matches = filterSongsByQuery(allSongs.value, cleanQuery)
            if (matches.isNotEmpty()) {
                playerManager.playSong(matches.first(), matches)
                _playlistSyncMessage.value = "Voice playing: ${matches.first().title}"
            } else {
                _playlistSyncMessage.value = "Voice search: no track matching \"$cleanQuery\""
            }
        } else {
            _playlistSyncMessage.value = "Voice filter: \"$cleanQuery\""
        }
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            val songs = musicRepository.getSongsByAlbum(album.name, album.artist).firstOrNull().orEmpty()
            if (songs.isNotEmpty()) {
                playerManager.playSong(songs.first(), songs)
            }
        }
    }

    fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            musicRepository.startScan().collect { progress ->
                _scanProgress.value = progress
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        viewModelScope.launch {
            musicRepository.cancelScan()
            _scanProgress.value = ScanProgress.Idle
        }
    }

    fun openRemoteFolderPicker(initialPath: String = "") {
        val clean = ServerConfig.normalizeFolderPath(initialPath)
        _remoteFolderBrowserState.value = RemoteFolderBrowserState(
            isOpen = true,
            currentPath = clean,
            isLoading = true
        )
        loadRemoteFolder(clean)
    }

    fun dismissRemoteFolderPicker() {
        _remoteFolderBrowserState.value = _remoteFolderBrowserState.value.copy(isOpen = false)
    }

    fun loadRemoteFolder(path: String) {
        val clean = ServerConfig.normalizeFolderPath(path)
        _remoteFolderBrowserState.value = _remoteFolderBrowserState.value.copy(
            isOpen = true,
            currentPath = clean,
            isLoading = true,
            errorMessage = null
        )
        viewModelScope.launch {
            when (val res = musicRepository.browseRemoteWebDavFolder(clean)) {
                is Resource.Success -> {
                    val dirs = res.data.filter { it.isDirectory }.map { item ->
                        val childRel = if (clean.isEmpty()) item.name else "$clean/${item.name}"
                        RemoteFolderEntry(name = item.name, relativePath = childRel)
                    }
                    val audioCount = res.data.count { !it.isDirectory && AudioFormat.isSupported(it.name) }
                    _remoteFolderBrowserState.value = _remoteFolderBrowserState.value.copy(
                        currentPath = clean,
                        folders = dirs,
                        audioFileCount = audioCount,
                        isLoading = false,
                        errorMessage = null
                    )
                }
                is Resource.Error -> {
                    _remoteFolderBrowserState.value = _remoteFolderBrowserState.value.copy(
                        currentPath = clean,
                        folders = emptyList(),
                        audioFileCount = 0,
                        isLoading = false,
                        errorMessage = res.message
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun navigateRemoteFolderUp() {
        val cur = _remoteFolderBrowserState.value.currentPath.trim('/')
        if (cur.isEmpty()) return
        val parent = if (cur.contains('/')) cur.substringBeforeLast('/') else ""
        loadRemoteFolder(parent)
    }

    fun scanAndAddRemoteFolder(folderPath: String, recursive: Boolean = true) {
        val clean = ServerConfig.normalizeFolderPath(folderPath)
        val display = if (clean.isEmpty()) "/" else "/$clean"
        dismissRemoteFolderPicker()
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val isDefault = ServerConfig.DEFAULT_MUSIC_FOLDERS.any { it.equals(clean, ignoreCase = true) }
            if (!isDefault) {
                settingsPreferences.addExtraWebDavFolder(if (clean.isEmpty()) "/" else clean)
            }
            _playlistSyncMessage.value = "Scanning $display & adding to library…"
            musicRepository.scanSpecificWebDavFolder(
                folderPath = if (clean.isEmpty()) "/" else clean,
                additive = true,
                recursive = recursive
            ).collect { progress ->
                _scanProgress.value = progress
                if (progress.status == ScanStatus.COMPLETED) {
                    _playlistSyncMessage.value = "Added ${progress.filesProcessed} track(s) from $display to library"
                } else if (progress.status == ScanStatus.ERROR && !progress.errorMessage.isNullOrBlank()) {
                    _playlistSyncMessage.value = "Scan error ($display): ${progress.errorMessage}"
                }
            }
        }
    }

    fun removeExtraRemoteFolder(folderPath: String) {
        viewModelScope.launch {
            settingsPreferences.removeExtraWebDavFolder(folderPath)
        }
    }

    fun startScanDiagnostics() {
        _showDiagnosticsDialog.value = true
        diagnosticsJob?.cancel()
        diagnosticsJob = viewModelScope.launch {
            musicRepository.runScanDiagnostics(_folderBrowserPath.value).collect { state ->
                _diagnosticsState.value = state
            }
        }
    }

    fun dismissScanDiagnostics() {
        diagnosticsJob?.cancel()
        _showDiagnosticsDialog.value = false
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            playerManager.pause()
            encryptedDataStore.clearAll()
            onLoggedOut()
        }
    }
}
