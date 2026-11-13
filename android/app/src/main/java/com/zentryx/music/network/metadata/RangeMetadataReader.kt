package com.zentryx.music.network.metadata

import com.zentryx.music.models.AudioFormat
import com.zentryx.music.models.Resource
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.network.webdav.WebDavClient
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val durationMs: Long = 0L,
    val hasEmbeddedArt: Boolean = false,
    val rawArtworkBytes: ByteArray? = null
)

/**
 * Reads audio metadata remotely using HTTP Range byte requests without downloading
 * full audio files. Extracts ID3v2, FLAC Vorbis Comments, and M4A atoms.
 * Provides fallback parsing based on directory path patterns (Artist/Album/01 - Title.ext).
 */
@Singleton
class RangeMetadataReader @Inject constructor(
    private val webDavClient: WebDavClient
) {
    companion object {
        private const val INITIAL_HEADER_CHUNK_SIZE = 65536L // 64 KB
    }

    /**
     * Reads metadata remotely using HTTP Range byte chunks.
     * Falls back to path parsing if tags are absent.
     */
    suspend fun extractMetadata(
        fileUrl: String,
        relativePath: String,
        fileSize: Long,
        config: ServerConfig
    ): ParsedMetadata {
        val format = AudioFormat.fromExtension(fileUrl.substringAfterLast('.', ""))
        var parsed: ParsedMetadata? = null

        // Fetch the first 64KB via HTTP Range
        val headResult = webDavClient.fetchRangeBytes(
            fileUrl = fileUrl,
            startByte = 0L,
            endByte = (INITIAL_HEADER_CHUNK_SIZE - 1).coerceAtMost(fileSize - 1),
            config = config
        )

        if (headResult is Resource.Success) {
            val headBytes = headResult.data
            parsed = when (format) {
                AudioFormat.MP3 -> parseId3v2(headBytes)
                AudioFormat.FLAC -> parseFlac(headBytes)
                AudioFormat.OGG, AudioFormat.OPUS -> parseOggVorbis(headBytes)
                AudioFormat.M4A, AudioFormat.AAC -> {
                    val m4aHead = parseM4aAtoms(headBytes)
                    if (m4aHead != null && m4aHead.title != null) {
                        m4aHead
                    } else if (fileSize > INITIAL_HEADER_CHUNK_SIZE) {
                        // In M4A/MP4 files, the 'moov' metadata atom may reside at the tail of the file
                        val tailStart = (fileSize - INITIAL_HEADER_CHUNK_SIZE).coerceAtLeast(0L)
                        val tailResult = webDavClient.fetchRangeBytes(
                            fileUrl = fileUrl,
                            startByte = tailStart,
                            endByte = fileSize - 1,
                            config = config
                        )
                        if (tailResult is Resource.Success) {
                            parseM4aAtoms(tailResult.data)
                        } else null
                    } else null
                }
                else -> null
            }
        }

        // Fallback to path pattern parsing if metadata is missing or empty
        val fallback = parseFromPath(relativePath)
        return ParsedMetadata(
            title = parsed?.title?.ifBlank { null } ?: fallback.title,
            artist = parsed?.artist?.ifBlank { null } ?: fallback.artist,
            album = parsed?.album?.ifBlank { null } ?: fallback.album,
            genre = parsed?.genre ?: fallback.genre,
            trackNumber = parsed?.trackNumber ?: fallback.trackNumber,
            year = parsed?.year ?: fallback.year,
            durationMs = parsed?.durationMs ?: 0L,
            hasEmbeddedArt = parsed?.hasEmbeddedArt ?: false,
            rawArtworkBytes = parsed?.rawArtworkBytes
        )
    }

    /**
     * Parses ID3v2.3 / ID3v2.4 tags from initial header byte slice.
     */
    private fun parseId3v2(bytes: ByteArray): ParsedMetadata? {
        if (bytes.size < 10) return null
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return null
        }

        val version = bytes[3].toInt() // 3 = v2.3, 4 = v2.4
        val tagSize = decodeSynchSafeInt(bytes, 6)
        val limit = (10 + tagSize).coerceAtMost(bytes.size)
        var offset = 10

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var genre: String? = null
        var track: Int? = null
        var year: Int? = null
        var hasArt = false
        var artBytes: ByteArray? = null

        while (offset + 10 < limit) {
            val frameId = String(bytes, offset, 4, Charsets.US_ASCII)
            if (frameId.all { it == '\u0000' } || !frameId.all { it.isLetterOrDigit() }) break

            val frameSize = if (version == 4) {
                decodeSynchSafeInt(bytes, offset + 4)
            } else {
                decodeInt(bytes, offset + 4)
            }

            offset += 10
            if (frameSize <= 0 || offset + frameSize > bytes.size) break

            when (frameId) {
                "TIT2" -> title = decodeTextFrame(bytes, offset, frameSize)
                "TPE1" -> artist = decodeTextFrame(bytes, offset, frameSize)
                "TALB" -> album = decodeTextFrame(bytes, offset, frameSize)
                "TCON" -> genre = decodeTextFrame(bytes, offset, frameSize)
                "TRCK" -> {
                    val rawTrack = decodeTextFrame(bytes, offset, frameSize)
                    track = rawTrack?.substringBefore('/')?.trim()?.toIntOrNull()
                }
                "TYER", "TDRC" -> {
                    val rawYear = decodeTextFrame(bytes, offset, frameSize)
                    year = rawYear?.take(4)?.toIntOrNull()
                }
                "APIC" -> {
                    hasArt = true
                    if (frameSize < 100000) { // Limit inline art memory to 100KB
                        artBytes = extractApicData(bytes, offset, frameSize)
                    }
                }
            }
            offset += frameSize
        }

        return ParsedMetadata(title, artist, album, genre, track, year, hasEmbeddedArt = hasArt, rawArtworkBytes = artBytes)
    }

    /**
     * Parses FLAC Vorbis Comments and Picture block.
     */
    private fun parseFlac(bytes: ByteArray): ParsedMetadata? {
        if (bytes.size < 4) return null
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "fLaC") return null

        var offset = 4
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var genre: String? = null
        var track: Int? = null
        var year: Int? = null
        var hasArt = false

        while (offset + 4 < bytes.size) {
            val headerByte = bytes[offset].toInt() and 0xFF
            val isLast = (headerByte and 0x80) != 0
            val blockType = headerByte and 0x7F
            val blockSize = ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 3].toInt() and 0xFF)

            offset += 4
            if (offset + blockSize > bytes.size) break

            when (blockType) {
                4 -> { // VORBIS_COMMENT block
                    val comments = parseVorbisCommentBlock(bytes, offset, blockSize)
                    title = comments["TITLE"]
                    artist = comments["ARTIST"]
                    album = comments["ALBUM"]
                    genre = comments["GENRE"]
                    track = comments["TRACKNUMBER"]?.substringBefore('/')?.trim()?.toIntOrNull()
                    year = comments["DATE"]?.take(4)?.toIntOrNull()
                }
                6 -> { // PICTURE block
                    hasArt = true
                }
            }

            offset += blockSize
            if (isLast) break
        }

        return ParsedMetadata(title, artist, album, genre, track, year, hasEmbeddedArt = hasArt)
    }

    private fun parseOggVorbis(bytes: ByteArray): ParsedMetadata? {
        val str = String(bytes, 0, bytes.size.coerceAtMost(8192), Charsets.ISO_8859_1)
        val vorbisIndex = str.indexOf("vorbis")
        if (vorbisIndex == -1) return null

        val comments = parseVorbisStringMap(str)
        return ParsedMetadata(
            title = comments["TITLE"],
            artist = comments["ARTIST"],
            album = comments["ALBUM"],
            genre = comments["GENRE"],
            trackNumber = comments["TRACKNUMBER"]?.toIntOrNull(),
            year = comments["DATE"]?.take(4)?.toIntOrNull()
        )
    }

    private fun parseM4aAtoms(bytes: ByteArray): ParsedMetadata? {
        val str = String(bytes, 0, bytes.size, Charsets.ISO_8859_1)
        // Detect common M4A metadata atoms (©nam, ©ART, ©alb, ©gen, ©day, trkn)
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        val namIdx = str.indexOf("©nam")
        if (namIdx != -1 && namIdx + 16 < bytes.size) {
            title = extractM4aDataString(bytes, namIdx + 4)
        }
        val artIdx = str.indexOf("©ART")
        if (artIdx != -1 && artIdx + 16 < bytes.size) {
            artist = extractM4aDataString(bytes, artIdx + 4)
        }
        val albIdx = str.indexOf("©alb")
        if (albIdx != -1 && albIdx + 16 < bytes.size) {
            album = extractM4aDataString(bytes, albIdx + 4)
        }

        return if (title != null || artist != null || album != null) {
            ParsedMetadata(title, artist, album)
        } else null
    }

    private fun extractM4aDataString(bytes: ByteArray, atomDataOffset: Int): String? {
        var offset = atomDataOffset
        while (offset + 8 < bytes.size && offset < atomDataOffset + 32) {
            val name = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val size = decodeInt(bytes, offset)
            if (name == "data" && offset + 16 < bytes.size) {
                val dataLen = (size - 16).coerceAtMost(100)
                if (dataLen > 0) {
                    return String(bytes, offset + 16, dataLen, Charsets.UTF_8).trim()
                }
            }
            if (size <= 0) break
            offset += size
        }
        return null
    }

    /**
     * Fallback path pattern parser:
     * Parses Artist/Album/01 - Title.ext or Artist - Title.ext.
     */
    fun parseFromPath(relativePath: String): ParsedMetadata {
        val clean = relativePath.trim().trim('/')
        val segments = clean.split('/')
        val fileName = segments.lastOrNull()?.substringBeforeLast('.') ?: "Unknown Title"

        var trackNumber: Int? = null
        var title = fileName
        var artist = "Unknown Artist"
        var album = "Unknown Album"

        // Pattern: "01 - Title" or "01. Title"
        val trackRegex = Regex("""^(\d{1,3})\s*[-.]\s*(.+)$""")
        val trackMatch = trackRegex.find(fileName)
        if (trackMatch != null) {
            trackNumber = trackMatch.groupValues[1].toIntOrNull()
            title = trackMatch.groupValues[2].trim()
        }

        // Pattern: "Artist - Title"
        if (title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            artist = parts[0].trim()
            title = parts[1].trim()
        }

        // Infer Artist & Album from directory hierarchy: .../Artist/Album/Track.ext
        if (segments.size >= 3) {
            album = segments[segments.size - 2]
            artist = segments[segments.size - 3]
        } else if (segments.size == 2) {
            album = segments[0]
        }

        return ParsedMetadata(
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber
        )
    }

    private fun parseVorbisCommentBlock(bytes: ByteArray, offset: Int, size: Int): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val buffer = ByteBuffer.wrap(bytes, offset, size).order(ByteOrder.LITTLE_ENDIAN)
        try {
            val vendorLen = buffer.int
            if (vendorLen in 1..(size - 4)) {
                buffer.position(buffer.position() + vendorLen)
            }
            val count = buffer.int
            for (i in 0 until count.coerceAtMost(50)) {
                if (buffer.remaining() < 4) break
                val len = buffer.int
                if (len in 1..buffer.remaining()) {
                    val strBytes = ByteArray(len)
                    buffer.get(strBytes)
                    val comment = String(strBytes, Charsets.UTF_8)
                    val eq = comment.indexOf('=')
                    if (eq != -1) {
                        map[comment.substring(0, eq).uppercase()] = comment.substring(eq + 1).trim()
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore corrupted block slices
        }
        return map
    }

    private fun parseVorbisStringMap(content: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val keys = listOf("TITLE", "ARTIST", "ALBUM", "GENRE", "TRACKNUMBER", "DATE")
        for (key in keys) {
            val prefix = "$key="
            val idx = content.indexOf(prefix, ignoreCase = true)
            if (idx != -1) {
                val end = content.indexOfAny(charArrayOf('\u0000', '\n', '\r'), idx)
                val value = if (end != -1) content.substring(idx + prefix.length, end) else content.substring(idx + prefix.length)
                map[key] = value.trim()
            }
        }
        return map
    }

    private fun decodeSynchSafeInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
                ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
                ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
                (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun decodeInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun decodeTextFrame(bytes: ByteArray, offset: Int, size: Int): String? {
        if (size <= 1) return null
        val encoding = bytes[offset].toInt()
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1, 2 -> Charsets.UTF_16
            3 -> Charsets.UTF_8
            else -> Charsets.UTF_8
        }
        return String(bytes, offset + 1, size - 1, charset).trim().removeSuffix("\u0000")
    }

    private fun extractApicData(bytes: ByteArray, offset: Int, size: Int): ByteArray? {
        try {
            // Skip text encoding byte
            var cur = offset + 1
            // Skip MIME type string (null-terminated)
            while (cur < offset + size && bytes[cur] != 0.toByte()) cur++
            cur++ // skip null byte
            cur++ // skip picture type byte
            // Skip description string
            while (cur < offset + size && bytes[cur] != 0.toByte()) cur++
            cur++
            val artLen = (offset + size) - cur
            if (artLen > 0) {
                val art = ByteArray(artLen)
                System.arraycopy(bytes, cur, art, 0, artLen)
                return art
            }
        } catch (e: Exception) {
            // Safe fallback
        }
        return null
    }
}
