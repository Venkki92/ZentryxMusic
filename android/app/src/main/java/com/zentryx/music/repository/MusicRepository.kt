package com.zentryx.music.repository

import android.net.Uri
import androidx.paging.PagingData
import com.zentryx.music.models.Album
import com.zentryx.music.models.Artist
import com.zentryx.music.models.Playlist
import com.zentryx.music.models.Resource
import com.zentryx.music.models.ScanProgress
import com.zentryx.music.models.Song
import com.zentryx.music.network.webdav.ScanDiagnosticsState
import com.zentryx.music.network.webdav.WebDavItem
import kotlinx.coroutines.flow.Flow

/**
 * Primary repository interface for audio catalog management.
 * Designed behind an interface so future backend sources (e.g. Nextcloud Music Ampache/Subsonic API)
 * can be plugged in without changing ViewModels or UI.
 */
interface MusicRepository {
    fun getPagedSongs(): Flow<PagingData<Song>>
    fun getAllSongs(): Flow<List<Song>>
    fun getSongsByAlbum(albumName: String, artistName: String): Flow<List<Song>>
    fun getSongsByArtist(artistName: String): Flow<List<Song>>
    fun getSongsByFolderPrefix(prefix: String): Flow<List<Song>>
    fun getAllRelativePaths(): Flow<List<String>>
    fun getAlbums(): Flow<List<Album>>
    fun getArtists(): Flow<List<Artist>>
    fun searchSongs(query: String): Flow<List<Song>>
    fun startScan(): Flow<ScanProgress>
    fun scanSpecificWebDavFolder(folderPath: String, additive: Boolean = true, recursive: Boolean = true): Flow<ScanProgress>
    suspend fun browseRemoteWebDavFolder(relativePath: String): Resource<List<WebDavItem>>
    suspend fun cancelScan()
    suspend fun scanLocalLibrary(treeUri: Uri? = null, additive: Boolean = false): Int
    fun runScanDiagnostics(startFolderPath: String = ""): Flow<ScanDiagnosticsState>
    suspend fun getSongCount(): Int
    suspend fun getSongById(id: String): Song?
    suspend fun setFavorite(songId: String, isFavorite: Boolean)
    fun getFavoriteSongs(): Flow<List<Song>>
    fun getDownloadedSongs(): Flow<List<Song>>
    fun getRecentlyPlayedSongs(): Flow<List<Song>>
    fun getMostPlayedSongs(): Flow<List<Song>>
    fun getResumableSongs(): Flow<List<Song>>
    suspend fun recordTrackPlay(songId: String)
    suspend fun updateResumePosition(songId: String, positionMs: Long)

    // Playlists & Nextcloud .m3u8 Sync
    fun getPlaylists(): Flow<List<Playlist>>
    fun getPlaylistSongs(playlistId: String): Flow<List<Song>>
    suspend fun createPlaylist(name: String): Playlist
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addSongToPlaylist(playlistId: String, songId: String)
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String)
    suspend fun syncPlaylistsWithNextcloud(): Result<Int>
}
