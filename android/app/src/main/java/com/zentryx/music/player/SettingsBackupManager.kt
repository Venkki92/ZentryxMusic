package com.zentryx.music.player

import android.content.Context
import android.net.Uri
import com.zentryx.music.di.IoDispatcher
import com.zentryx.music.models.Resource
import com.zentryx.music.network.webdav.WebDavClient
import com.zentryx.music.network.webdav.WebDavUrlHelper
import com.zentryx.music.security.EncryptedDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports & imports complete Zentryx DSP curves, custom presets, SWC mappings,
 * and cockpit ergonomics to/from local JSON files (SAF) or Nextcloud WebDAV (`/ZentryxBackup/zentryx-tune.json`).
 */
@Singleton
class SettingsBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPreferences: SettingsPreferences,
    private val encryptedDataStore: EncryptedDataStore,
    private val webDavClient: WebDavClient,
    private val urlHelper: WebDavUrlHelper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val BACKUP_FOLDER = "ZentryxBackup"
        private const val BACKUP_FILENAME = "zentryx-tune.json"
    }

    suspend fun exportToUri(targetUri: Uri): Result<String> = withContext(ioDispatcher) {
        try {
            val config = encryptedDataStore.serverConfigFlow.firstOrNull()
            val json = settingsPreferences.exportBackupJson(config?.serverUrl, config?.musicFolderPath)
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@withContext Result.failure(IllegalStateException("Cannot write selected file"))
            Result.success("Exported tune backup successfully")
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun importFromUri(sourceUri: Uri): Result<String> = withContext(ioDispatcher) {
        try {
            val raw = context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext Result.failure(IllegalStateException("Cannot open selected file"))
            if (settingsPreferences.restoreBackupJson(raw)) {
                Result.success("DSP curves & settings restored from JSON")
            } else {
                Result.failure(IllegalArgumentException("Invalid Zentryx backup JSON file"))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun pushBackupToNextcloud(): Result<String> = withContext(ioDispatcher) {
        val config = encryptedDataStore.serverConfigFlow.firstOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Connect to Nextcloud first"))
        val json = settingsPreferences.exportBackupJson(config.serverUrl, config.musicFolderPath)
        val folderUrl = urlHelper.combineUrl(config.webDavBaseUrl, BACKUP_FOLDER)
        val mkRes = webDavClient.ensureCollection(folderUrl, config)
        if (mkRes is Resource.Error) {
            return@withContext Result.failure(IllegalStateException(mkRes.message))
        }
        val fileUrl = urlHelper.combineUrl(folderUrl, BACKUP_FILENAME)
        when (val putRes = webDavClient.uploadBytes(fileUrl, json.toByteArray(Charsets.UTF_8), "application/json", config)) {
            is Resource.Success -> Result.success("Backed up to Nextcloud /$BACKUP_FOLDER/$BACKUP_FILENAME")
            is Resource.Error -> Result.failure(IllegalStateException(putRes.message))
            else -> Result.failure(IllegalStateException("Unexpected backup error"))
        }
    }

    suspend fun pullBackupFromNextcloud(): Result<String> = withContext(ioDispatcher) {
        val config = encryptedDataStore.serverConfigFlow.firstOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Connect to Nextcloud first"))
        val fileUrl = urlHelper.combineUrl(config.webDavBaseUrl, "$BACKUP_FOLDER/$BACKUP_FILENAME")
        when (val res = webDavClient.downloadText(fileUrl, config)) {
            is Resource.Success -> {
                if (settingsPreferences.restoreBackupJson(res.data)) {
                    Result.success("Restored tune from Nextcloud /$BACKUP_FOLDER/$BACKUP_FILENAME")
                } else {
                    Result.failure(IllegalArgumentException("Corrupt cloud backup payload"))
                }
            }
            is Resource.Error -> Result.failure(IllegalStateException(res.message))
            else -> Result.failure(IllegalStateException("Unexpected restore error"))
        }
    }
}
