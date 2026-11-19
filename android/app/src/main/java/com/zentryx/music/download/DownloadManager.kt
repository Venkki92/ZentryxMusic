package com.zentryx.music.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.zentryx.music.database.dao.SongDao
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.models.Song
import com.zentryx.music.network.TofuTrustManager
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.security.EncryptedDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val songId: String, val progressPercent: Int) : DownloadState()
    data class Completed(val songId: String) : DownloadState()
    data class Failed(val songId: String, val error: String) : DownloadState()
}

/**
 * Offline Pinning, Batch Album/Favorites Downloader & Storage Selector for Dead Zone Protection.
 * Supports TOFU-pinned SSL hosts, SAF custom directories, and removable SD/USB storage.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val encryptedDataStore: EncryptedDataStore,
    private val settingsPreferences: SettingsPreferences,
    private val baseOkHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _batchProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val batchProgress: StateFlow<Pair<Int, Int>?> = _batchProgress.asStateFlow()

    private fun buildClient(config: ServerConfig?): OkHttpClient {
        val builder = baseOkHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)

        val fp = config?.sslCertificateFingerprint
        if (!fp.isNullOrBlank()) {
            val trustManager = TofuTrustManager(fp)
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustManager), SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        }
        return builder.build()
    }

    private fun findRemovableVolumeDir(): File? {
        val dirs = context.getExternalFilesDirs(Environment.DIRECTORY_MUSIC)
        for (dir in dirs) {
            if (dir == null) continue
            val isRemovable = try {
                Environment.isExternalStorageRemovable(dir)
            } catch (_: Throwable) {
                false
            }
            val state = try {
                Environment.getExternalStorageState(dir)
            } catch (_: Throwable) {
                ""
            }
            if (isRemovable && state == Environment.MEDIA_MOUNTED && dir.canWrite()) {
                return dir
            }
        }
        return null
    }

    private suspend fun getTargetDirectory(): File {
        val location = settingsPreferences.storageLocationFlow.firstOrNull() ?: "INTERNAL"
        val internalFallback = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File(context.filesDir, "Music")

        val baseDir = when (location) {
            "SDCARD" -> {
                val mountedRemovable = findRemovableVolumeDir()
                val legacySd = File("/storage/sdcard1/ZentryxMusic")
                when {
                    mountedRemovable != null -> mountedRemovable
                    (legacySd.exists() || legacySd.mkdirs()) && legacySd.canWrite() -> legacySd
                    else -> internalFallback
                }
            }
            "USB" -> {
                val legacyUsb = File("/storage/usbotg/ZentryxMusic")
                val usbOtgAlt = File("/mnt/usb_storage/ZentryxMusic")
                val mountedRemovable = findRemovableVolumeDir()
                when {
                    (legacyUsb.exists() || legacyUsb.mkdirs()) && legacyUsb.canWrite() -> legacyUsb
                    (usbOtgAlt.exists() || usbOtgAlt.mkdirs()) && usbOtgAlt.canWrite() -> usbOtgAlt
                    mountedRemovable != null -> mountedRemovable
                    else -> internalFallback
                }
            }
            else -> internalFallback
        }

        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    fun getActiveStorageSummary(
        location: String,
        customLabel: String? = null,
        customUri: String? = null
    ): String {
        return when (location) {
            "CUSTOM" -> {
                if (!customUri.isNullOrBlank()) {
                    val tree = try {
                        DocumentFile.fromTreeUri(context, Uri.parse(customUri))
                    } catch (_: Throwable) {
                        null
                    }
                    if (tree != null && tree.canWrite()) {
                        "Custom Folder: ${customLabel ?: tree.name ?: "Selected"}"
                    } else {
                        "Custom Folder (${customLabel ?: "Selected"}) unavailable — falls back to eMMC"
                    }
                } else {
                    "No custom folder picked — using eMMC Flash"
                }
            }
            "SDCARD" -> {
                val mounted = findRemovableVolumeDir()
                val legacySd = File("/storage/sdcard1")
                when {
                    mounted != null -> "Mounted: ${mounted.path}"
                    legacySd.exists() && legacySd.canWrite() -> "Mounted: /storage/sdcard1"
                    else -> "MicroSD not detected — falls back to eMMC"
                }
            }
            "USB" -> {
                val usb = File("/storage/usbotg")
                val alt = File("/mnt/usb_storage")
                when {
                    usb.exists() && usb.canWrite() -> "Mounted: /storage/usbotg"
                    alt.exists() && alt.canWrite() -> "Mounted: /mnt/usb_storage"
                    else -> "USB OTG not detected — falls back to eMMC"
                }
            }
            else -> "Internal eMMC Flash (${context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.path ?: context.filesDir.path})"
        }
    }

    fun downloadSong(song: Song) {
        scope.launch {
            downloadSongSuspend(song)
        }
    }

    fun downloadBatch(songs: List<Song>) {
        val pending = songs.filter { !it.isDownloaded && it.sourceType == "WEBDAV" }
        if (pending.isEmpty()) return
        scope.launch {
            _batchProgress.value = 0 to pending.size
            pending.forEachIndexed { idx, song ->
                downloadSongSuspend(song)
                _batchProgress.value = (idx + 1) to pending.size
            }
            _batchProgress.value = null
        }
    }

    private suspend fun downloadSongSuspend(song: Song) {
        if (song.sourceType == "LOCAL") return
        try {
            _downloadState.value = DownloadState.Downloading(song.id, 0)
            val location = settingsPreferences.storageLocationFlow.firstOrNull() ?: "INTERNAL"
            val customUriStr = settingsPreferences.customDownloadUriFlow.firstOrNull()
            val safTree = if (location == "CUSTOM" && !customUriStr.isNullOrBlank()) {
                try {
                    DocumentFile.fromTreeUri(context, Uri.parse(customUriStr))?.takeIf { it.canWrite() }
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }

            val safeFilename = "${song.id}_${song.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.audio"

            val config = encryptedDataStore.serverConfigFlow.firstOrNull()
            val client = buildClient(config)
            val requestBuilder = Request.Builder()
                .url(song.fileUrl)
                .header("User-Agent", "ZentryxMusic/1.0 (AutoChips AC8259P Headunit)")

            if (config != null) {
                val basicAuth = Credentials.basic(config.username, config.tokenOrPassword)
                requestBuilder.header("Authorization", basicAuth)
            }

            var savedPathOrUri: String? = null

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    _downloadState.value = DownloadState.Failed(song.id, "HTTP error: ${response.code}")
                    return
                }

                val body = response.body ?: throw IllegalStateException("Empty body")
                val totalBytes = body.contentLength()
                var bytesCopied = 0L

                if (safTree != null) {
                    safTree.findFile(safeFilename)?.delete()
                    val docFile = safTree.createFile(song.mimeType.ifBlank { "audio/*" }, safeFilename)
                        ?: throw IllegalStateException("Cannot create file in selected folder")
                    val outStream = context.contentResolver.openOutputStream(docFile.uri)
                        ?: throw IllegalStateException("Cannot open output stream for folder")

                    body.byteStream().use { input ->
                        outStream.use { output ->
                            val buffer = ByteArray(8192)
                            var bytes = input.read(buffer)
                            while (bytes >= 0) {
                                output.write(buffer, 0, bytes)
                                bytesCopied += bytes
                                if (totalBytes > 0) {
                                    val percent = ((bytesCopied * 100) / totalBytes).toInt().coerceIn(0, 100)
                                    _downloadState.value = DownloadState.Downloading(song.id, percent)
                                }
                                bytes = input.read(buffer)
                            }
                        }
                    }
                    savedPathOrUri = docFile.uri.toString()
                } else {
                    val targetDir = getTargetDirectory()
                    val destinationFile = File(targetDir, safeFilename)
                    body.byteStream().use { input ->
                        FileOutputStream(destinationFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytes = input.read(buffer)
                            while (bytes >= 0) {
                                output.write(buffer, 0, bytes)
                                bytesCopied += bytes
                                if (totalBytes > 0) {
                                    val percent = ((bytesCopied * 100) / totalBytes).toInt().coerceIn(0, 100)
                                    _downloadState.value = DownloadState.Downloading(song.id, percent)
                                }
                                bytes = input.read(buffer)
                            }
                        }
                    }
                    savedPathOrUri = destinationFile.absolutePath
                }
            }

            val finalPath = savedPathOrUri ?: throw IllegalStateException("No download output")
            songDao.updateDownloadStatus(song.id, true, finalPath)
            _downloadState.value = DownloadState.Completed(song.id)
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Failed(song.id, e.message ?: "Download failed")
        }
    }

    fun removeDownload(song: Song) {
        if (song.sourceType == "LOCAL") return
        scope.launch {
            val path = song.localFilePath
            if (!path.isNullOrBlank()) {
                if (path.startsWith("content://")) {
                    try {
                        DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete()
                    } catch (_: Throwable) {}
                } else {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
            songDao.updateDownloadStatus(song.id, false, null)
        }
    }
}
