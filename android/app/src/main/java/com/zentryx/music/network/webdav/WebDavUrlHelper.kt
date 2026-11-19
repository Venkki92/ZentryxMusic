package com.zentryx.music.network.webdav

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Robust URL formatting and encoding utility for Nextcloud WebDAV paths.
 * Guarantees proper percent-encoding for spaces, brackets, German umlauts (ä, ö, ü, ß),
 * and unicode characters without mangling path separators or double-encoding SabreDAV hrefs.
 * Ensures credentials are NEVER embedded in URLs.
 */
@Singleton
class WebDavUrlHelper @Inject constructor() {

    private val percentTripletRegex = Regex("%[0-9A-Fa-f]{2}")

    /**
     * Encodes a single raw path segment according to RFC 3986 (spaces as %20).
     */
    fun encodeSegment(segment: String): String {
        if (segment.isEmpty()) return ""
        return URLEncoder.encode(segment, "UTF-8")
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")
    }

    /**
     * Encodes a WebDAV path segment-by-segment preserving '/' delimiters.
     * Replaces standard '+' space encoding with '%20' for strict RFC 3986 WebDAV compliance.
     */
    fun encodePath(path: String): String {
        if (path.isEmpty()) return ""
        return path.split('/').joinToString("/") { segment -> encodeSegment(segment) }
    }

    /**
     * Decodes a percent-encoded path or filename back into clean UTF-8 string.
     * Preserves literal '+' characters in filenames (e.g. "DJ+Mix.mp3") rather than converting them to spaces.
     */
    fun decodePath(encodedPath: String): String {
        return try {
            URLDecoder.decode(encodedPath.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) {
            encodedPath
        }
    }

    /**
     * Combines a base URL with an UNENCODED relative path cleanly, normalizing slashes and encoding segments.
     */
    fun combineUrl(baseUrl: String, relativePath: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val cleanRelative = relativePath.trimStart('/')
        val encodedRelative = encodePath(cleanRelative)
        return if (encodedRelative.isEmpty()) cleanBase else "$cleanBase/$encodedRelative"
    }

    /**
     * Resolves an href returned by SabreDAV XML (<d:href>) against serverUrl WITHOUT double-encoding
     * already percent-encoded sequences (e.g., "%20" stays "%20", not "%2520").
     */
    fun resolveHrefUrl(serverUrl: String, rawHref: String): String {
        val trimmedHref = rawHref.trim()
        if (trimmedHref.startsWith("http://") || trimmedHref.startsWith("https://")) {
            return trimmedHref
        }
        val baseOrigin = extractOrigin(serverUrl)
        val normalizedPath = if (trimmedHref.startsWith('/')) trimmedHref else "/$trimmedHref"

        val hasInvalidRawChars = normalizedPath.any { ch ->
            ch == ' ' || ch.code > 127 || ch in setOf('[', ']', '#', '?')
        }
        val finalPath = if (percentTripletRegex.containsMatchIn(normalizedPath) && !hasInvalidRawChars) {
            normalizedPath
        } else {
            "/" + encodePath(decodePath(normalizedPath).trimStart('/'))
        }
        return "$baseOrigin$finalPath"
    }

    /**
     * Extracts the clean relative path beneath the user's Nextcloud WebDAV files root.
     * Supports root installs, subfolder Nextcloud installs (/nextcloud/...), and legacy /remote.php/webdav/.
     */
    fun extractRelativePath(hrefOrUrl: String, username: String): String {
        val pathOnly = try {
            if (hrefOrUrl.startsWith("http://") || hrefOrUrl.startsWith("https://")) {
                URI(hrefOrUrl).rawPath ?: hrefOrUrl
            } else {
                hrefOrUrl
            }
        } catch (_: Exception) {
            hrefOrUrl
        }

        val decoded = decodePath(pathOnly).trimStart('/')
        val davPrefix = "remote.php/dav/files/$username/"
        val davRoot = "remote.php/dav/files/$username"
        val legacyPrefix = "remote.php/webdav/"

        val davIdx = decoded.indexOf(davPrefix, ignoreCase = true)
        if (davIdx >= 0) {
            return decoded.substring(davIdx + davPrefix.length).trim('/')
        }
        if (decoded.endsWith(davRoot, ignoreCase = true)) {
            return ""
        }
        val legacyIdx = decoded.indexOf(legacyPrefix, ignoreCase = true)
        if (legacyIdx >= 0) {
            return decoded.substring(legacyIdx + legacyPrefix.length).trim('/')
        }
        if (decoded.endsWith("remote.php/webdav", ignoreCase = true)) {
            return ""
        }
        return decoded.trim('/')
    }

    /**
     * Extracts scheme + authority (e.g. "https://cloud.zentryx.bg") from a server URL.
     */
    fun extractOrigin(serverUrl: String): String {
        val normalized = normalizeServerUrl(serverUrl)
        return try {
            val uri = URI(normalized)
            val portPart = if (uri.port != -1) ":${uri.port}" else ""
            "${uri.scheme}://${uri.host}$portPart"
        } catch (_: Exception) {
            normalized.trimEnd('/')
        }
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
     * Normalizes a server URL string (removes trailing slashes, ensures https if scheme missing).
     */
    fun normalizeServerUrl(rawUrl: String): String {
        var clean = rawUrl.trim().trimEnd('/')
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean
    }
}
