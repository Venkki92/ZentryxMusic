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
 * Data Access Object for audio tracks, FTS full-text search, Paging 3 queries, and local/folder queries.
 */
@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getPagingSongs(): PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongsFlow(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAllSongsOnce(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE album = :album AND artist = :artist ORDER BY trackNumber ASC, title ASC")
    fun getSongsByAlbum(album: String, artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY album ASC, trackNumber ASC")
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE relativePath = :relativePath LIMIT 1")
    suspend fun getSongByRelativePath(relativePath: String): SongEntity?

    @Query("SELECT relativePath FROM songs")
    fun getAllRelativePathsFlow(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE relativePath LIKE :prefix || '%' ORDER BY relativePath COLLATE NOCASE ASC")
    fun getSongsByFolderPrefix(prefix: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongs(ids: List<String>)

    @Query("DELETE FROM songs WHERE sourceType = 'WEBDAV' AND relativePath LIKE :folderPrefix || '%' AND id NOT IN (:keepIds)")
    suspend fun deleteRemovedSongsInFolder(folderPrefix: String, keepIds: List<String>)

    @Query("DELETE FROM songs WHERE sourceType = 'WEBDAV' AND id NOT IN (:keepIds)")
    suspend fun deleteWebDavSongsNotIn(keepIds: List<String>)

    @Query("DELETE FROM songs WHERE sourceType = 'WEBDAV'")
    suspend fun deleteAllWebDavSongs()

    @Query("DELETE FROM songs WHERE sourceType = 'LOCAL' AND id NOT IN (:keepIds)")
    suspend fun deleteLocalSongsNotIn(keepIds: List<String>)

    @Query("DELETE FROM songs WHERE sourceType = 'LOCAL'")
    suspend fun deleteAllLocalSongs()

    @Query("DELETE FROM songs")
    suspend fun clearAll()

    @Transaction
    @Query("""
        SELECT s.* FROM songs s
        JOIN songs_fts f ON s.rowid = f.docid
        WHERE songs_fts MATCH :query
        ORDER BY s.title COLLATE NOCASE ASC
        LIMIT 100
    """)
    fun searchSongsFts(query: String): Flow<List<SongEntity>>

    @Query("""
        SELECT album, artist, COUNT(*) as songCount, SUM(durationMs) as totalDurationMs,
               MAX(year) as year, MAX(artworkPath) as artworkPath
        FROM songs
        GROUP BY album, artist
        ORDER BY album COLLATE NOCASE ASC
    """)
    fun getDistinctAlbums(): Flow<List<AlbumSummary>>

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

    @Query("UPDATE songs SET lastPlayedAt = :timestampMs, playCount = playCount + 1 WHERE id = :id")
    suspend fun recordTrackPlay(id: String, timestampMs: Long)

    @Query("UPDATE songs SET lastPositionMs = :positionMs WHERE id = :id")
    suspend fun updateResumePosition(id: String, positionMs: Long)

    @Query("SELECT * FROM songs WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 50")
    fun getRecentlyPlayedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT 50")
    fun getMostPlayedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPositionMs > 10000 AND (durationMs = 0 OR lastPositionMs < (durationMs - 10000)) ORDER BY lastPlayedAt DESC, title COLLATE NOCASE ASC LIMIT 50")
    fun getResumableSongs(): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM songs")
    fun getTotalSongCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getTotalSongCountOnce(): Int
}
