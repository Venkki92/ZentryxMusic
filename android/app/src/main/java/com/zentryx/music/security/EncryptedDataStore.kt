package com.zentryx.music.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zentryx.music.models.ServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zentryx_secure_prefs")

/**
 * DataStore backed by KeystoreCryptoManager for saving encrypted Nextcloud credentials.
 */
@Singleton
class EncryptedDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: KeystoreCryptoManager
) {
    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_ENCRYPTED_TOKEN = stringPreferencesKey("encrypted_token")
        private val KEY_SSL_FINGERPRINT = stringPreferencesKey("ssl_fingerprint")
        private val KEY_MUSIC_FOLDER = stringPreferencesKey("music_folder")
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val KEY_LEGACY_WEBDAV = booleanPreferencesKey("use_legacy_webdav")
    }

    val serverConfigFlow: Flow<ServerConfig?> = context.dataStore.data.map { preferences ->
        val serverUrl = preferences[KEY_SERVER_URL] ?: return@map null
        val username = preferences[KEY_USERNAME] ?: return@map null
        val encryptedToken = preferences[KEY_ENCRYPTED_TOKEN] ?: return@map null
        val isLoggedIn = preferences[KEY_IS_LOGGED_IN] ?: false

        if (!isLoggedIn) return@map null

        val decryptedToken = try {
            cryptoManager.decrypt(encryptedToken)
        } catch (e: Exception) {
            return@map null
        }

        ServerConfig(
            serverUrl = serverUrl,
            username = username,
            tokenOrPassword = decryptedToken,
            sslCertificateFingerprint = preferences[KEY_SSL_FINGERPRINT],
            musicFolderPath = preferences[KEY_MUSIC_FOLDER] ?: "/Music",
            isLoggedIn = true,
            useLegacyWebDav = preferences[KEY_LEGACY_WEBDAV] ?: false
        )
    }

    suspend fun saveConfig(config: ServerConfig) {
        val encryptedToken = cryptoManager.encrypt(config.tokenOrPassword)
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = config.serverUrl
            preferences[KEY_USERNAME] = config.username
            preferences[KEY_ENCRYPTED_TOKEN] = encryptedToken
            if (config.sslCertificateFingerprint != null) {
                preferences[KEY_SSL_FINGERPRINT] = config.sslCertificateFingerprint
            } else {
                preferences.remove(KEY_SSL_FINGERPRINT)
            }
            preferences[KEY_MUSIC_FOLDER] = config.musicFolderPath
            preferences[KEY_IS_LOGGED_IN] = config.isLoggedIn
            preferences[KEY_LEGACY_WEBDAV] = config.useLegacyWebDav
        }
    }

    suspend fun getServerUrl(): String? = context.dataStore.data.map { it[KEY_SERVER_URL] }.firstOrNull()

    suspend fun hasCredentials(): Boolean = context.dataStore.data.map { it[KEY_IS_LOGGED_IN] ?: false }.firstOrNull() ?: false

    suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun clearAll() = clear()
}
