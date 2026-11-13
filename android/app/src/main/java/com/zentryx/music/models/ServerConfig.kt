package com.zentryx.music.models

/**
 * Encapsulates Nextcloud connection parameters.
 * Persisted securely using Android Keystore AES-GCM + DataStore.
 */
data class ServerConfig(
    val serverUrl: String,                    // e.g. "https://cloud.example.com"
    val username: String,                     // Nextcloud username
    val tokenOrPassword: String,              // App password or user password
    val sslCertificateFingerprint: String? = null, // SHA-256 hash for Trust-On-First-Use (TOFU)
    val musicFolderPath: String = "/Music",   // Root folder path for library scans
    val isLoggedIn: Boolean = false
) {
    /**
     * Builds the standard WebDAV endpoint for this user.
     * Normalized to avoid double slashes.
     */
    val webDavBaseUrl: String
        get() {
            val cleanBase = serverUrl.trimEnd('/')
            return "$cleanBase/remote.php/dav/files/$username"
        }

    /**
     * Builds the WebDAV endpoint pointing specifically to the music directory.
     */
    val musicWebDavUrl: String
        get() {
            val cleanRoot = musicFolderPath.trim('/')
            return if (cleanRoot.isEmpty()) {
                webDavBaseUrl
            } else {
                "$webDavBaseUrl/$cleanRoot"
            }
        }
}
