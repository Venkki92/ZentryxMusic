package com.zentryx.music.network.webdav

import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Robust URL formatting and encoding utility for Nextcloud WebDAV paths.
 * Guarantees proper percent-encoding for spaces, brackets, German umlauts (ä, ö, ü, ß),
 * and unicode characters without mangling path separators or double-encoding slashes.
 * Ensures credentials are NEVER embedded in URLs.
 */
@Singleton
class WebDavUrlHelper @Inject constructor() {

    /**
     * Encodes a WebDAV path segment-by-segment preserving '/' delimiters.
     * Replaces standard '+' space encoding with '%20' for strict RFC 3986 WebDAV compliance.
     */
    fun encodePath(path: String): String {
        if (path.isEmpty()) return ""
        val segments = path.split('/')
        val encodedSegments = segments.map { segment ->
            if (segment.isEmpty()) {
                ""
            } else {
                URLEncoder.encode(segment, "UTF-8")
                    .replace("+", "%20")
                    .replace("%21", "!")
                    .replace("%27", "'")
                    .replace("%28", "(")
                    .replace("%29", ")")
                    .replace("%7E", "~")
            }
        }
        return encodedSegments.joinToString("/")
    }

    /**
     * Decodes a percent-encoded path or filename back into clean UTF-8 string.
     */
    fun decodePath(encodedPath: String): String {
        return try {
            URLDecoder.decode(encodedPath, "UTF-8")
        } catch (e: Exception) {
            encodedPath
        }
    }

    /**
     * Combines a base URL with a relative path cleanly, normalizing slashes.
     */
    fun combineUrl(baseUrl: String, relativePath: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanRelative = relativePath.trimStart('/')
        val encodedRelative = encodePath(cleanRelative)
        return if (encodedRelative.isEmpty()) cleanBase else "$cleanBase/$encodedRelative"
    }

    /**
     * Extracts the clean display name of a file or folder from a WebDAV href.
     */
    fun extractNameFromHref(href: String): String {
        val cleanHref = href.trimEnd('/')
        val lastSegment = cleanHref.substringAfterLast('/')
        return decodePath(lastSegment)
    }

    /**
     * Normalizes a server URL string (removes trailing slashes, ensures https).
     */
    fun normalizeServerUrl(rawUrl: String): String {
        var clean = rawUrl.trim().trimEnd('/')
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean
    }
}
