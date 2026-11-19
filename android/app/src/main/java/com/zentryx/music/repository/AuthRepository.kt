package com.zentryx.music.repository

import com.zentryx.music.models.Resource
import com.zentryx.music.models.ServerConfig
import kotlinx.coroutines.flow.Flow

/**
 * Authentication and connection repository.
 * Manages Keystore-backed credentials and Nextcloud Login Flow v2.
 */
interface AuthRepository {
    /**
     * Flow emitting the currently active server configuration.
     */
    val serverConfig: Flow<ServerConfig?>

    /**
     * Tests WebDAV connectivity against the specified server parameters.
     * Differentiates bad URL, no network, SSL error, wrong credentials, and WebDAV unreachable.
     */
    suspend fun testConnection(config: ServerConfig): Resource<Unit>

    /**
     * Resolves canonical Nextcloud user ID (via OCS when an email/alias is entered),
     * probes WebDAV (with /remote.php/webdav fallback on 404), and returns a verified ServerConfig.
     */
    suspend fun resolveAndTestConnection(config: ServerConfig): Resource<ServerConfig>

    /**
     * Saves validated server credentials into Android Keystore-backed AES-GCM DataStore.
     */
    suspend fun saveCredentials(config: ServerConfig)

    /**
     * Initiates Nextcloud Login Flow v2.
     * Supports pinned TOFU SSL fingerprints for self-signed instances.
     */
    suspend fun startLoginFlowV2(
        serverUrl: String,
        pinnedFingerprint: String? = null
    ): Resource<LoginFlowSession>

    /**
     * Polls the Nextcloud Login Flow v2 endpoint until the user authorizes the app in browser.
     */
    suspend fun pollLoginFlowV2(
        token: String,
        endpoint: String,
        pinnedFingerprint: String? = null
    ): Resource<ServerConfig>

    /**
     * Logs out the user and clears stored credentials.
     */
    suspend fun logout()
}

/**
 * Session data for Nextcloud Login Flow v2.
 */
data class LoginFlowSession(
    val loginUrl: String,
    val token: String,
    val pollEndpoint: String
)
