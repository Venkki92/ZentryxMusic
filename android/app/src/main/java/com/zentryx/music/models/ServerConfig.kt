package com.zentryx.music.models

import java.net.URLEncoder

/**
 * Encapsulates Nextcloud connection parameters.
 * Persisted securely using Android Keystore AES-GCM + DataStore.
 */
data class ServerConfig(
    val serverUrl: String,                    // e.g. "https://cloud.example.com"
    val username: String,                     // Canonical Nextcloud username / user_id
    val tokenOrPassword: String,              // App password or user password
    val sslCertificateFingerprint: String? = null, // SHA-256 hash for Trust-On-First-Use (TOFU)
    val musicFolderPath: String = DEFAULT_MUSIC_FOLDER_PATH, // Default folders for library scans
    val isLoggedIn: Boolean = false,
    val useLegacyWebDav: Boolean = false      // Fallback to /remote.php/webdav if /dav/files/<user> returns 404
) {
    companion object {
        val DEFAULT_MUSIC_FOLDERS: List<String> = listOf("WoW Radio", "Music")
        const val DEFAULT_MUSIC_FOLDER_PATH: String = "/WoW Radio, /Music"

        fun normalizeFolderPath(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw.trim()
                .replace('\\', '/')
                .replace(Regex("/+"), "/")
                .trim('/')
                .trim()
        }

        /**
         * Parses comma/semicolon/newline-separated music folders plus optional user-added folders.
         * Defaults to ["WoW Radio", "Music"] when blank. Preserves explicit "/" as "" (Nextcloud root).
         */
        fun parseFolderList(raw: String?, extraFolders: List<String> = emptyList()): List<String> {
            val out = mutableListOf<String>()
            val seen = mutableSetOf<String>()

            fun addUnique(path: String) {
                val key = path.lowercase()
                if (seen.add(key)) out.add(path)
            }

            val trimmed = raw?.trim().orEmpty()
            if (trimmed == "/") {
                addUnique("")
            } else if (trimmed.isNotEmpty()) {
                trimmed.split(Regex("[,;\\n]+")).forEach { token ->
                    val t = token.trim()
                    if (t == "/") {
                        addUnique("")
                    } else {
                        val clean = normalizeFolderPath(t)
                        if (clean.isNotEmpty()) addUnique(clean)
                    }
                }
            }

            if (out.isEmpty()) {
                DEFAULT_MUSIC_FOLDERS.forEach { addUnique(it) }
            }

            extraFolders.forEach { extra ->
                val t = extra.trim()
                if (t == "/") {
                    addUnique("")
                } else {
                    val clean = normalizeFolderPath(t)
                    if (clean.isNotEmpty()) addUnique(clean)
                }
            }

            return out
        }
    }

    private fun encodeSegment(seg: String): String =
        URLEncoder.encode(seg, "UTF-8")
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")

    /**
     * Builds the standard WebDAV endpoint for this user (`/remote.php/dav/files/<user>`),
     * or `/remote.php/webdav` when [useLegacyWebDav] is enabled.
     */
    val webDavBaseUrl: String
        get() {
            val cleanBase = serverUrl.trimEnd('/')
            return if (useLegacyWebDav) {
                "$cleanBase/remote.php/webdav"
            } else {
                "$cleanBase/remote.php/dav/files/${encodeSegment(username.trim())}"
            }
        }

    /**
     * Explicit legacy Nextcloud WebDAV root (`/remote.php/webdav`), useful as a 404 fallback
     * when email-based usernames or federated auth aliases are used.
     */
    val legacyWebDavBaseUrl: String
        get() = "${serverUrl.trimEnd('/')}/remote.php/webdav"

    /**
     * Returns all configured primary music folders (defaulting to ["WoW Radio", "Music"])
     * combined with any extra manually added folders.
     */
    fun configuredMusicFolders(extraFolders: List<String> = emptyList()): List<String> =
        parseFolderList(musicFolderPath, extraFolders)

    /**
     * Builds a properly percent-encoded WebDAV URL for a specific relative folder path.
     */
    fun buildWebDavFolderUrl(relativeFolderPath: String): String {
        val cleanRoot = normalizeFolderPath(relativeFolderPath)
        return if (cleanRoot.isEmpty()) {
            webDavBaseUrl
        } else {
            val encodedSegments = cleanRoot.split('/')
                .filter { it.isNotEmpty() }
                .joinToString("/") { encodeSegment(it) }
            if (encodedSegments.isEmpty()) webDavBaseUrl else "$webDavBaseUrl/$encodedSegments"
        }
    }

    /**
     * Builds all primary WebDAV folder URLs configured for this server.
     */
    val musicWebDavUrls: List<String>
        get() = configuredMusicFolders().map { buildWebDavFolderUrl(it) }

    /**
     * Builds the WebDAV endpoint pointing to the first configured music directory.
     */
    val musicWebDavUrl: String
        get() = buildWebDavFolderUrl(configuredMusicFolders().firstOrNull().orEmpty())
}
