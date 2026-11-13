package com.zentryx.music.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.zentryx.music.database.entities.AlbumSummary
import com.zentryx.music.database.entities.ArtistSummary
import com.zentryx.music.database.entities.SongEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for audio tracks, FTS full-text search, and Paging 3 queries.
 */
@Dao
interface SongDao {

    /**
     * Paging 3 source for virtualized scrolling over large libraries on low-RAM headunits.
     */
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getPagingSongs(): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album = :album AND artist = :artist ORDER BY trackNumber ASC, title ASC")
    fun getSongsByAlbum(album: String, artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album ASC, trackNumber ASC")
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongs(ids: List<String>)

    /**
     * Deletes tracks in a specific folder that no longer exist on the remote WebDAV server.
     */
    @Query("DELETE FROM songs WHERE relativePath LIKE :folderPrefix || '%' AND id NOT IN (:keepIds)")
    suspend fun deleteRemovedSongsInFolder(folderPrefix: String, keepIds: List<String>)

    @Query("DELETE FROM songs")
    suspend fun clearAll()

    /**
     * Fast full-text search (FTS4) matching title, artist, album, and genre.
     * Uses prefix query pattern ':query*' for real-time keystroke searches.
     */
    @Transaction
    @Query("""
        SELECT s.* FROM songs s
        JOIN songs_fts f ON s.rowid = f.docid
        WHERE songs_fts MATCH :query
        ORDER BY s.title COLLATE NOCASE ASC
        LIMIT 100
    """)
    fun searchSongsFts(query: String): Flow<List<SongEntity>>

    /**
     * Distinct albums aggregation for the Library screen.
     */
    @Query("""
        SELECT album, artist, COUNT(*) as songCount, SUM(durationMs) as totalDurationMs,
               MAX(year) as year, MAX(artworkPath) as artworkPath
        FROM songs
        GROUP BY album, artist
        ORDER BY album COLLATE NOCASE ASC
    """)
    fun getDistinctAlbums(): Flow<List<AlbumSummary>>

    /**
     * Distinct artists aggregation for the Library screen.
     */
    @Query("""
        SELECT artist, COUNT(DISTINCT album) as albumCount, COUNT(*) as songCount
        FROM songs
        GROUP BY artist
        ORDER BY artist COLLATE NOCASE ASC
    """)
    fun getDistinctArtists(): Flow<List<ArtistSummary>>

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE songs SET isDownloaded = :isDownloaded, localFilePath = :localPath WHERE id = :id")
    suspend fun updateDownloadStatus(id: String, isDownloaded: Boolean, localPath: String?)

    @Query("UPDATE songs SET lyricsText = :lyrics WHERE id = :id")
    suspend fun updateLyrics(id: String, lyrics: String)

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getDownloadedSongs(): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM songs")
    fun getTotalSongCountFlow(): Flow<Int>
}
