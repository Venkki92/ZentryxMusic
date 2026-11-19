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
    val rawArtworkBytes: ByteArray? = null,
    val sampleRateHz: Int? = null,
    val bitsPerSample: Int? = null,
    val bitrateKbps: Int? = null,
    val replayGainDb: Float? = null
)

/**
 * Reads audio metadata remotely using HTTP Range byte requests without downloading
 * full audio files. Extracts ID3v2 (including TXXX ReplayGain), MPEG header bitrate/sample rate,
 * FLAC STREAMINFO (Hi-Res bit depth / sample rate / duration) & Vorbis Comments, and M4A atoms.
 */
@Singleton
class RangeMetadataReader @Inject constructor(
    private val webDavClient: WebDavClient
) {
    companion object {
        private const val INITIAL_HEADER_CHUNK_SIZE = 65536L // 64 KB
    }

    suspend fun extractMetadata(
        fileUrl: String,
        relativePath: String,
        fileSize: Long,
        config: ServerConfig
    ): ParsedMetadata {
        val format = AudioFormat.fromExtension(fileUrl.substringAfterLast('.', ""))
        var parsed: ParsedMetadata? = null

        val headResult = webDavClient.fetchRangeBytes(
            fileUrl = fileUrl,
            startByte = 0L,
            endByte = (INITIAL_HEADER_CHUNK_SIZE - 1).coerceAtMost((fileSize - 1).coerceAtLeast(0L)),
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
            rawArtworkBytes = parsed?.rawArtworkBytes,
            sampleRateHz = parsed?.sampleRateHz,
            bitsPerSample = parsed?.bitsPerSample,
            bitrateKbps = parsed?.bitrateKbps,
            replayGainDb = parsed?.replayGainDb
        )
    }

    /**
     * Parses ID3v2.3 / ID3v2.4 tags + TXXX ReplayGain + first MPEG frame header (sampleRate/bitrate).
     */
    private fun parseId3v2(bytes: ByteArray): ParsedMetadata? {
        if (bytes.size < 10) return null
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            val mpeg = parseFirstMpegFrame(bytes, 0)
            return if (mpeg != null) {
                ParsedMetadata(sampleRateHz = mpeg.first, bitrateKbps = mpeg.second)
            } else null
        }

        val version = bytes[3].toInt()
        val tagSize = decodeSynchSafeInt(bytes, 6)
        val audioFrameOffset = (10 + tagSize).coerceAtMost(bytes.size)
        val limit = audioFrameOffset
        var offset = 10

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var genre: String? = null
        var track: Int? = null
        var year: Int? = null
        var hasArt = false
        var artBytes: ByteArray? = null
        var replayGainTrackDb: Float? = null
        var replayGainAlbumDb: Float? = null

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
                "TXXX" -> {
                    val (desc, value) = decodeTxxxFrame(bytes, offset, frameSize)
                    when {
                        desc.equals("REPLAYGAIN_TRACK_GAIN", ignoreCase = true) -> {
                            replayGainTrackDb = parseReplayGainValue(value)
                        }
                        desc.equals("REPLAYGAIN_ALBUM_GAIN", ignoreCase = true) -> {
                            replayGainAlbumDb = parseReplayGainValue(value)
                        }
                    }
                }
                "APIC" -> {
                    hasArt = true
                    if (frameSize < 100000) {
                        artBytes = extractApicData(bytes, offset, frameSize)
                    }
                }
            }
            offset += frameSize
        }

        val mpeg = parseFirstMpegFrame(bytes, audioFrameOffset)
        return ParsedMetadata(
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            trackNumber = track,
            year = year,
            hasEmbeddedArt = hasArt,
            rawArtworkBytes = artBytes,
            sampleRateHz = mpeg?.first,
            bitrateKbps = mpeg?.second,
            replayGainDb = replayGainTrackDb ?: replayGainAlbumDb
        )
    }

    /**
     * Parses FLAC STREAMINFO (sampleRate, bitDepth, durationMs) + Vorbis Comments (tags + ReplayGain).
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
        var sampleRateHz: Int? = null
        var bitsPerSample: Int? = null
        var durationMs = 0L
        var replayGainDb: Float? = null

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
                0 -> { // STREAMINFO (bytes 10..17 contain SR/channels/BPS/totalSamples)
                    if (blockSize >= 18) {
                        val b10 = bytes[offset + 10].toInt() and 0xFF
                        val b11 = bytes[offset + 11].toInt() and 0xFF
                        val b12 = bytes[offset + 12].toInt() and 0xFF
                        val b13 = bytes[offset + 13].toInt() and 0xFF
                        val sr = (b10 shl 12) or (b11 shl 4) or ((b12 ushr 4) and 0x0F)
                        val bps = (((b12 and 0x01) shl 4) or ((b13 ushr 4) and 0x0F)) + 1
                        if (sr > 0) sampleRateHz = sr
                        if (bps in 8..32) bitsPerSample = bps

                        val totalSamples = (((b13.toLong() and 0x0F) shl 32) or
                            ((bytes[offset + 14].toLong() and 0xFF) shl 24) or
                            ((bytes[offset + 15].toLong() and 0xFF) shl 16) or
                            ((bytes[offset + 16].toLong() and 0xFF) shl 8) or
                            (bytes[offset + 17].toLong() and 0xFF))
                        if (sr > 0 && totalSamples > 0) {
                            durationMs = (totalSamples * 1000L) / sr
                        }
                    }
                }
                4 -> { // VORBIS_COMMENT block
                    val comments = parseVorbisCommentBlock(bytes, offset, blockSize)
                    title = comments["TITLE"]
                    artist = comments["ARTIST"]
                    album = comments["ALBUM"]
                    genre = comments["GENRE"]
                    track = comments["TRACKNUMBER"]?.substringBefore('/')?.trim()?.toIntOrNull()
                    year = comments["DATE"]?.take(4)?.toIntOrNull()
                    replayGainDb = parseReplayGainValue(comments["REPLAYGAIN_TRACK_GAIN"])
                        ?: parseReplayGainValue(comments["REPLAYGAIN_ALBUM_GAIN"])
                }
                6 -> { // PICTURE block
                    hasArt = true
                }
            }

            offset += blockSize
            if (isLast) break
        }

        return ParsedMetadata(
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            trackNumber = track,
            year = year,
            durationMs = durationMs,
            hasEmbeddedArt = hasArt,
            sampleRateHz = sampleRateHz,
            bitsPerSample = bitsPerSample,
            replayGainDb = replayGainDb
        )
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
            year = comments["DATE"]?.take(4)?.toIntOrNull(),
            replayGainDb = parseReplayGainValue(comments["REPLAYGAIN_TRACK_GAIN"])
                ?: parseReplayGainValue(comments["REPLAYGAIN_ALBUM_GAIN"])
        )
    }

    private fun parseM4aAtoms(bytes: ByteArray): ParsedMetadata? {
        val str = String(bytes, 0, bytes.size, Charsets.ISO_8859_1)
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

    fun parseFromPath(relativePath: String): ParsedMetadata {
        val clean = relativePath.trim().trim('/')
        val segments = clean.split('/')
        val fileName = segments.lastOrNull()?.substringBeforeLast('.') ?: "Unknown Title"

        var trackNumber: Int? = null
        var title = fileName
        var artist = "Unknown Artist"
        var album = "Unknown Album"

        val trackRegex = Regex("""^(\d{1,3})\s*[-.]\s*(.+)$""")
        val trackMatch = trackRegex.find(fileName)
        if (trackMatch != null) {
            trackNumber = trackMatch.groupValues[1].toIntOrNull()
            title = trackMatch.groupValues[2].trim()
        }

        if (title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            artist = parts[0].trim()
            title = parts[1].trim()
        }

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

    private fun parseFirstMpegFrame(bytes: ByteArray, startOffset: Int): Pair<Int, Int>? {
        val limit = (startOffset + 4096).coerceAtMost(bytes.size - 4)
        for (i in startOffset.coerceAtLeast(0) until limit) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val versionBits = (b1 ushr 3) and 0x03 // 3 = MPEG 1, 2 = MPEG 2, 0 = MPEG 2.5
                val layerBits = (b1 ushr 1) and 0x03   // 1 = Layer III (MP3)
                if (versionBits == 1 || layerBits != 1) continue

                val b2 = bytes[i + 2].toInt() and 0xFF
                val bitrateIdx = (b2 ushr 4) and 0x0F
                val srIdx = (b2 ushr 2) and 0x03
                if (bitrateIdx == 0 || bitrateIdx == 15 || srIdx == 3) continue

                val srTable = when (versionBits) {
                    3 -> intArrayOf(44100, 48000, 32000)
                    2 -> intArrayOf(22050, 24000, 16000)
                    else -> intArrayOf(11025, 12000, 8000)
                }
                val brTableV1L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
                val brTableV2L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
                val br = if (versionBits == 3) brTableV1L3[bitrateIdx] else brTableV2L3[bitrateIdx]
                return srTable[srIdx] to br
            }
        }
        return null
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
            for (i in 0 until count.coerceAtMost(60)) {
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
        } catch (_: Exception) {
        }
        return map
    }

    private fun parseVorbisStringMap(content: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val keys = listOf(
            "TITLE", "ARTIST", "ALBUM", "GENRE", "TRACKNUMBER", "DATE",
            "REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_ALBUM_GAIN"
        )
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

    private fun decodeTxxxFrame(bytes: ByteArray, offset: Int, size: Int): Pair<String, String> {
        if (size <= 2) return "" to ""
        val encoding = bytes[offset].toInt()
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1, 2 -> Charsets.UTF_16
            else -> Charsets.UTF_8
        }
        val raw = String(bytes, offset + 1, size - 1, charset)
        val sep = raw.indexOf('\u0000')
        return if (sep >= 0) {
            raw.substring(0, sep).trim() to raw.substring(sep + 1).replace("\u0000", "").trim()
        } else {
            "" to raw.replace("\u0000", "").trim()
        }
    }

    private fun parseReplayGainValue(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.lowercase()
            .replace("db", "")
            .replace(",", ".")
            .trim()
        val parsed = cleaned.toFloatOrNull() ?: return null
        return parsed.coerceIn(-18f, 12f)
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
            var cur = offset + 1
            while (cur < offset + size && bytes[cur] != 0.toByte()) cur++
            cur++
            cur++
            while (cur < offset + size && bytes[cur] != 0.toByte()) cur++
            cur++
            val artLen = (offset + size) - cur
            if (artLen > 0) {
                val art = ByteArray(artLen)
                System.arraycopy(bytes, cur, art, 0, artLen)
                return art
            }
        } catch (_: Exception) {
        }
        return null
    }
}
