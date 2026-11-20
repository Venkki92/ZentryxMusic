package com.zentryx.music.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.zentryx.music.models.AudioFormat
import com.zentryx.music.models.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parsed envelope metadata extracted from a `.song` file or IndexedDB-cached offline blob.
 *
 * Supported `.song` payload formats:
 * 1. Binary `ZSONG\u0001` container: 6-byte magic + 4-byte big-endian UTF-8 JSON header length + JSON + raw audio bytes.
 * 2. JSON-encoded IndexedDB `OfflineTrackData` envelope: `{ "id", "mimeType", "blobBase64" | "audioBase64" | "dataUrl", "track": { ... } }`.
 * 3. Legacy raw audio bytes saved directly with a `.song` or `.audio` extension (sniffed via audio magic bytes).
 */
data class ParsedSongContainer(
    val formatKind: SongContainerKind,
    val detectedMimeType: String?,
    val payloadByteOffset: Long = 0L,
    val decodedBlobBytes: ByteArray? = null,
    val embeddedTrackId: String? = null,
    val embeddedTitle: String? = null,
    val embeddedArtist: String? = null,
    val embeddedAlbum: String? = null
)

enum class SongContainerKind {
    RAW_AUDIO,
    ZSONG_BINARY_CONTAINER,
    INDEXED_DB_JSON_BLOB
}

/**
 * Converts Zentryx [Song] records, legacy/offline `.song` files, and IndexedDB-cached audio blobs
 * (`idb://offline_tracks/<id>`, `zentryx-idb://...`, `data:audio/...;base64,...`) into valid
 * Media3 [MediaItem] instances with concrete MIME types and rich cockpit metadata.
 */
@OptIn(UnstableApi::class)
@Singleton
class SongMediaItemConverter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SCHEME_IDB = "idb"
        const val SCHEME_ZENTRYX_IDB = "zentryx-idb"
        const val SCHEME_DATA = "data"
        const val SCHEME_BLOB = "blob"

        const val EXTRA_IS_SONG_CONTAINER = "zentryx.extra.is_song_container"
        const val EXTRA_ORIGINAL_SOURCE_URI = "zentryx.extra.original_source_uri"
        const val EXTRA_RESOLVED_MIME_TYPE = "zentryx.extra.resolved_mime_type"
        const val EXTRA_IDB_TRACK_ID = "zentryx.extra.idb_track_id"

        val ZSONG_MAGIC_BYTES: ByteArray = byteArrayOf(
            'Z'.code.toByte(),
            'S'.code.toByte(),
            'O'.code.toByte(),
            'N'.code.toByte(),
            'G'.code.toByte(),
            0x01
        )

        private const val HEADER_SNIFF_BYTES = 128

        private val EXT_TO_MIME: Map<String, String> = mapOf(
            "mp3" to MimeTypes.AUDIO_MPEG,
            "flac" to MimeTypes.AUDIO_FLAC,
            "m4a" to MimeTypes.AUDIO_MP4,
            "mp4" to MimeTypes.AUDIO_MP4,
            "aac" to MimeTypes.AUDIO_AAC,
            "ogg" to MimeTypes.AUDIO_OGG,
            "oga" to MimeTypes.AUDIO_OGG,
            "opus" to MimeTypes.AUDIO_OPUS,
            "wav" to MimeTypes.AUDIO_WAV,
            "webm" to MimeTypes.AUDIO_WEBM,
            "wma" to "audio/x-ms-wma"
        )

        fun isSongFilePath(pathOrUri: String?): Boolean {
            if (pathOrUri.isNullOrBlank()) return false
            val clean = pathOrUri.substringBefore('?').substringBefore('#').lowercase(Locale.US)
            return clean.endsWith(".song") || clean.endsWith(".audio")
        }

        fun isIndexedDbOrBlobUriString(uriString: String?): Boolean {
            if (uriString.isNullOrBlank()) return false
            val lower = uriString.trim().lowercase(Locale.US)
            return lower.startsWith("$SCHEME_IDB://") ||
                lower.startsWith("$SCHEME_ZENTRYX_IDB://") ||
                lower.startsWith("$SCHEME_DATA:") ||
                lower.startsWith("$SCHEME_BLOB:")
        }

        fun isOfflineBlobOrSongPath(pathOrUri: String?): Boolean {
            return isSongFilePath(pathOrUri) || isIndexedDbOrBlobUriString(pathOrUri)
        }

        /**
         * Extracts the track ID from an `idb://offline_tracks/<trackId>` or `zentryx-idb://...` URI string.
         */
        fun parseIndexedDbTrackId(uriString: String?): String? {
            if (uriString.isNullOrBlank()) return null
            val trimmed = uriString.trim()
            val afterScheme = when {
                trimmed.startsWith("$SCHEME_IDB://", ignoreCase = true) -> trimmed.substring("$SCHEME_IDB://".length)
                trimmed.startsWith("$SCHEME_ZENTRYX_IDB://", ignoreCase = true) -> trimmed.substring("$SCHEME_ZENTRYX_IDB://".length)
                else -> return null
            }
            val withoutQuery = afterScheme.substringBefore('?').substringBefore('#').trim('/')
            if (withoutQuery.isEmpty()) return null
            val segments = withoutQuery.split('/').filter { it.isNotBlank() }
            return when {
                segments.isEmpty() -> null
                segments.size == 1 -> segments[0]
                segments[0].equals("offline_tracks", ignoreCase = true) -> segments.drop(1).joinToString("/")
                else -> segments.last()
            }
        }

        /**
         * Builds the canonical IndexedDB offline blob URI for a track ID.
         */
        fun buildIndexedDbBlobUriString(trackId: String): String {
            return "$SCHEME_IDB://offline_tracks/${trackId.trimStart('/')}"
        }

        /**
         * Sniffs the true audio container MIME type directly from the leading bytes of an audio stream or blob.
         * Essential for `.song` files and raw IndexedDB blobs where filename extensions are missing or non-standard.
         */
        fun sniffAudioMimeType(bytes: ByteArray, length: Int = bytes.size): String? {
            val len = minOf(length, bytes.size)
            if (len < 4) return null

            // fLaC
            if (bytes[0] == 0x66.toByte() && bytes[1] == 0x4C.toByte() &&
                bytes[2] == 0x61.toByte() && bytes[3] == 0x43.toByte()
            ) {
                return MimeTypes.AUDIO_FLAC
            }

            // ID3 tag header (MP3)
            if (bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) {
                return MimeTypes.AUDIO_MPEG
            }

            // OggS container (distinguish Opus vs Vorbis)
            if (bytes[0] == 0x4F.toByte() && bytes[1] == 0x67.toByte() &&
                bytes[2] == 0x67.toByte() && bytes[3] == 0x53.toByte()
            ) {
                val ascii = String(bytes, 0, minOf(len, 64), Charsets.ISO_8859_1)
                return if (ascii.contains("OpusHead")) MimeTypes.AUDIO_OPUS else MimeTypes.AUDIO_OGG
            }

            // RIFF....WAVE
            if (len >= 12 &&
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() && bytes[9] == 0x41.toByte() &&
                bytes[10] == 0x56.toByte() && bytes[11] == 0x45.toByte()
            ) {
                return MimeTypes.AUDIO_WAV
            }

            // ISO BMFF / MP4 / M4A (`....ftyp`)
            if (len >= 8 &&
                bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
                bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()
            ) {
                return MimeTypes.AUDIO_MP4
            }

            // Matroska / WebM EBML header (`1A 45 DF A3`)
            if (bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
                bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
            ) {
                return MimeTypes.AUDIO_WEBM
            }

            // Frame sync: distinguish ADTS AAC (layer == 00) vs raw MPEG-1/2 Layer III MP3 (layer != 00)
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val layer = (b1 ushr 1) and 0x03
                return if (layer == 0 && (b1 and 0xF6) == 0xF0) {
                    MimeTypes.AUDIO_AAC
                } else if (layer != 0) {
                    MimeTypes.AUDIO_MPEG
                } else {
                    null
                }
            }

            return null
        }

        /**
         * Inspects a byte array or file prefix representing a `.song` file or IndexedDB offline blob,
         * detecting whether it is:
         * - a binary `ZSONG\u0001` container with metadata header + payload offset,
         * - a JSON-encoded IndexedDB `OfflineTrackData` blob export, or
         * - raw audio bytes saved under `.song`.
         */
        fun parseSongBytes(rawBytes: ByteArray): ParsedSongContainer {
            if (rawBytes.size >= ZSONG_MAGIC_BYTES.size + 4 && hasZsongMagic(rawBytes)) {
                val metaLen = ((rawBytes[6].toInt() and 0xFF) shl 24) or
                    ((rawBytes[7].toInt() and 0xFF) shl 16) or
                    ((rawBytes[8].toInt() and 0xFF) shl 8) or
                    (rawBytes[9].toInt() and 0xFF)
                val payloadOffset = 10 + metaLen
                if (metaLen >= 0 && payloadOffset <= rawBytes.size) {
                    val metaJson = String(rawBytes, 10, metaLen, Charsets.UTF_8)
                    val declaredMime = normalizeConcreteMimeType(extractJsonStringField(metaJson, "mimeType"))
                    val payloadHeader = if (payloadOffset < rawBytes.size) {
                        rawBytes.copyOfRange(payloadOffset, minOf(rawBytes.size, payloadOffset + HEADER_SNIFF_BYTES))
                    } else {
                        ByteArray(0)
                    }
                    val sniffedMime = sniffAudioMimeType(payloadHeader)
                    return ParsedSongContainer(
                        formatKind = SongContainerKind.ZSONG_BINARY_CONTAINER,
                        detectedMimeType = sniffedMime ?: declaredMime,
                        payloadByteOffset = payloadOffset.toLong(),
                        embeddedTrackId = extractJsonStringField(metaJson, "id"),
                        embeddedTitle = extractJsonStringField(metaJson, "title"),
                        embeddedArtist = extractJsonStringField(metaJson, "artist"),
                        embeddedAlbum = extractJsonStringField(metaJson, "album")
                    )
                }
            }

            // Detect JSON-encoded IndexedDB OfflineTrackData envelope (`{ ... }`)
            val firstNonWs = rawBytes.firstOrNull { b ->
                val c = b.toInt().toChar()
                c != ' ' && c != '\n' && c != '\r' && c != '\t'
            }
            if (firstNonWs == '{'.code.toByte()) {
                val jsonText = runCatching { String(rawBytes, Charsets.UTF_8) }.getOrNull()
                if (jsonText != null) {
                    val parsedJson = parseIndexedDbJsonEnvelope(jsonText)
                    if (parsedJson != null) {
                        return parsedJson
                    }
                }
            }

            // Legacy raw audio file stored with `.song` or `.audio` extension
            return ParsedSongContainer(
                formatKind = SongContainerKind.RAW_AUDIO,
                detectedMimeType = sniffAudioMimeType(rawBytes),
                payloadByteOffset = 0L
            )
        }

        /**
         * Inspects a `.song` file on disk without loading large raw FLAC/MP3 payloads fully into memory.
         */
        fun inspectSongFile(file: File): ParsedSongContainer {
            if (!file.exists() || !file.isFile || file.length() <= 0L) {
                return ParsedSongContainer(SongContainerKind.RAW_AUDIO, null, 0L)
            }
            val prefix = ByteArray(minOf(file.length(), 4096L).toInt())
            val bytesRead = runCatching {
                FileInputStream(file).use { it.read(prefix) }
            }.getOrDefault(0)
            if (bytesRead <= 0) {
                return ParsedSongContainer(SongContainerKind.RAW_AUDIO, null, 0L)
            }

            // Binary ZSONG container: read just the metadata JSON + first 128 bytes of payload
            if (bytesRead >= 10 && hasZsongMagic(prefix)) {
                val metaLen = ((prefix[6].toInt() and 0xFF) shl 24) or
                    ((prefix[7].toInt() and 0xFF) shl 16) or
                    ((prefix[8].toInt() and 0xFF) shl 8) or
                    (prefix[9].toInt() and 0xFF)
                val headerEnd = 10L + metaLen
                if (metaLen in 0..1_048_576 && headerEnd <= file.length()) {
                    val headerAndSniff = ByteArray(minOf(file.length(), headerEnd + HEADER_SNIFF_BYTES).toInt())
                    FileInputStream(file).use { input ->
                        var offset = 0
                        while (offset < headerAndSniff.size) {
                            val n = input.read(headerAndSniff, offset, headerAndSniff.size - offset)
                            if (n <= 0) break
                            offset += n
                        }
                    }
                    return parseSongBytes(headerAndSniff)
                }
            }

            // JSON-encoded IndexedDB blob export (bounded to 64 MB safety guard)
            val firstNonWs = prefix.take(bytesRead).firstOrNull { b ->
                val c = b.toInt().toChar()
                c != ' ' && c != '\n' && c != '\r' && c != '\t'
            }
            if (firstNonWs == '{'.code.toByte() && file.length() <= 64L * 1024L * 1024L) {
                val fullBytes = runCatching { file.readBytes() }.getOrNull()
                if (fullBytes != null) {
                    val parsed = parseSongBytes(fullBytes)
                    if (parsed.formatKind == SongContainerKind.INDEXED_DB_JSON_BLOB) {
                        return parsed
                    }
                }
            }

            // Raw audio file saved with `.song` extension
            return ParsedSongContainer(
                formatKind = SongContainerKind.RAW_AUDIO,
                detectedMimeType = sniffAudioMimeType(prefix, bytesRead),
                payloadByteOffset = 0L
            )
        }

        /**
         * Decodes a `data:audio/...;base64,<payload>` URI into `(mimeType, rawAudioBytes)`.
         */
        fun decodeDataUri(dataUri: String): Pair<String?, ByteArray>? {
            if (!dataUri.startsWith("data:", ignoreCase = true)) return null
            val commaIdx = dataUri.indexOf(',')
            if (commaIdx <= 5) return null
            val header = dataUri.substring(5, commaIdx)
            val isBase64 = header.contains(";base64", ignoreCase = true)
            if (!isBase64) return null
            val declaredMime = normalizeConcreteMimeType(header.substringBefore(';').trim())
            val b64Payload = dataUri.substring(commaIdx + 1).replace(Regex("\\s+"), "")
            val decoded = runCatching { Base64.getDecoder().decode(b64Payload) }.getOrNull() ?: return null
            val sniffed = sniffAudioMimeType(decoded)
            return (sniffed ?: declaredMime) to decoded
        }

        /**
         * Resolves the most reliable concrete audio MIME type for Media3 ExoPlayer,
         * prioritizing sniffed container bytes over metadata hints and rejecting generic wildcard MIME types.
         */
        fun resolveCanonicalMimeType(
            song: Song,
            sniffedMimeType: String? = null
        ): String? {
            normalizeConcreteMimeType(sniffedMimeType)?.let { return it }

            // Check real filename extensions on relativePath and remote fileUrl (skip `.song` / `.audio`)
            for (candidate in listOf(song.relativePath, song.fileUrl)) {
                if (candidate.isBlank()) continue
                val clean = candidate.substringBefore('?').substringBefore('#')
                val dot = clean.lastIndexOf('.')
                val slash = clean.lastIndexOf('/')
                if (dot > slash && dot in 0 until clean.lastIndex) {
                    val ext = clean.substring(dot + 1).lowercase(Locale.US)
                    EXT_TO_MIME[ext]?.let { return it }
                }
            }

            // Concrete song.mimeType (ignore wildcard audio/* or application/octet-stream)
            normalizeConcreteMimeType(song.mimeType)?.let { return it }

            // Fallback to typed AudioFormat enum
            return when (song.format) {
                AudioFormat.MP3 -> MimeTypes.AUDIO_MPEG
                AudioFormat.FLAC -> MimeTypes.AUDIO_FLAC
                AudioFormat.AAC -> MimeTypes.AUDIO_AAC
                AudioFormat.M4A -> MimeTypes.AUDIO_MP4
                AudioFormat.OGG -> MimeTypes.AUDIO_OGG
                AudioFormat.OPUS -> MimeTypes.AUDIO_OPUS
                AudioFormat.WAV -> MimeTypes.AUDIO_WAV
            }
        }

        fun normalizeConcreteMimeType(rawMime: String?): String? {
            if (rawMime.isNullOrBlank()) return null
            val clean = rawMime.substringBefore(';').trim().lowercase(Locale.US)
            if (clean.isEmpty() || clean == "audio/*" || clean == "*/*" || clean == "application/octet-stream") {
                return null
            }
            return when (clean) {
                "audio/mp3" -> MimeTypes.AUDIO_MPEG
                "audio/x-flac" -> MimeTypes.AUDIO_FLAC
                "audio/x-m4a", "audio/m4a" -> MimeTypes.AUDIO_MP4
                "audio/x-wav" -> MimeTypes.AUDIO_WAV
                else -> if (clean.startsWith("audio/") || clean == "application/ogg") clean else null
            }
        }

        private fun hasZsongMagic(bytes: ByteArray): Boolean {
            if (bytes.size < ZSONG_MAGIC_BYTES.size) return false
            for (i in ZSONG_MAGIC_BYTES.indices) {
                if (bytes[i] != ZSONG_MAGIC_BYTES[i]) return false
            }
            return true
        }

        /**
         * Pure-Kotlin parser for JSON-serialized IndexedDB `OfflineTrackData` blobs:
         * `{ "id": "...", "mimeType": "audio/mpeg", "blobBase64": "...", "dataUrl": "data:audio/...;base64,...", "track": { "title": ..., "artist": ..., "album": ... } }`
         */
        private fun parseIndexedDbJsonEnvelope(json: String): ParsedSongContainer? {
            val dataUrl = extractJsonStringField(json, "dataUrl")
            val rawBase64 = extractJsonStringField(json, "blobBase64")
                ?: extractJsonStringField(json, "audioBase64")
                ?: extractJsonStringField(json, "blob")

            val decodedPair: Pair<String?, ByteArray> = when {
                !dataUrl.isNullOrBlank() -> decodeDataUri(dataUrl) ?: return null
                !rawBase64.isNullOrBlank() -> {
                    if (rawBase64.startsWith("data:", ignoreCase = true)) {
                        decodeDataUri(rawBase64) ?: return null
                    } else {
                        val cleaned = rawBase64.replace(Regex("\\s+"), "")
                        val bytes = runCatching { Base64.getDecoder().decode(cleaned) }.getOrNull() ?: return null
                        sniffAudioMimeType(bytes) to bytes
                    }
                }
                else -> return null
            }

            val declaredMime = normalizeConcreteMimeType(extractJsonStringField(json, "mimeType"))
            return ParsedSongContainer(
                formatKind = SongContainerKind.INDEXED_DB_JSON_BLOB,
                detectedMimeType = decodedPair.first ?: declaredMime,
                payloadByteOffset = 0L,
                decodedBlobBytes = decodedPair.second,
                embeddedTrackId = extractJsonStringField(json, "id"),
                embeddedTitle = extractJsonStringField(json, "title"),
                embeddedArtist = extractJsonStringField(json, "artist"),
                embeddedAlbum = extractJsonStringField(json, "album")
            )
        }

        private fun extractJsonStringField(json: String, fieldName: String): String? {
            val pattern = Regex("\"${Regex.escape(fieldName)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            val match = pattern.find(json) ?: return null
            val raw = match.groupValues[1]
            return raw
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        }
    }

    /**
     * Converts a domain [Song] into a valid Media3 [MediaItem], automatically resolving:
     * - Local `.song` / `.audio` containers (sniffing real codec headers to avoid ExoPlayer extractor failures),
     * - IndexedDB cached blob URIs (`idb://offline_tracks/<id>`, `zentryx-idb://...`, `data:audio/...`),
     * - Standard local `file://` and SAF `content://` downloads,
     * - Remote Nextcloud WebDAV HTTP/HTTPS streams.
     */
    fun toMediaItem(song: Song, isLocalAvailable: Boolean): MediaItem {
        val localPath = song.localFilePath?.takeIf { isLocalAvailable && it.isNotBlank() }
        val isSongContainer = localPath != null && isOfflineBlobOrSongPath(localPath)

        var sniffedMime: String? = null
        var embeddedTitle: String? = null
        var embeddedArtist: String? = null
        var embeddedAlbum: String? = null

        if (localPath != null && isSongFilePath(localPath)) {
            if (localPath.startsWith("content://", ignoreCase = true)) {
                sniffedMime = sniffContentUriMime(Uri.parse(localPath))
            } else {
                val file = File(localPath.removePrefix("file://"))
                val parsed = inspectSongFile(file)
                sniffedMime = parsed.detectedMimeType
                embeddedTitle = parsed.embeddedTitle
                embeddedArtist = parsed.embeddedArtist
                embeddedAlbum = parsed.embeddedAlbum
            }
        } else if (localPath != null && localPath.startsWith("data:", ignoreCase = true)) {
            sniffedMime = decodeDataUri(localPath)?.first
        }

        val playbackUri: Uri = when {
            localPath == null -> Uri.parse(song.fileUrl)
            localPath.startsWith("content://", ignoreCase = true) ||
                localPath.startsWith("$SCHEME_IDB://", ignoreCase = true) ||
                localPath.startsWith("$SCHEME_ZENTRYX_IDB://", ignoreCase = true) ||
                localPath.startsWith("$SCHEME_DATA:", ignoreCase = true) ||
                localPath.startsWith("file://", ignoreCase = true) -> Uri.parse(localPath)
            else -> Uri.fromFile(File(localPath))
        }

        val resolvedMime = resolveCanonicalMimeType(song, sniffedMime)

        val extras = Bundle().apply {
            putBoolean(EXTRA_IS_SONG_CONTAINER, isSongContainer)
            putString(EXTRA_ORIGINAL_SOURCE_URI, playbackUri.toString())
            if (resolvedMime != null) {
                putString(EXTRA_RESOLVED_MIME_TYPE, resolvedMime)
            }
            parseIndexedDbTrackId(localPath)?.let { putString(EXTRA_IDB_TRACK_ID, it) }
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(embeddedTitle?.takeIf { it.isNotBlank() } ?: song.title)
            .setArtist(embeddedArtist?.takeIf { it.isNotBlank() } ?: song.artist)
            .setAlbumTitle(embeddedAlbum?.takeIf { it.isNotBlank() } ?: song.album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)

        if (!song.artworkPath.isNullOrBlank()) {
            metadataBuilder.setArtworkUri(Uri.parse(song.artworkPath))
        }

        val itemBuilder = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(playbackUri)
            .setCustomCacheKey(song.id)
            .setMediaMetadata(metadataBuilder.build())

        if (resolvedMime != null) {
            itemBuilder.setMimeType(resolvedMime)
        }

        return itemBuilder.build()
    }

    /**
     * Directly converts a standalone `.song` file on disk into a valid Media3 [MediaItem].
     */
    fun fromSongFile(file: File, fallbackTitle: String? = null): MediaItem {
        val parsed = inspectSongFile(file)
        val mime = parsed.detectedMimeType ?: MimeTypes.AUDIO_MPEG
        val title = parsed.embeddedTitle?.takeIf { it.isNotBlank() }
            ?: fallbackTitle?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension
        val mediaId = parsed.embeddedTrackId?.takeIf { it.isNotBlank() } ?: file.absolutePath
        val uri = Uri.fromFile(file)

        val extras = Bundle().apply {
            putBoolean(EXTRA_IS_SONG_CONTAINER, true)
            putString(EXTRA_ORIGINAL_SOURCE_URI, uri.toString())
            putString(EXTRA_RESOLVED_MIME_TYPE, mime)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(parsed.embeddedArtist ?: "Offline Library")
            .setAlbumTitle(parsed.embeddedAlbum ?: "Offline Downloads")
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMimeType(mime)
            .setCustomCacheKey(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Converts an IndexedDB offline cached track entry (`idb://offline_tracks/<trackId>`) into a Media3 [MediaItem].
     */
    fun fromIndexedDbBlob(
        trackId: String,
        title: String,
        artist: String = "Unknown Artist",
        album: String = "Offline Cache",
        mimeType: String? = null,
        blobUri: Uri = Uri.parse(buildIndexedDbBlobUriString(trackId))
    ): MediaItem {
        val resolvedMime = normalizeConcreteMimeType(mimeType)
        val extras = Bundle().apply {
            putBoolean(EXTRA_IS_SONG_CONTAINER, true)
            putString(EXTRA_ORIGINAL_SOURCE_URI, blobUri.toString())
            putString(EXTRA_IDB_TRACK_ID, trackId)
            if (resolvedMime != null) {
                putString(EXTRA_RESOLVED_MIME_TYPE, resolvedMime)
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()

        val builder = MediaItem.Builder()
            .setMediaId(trackId)
            .setUri(blobUri)
            .setCustomCacheKey(trackId)
            .setMediaMetadata(metadata)

        if (resolvedMime != null) {
            builder.setMimeType(resolvedMime)
        }
        return builder.build()
    }

    private fun sniffContentUriMime(contentUri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(contentUri)?.use { stream ->
                val buf = ByteArray(HEADER_SNIFF_BYTES)
                val read = stream.read(buf)
                if (read > 0) sniffAudioMimeType(buf, read) else null
            }
        }.getOrNull()
    }
}
