package com.zentryx.music.repository

import androidx.paging.PagingData
import com.zentryx.music.models.Album
import com.zentryx.music.models.Artist
import com.zentryx.music.models.ScanProgress
import com.zentryx.music.models.Song
import kotlinx.coroutines.flow.Flow

/**
 * Primary repository interface for audio catalog management.
 * Designed behind an interface so future backend sources (e.g. Nextcloud Music Ampache/Subsonic API)
 * can be plugged in without changing ViewModels or UI.
 */
interface MusicRepository {
    /**
     * Returns a paginated flow of all songs ordered by title or artist.
     */
    fun getPagedSongs(): Flow<PagingData<Song>>

    /**
     * Returns all songs for a specific album ordered by track number.
     */
    fun getSongsByAlbum(albumName: String, artistName: String): Flow<List<Song>>

    /**
     * Returns all unique albums.
     */
    fun getAlbums(): Flow<List<Album>>

    /**
     * Returns all unique artists.
     */
    fun getArtists(): Flow<List<Artist>>

    /**
     * Performs fast local full-text search (FTS) across songs, albums, and artists.
     */
    fun searchSongs(query: String): Flow<List<Song>>

    /**
     * Triggers incremental library scan via WorkManager.
     */
    fun startScan(): Flow<ScanProgress>

    /**
     * Cancels an ongoing library scan.
     */
    suspend fun cancelScan()

    /**
     * Retrieves a song by its unique identifier.
     */
    suspend fun getSongById(id: String): Song?

    /**
     * Toggles favorite heart state on a song.
     */
    suspend fun setFavorite(songId: String, isFavorite: Boolean)

    /**
     * Returns flow of user favorite songs.
     */
    fun getFavoriteSongs(): Flow<List<Song>>

    /**
     * Returns flow of offline downloaded songs.
     */
    fun getDownloadedSongs(): Flow<List<Song>>
}
