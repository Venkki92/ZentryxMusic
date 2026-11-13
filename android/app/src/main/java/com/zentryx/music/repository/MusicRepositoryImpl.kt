package com.zentryx.music.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.zentryx.music.database.dao.SongDao
import com.zentryx.music.database.entities.toDomainModel
import com.zentryx.music.di.IoDispatcher
import com.zentryx.music.models.Album
import com.zentryx.music.models.Artist
import com.zentryx.music.models.ScanProgress
import com.zentryx.music.models.ScanStatus
import com.zentryx.music.models.Song
import com.zentryx.music.scanner.LibraryScanWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MusicRepository {

    private val workManager: WorkManager by lazy {
        WorkManager.getInstance(context)
    }

    override fun getPagedSongs(): Flow<PagingData<Song>> {
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                prefetchDistance = 10,
                enablePlaceholders = true,
                initialLoadSize = 60
            ),
            pagingSourceFactory = { songDao.getPagingSongs() }
        ).flow.map { pagingData ->
            pagingData.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun getSongsByAlbum(albumName: String, artistName: String): Flow<List<Song>> {
        return songDao.getSongsByAlbum(albumName, artistName).map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun getAlbums(): Flow<List<Album>> {
        return songDao.getDistinctAlbums().map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun getArtists(): Flow<List<Artist>> {
        return songDao.getDistinctArtists().map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun searchSongs(query: String): Flow<List<Song>> {
        val cleanQuery = query.trim().replace("\"", "").replace("'", "")
        val ftsQuery = if (cleanQuery.isEmpty()) "" else "$cleanQuery*"

        return songDao.searchSongsFts(ftsQuery).map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun startScan(): Flow<ScanProgress> {
        val scanRequest = OneTimeWorkRequestBuilder<LibraryScanWorker>().build()
        workManager.enqueueUniqueWork(
            LibraryScanWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            scanRequest
        )

        return workManager.getWorkInfosForUniqueWorkFlow(LibraryScanWorker.UNIQUE_WORK_NAME).map { workInfoList ->
            val workInfo = workInfoList.firstOrNull() ?: return@map ScanProgress(status = ScanStatus.IDLE)
            val progress = workInfo.progress

            val currentFolder = progress.getString(LibraryScanWorker.KEY_CURRENT_FOLDER) ?: ""
            val discovered = progress.getInt(LibraryScanWorker.KEY_FILES_DISCOVERED, 0)
            val processed = progress.getInt(LibraryScanWorker.KEY_FILES_PROCESSED, 0)
            val percent = if (discovered > 0) (processed.toFloat() / discovered.toFloat()) * 100f else 0f

            when (workInfo.state) {
                WorkInfo.State.ENQUEUED -> ScanProgress(
                    status = ScanStatus.CONNECTING,
                    currentFolder = "Queueing background scan…"
                )
                WorkInfo.State.RUNNING -> ScanProgress(
                    status = ScanStatus.SCANNING,
                    currentFolder = currentFolder,
                    filesDiscovered = discovered,
                    filesProcessed = processed,
                    percentComplete = percent
                )
                WorkInfo.State.SUCCEEDED -> {
                    val finalDiscovered = workInfo.outputData.getInt(LibraryScanWorker.KEY_FILES_DISCOVERED, discovered)
                    val finalProcessed = workInfo.outputData.getInt(LibraryScanWorker.KEY_FILES_PROCESSED, processed)
                    ScanProgress(
                        status = ScanStatus.COMPLETED,
                        filesDiscovered = finalDiscovered,
                        filesProcessed = finalProcessed,
                        percentComplete = 100f
                    )
                }
                WorkInfo.State.CANCELLED -> ScanProgress(status = ScanStatus.CANCELLED)
                WorkInfo.State.FAILED -> {
                    val error = workInfo.outputData.getString(LibraryScanWorker.KEY_ERROR_MESSAGE) ?: "Scan failed"
                    ScanProgress(status = ScanStatus.ERROR, errorMessage = error)
                }
                WorkInfo.State.BLOCKED -> ScanProgress(status = ScanStatus.CONNECTING)
            }
        }.flowOn(ioDispatcher)
    }

    override suspend fun cancelScan() {
        workManager.cancelUniqueWork(LibraryScanWorker.UNIQUE_WORK_NAME)
    }

    override suspend fun getSongById(id: String): Song? {
        return songDao.getSongById(id)?.toDomainModel()
    }

    override suspend fun setFavorite(songId: String, isFavorite: Boolean) {
        withContext(ioDispatcher) {
            songDao.updateFavorite(songId, isFavorite)
        }
    }

    override fun getFavoriteSongs(): Flow<List<Song>> {
        return songDao.getFavoriteSongs().map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun getDownloadedSongs(): Flow<List<Song>> {
        return songDao.getDownloadedSongs().map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }
}
