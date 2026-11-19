package com.zentryx.music.scanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
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
import com.zentryx.music.models.ErrorType
import com.zentryx.music.models.Resource
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.network.metadata.ParsedMetadata
import com.zentryx.music.network.metadata.RangeMetadataReader
import com.zentryx.music.network.webdav.WebDavClient
import com.zentryx.music.network.webdav.WebDavItem
import com.zentryx.music.network.webdav.WebDavUrlHelper
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.security.EncryptedDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.ArrayDeque

/**
 * WorkManager worker responsible for scanning Nextcloud WebDAV music libraries.
 * Scans all configured default folders (WoW Radio + Music) and user-added extra folders,
 * supports additive single-folder scans (merging discovered tracks without pruning existing songs),
 * and falls back to Nextcloud root only if all primary folders are empty/unavailable.
 */
class LibraryScanWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val webDavClient: WebDavClient,
    private val urlHelper: WebDavUrlHelper,
    private val rangeMetadataReader: RangeMetadataReader,
    private val songDao: SongDao,
    private val folderSyncDao: FolderSyncDao,
    private val encryptedDataStore: EncryptedDataStore,
    private val settingsPreferences: SettingsPreferences,
    private val artworkDiskCacheManager: ArtworkDiskCacheManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(appContext, workerParams) {

    @AssistedFactory
    interface Factory {
        fun create(appContext: Context, workerParams: WorkerParameters): LibraryScanWorker
    }

    companion object {
        const val UNIQUE_WORK_NAME = "zentryx_library_scan_work"
        const val NOTIFICATION_CHANNEL_ID = "zentryx_library_sync_channel"
        const val NOTIFICATION_ID = 2001

        const val KEY_CURRENT_FOLDER = "key_current_folder"
        const val KEY_FILES_DISCOVERED = "key_files_discovered"
        const val KEY_FILES_PROCESSED = "key_files_processed"
        const val KEY_STATUS = "key_status"
        const val KEY_ERROR_MESSAGE = "key_error_message"

        const val KEY_TARGET_FOLDER_PATH = "key_target_folder_path"
        const val KEY_ADDITIVE_MODE = "key_additive_mode"
        const val KEY_RECURSIVE = "key_recursive"

        internal fun buildCandidateRootsStatic(
            config: ServerConfig,
            extraFolders: List<String> = emptyList(),
            explicitTargetFolder: String? = null
        ): List<RootCandidate> {
            val list = mutableListOf<RootCandidate>()
            val seenUrls = mutableSetOf<String>()

            fun addCandidate(cfg: ServerConfig, url: String, isFallback: Boolean) {
                val norm = url.trimEnd('/')
                if (seenUrls.add(norm.lowercase())) {
                    list.add(RootCandidate(cfg, norm, isFallback))
                }
            }

            if (explicitTargetFolder != null) {
                val targetClean = ServerConfig.normalizeFolderPath(explicitTargetFolder)
                val targetUrl = config.buildWebDavFolderUrl(targetClean)
                addCandidate(
                    config.copy(musicFolderPath = if (targetClean.isEmpty()) "/" else "/$targetClean"),
                    targetUrl,
                    isFallback = false
                )
                if (!config.useLegacyWebDav) {
                    val legacyConfig = config.copy(
                        useLegacyWebDav = true,
                        musicFolderPath = if (targetClean.isEmpty()) "/" else "/$targetClean"
                    )
                    addCandidate(legacyConfig, legacyConfig.buildWebDavFolderUrl(targetClean), isFallback = true)
                }
                return list
            }

            // Full scan: enqueue EVERY configured default folder (e.g. WoW Radio, Music) + saved extra folders
            val primaryFolders = config.configuredMusicFolders(extraFolders)
            for (folder in primaryFolders) {
                val folderUrl = config.buildWebDavFolderUrl(folder)
                val folderLabel = if (folder.isEmpty()) "/" else "/$folder"
                addCandidate(config.copy(musicFolderPath = folderLabel), folderUrl, isFallback = false)
            }

            // Fallback to Nextcloud root only if all primary folders combined discover 0 tracks
            val baseRootUrl = config.webDavBaseUrl.trimEnd('/')
            addCandidate(config.copy(musicFolderPath = "/"), baseRootUrl, isFallback = true)

            if (!config.useLegacyWebDav) {
                val legacyConfig = config.copy(useLegacyWebDav = true, musicFolderPath = "/")
                addCandidate(legacyConfig, legacyConfig.webDavBaseUrl.trimEnd('/'), isFallback = true)
            }
            return list
        }
    }

    private val notificationManager: NotificationManager?
        get() = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        try {
            val rawConfig = encryptedDataStore.serverConfigFlow.firstOrNull()
                ?: return@withContext Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to "Not logged in to Nextcloud")
                )

            if (rawConfig.serverUrl.isBlank() || rawConfig.username.isBlank() || rawConfig.tokenOrPassword.isBlank()) {
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to "Incomplete Nextcloud credentials — please sign in again")
                )
            }

            runCatching { createNotificationChannel() }
            runCatching { setForeground(createForegroundInfo("Connecting to Nextcloud WebDAV…", 0, 0)) }

            val explicitTargetFolder = inputData.getString(KEY_TARGET_FOLDER_PATH)?.takeIf { it.isNotEmpty() }
            val isAdditiveMode = inputData.getBoolean(KEY_ADDITIVE_MODE, false)
            val isRecursive = inputData.getBoolean(KEY_RECURSIVE, true)
            val extraFolders = if (explicitTargetFolder == null) {
                settingsPreferences.extraWebDavFoldersFlow.firstOrNull().orEmpty()
            } else {
                emptyList()
            }

            val candidates = buildCandidateRoots(rawConfig, extraFolders, explicitTargetFolder)
            var lastErrorMessage: String? = null
            var anyRootSucceeded = false
            var totalFilesDiscovered = 0
            var totalFilesProcessed = 0
            val validWebDavSongIds = mutableListOf<String>()
            val visitedFolders = mutableSetOf<String>()

            for ((activeConfig, rootUrl, isFallbackCandidate) in candidates) {
                if (isStopped) return@withContext Result.retry()

                // Skip fallback root if primary configured folder(s) already found tracks
                if (isFallbackCandidate && totalFilesProcessed > 0) {
                    break
                }

                val folderQueue = ArrayDeque<String>()
                folderQueue.add(rootUrl)
                var isFirstFolderForCandidate = true

                while (folderQueue.isNotEmpty()) {
                    if (isStopped) {
                        return@withContext Result.retry()
                    }

                    val currentFolderUrl = folderQueue.poll() ?: break
                    val normalizedFolderKey = currentFolderUrl.trimEnd('/')
                    if (!visitedFolders.add(normalizedFolderKey)) {
                        continue
                    }

                    val relativeFolderPath = urlHelper.extractRelativePath(currentFolderUrl, activeConfig.username)
                        .ifEmpty { if (isFallbackCandidate) "Root" else activeConfig.musicFolderPath.trim('/').ifEmpty { "Root" } }

                    runCatching {
                        updateNotification("Scanning: /$relativeFolderPath", totalFilesDiscovered, totalFilesProcessed)
                    }
                    setProgress(
                        workDataOf(
                            KEY_CURRENT_FOLDER to "/$relativeFolderPath",
                            KEY_FILES_DISCOVERED to totalFilesDiscovered,
                            KEY_FILES_PROCESSED to totalFilesProcessed,
                            KEY_STATUS to "SCANNING"
                        )
                    )

                    val listResult = webDavClient.listFolder(currentFolderUrl, activeConfig)
                    if (listResult is Resource.Error) {
                        if (isFirstFolderForCandidate) {
                            lastErrorMessage = listResult.message
                            // Auth / SSL / Network errors shouldn't silently keep probing legacy paths
                            if (listResult.errorType == ErrorType.UNAUTHORIZED ||
                                listResult.errorType == ErrorType.SSL_ERROR ||
                                listResult.errorType == ErrorType.NO_NETWORK ||
                                listResult.errorType == ErrorType.BAD_URL
                            ) {
                                return@withContext Result.failure(
                                    workDataOf(KEY_ERROR_MESSAGE to listResult.message)
                                )
                            }
                            break
                        }
                        continue
                    }

                    isFirstFolderForCandidate = false
                    anyRootSucceeded = true

                    val items = (listResult as Resource.Success).data
                    val subfolders = mutableListOf<WebDavItem>()
                    val audioFiles = mutableListOf<WebDavItem>()

                    for (item in items) {
                        if (item.name.startsWith(".")) continue
                        if (item.isDirectory) {
                            subfolders.add(item)
                        } else if (AudioFormat.isSupported(item.name)) {
                            audioFiles.add(item)
                        }
                    }

                    totalFilesDiscovered += audioFiles.size

                    if (isRecursive) {
                        for (subfolder in subfolders) {
                            val childUrl = urlHelper.resolveHrefUrl(activeConfig.serverUrl, subfolder.href)
                            if (!visitedFolders.contains(childUrl.trimEnd('/'))) {
                                folderQueue.add(childUrl)
                            }
                        }
                    }

                    val songsToUpsert = mutableListOf<SongEntity>()

                    for (audioFile in audioFiles) {
                        if (isStopped) return@withContext Result.retry()

                        val fileUrl = urlHelper.resolveHrefUrl(activeConfig.serverUrl, audioFile.href)
                        val relativeFilePath = urlHelper.extractRelativePath(audioFile.href, activeConfig.username)
                        val songId = generateSongId(relativeFilePath)
                        validWebDavSongIds.add(songId)

                        val existingSong = songDao.getSongById(songId)
                        if (existingSong != null &&
                            existingSong.eTag != null &&
                            existingSong.eTag == audioFile.eTag &&
                            existingSong.fileSize == audioFile.contentLength
                        ) {
                            songsToUpsert.add(
                                if (existingSong.fileUrl != fileUrl) existingSong.copy(fileUrl = fileUrl) else existingSong
                            )
                            totalFilesProcessed++
                            continue
                        }

                        val metadata = try {
                            rangeMetadataReader.extractMetadata(
                                fileUrl = fileUrl,
                                relativePath = relativeFilePath,
                                fileSize = audioFile.contentLength,
                                config = activeConfig
                            )
                        } catch (_: Throwable) {
                            rangeMetadataReader.parseFromPath(relativeFilePath)
                        }

                        val artistName = metadata.artist?.ifBlank { null } ?: "Unknown Artist"
                        val albumName = metadata.album?.ifBlank { null } ?: "Unknown Album"
                        val artworkPath = runCatching {
                            val existingArt = artworkDiskCacheManager.getExistingArtworkPath(artistName, albumName)
                            existingArt ?: if (metadata.rawArtworkBytes != null) {
                                artworkDiskCacheManager.saveArtwork(artistName, albumName, metadata.rawArtworkBytes)
                            } else null
                        }.getOrNull()

                        val format = AudioFormat.fromExtension(audioFile.extension)
                        val mimeType = audioFile.contentType?.ifBlank { null } ?: format?.mimeType ?: "audio/mpeg"

                        songsToUpsert.add(
                            SongEntity(
                                id = songId,
                                title = metadata.title?.ifBlank { null } ?: audioFile.name.substringBeforeLast('.'),
                                artist = artistName,
                                album = albumName,
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
                                artworkPath = artworkPath,
                                isFavorite = existingSong?.isFavorite ?: false,
                                isDownloaded = existingSong?.isDownloaded ?: false,
                                localFilePath = existingSong?.localFilePath,
                                lyricsText = existingSong?.lyricsText,
                                sourceType = "WEBDAV",
                                sampleRateHz = metadata.sampleRateHz,
                                bitsPerSample = metadata.bitsPerSample,
                                bitrateKbps = metadata.bitrateKbps,
                                replayGainDb = metadata.replayGainDb
                            )
                        )
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

                    runCatching {
                        val folderMetadata = webDavClient.getFolderMetadata(currentFolderUrl, activeConfig)
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
                }
            }

            if (!anyRootSucceeded) {
                val msg = lastErrorMessage ?: "Could not reach Nextcloud WebDAV folder"
                runCatching { updateNotification("Sync error: $msg", 0, 0) }
                return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to msg))
            }

            // Prune deleted WebDAV tracks ONLY on full non-additive scans so manually added folders never wipe existing songs
            if (!isAdditiveMode) {
                pruneRemovedWebDavSongs(validWebDavSongIds)
            }

            runCatching {
                val doneMsg = if (isAdditiveMode) {
                    "Added $totalFilesProcessed track(s) to library"
                } else {
                    "Library scan complete ($totalFilesProcessed tracks)"
                }
                updateNotification(doneMsg, totalFilesDiscovered, totalFilesProcessed)
            }
            Result.success(
                workDataOf(
                    KEY_FILES_DISCOVERED to totalFilesDiscovered,
                    KEY_FILES_PROCESSED to totalFilesProcessed,
                    KEY_STATUS to "COMPLETED"
                )
            )
        } catch (t: Throwable) {
            val message = t.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "${t::class.simpleName ?: "ScanError"} during WebDAV scan"
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
        }
    }

    private suspend fun pruneRemovedWebDavSongs(validIds: List<String>) {
        if (validIds.isEmpty()) {
            songDao.deleteAllWebDavSongs()
            return
        }
        if (validIds.size <= 900) {
            songDao.deleteWebDavSongsNotIn(validIds)
            return
        }
        val keepSet = validIds.toHashSet()
        val staleIds = songDao.getAllSongsOnce()
            .filter { it.sourceType == "WEBDAV" && !keepSet.contains(it.id) }
            .map { it.id }
        staleIds.chunked(500).forEach { chunk ->
            songDao.deleteSongs(chunk)
        }
    }

    internal data class RootCandidate(
        val config: ServerConfig,
        val url: String,
        val isFallback: Boolean
    )

    internal fun buildCandidateRoots(
        config: ServerConfig,
        extraFolders: List<String> = emptyList(),
        explicitTargetFolder: String? = null
    ): List<RootCandidate> = buildCandidateRootsStatic(config, extraFolders, explicitTargetFolder)

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
            notificationManager?.createNotificationChannel(channel)
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
        notificationManager?.notify(NOTIFICATION_ID, notification)
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
