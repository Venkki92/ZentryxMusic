package com.zentryx.music.scanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.zentryx.music.R
import com.zentryx.music.database.dao.FolderSyncDao
import com.zentryx.music.database.dao.SongDao
import com.zentryx.music.database.entities.FolderSyncEntity
import com.zentryx.music.database.entities.SongEntity
import com.zentryx.music.di.IoDispatcher
import com.zentryx.music.models.AudioFormat
import com.zentryx.music.models.Resource
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.network.metadata.RangeMetadataReader
import com.zentryx.music.network.webdav.WebDavClient
import com.zentryx.music.network.webdav.WebDavItem
import com.zentryx.music.network.webdav.WebDavUrlHelper
import com.zentryx.music.security.EncryptedDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.ArrayDeque

/**
 * Foreground WorkManager worker responsible for scanning Nextcloud WebDAV music libraries.
 * Operates in foreground mode to survive aggressive task termination on AutoChips AC8259P headunits.
 * Implements manual Depth: 1 folder recursion, incremental sync via ETags, and Range metadata reads.
 */
@HiltWorker
class LibraryScanWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val webDavClient: WebDavClient,
    private val urlHelper: WebDavUrlHelper,
    private val rangeMetadataReader: RangeMetadataReader,
    private val songDao: SongDao,
    private val folderSyncDao: FolderSyncDao,
    private val encryptedDataStore: EncryptedDataStore,
    private val artworkDiskCacheManager: ArtworkDiskCacheManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val UNIQUE_WORK_NAME = "zentryx_library_scan_work"
        const val NOTIFICATION_CHANNEL_ID = "zentryx_library_sync_channel"
        const val NOTIFICATION_ID = 2001

        const val KEY_CURRENT_FOLDER = "key_current_folder"
        const val KEY_FILES_DISCOVERED = "key_files_discovered"
        const val KEY_FILES_PROCESSED = "key_files_processed"
        const val KEY_STATUS = "key_status"
        const val KEY_ERROR_MESSAGE = "key_error_message"
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val config = encryptedDataStore.serverConfigFlow.firstOrNull()
            ?: return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Not logged in to Nextcloud"))

        createNotificationChannel()
        setForeground(createForegroundInfo("Connecting to Nextcloud WebDAV…", 0, 0))

        val rootUrl = config.musicWebDavUrl
        val folderQueue = ArrayDeque<String>()
        folderQueue.add(rootUrl)

        var totalFilesDiscovered = 0
        var totalFilesProcessed = 0

        try {
            while (folderQueue.isNotEmpty()) {
                if (isStopped) {
                    return@withContext Result.retry()
                }

                val currentFolderUrl = folderQueue.poll() ?: break
                val relativeFolderPath = currentFolderUrl
                    .removePrefix(config.webDavBaseUrl)
                    .trim('/')
                    .ifEmpty { "Music" }

                updateNotification("Scanning: /$relativeFolderPath", totalFilesDiscovered, totalFilesProcessed)
                setProgress(
                    workDataOf(
                        KEY_CURRENT_FOLDER to relativeFolderPath,
                        KEY_FILES_DISCOVERED to totalFilesDiscovered,
                        KEY_FILES_PROCESSED to totalFilesProcessed,
                        KEY_STATUS to "SCANNING"
                    )
                )

                // 1. Fetch directory listing with Depth: 1
                val listResult = webDavClient.listFolder(currentFolderUrl, config)
                if (listResult is Resource.Error) {
                    // Continue to next folder on non-fatal folder read errors
                    continue
                }

                val items = (listResult as Resource.Success).data
                val cachedSync = folderSyncDao.getFolderSync(relativeFolderPath)

                val subfolders = mutableListOf<WebDavItem>()
                val audioFiles = mutableListOf<WebDavItem>()

                for (item in items) {
                    if (item.isDirectory) {
                        subfolders.add(item)
                    } else if (AudioFormat.isSupported(item.name)) {
                        audioFiles.add(item)
                    }
                }

                totalFilesDiscovered += audioFiles.size

                // 2. Enqueue child subfolders for manual Depth: 1 recursion
                for (subfolder in subfolders) {
                    val childUrl = if (subfolder.href.startsWith("http")) {
                        subfolder.href
                    } else {
                        urlHelper.combineUrl(config.serverUrl, subfolder.href)
                    }
                    folderQueue.add(childUrl)
                }

                // 3. Process audio files with incremental sync
                val songsToUpsert = mutableListOf<SongEntity>()
                val validSongIdsInFolder = mutableListOf<String>()

                for (audioFile in audioFiles) {
                    if (isStopped) return@withContext Result.retry()

                    val fileUrl = if (audioFile.href.startsWith("http")) {
                        audioFile.href
                    } else {
                        urlHelper.combineUrl(config.serverUrl, audioFile.href)
                    }

                    val relativeFilePath = audioFile.href
                        .removePrefix(config.webDavBaseUrl)
                        .trimStart('/')

                    val songId = generateSongId(relativeFilePath)
                    validSongIdsInFolder.add(songId)

                    // Check if file already exists in database with matching ETag and size
                    val existingSong = songDao.getSongById(songId)
                    if (existingSong != null &&
                        existingSong.eTag != null &&
                        existingSong.eTag == audioFile.eTag &&
                        existingSong.fileSize == audioFile.contentLength
                    ) {
                        // File unmodified - reuse cached metadata
                        songsToUpsert.add(existingSong)
                        totalFilesProcessed++
                        continue
                    }

                    // New or modified file: extract metadata remotely using HTTP Range (first 64KB)
                    val metadata = rangeMetadataReader.extractMetadata(
                        fileUrl = fileUrl,
                        relativePath = relativeFilePath,
                        fileSize = audioFile.contentLength,
                        config = config
                    )

                    // Save extracted embedded album artwork to disk cache if present
                    val existingArt = artworkDiskCacheManager.getExistingArtworkPath(
                        metadata.artist ?: "Unknown Artist",
                        metadata.album ?: "Unknown Album"
                    )
                    val artworkPath = existingArt ?: if (metadata.rawArtworkBytes != null) {
                        artworkDiskCacheManager.saveArtwork(
                            artist = metadata.artist ?: "Unknown Artist",
                            album = metadata.album ?: "Unknown Album",
                            rawBytes = metadata.rawArtworkBytes
                        )
                    } else null

                    val format = AudioFormat.fromExtension(audioFile.extension)
                    val mimeType = audioFile.contentType ?: format?.mimeType ?: "audio/mpeg"

                    val songEntity = SongEntity(
                        id = songId,
                        title = metadata.title ?: audioFile.name.substringBeforeLast('.'),
                        artist = metadata.artist ?: "Unknown Artist",
                        album = metadata.album ?: "Unknown Album",
                        durationMs = metadata.durationMs,
                        fileUrl = fileUrl,
                        relativePath = relativeFilePath,
                        mimeType = mimeType,
                        trackNumber = metadata.trackNumber,
                        year = metadata.year,
                        genre = metadata.genre,
                        fileSize = audioFile.contentLength,
                        eTag = audioFile.eTag,
                        lastModified = audioFile.lastModified,
                        artworkPath = artworkPath
                    )

                    songsToUpsert.add(songEntity)
                    totalFilesProcessed++

                    if (songsToUpsert.size >= 25) {
                        songDao.insertSongs(songsToUpsert)
                        songsToUpsert.clear()
                    }
                }

                if (songsToUpsert.isNotEmpty()) {
                    songDao.insertSongs(songsToUpsert)
                    songsToUpsert.clear()
                }

                // 4. Remove stale tracks in this folder that were deleted from Nextcloud
                songDao.deleteRemovedSongsInFolder(relativeFolderPath, validSongIdsInFolder)

                // 5. Update FolderSyncEntity
                val folderMetadata = webDavClient.getFolderMetadata(currentFolderUrl, config)
                val folderEtag = (folderMetadata as? Resource.Success)?.data?.eTag
                folderSyncDao.upsertFolderSync(
                    FolderSyncEntity(
                        folderPath = relativeFolderPath,
                        eTag = folderEtag,
                        lastModified = System.currentTimeMillis(),
                        fileCount = audioFiles.size
                    )
                )
            }

            updateNotification("Library scan complete ($totalFilesProcessed tracks)", totalFilesDiscovered, totalFilesProcessed)
            Result.success(
                workDataOf(
                    KEY_FILES_DISCOVERED to totalFilesDiscovered,
                    KEY_FILES_PROCESSED to totalFilesProcessed,
                    KEY_STATUS to "COMPLETED"
                )
            )
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.localizedMessage ?: "Unknown scanning error")))
        }
    }

    private fun generateSongId(relativePath: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(relativePath.trim().lowercase().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Library Synchronization",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress during Nextcloud WebDAV music scanning"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(message: String, discovered: Int, processed: Int): ForegroundInfo {
        val notification = buildNotification(message, discovered, processed)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(message: String, discovered: Int, processed: Int) {
        val notification = buildNotification(message, discovered, processed)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(message: String, discovered: Int, processed: Int): Notification {
        val progressText = if (discovered > 0) "$processed of $discovered songs" else "$processed songs scanned"
        return NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Zentryx Music: Syncing Library")
            .setContentText(message)
            .setSubText(progressText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(discovered.coerceAtLeast(1), processed, discovered == 0)
            .build()
    }
}
