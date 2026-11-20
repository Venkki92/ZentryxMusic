package com.zentryx.music.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.zentryx.music.database.dao.SongDao
import com.zentryx.music.models.AudioFormat
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.models.Song
import com.zentryx.music.network.TofuTrustManager
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.security.EncryptedDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val songId: String, val progressPercent: Int) : DownloadState()
    data class Completed(val songId: String) : DownloadState()
    data class Failed(val songId: String, val error: String) : DownloadState()
}

data class DownloadFeedbackEvent(
    val id: Long,
    val songId: String?,
    val message: String,
    val isError: Boolean = false,
    val progressPercent: Int? = null
)

data class DownloadStatusUpdate(
    val songId: String,
    val isDownloaded: Boolean,
    val localFilePath: String?
)

/**
 * Offline Pinning, Batch Album/Favorites Downloader & Storage Selector for Dead Zone Protection.
 * Supports TOFU-pinned SSL hosts, SAF custom directories, removable SD/USB storage,
 * proper audio extensions (.mp3/.flac/.m4a/.ogg/.opus/.wav), and live UI progress feedback.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val encryptedDataStore: EncryptedDataStore,
    private val settingsPreferences: SettingsPreferences,
    private val baseOkHttpClient: OkHttpClient
) {
    companion object {
        private val KNOWN_EXTENSIONS_TO_MIME: Map<String, String> = mapOf(
            "mp3" to "audio/mpeg",
            "flac" to "audio/flac",
            "m4a" to "audio/mp4",
            "mp4" to "audio/mp4",
            "aac" to "audio/aac",
            "ogg" to "audio/ogg",
            "oga" to "audio/ogg",
            "opus" to "audio/opus",
            "wav" to "audio/wav",
            "wma" to "audio/x-ms-wma"
        )

        /**
         * Derives the real lowercase audio extension (without dot) and concrete MIME type
         * from the song's relative path, URL, format enum, or mimeType. Never emits `.audio` or `.song`.
         */
        fun resolveAudioExtensionAndMime(song: Song): Pair<String, String> {
            val candidates = listOf(song.relativePath, song.fileUrl)
            for (raw in candidates) {
                if (raw.isBlank()) continue
                val clean = raw.substringBefore('?').substringBefore('#')
                val dot = clean.lastIndexOf('.')
                val slash = clean.lastIndexOf('/')
                if (dot > slash && dot in 0 until clean.lastIndex) {
                    val ext = clean.substring(dot + 1).lowercase(Locale.US)
                    val mime = KNOWN_EXTENSIONS_TO_MIME[ext]
                    if (mime != null) return ext to mime
                }
            }

            val fromFormat = when (song.format) {
                AudioFormat.MP3 -> "mp3" to "audio/mpeg"
                AudioFormat.FLAC -> "flac" to "audio/flac"
                AudioFormat.M4A -> "m4a" to "audio/mp4"
                AudioFormat.AAC -> "aac" to "audio/aac"
                AudioFormat.OGG -> "ogg" to "audio/ogg"
                AudioFormat.OPUS -> "opus" to "audio/opus"
                AudioFormat.WAV -> "wav" to "audio/wav"
            }
            val lowerMime = song.mimeType.lowercase(Locale.US).trim()
            return when {
                lowerMime.contains("flac") -> "flac" to "audio/flac"
                lowerMime.contains("mp4") || lowerMime.contains("m4a") -> "m4a" to "audio/mp4"
                lowerMime.contains("aac") -> "aac" to "audio/aac"
                lowerMime.contains("opus") -> "opus" to "audio/opus"
                lowerMime.contains("ogg") -> "ogg" to "audio/ogg"
                lowerMime.contains("wav") -> "wav" to "audio/wav"
                lowerMime.contains("wma") -> "wma" to "audio/x-ms-wma"
                else -> fromFormat
            }
        }

        /**
         * Builds FAT32/exFAT-safe filenames:
         * - first: full filename with real extension (e.g. `ab12cd34_Midnight_City.mp3`)
         * - second: base name without extension (for SAF DocumentFile.createFile so Android doesn't double-append)
         */
        fun buildDownloadFilenames(song: Song): Pair<String, String> {
            val (ext, _) = resolveAudioExtensionAndMime(song)
            val idPrefix = song.id.replace(Regex("[^a-zA-Z0-9_-]"), "").take(16).ifBlank { "track" }
            val cleanTitle = song.title
                .replace(Regex("[^a-zA-Z0-9 _-]"), "_")
                .replace(Regex("\\s+"), "_")
                .trim('_')
                .take(48)
                .ifBlank { "track" }
            val baseName = "${idPrefix}_${cleanTitle}"
            return "$baseName.$ext" to baseName
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val feedbackCounter = AtomicLong(1L)

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _activeDownloads = MutableStateFlow<Map<String, Int>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, Int>> = _activeDownloads.asStateFlow()

    private val _downloadFeedback = MutableStateFlow<DownloadFeedbackEvent?>(null)
    val downloadFeedback: StateFlow<DownloadFeedbackEvent?> = _downloadFeedback.asStateFlow()

    private val _batchProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val batchProgress: StateFlow<Pair<Int, Int>?> = _batchProgress.asStateFlow()

    private val _downloadStatusUpdates = MutableSharedFlow<DownloadStatusUpdate>(extraBufferCapacity = 32)
    val downloadStatusUpdates: SharedFlow<DownloadStatusUpdate> = _downloadStatusUpdates.asSharedFlow()

    private fun emitFeedback(
        songId: String?,
        message: String,
        isError: Boolean = false,
        progressPercent: Int? = null
    ) {
        _downloadFeedback.value = DownloadFeedbackEvent(
            id = feedbackCounter.incrementAndGet(),
            songId = songId,
            message = message,
            isError = isError,
            progressPercent = progressPercent
        )
    }

    fun clearFeedback() {
        _downloadFeedback.value = null
    }

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

    private fun isDirectoryWritableSafe(dir: File?): Boolean {
        if (dir == null) return false
        return runCatching {
            val ready = dir.exists() || dir.mkdirs()
            ready && dir.isDirectory && dir.canWrite()
        }.getOrDefault(false)
    }

    private fun findRemovableVolumeDir(): File? {
        return runCatching {
            val dirs = context.getExternalFilesDirs(Environment.DIRECTORY_MUSIC) ?: return@runCatching null
            for (dir in dirs) {
                if (dir == null) continue
                val isRemovable = runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(false)
                val state = runCatching { Environment.getExternalStorageState(dir) }.getOrDefault("")
                if (isRemovable && state == Environment.MEDIA_MOUNTED && isDirectoryWritableSafe(dir)) {
                    return@runCatching dir
                }
            }
            null
        }.getOrNull()
    }

    private fun findWritableUsbRootDir(): File? {
        return runCatching {
            val staticCandidates = listOf(
                File("/storage/usbotg"),
                File("/mnt/usb_storage"),
                File("/mnt/usb"),
                File("/mnt/udisk"),
                File("/storage/udisk")
            )
            for (root in staticCandidates) {
                if (runCatching { root.exists() && root.isDirectory && root.canWrite() }.getOrDefault(false)) {
                    return@runCatching root
                }
            }
            // Scan /storage/<UUID> removable mount points commonly created on Android headunits
            val storageRoot = File("/storage")
            val children = runCatching { storageRoot.listFiles() }.getOrNull().orEmpty()
            for (child in children) {
                val name = child.name
                if (name.equals("emulated", true) || name.equals("self", true) || name.equals("sdcard0", true)) {
                    continue
                }
                if (runCatching { child.exists() && child.isDirectory && child.canWrite() }.getOrDefault(false)) {
                    return@runCatching child
                }
            }
            null
        }.getOrNull()
    }

    private fun getInternalFallbackDir(): File {
        val candidate = runCatching { context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) }.getOrNull()
        val fallback = candidate ?: File(context.filesDir, "Music")
        runCatching { if (!fallback.exists()) fallback.mkdirs() }
        return fallback
    }

    private suspend fun getTargetDirectoryWithLabel(): Pair<File, String> {
        val location = settingsPreferences.storageLocationFlow.firstOrNull() ?: "INTERNAL"
        val internalFallback = getInternalFallbackDir()

        val (baseDir, label) = when (location) {
            "SDCARD" -> {
                val mountedRemovable = findRemovableVolumeDir()
                val legacySd = File("/storage/sdcard1/ZentryxMusic")
                when {
                    mountedRemovable != null -> mountedRemovable to "MicroSD"
                    isDirectoryWritableSafe(legacySd) -> legacySd to "MicroSD"
                    else -> internalFallback to "eMMC Flash"
                }
            }
            "USB" -> {
                val usbRoot = findWritableUsbRootDir()
                val usbSub = usbRoot?.let { File(it, "ZentryxMusic") }
                val mountedRemovable = findRemovableVolumeDir()
                when {
                    usbSub != null && isDirectoryWritableSafe(usbSub) -> usbSub to "USB OTG"
                    mountedRemovable != null -> mountedRemovable to "USB / Removable"
                    else -> internalFallback to "eMMC Flash"
                }
            }
            else -> internalFallback to "eMMC Flash"
        }

        runCatching { if (!baseDir.exists()) baseDir.mkdirs() }
        return baseDir to label
    }

    fun getActiveStorageSummary(
        location: String,
        customLabel: String? = null,
        customUri: String? = null
    ): String {
        return runCatching {
            when (location) {
                "CUSTOM" -> {
                    if (!customUri.isNullOrBlank()) {
                        val tree = runCatching {
                            DocumentFile.fromTreeUri(context, Uri.parse(customUri))
                        }.getOrNull()
                        val writable = runCatching { tree != null && tree.canWrite() }.getOrDefault(false)
                        if (writable) {
                            "Custom Folder: ${customLabel ?: runCatching { tree?.name }.getOrNull() ?: "Selected"}"
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
                    val legacyWritable = runCatching { legacySd.exists() && legacySd.canWrite() }.getOrDefault(false)
                    when {
                        mounted != null -> "Mounted MicroSD: ${mounted.path}"
                        legacyWritable -> "Mounted: /storage/sdcard1"
                        else -> "MicroSD not detected — falls back to eMMC Flash"
                    }
                }
                "USB" -> {
                    val usbRoot = findWritableUsbRootDir()
                    val mounted = findRemovableVolumeDir()
                    when {
                        usbRoot != null -> "Mounted USB: ${usbRoot.path}"
                        mounted != null -> "Mounted Removable: ${mounted.path}"
                        else -> "USB OTG not writable/detected — falls back to eMMC Flash"
                    }
                }
                else -> {
                    val path = runCatching {
                        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.path
                    }.getOrNull() ?: context.filesDir.path
                    "Internal eMMC Flash ($path)"
                }
            }
        }.getOrElse {
            "Internal eMMC Flash (Safe Fallback)"
        }
    }

    fun downloadSong(song: Song) {
        if (song.sourceType == "LOCAL") {
            emitFeedback(song.id, "\"${song.title}\" is already a local USB/device file")
            return
        }
        if (_activeDownloads.value.containsKey(song.id)) {
            emitFeedback(song.id, "Already downloading \"${song.title}\"…", progressPercent = _activeDownloads.value[song.id])
            return
        }
        scope.launch {
            downloadSongSuspend(song)
        }
    }

    fun downloadBatch(songs: List<Song>) {
        val pending = songs.filter { !it.isDownloaded && it.sourceType == "WEBDAV" }
        if (pending.isEmpty()) {
            emitFeedback(null, "All selected tracks are already saved offline")
            return
        }
        scope.launch {
            _batchProgress.value = 0 to pending.size
            emitFeedback(null, "Downloading ${pending.size} track(s) for offline playback…", progressPercent = 0)
            var succeeded = 0
            pending.forEachIndexed { idx, song ->
                if (downloadSongSuspend(song, suppressCompletionToast = true)) {
                    succeeded++
                }
                _batchProgress.value = (idx + 1) to pending.size
            }
            _batchProgress.value = null
            emitFeedback(
                null,
                "Saved $succeeded / ${pending.size} tracks for offline playback",
                isError = succeeded == 0
            )
        }
    }

    private suspend fun downloadSongSuspend(song: Song, suppressCompletionToast: Boolean = false): Boolean {
        if (song.sourceType == "LOCAL") return false
        _activeDownloads.update { it + (song.id to 0) }
        _downloadState.value = DownloadState.Downloading(song.id, 0)
        if (!suppressCompletionToast) {
            emitFeedback(song.id, "Downloading \"${song.title}\"… 0%", progressPercent = 0)
        }
        try {
            val location = settingsPreferences.storageLocationFlow.firstOrNull() ?: "INTERNAL"
            val customUriStr = settingsPreferences.customDownloadUriFlow.firstOrNull()
            val safTree = if (location == "CUSTOM" && !customUriStr.isNullOrBlank()) {
                runCatching {
                    DocumentFile.fromTreeUri(context, Uri.parse(customUriStr))?.takeIf { it.canWrite() }
                }.getOrNull()
            } else {
                null
            }

            val (_, resolvedMime) = resolveAudioExtensionAndMime(song)
            val (fullFilename, baseNameWithoutExt) = buildDownloadFilenames(song)

            val config = encryptedDataStore.serverConfigFlow.firstOrNull()
            val client = buildClient(config)
            val requestBuilder = Request.Builder()
                .url(song.fileUrl)
                .header("User-Agent", "ZentryxMusic/1.0 (AutoChips AC8259P Headunit)")

            if (config != null && config.username.isNotBlank()) {
                val basicAuth = Credentials.basic(config.username, config.tokenOrPassword)
                requestBuilder.header("Authorization", basicAuth)
            }

            var savedPathOrUri: String? = null
            var storageTargetLabel = "eMMC Flash"
            var lastReportedPct = -1

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = "HTTP ${response.code} while downloading \"${song.title}\""
                    _downloadState.value = DownloadState.Failed(song.id, err)
                    emitFeedback(song.id, err, isError = true)
                    return false
                }

                val body = response.body ?: throw IllegalStateException("Empty server response")
                val totalBytes = body.contentLength()
                var bytesCopied = 0L

                val updateProgress: (Long) -> Unit = { copied ->
                    if (totalBytes > 0) {
                        val percent = ((copied * 100) / totalBytes).toInt().coerceIn(0, 100)
                        if (percent != lastReportedPct && (percent == 100 || percent - lastReportedPct >= 4)) {
                            lastReportedPct = percent
                            _activeDownloads.update { it + (song.id to percent) }
                            _downloadState.value = DownloadState.Downloading(song.id, percent)
                            if (!suppressCompletionToast) {
                                emitFeedback(song.id, "Downloading \"${song.title}\"… $percent%", progressPercent = percent)
                            }
                        }
                    }
                }

                if (safTree != null) {
                    storageTargetLabel = settingsPreferences.customDownloadLabelFlow.firstOrNull()
                        ?: runCatching { safTree.name }.getOrNull()
                        ?: "Custom Folder"
                    runCatching { safTree.findFile(fullFilename)?.delete() }
                    runCatching { safTree.findFile(baseNameWithoutExt)?.delete() }
                    // Pass baseNameWithoutExt + concrete audio MIME so Android SAF appends the proper audio extension once
                    val docFile = safTree.createFile(resolvedMime, baseNameWithoutExt)
                        ?: throw IllegalStateException("Cannot create audio file in selected folder")
                    val outStream = context.contentResolver.openOutputStream(docFile.uri)
                        ?: throw IllegalStateException("Cannot open output stream for selected folder")

                    body.byteStream().use { input ->
                        outStream.use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var bytes = input.read(buffer)
                            while (bytes >= 0) {
                                output.write(buffer, 0, bytes)
                                bytesCopied += bytes
                                updateProgress(bytesCopied)
                                bytes = input.read(buffer)
                            }
                            output.flush()
                        }
                    }
                    savedPathOrUri = docFile.uri.toString()
                } else {
                    val (targetDir, targetLabel) = getTargetDirectoryWithLabel()
                    storageTargetLabel = targetLabel
                    val destinationFile = File(targetDir, fullFilename)
                    val tempFile = File(targetDir, "$fullFilename.tmp")
                    runCatching { if (tempFile.exists()) tempFile.delete() }

                    try {
                        body.byteStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buffer = ByteArray(16 * 1024)
                                var bytes = input.read(buffer)
                                while (bytes >= 0) {
                                    output.write(buffer, 0, bytes)
                                    bytesCopied += bytes
                                    updateProgress(bytesCopied)
                                    bytes = input.read(buffer)
                                }
                                output.flush()
                            }
                        }
                        if (destinationFile.exists()) {
                            destinationFile.delete()
                        }
                        if (!tempFile.renameTo(destinationFile)) {
                            tempFile.copyTo(destinationFile, overwrite = true)
                            tempFile.delete()
                        }
                        savedPathOrUri = destinationFile.absolutePath
                    } catch (e: Throwable) {
                        runCatching { tempFile.delete() }
                        throw e
                    }
                }
            }

            val finalPath = savedPathOrUri ?: throw IllegalStateException("No download output written")
            songDao.updateDownloadStatus(song.id, true, finalPath)
            _downloadStatusUpdates.tryEmit(DownloadStatusUpdate(song.id, true, finalPath))
            _downloadState.value = DownloadState.Completed(song.id)
            if (!suppressCompletionToast) {
                emitFeedback(song.id, "Saved offline ($storageTargetLabel): ${song.title}")
            }
            return true
        } catch (e: Exception) {
            val msg = "Download failed (${song.title}): ${e.localizedMessage ?: "I/O error"}"
            _downloadState.value = DownloadState.Failed(song.id, msg)
            emitFeedback(song.id, msg, isError = true)
            return false
        } finally {
            _activeDownloads.update { it - song.id }
        }
    }

    fun removeDownload(song: Song) {
        if (song.sourceType == "LOCAL") return
        scope.launch {
            val path = song.localFilePath
            if (!path.isNullOrBlank()) {
                if (path.startsWith("content://")) {
                    runCatching {
                        DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete()
                    }
                } else {
                    runCatching {
                        val file = File(path)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                }
            }
            songDao.updateDownloadStatus(song.id, false, null)
            _downloadStatusUpdates.tryEmit(DownloadStatusUpdate(song.id, false, null))
            emitFeedback(song.id, "Removed offline copy: ${song.title}")
        }
    }
}
