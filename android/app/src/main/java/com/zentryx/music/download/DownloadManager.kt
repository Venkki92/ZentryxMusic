package com.zentryx.music.download

import android.content.Context
import android.os.Environment
import com.zentryx.music.database.dao.SongDao
import com.zentryx.music.database.entities.toDomainModel
import com.zentryx.music.models.Song
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.security.EncryptedDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val songId: String, val progressPercent: Int) : DownloadState()
    data class Completed(val songId: String) : DownloadState()
    data class Failed(val songId: String, val error: String) : DownloadState()
}

/**
 * Feature 3: Offline Pinning & Storage Selector for Dead Zone Protection.
 * Downloads full lossless/lossy tracks from Nextcloud to headunit eMMC flash,
 * MicroSD card (/storage/sdcard1), or external USB drive.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val encryptedDataStore: EncryptedDataStore,
    private val settingsPreferences: SettingsPreferences,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    val downloadedSongsFlow: Flow<List<Song>> = songDao.getDownloadedSongs().map { list ->
        list.map { it.toDomainModel() }
    }

    private suspend fun getTargetDirectory(): File {
        val location = settingsPreferences.storageLocationFlow.firstOrNull() ?: "INTERNAL"
        val baseDir = when (location) {
            "SDCARD" -> {
                val sdCard = File("/storage/sdcard1/ZentryxMusic")
                if (sdCard.exists() || sdCard.mkdirs()) sdCard else context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            }
            "USB" -> {
                val usb = File("/storage/usbotg/ZentryxMusic")
                if (usb.exists() || usb.mkdirs()) usb else context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            }
            else -> context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        } ?: File(context.filesDir, "Music")

        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    fun downloadSong(song: Song) {
        scope.launch {
            try {
                _downloadState.value = DownloadState.Downloading(song.id, 0)
                val targetDir = getTargetDirectory()
                val safeFilename = "${song.id}_${song.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.audio"
                val destinationFile = File(targetDir, safeFilename)

                val config = encryptedDataStore.serverConfigFlow.firstOrNull()
                val requestBuilder = Request.Builder().url(song.fileUrl)
                if (config != null) {
                    val basicAuth = Credentials.basic(config.username, config.tokenOrPassword)
                    requestBuilder.header("Authorization", basicAuth)
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    _downloadState.value = DownloadState.Failed(song.id, "HTTP error: ${response.code}")
                    return@launch
                }

                val body = response.body ?: throw IllegalStateException("Empty body")
                val totalBytes = body.contentLength()
                var bytesCopied = 0L

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            if (totalBytes > 0) {
                                val percent = ((bytesCopied * 100) / totalBytes).toInt()
                                _downloadState.value = DownloadState.Downloading(song.id, percent)
                            }
                            bytes = input.read(buffer)
                        }
                    }
                }

                songDao.updateDownloadStatus(song.id, true, destinationFile.absolutePath)
                _downloadState.value = DownloadState.Completed(song.id)
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Failed(song.id, e.message ?: "Download failed")
            }
        }
    }

    fun removeDownload(song: Song) {
        scope.launch {
            if (song.localFilePath != null) {
                val file = File(song.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
            }
            songDao.updateDownloadStatus(song.id, false, null)
        }
    }
}
