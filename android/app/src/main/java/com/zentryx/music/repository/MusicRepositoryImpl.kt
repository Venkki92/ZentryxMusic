package com.zentryx.music.repository

import android.content.Context
import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.zentryx.music.database.dao.PlaylistDao
import com.zentryx.music.database.dao.SongDao
import com.zentryx.music.database.entities.PlaylistEntity
import com.zentryx.music.database.entities.PlaylistSongCrossRef
import com.zentryx.music.database.entities.toDomainModel
import com.zentryx.music.di.IoDispatcher
import com.zentryx.music.models.Album
import com.zentryx.music.models.Artist
import com.zentryx.music.models.AudioFormat
import com.zentryx.music.models.Playlist
import com.zentryx.music.models.Resource
import com.zentryx.music.models.ScanProgress
import com.zentryx.music.models.ScanStatus
import com.zentryx.music.models.Song
import com.zentryx.music.network.webdav.DiagnosticProbeEntry
import com.zentryx.music.network.webdav.DiagnosticStepStatus
import com.zentryx.music.network.webdav.ScanDiagnosticsState
import com.zentryx.music.network.webdav.WebDavClient
import com.zentryx.music.network.webdav.WebDavPermissionDecoder
import com.zentryx.music.network.webdav.WebDavUrlHelper
import com.zentryx.music.playlists.M3uPlaylistParser
import com.zentryx.music.scanner.LibraryScanWorker
import com.zentryx.music.scanner.LocalMediaScanner
import com.zentryx.music.security.EncryptedDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val localMediaScanner: LocalMediaScanner,
    private val webDavClient: WebDavClient,
    private val urlHelper: WebDavUrlHelper,
    private val encryptedDataStore: EncryptedDataStore,
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

    override fun getAllSongs(): Flow<List<Song>> {
        return songDao.getAllSongsFlow().map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun getSongsByAlbum(albumName: String, artistName: String): Flow<List<Song>> {
        return songDao.getSongsByAlbum(albumName, artistName).map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun getSongsByArtist(artistName: String): Flow<List<Song>> {
        return songDao.getSongsByArtist(artistName).map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun getSongsByFolderPrefix(prefix: String): Flow<List<Song>> {
        return songDao.getSongsByFolderPrefix(prefix).map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun getAllRelativePaths(): Flow<List<String>> {
        return songDao.getAllRelativePathsFlow().flowOn(ioDispatcher)
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
            ExistingWorkPolicy.REPLACE,
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
                    currentFolder = "Connecting to Nextcloud…"
                )
                WorkInfo.State.RUNNING -> ScanProgress(
                    status = ScanStatus.SCANNING,
                    currentFolder = currentFolder.ifBlank { "Scanning Nextcloud…" },
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
                WorkInfo.State.BLOCKED -> ScanProgress(
                    status = ScanStatus.CONNECTING,
                    currentFolder = "Waiting for network…"
                )
            }
        }.flowOn(ioDispatcher)
    }

    override suspend fun cancelScan() {
        workManager.cancelUniqueWork(LibraryScanWorker.UNIQUE_WORK_NAME)
    }

    override suspend fun scanLocalLibrary(treeUri: Uri?): Int {
        return localMediaScanner.scanLocalAudio(treeUri)
    }

    override fun runScanDiagnostics(startFolderPath: String): Flow<ScanDiagnosticsState> = flow {
        val entries = mutableListOf<DiagnosticProbeEntry>()
        fun snapshot(
            running: Boolean,
            complete: Boolean,
            stepText: String,
            summary: String? = null
        ) = ScanDiagnosticsState(
            isRunning = running,
            isComplete = complete,
            currentStepText = stepText,
            summaryMessage = summary,
            entries = entries.toList()
        )

        emit(snapshot(running = true, complete = false, stepText = "Checking stored Nextcloud configuration…"))
        val config = encryptedDataStore.serverConfigFlow.firstOrNull()
        if (config == null || config.serverUrl.isBlank() || config.username.isBlank() || config.tokenOrPassword.isBlank()) {
            entries.add(
                DiagnosticProbeEntry(
                    id = "step_auth",
                    stepTitle = "Step 1 · Server Configuration",
                    targetPath = "Keystore Session",
                    httpStatusCode = null,
                    permissionsLabel = "Not Authenticated",
                    detailMessage = "No active Nextcloud WebDAV credentials saved. Sign in to run WebDAV diagnostics.",
                    status = DiagnosticStepStatus.FAIL
                )
            )
            emit(
                snapshot(
                    running = false,
                    complete = true,
                    stepText = "Diagnostics stopped",
                    summary = "Failed at Step 1: Not signed in to Nextcloud WebDAV."
                )
            )
            return@flow
        }

        val sslInfo = if (!config.sslCertificateFingerprint.isNullOrBlank()) "TOFU Cert Pinned" else "System CA"
        entries.add(
            DiagnosticProbeEntry(
                id = "step_config",
                stepTitle = "Step 1 · Credentials & Target",
                targetPath = config.serverUrl,
                httpStatusCode = null,
                permissionsLabel = "Configured ($sslInfo)",
                detailMessage = "User: ${config.username} • Music Root: ${config.musicFolderPath}",
                status = DiagnosticStepStatus.PASS
            )
        )
        emit(snapshot(running = true, complete = false, stepText = "Probing WebDAV root endpoint…"))

        // Step 2: Test WebDAV root endpoint (Depth: 0)
        var activeConfig = config
        val rootProbe = webDavClient.probeFolder(config.webDavBaseUrl, config, depth = 0)
        entries.add(
            DiagnosticProbeEntry(
                id = "step_root",
                stepTitle = "Step 2 · WebDAV Root Endpoint",
                targetPath = if (config.useLegacyWebDav) "/remote.php/webdav" else "/remote.php/dav/files/${config.username}",
                httpStatusCode = rootProbe.httpStatusCode,
                permissionsLabel = WebDavPermissionDecoder.decode(rootProbe.selfPermissions, rootProbe.httpStatusCode),
                rawPermissions = rootProbe.selfPermissions,
                detailMessage = rootProbe.errorMessage ?: "DAV: ${rootProbe.davHeader ?: "1, 3"} • Auth & endpoint verified",
                latencyMs = rootProbe.latencyMs,
                status = if (rootProbe.isSuccess) DiagnosticStepStatus.PASS else DiagnosticStepStatus.FAIL
            )
        )
        emit(snapshot(running = true, complete = false, stepText = "Checking music directory & fallback…"))

        var effectiveRootSuccess = rootProbe.isSuccess
        if (!rootProbe.isSuccess && !config.useLegacyWebDav && (rootProbe.httpStatusCode == 404 || rootProbe.httpStatusCode == 405)) {
            val legacyConfig = config.copy(useLegacyWebDav = true)
            val legacyProbe = webDavClient.probeFolder(legacyConfig.webDavBaseUrl, legacyConfig, depth = 0)
            entries.add(
                DiagnosticProbeEntry(
                    id = "step_legacy_root",
                    stepTitle = "Step 2b · Legacy WebDAV Fallback",
                    targetPath = "/remote.php/webdav",
                    httpStatusCode = legacyProbe.httpStatusCode,
                    permissionsLabel = WebDavPermissionDecoder.decode(legacyProbe.selfPermissions, legacyProbe.httpStatusCode),
                    rawPermissions = legacyProbe.selfPermissions,
                    detailMessage = legacyProbe.errorMessage ?: "Legacy WebDAV endpoint reachable",
                    latencyMs = legacyProbe.latencyMs,
                    status = if (legacyProbe.isSuccess) DiagnosticStepStatus.PASS else DiagnosticStepStatus.FAIL
                )
            )
            if (legacyProbe.isSuccess) {
                activeConfig = legacyConfig
                effectiveRootSuccess = true
            }
            emit(snapshot(running = true, complete = false, stepText = "Probing music directories…"))
        }

        if (!effectiveRootSuccess) {
            emit(
                snapshot(
                    running = false,
                    complete = true,
                    stepText = "Connectivity check failed",
                    summary = "Nextcloud WebDAV root unreachable (HTTP ${rootProbe.httpStatusCode ?: "ERR"}): ${rootProbe.errorMessage ?: "Check URL/credentials"}"
                )
            )
            return@flow
        }

        // Step 3+: Probe configured folder or currently open folder + discovered subdirectories
        val cleanStart = startFolderPath.trim().trim('/')
        val isLocalFolderOpen = cleanStart.startsWith("Local/", ignoreCase = true) || cleanStart.startsWith("USB/", ignoreCase = true)
        val configuredRel = activeConfig.musicFolderPath.trim('/')
        val primaryRelPath = if (cleanStart.isNotEmpty() && !isLocalFolderOpen) cleanStart else configuredRel
        val primaryUrl = if (primaryRelPath.isEmpty()) {
            activeConfig.webDavBaseUrl
        } else {
            urlHelper.combineUrl(activeConfig.webDavBaseUrl, primaryRelPath)
        }

        val queue = ArrayDeque<Triple<String, String, String?>>() // (url, displayPath, parentReportedPerms)
        queue.add(Triple(primaryUrl, "/${primaryRelPath}".replace("//", "/"), null))
        val visited = mutableSetOf<String>()
        var probedDirs = 0
        var passedDirs = 0
        var warningDirs = 0
        var failedDirs = 0
        var totalAudioFound = 0
        val maxSubdirs = 40
        var truncated = false

        while (queue.isNotEmpty()) {
            if (probedDirs >= maxSubdirs) {
                truncated = true
                break
            }
            val (targetUrl, displayPath, inheritedPerms) = queue.poll() ?: break
            val normKey = targetUrl.trimEnd('/')
            if (!visited.add(normKey)) continue

            probedDirs++
            val label = if (probedDirs == 1) "Step 3 · Target Folder" else "Subdirectory #${probedDirs - 1}"
            emit(snapshot(running = true, complete = false, stepText = "Probing $displayPath ($probedDirs)…"))

            val probe = webDavClient.probeFolder(targetUrl, activeConfig, depth = 1)
            val effectiveRawPerms = probe.selfPermissions ?: inheritedPerms
            val permText = WebDavPermissionDecoder.decode(effectiveRawPerms, probe.httpStatusCode)

            if (probe.isSuccess) {
                val subFolders = probe.items.filter { it.isDirectory && !it.name.startsWith(".") }
                val audioFiles = probe.items.filter { !it.isDirectory && AudioFormat.isSupported(it.name) }
                val otherFiles = probe.items.count { !it.isDirectory && !AudioFormat.isSupported(it.name) }
                totalAudioFound += audioFiles.size

                val dirStatus = if (probedDirs == 1 && subFolders.isEmpty() && audioFiles.isEmpty()) {
                    warningDirs++
                    DiagnosticStepStatus.WARN
                } else {
                    passedDirs++
                    DiagnosticStepStatus.PASS
                }

                val detail = buildString {
                    append("${audioFiles.size} audio file(s), ${subFolders.size} subfolder(s)")
                    if (otherFiles > 0) append(", $otherFiles other file(s)")
                }
                entries.add(
                    DiagnosticProbeEntry(
                        id = "dir_$probedDirs",
                        stepTitle = label,
                        targetPath = displayPath,
                        httpStatusCode = probe.httpStatusCode,
                        permissionsLabel = permText,
                        rawPermissions = effectiveRawPerms,
                        detailMessage = detail,
                        latencyMs = probe.latencyMs,
                        status = dirStatus
                    )
                )

                for (sub in subFolders) {
                    val subUrl = urlHelper.resolveHrefUrl(activeConfig.serverUrl, sub.href)
                    val relSub = urlHelper.extractRelativePath(sub.href, activeConfig.username)
                        .ifEmpty { sub.name }
                    if (!visited.contains(subUrl.trimEnd('/'))) {
                        queue.add(Triple(subUrl, "/$relSub", sub.permissions))
                    }
                }

                // If primary configured folder succeeded with 0 files & 0 subfolders, also check root "/"
                if (probedDirs == 1 && primaryRelPath.isNotEmpty() && subFolders.isEmpty() && audioFiles.isEmpty()) {
                    val baseRoot = activeConfig.webDavBaseUrl.trimEnd('/')
                    if (!visited.contains(baseRoot)) {
                        queue.add(Triple(baseRoot, "/ (Fallback Root)", null))
                    }
                }
            } else {
                if (probe.httpStatusCode == 404 && probedDirs == 1 && primaryRelPath.isNotEmpty()) {
                    warningDirs++
                    entries.add(
                        DiagnosticProbeEntry(
                            id = "dir_$probedDirs",
                            stepTitle = label,
                            targetPath = displayPath,
                            httpStatusCode = probe.httpStatusCode,
                            permissionsLabel = permText,
                            rawPermissions = effectiveRawPerms,
                            detailMessage = "${probe.errorMessage ?: "HTTP 404"} — falling back to Nextcloud root /",
                            latencyMs = probe.latencyMs,
                            status = DiagnosticStepStatus.WARN
                        )
                    )
                    val baseRoot = activeConfig.webDavBaseUrl.trimEnd('/')
                    if (!visited.contains(baseRoot)) {
                        queue.add(Triple(baseRoot, "/ (Fallback Root)", null))
                    }
                } else {
                    failedDirs++
                    entries.add(
                        DiagnosticProbeEntry(
                            id = "dir_$probedDirs",
                            stepTitle = label,
                            targetPath = displayPath,
                            httpStatusCode = probe.httpStatusCode,
                            permissionsLabel = permText,
                            rawPermissions = effectiveRawPerms,
                            detailMessage = probe.errorMessage ?: "Probe failed",
                            latencyMs = probe.latencyMs,
                            status = DiagnosticStepStatus.FAIL
                        )
                    )
                }
            }

            emit(snapshot(running = true, complete = false, stepText = "Checked $displayPath (${audioFilesCountText(totalAudioFound)})"))
        }

        val summary = buildString {
            append("Probed $probedDirs folder(s): $passedDirs accessible")
            if (warningDirs > 0) append(", $warningDirs warning(s)")
            if (failedDirs > 0) append(", $failedDirs failed")
            append(" • $totalAudioFound audio track(s) visible")
            if (truncated) append(" (capped at $maxSubdirs folders)")
        }
        emit(snapshot(running = false, complete = true, stepText = "Diagnostics Complete", summary = summary))
    }.flowOn(ioDispatcher)

    private fun audioFilesCountText(count: Int): String = "$count audio track${if (count == 1) "" else "s"} found"

    override suspend fun getSongCount(): Int = withContext(ioDispatcher) {
        songDao.getTotalSongCountOnce()
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

    override fun getRecentlyPlayedSongs(): Flow<List<Song>> {
        return songDao.getRecentlyPlayedSongs().map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun getMostPlayedSongs(): Flow<List<Song>> {
        return songDao.getMostPlayedSongs().map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun getResumableSongs(): Flow<List<Song>> {
        return songDao.getResumableSongs().map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override suspend fun recordTrackPlay(songId: String) {
        withContext(ioDispatcher) {
            songDao.recordTrackPlay(songId, System.currentTimeMillis())
        }
    }

    override suspend fun updateResumePosition(songId: String, positionMs: Long) {
        withContext(ioDispatcher) {
            songDao.updateResumePosition(songId, positionMs.coerceAtLeast(0L))
        }
    }

    override fun getPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylistsFlow().map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun getPlaylistSongs(playlistId: String): Flow<List<Song>> {
        return playlistDao.getSongsForPlaylistFlow(playlistId).map { list ->
            list.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override suspend fun createPlaylist(name: String): Playlist = withContext(ioDispatcher) {
        val cleanName = name.trim().ifBlank { "Untitled Playlist" }
        val now = System.currentTimeMillis()
        val id = "pl_" + UUID.randomUUID().toString().replace("-", "").take(16)
        playlistDao.upsertPlaylist(PlaylistEntity(id = id, name = cleanName, createdAt = now))
        Playlist(id = id, name = cleanName, createdAt = now, songCount = 0)
    }

    override suspend fun deletePlaylist(playlistId: String) {
        withContext(ioDispatcher) {
            playlistDao.deletePlaylistCrossRefs(playlistId)
            playlistDao.deletePlaylist(playlistId)
        }
    }

    override suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        withContext(ioDispatcher) {
            val nextPos = (playlistDao.getMaxPosition(playlistId) ?: -1) + 1
            playlistDao.insertCrossRef(
                PlaylistSongCrossRef(playlistId = playlistId, songId = songId, position = nextPos)
            )
        }
    }

    override suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        withContext(ioDispatcher) {
            playlistDao.deleteCrossRef(playlistId, songId)
        }
    }

    override suspend fun syncPlaylistsWithNextcloud(): Result<Int> = withContext(ioDispatcher) {
        val config = encryptedDataStore.serverConfigFlow.firstOrNull()
        if (config == null || config.tokenOrPassword.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Connect to Nextcloud first to sync .m3u8 playlists"))
        }

        val musicRootUrl = urlHelper.combineUrl(config.webDavBaseUrl, config.musicFolderPath)
        val playlistsFolderUrl = urlHelper.combineUrl(musicRootUrl, "Playlists")
        val mkRes = webDavClient.ensureCollection(playlistsFolderUrl, config)
        if (mkRes is Resource.Error) {
            return@withContext Result.failure(IllegalStateException(mkRes.message))
        }

        var syncedCount = 0
        val localPlaylists = playlistDao.getAllPlaylistsOnce()
        val localNamesLower = localPlaylists.associateBy { it.name.lowercase() }

        // Push local playlists as .m3u8 files
        for (pl in localPlaylists) {
            val songs = playlistDao.getSongsForPlaylistOnce(pl.id).map { it.toDomainModel() }
            val safeName = pl.name.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim().ifBlank { pl.id }
            val m3u8 = M3uPlaylistParser.serializeM3u8(pl.name, songs)
            val targetUrl = urlHelper.combineUrl(playlistsFolderUrl, "$safeName.m3u8")
            val putRes = webDavClient.uploadBytes(
                targetUrl,
                m3u8.toByteArray(Charsets.UTF_8),
                "audio/x-mpegurl",
                config
            )
            if (putRes is Resource.Success) {
                syncedCount++
            }
        }

        // Pull any remote .m3u8 / .m3u playlists not yet in local Room DB
        val listRes = webDavClient.listFolder(playlistsFolderUrl, config)
        if (listRes is Resource.Success) {
            val allSongs = songDao.getAllSongsOnce().map { it.toDomainModel() }
            val byRelPath = allSongs.associateBy { it.relativePath.trimStart('/').lowercase() }

            for (item in listRes.data) {
                if (item.isDirectory) continue
                val fileName = urlHelper.extractNameFromHref(item.href)
                if (!fileName.endsWith(".m3u8", ignoreCase = true) && !fileName.endsWith(".m3u", ignoreCase = true)) {
                    continue
                }
                val plName = fileName.substringBeforeLast('.')
                if (localNamesLower.containsKey(plName.lowercase())) continue

                val remoteUrl = urlHelper.resolveHrefUrl(config.serverUrl, item.href)
                val textRes = webDavClient.downloadText(remoteUrl, config)
                if (textRes is Resource.Success) {
                    val parsedPaths = M3uPlaylistParser.parseM3u8Paths(textRes.data)
                    val matchedSongs = parsedPaths.mapNotNull { rel ->
                        val clean = rel.trimStart('/').lowercase()
                        byRelPath[clean] ?: allSongs.firstOrNull {
                            it.relativePath.lowercase().endsWith(clean)
                        }
                    }
                    val created = createPlaylist(plName)
                    matchedSongs.forEachIndexed { idx, song ->
                        playlistDao.insertCrossRef(
                            PlaylistSongCrossRef(playlistId = created.id, songId = song.id, position = idx)
                        )
                    }
                    syncedCount++
                }
            }
        }

        Result.success(syncedCount)
    }
}
