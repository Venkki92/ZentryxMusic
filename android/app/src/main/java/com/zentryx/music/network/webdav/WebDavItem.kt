package com.zentryx.music.network.webdav

/**
 * Lightweight representation of a file or directory item returned by Nextcloud WebDAV.
 * Parsed efficiently with XmlPullParser with zero DOM allocation overhead.
 */
data class WebDavItem(
    val href: String,                  // Relative or absolute URL path returned by server
    val name: String,                  // Display file or folder name
    val isDirectory: Boolean,          // True if item contains <d:collection/>
    val contentLength: Long = 0L,      // File size in bytes (<d:getcontentlength>)
    val contentType: String? = null,   // MIME type (<d:getcontenttype>)
    val eTag: String? = null,          // ETag for incremental sync invalidation (<d:getetag>)
    val lastModified: Long = 0L,       // Modification timestamp in ms (<d:getlastmodified>)
    val permissions: String? = null    // Nextcloud/ownCloud <oc:permissions> flags (e.g. RGDNVWCK)
) {
    /**
     * File extension in lowercase without leading dot (e.g. "flac", "mp3", "m4a").
     */
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
}
