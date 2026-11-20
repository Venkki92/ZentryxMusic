package com.zentryx.music.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.zentryx.music.download.DownloadManager
import com.zentryx.music.models.AudioFormat
import com.zentryx.music.models.Song
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.Base64

@OptIn(UnstableApi::class)
class DownloadAndOfflineRoutingTest {

    private fun sampleSong(
        id: String = "song-01",
        title: String = "Night Drive (Remix)",
        relativePath: String = "Music/Electronic/Night Drive.flac",
        fileUrl: String = "https://cloud.zentryx.bg/remote.php/dav/files/user/Music/Night%20Drive.flac",
        mimeType: String = "audio/flac"
    ): Song = Song(
        id = id,
        title = title,
        artist = "Zentryx Artist",
        album = "Autobahn Sessions",
        durationMs = 215_000L,
        fileUrl = fileUrl,
        relativePath = relativePath,
        fileSize = 24_000_000L,
        mimeType = mimeType
    )

    private fun fakeFlacPayload(extraBytes: ByteArray = "FLAC_AUDIO_FRAMES_1234567890".toByteArray()): ByteArray {
        val header = byteArrayOf(0x66, 0x4C, 0x61, 0x43, 0x00, 0x00, 0x00, 0x22)
        return header + extraBytes
    }

    private fun fakeMp3Id3Payload(extraBytes: ByteArray = "MP3_ID3_AUDIO_PAYLOAD_XYZ".toByteArray()): ByteArray {
        val header = byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x00, 0x00, 0x00)
        return header + extraBytes
    }

    private fun buildZsongContainer(metadataJson: String, audioPayload: ByteArray): ByteArray {
        val metaBytes = metadataJson.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        out.write(SongMediaItemConverter.ZSONG_MAGIC_BYTES)
        val lenBuf = ByteBuffer.allocate(4).putInt(metaBytes.size).array()
        out.write(lenBuf)
        out.write(metaBytes)
        out.write(audioPayload)
        return out.toByteArray()
    }

    private fun readAllFromDataSource(ds: OfflineBlobDataSource): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(16)
        while (true) {
            val n = ds.read(chunk, 0, chunk.size)
            if (n == C.RESULT_END_OF_INPUT || n < 0) break
            out.write(chunk, 0, n)
        }
        return out.toByteArray()
    }

    @Test
    fun `resolveAudioExtensionAndMime preserves real extensions and never emits song or audio`() {
        val cases = listOf(
            sampleSong(relativePath = "a/track.mp3", fileUrl = "https://ex/track.mp3", mimeType = "audio/*") to ("mp3" to "audio/mpeg"),
            sampleSong(relativePath = "a/track.flac", fileUrl = "https://ex/track.flac", mimeType = "audio/*") to ("flac" to "audio/flac"),
            sampleSong(relativePath = "a/track.m4a", fileUrl = "https://ex/track.m4a", mimeType = "") to ("m4a" to "audio/mp4"),
            sampleSong(relativePath = "a/track.opus", fileUrl = "https://ex/track.opus", mimeType = "") to ("opus" to "audio/opus"),
            sampleSong(relativePath = "a/track.ogg", fileUrl = "https://ex/track.ogg", mimeType = "") to ("ogg" to "audio/ogg"),
            sampleSong(relativePath = "a/track.wav", fileUrl = "https://ex/track.wav", mimeType = "") to ("wav" to "audio/wav"),
            sampleSong(relativePath = "a/no_ext", fileUrl = "https://ex/no_ext", mimeType = "audio/*") to ("mp3" to "audio/mpeg")
        )

        for ((song, expected) in cases) {
            val actual = DownloadManager.resolveAudioExtensionAndMime(song)
            assertEquals("Mismatch for ${song.relativePath}", expected, actual)
            assertFalse("Extension must never be .song", actual.first == "song")
            assertFalse("Extension must never be .audio", actual.first == "audio")
            assertFalse("MIME must be concrete, not wildcard", actual.second == "audio/*")
        }
    }

    @Test
    fun `buildDownloadFilenames produces clean FAT32-safe name with real extension and extensionless SAF base`() {
        val song = sampleSong(
            id = "abc/123:def",
            title = "Autobahn: Midnight / Run?*",
            relativePath = "Synth/Autobahn.flac"
        )
        val (fullName, safBaseName) = DownloadManager.buildDownloadFilenames(song)
        assertTrue("Full filename should end with .flac (was $fullName)", fullName.endsWith(".flac"))
        assertFalse("SAF base name must omit extension to prevent double-extension (was $safBaseName)", safBaseName.endsWith(".flac"))
        assertFalse("Full filename must not contain .song or .audio", fullName.endsWith(".song") || fullName.endsWith(".audio"))
        assertFalse("Full filename must not contain invalid FAT32 characters", Regex("[/:*?\"<>|]").containsMatchIn(fullName))
    }

    @Test
    fun `isRemoteHttpScheme and isOfflineBlobOrSongPath route URIs to the right DataSource`() {
        assertTrue(CacheDataSourceFactory.isRemoteHttpScheme("http"))
        assertTrue(CacheDataSourceFactory.isRemoteHttpScheme("https"))
        assertTrue(CacheDataSourceFactory.isRemoteHttpScheme("HTTPS"))
        assertFalse(CacheDataSourceFactory.isRemoteHttpScheme("file"))
        assertFalse(CacheDataSourceFactory.isRemoteHttpScheme("content"))
        assertFalse(CacheDataSourceFactory.isRemoteHttpScheme("idb"))
        assertFalse(CacheDataSourceFactory.isRemoteHttpScheme(null))

        assertTrue(CacheDataSourceFactory.isOfflineBlobOrSongPath("file:///storage/usb/track.song"))
        assertTrue(CacheDataSourceFactory.isOfflineBlobOrSongPath("content://provider/doc/legacy.song"))
        assertTrue(CacheDataSourceFactory.isOfflineBlobOrSongPath("idb://offline_tracks/track-42"))
        assertTrue(CacheDataSourceFactory.isOfflineBlobOrSongPath("zentryx-idb://offline_tracks/track-42"))
        assertTrue(CacheDataSourceFactory.isOfflineBlobOrSongPath("data:audio/mpeg;base64,SUQz"))
        assertFalse(CacheDataSourceFactory.isOfflineBlobOrSongPath("https://cloud.zentryx.bg/Music/song.flac"))
        assertFalse(CacheDataSourceFactory.isOfflineBlobOrSongPath("file:///storage/usb/track.mp3"))
    }

    @Test
    fun `SongMediaItemConverter sniffs real container MIME types from audio magic bytes`() {
        assertEquals(MimeTypes.AUDIO_FLAC, SongMediaItemConverter.sniffAudioMimeType(fakeFlacPayload()))
        assertEquals(MimeTypes.AUDIO_MPEG, SongMediaItemConverter.sniffAudioMimeType(fakeMp3Id3Payload()))
        assertEquals(MimeTypes.AUDIO_MPEG, SongMediaItemConverter.sniffAudioMimeType(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)))
        assertEquals(MimeTypes.AUDIO_AAC, SongMediaItemConverter.sniffAudioMimeType(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte())))
        assertEquals(MimeTypes.AUDIO_OGG, SongMediaItemConverter.sniffAudioMimeType("OggS\u0000\u0002vorbis".toByteArray(Charsets.ISO_8859_1)))
        assertEquals(MimeTypes.AUDIO_OPUS, SongMediaItemConverter.sniffAudioMimeType("OggS\u0000\u0002....OpusHead".toByteArray(Charsets.ISO_8859_1)))
        assertEquals(MimeTypes.AUDIO_WAV, SongMediaItemConverter.sniffAudioMimeType("RIFF1234WAVEfmt ".toByteArray(Charsets.ISO_8859_1)))
        assertEquals(MimeTypes.AUDIO_MP4, SongMediaItemConverter.sniffAudioMimeType(byteArrayOf(0x00, 0x00, 0x00, 0x20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())))
    }

    @Test
    fun `SongMediaItemConverter parses raw, ZSONG binary, and IndexedDB JSON song containers`() {
        val flacPayload = fakeFlacPayload()
        val rawParsed = SongMediaItemConverter.parseSongBytes(flacPayload)
        assertEquals(SongContainerKind.RAW_AUDIO, rawParsed.formatKind)
        assertEquals(MimeTypes.AUDIO_FLAC, rawParsed.detectedMimeType)
        assertEquals(0L, rawParsed.payloadByteOffset)

        val zsongBytes = buildZsongContainer(
            """{"id":"trk-99","title":"Autobahn Night","artist":"MBUX","album":"W203","mimeType":"audio/flac"}""",
            flacPayload
        )
        val zsongParsed = SongMediaItemConverter.parseSongBytes(zsongBytes)
        assertEquals(SongContainerKind.ZSONG_BINARY_CONTAINER, zsongParsed.formatKind)
        assertEquals(MimeTypes.AUDIO_FLAC, zsongParsed.detectedMimeType)
        assertEquals("trk-99", zsongParsed.embeddedTrackId)
        assertEquals("Autobahn Night", zsongParsed.embeddedTitle)
        assertTrue(zsongParsed.payloadByteOffset > 10L)

        val mp3Payload = fakeMp3Id3Payload()
        val b64 = Base64.getEncoder().encodeToString(mp3Payload)
        val idbJson = """{"id":"idb-77","mimeType":"audio/mpeg","title":"IndexedDB Track","artist":"Cached Artist","blobBase64":"$b64"}"""
        val idbParsed = SongMediaItemConverter.parseSongBytes(idbJson.toByteArray(Charsets.UTF_8))
        assertEquals(SongContainerKind.INDEXED_DB_JSON_BLOB, idbParsed.formatKind)
        assertEquals(MimeTypes.AUDIO_MPEG, idbParsed.detectedMimeType)
        assertEquals("idb-77", idbParsed.embeddedTrackId)
        assertEquals("IndexedDB Track", idbParsed.embeddedTitle)
        assertArrayEquals(mp3Payload, idbParsed.decodedBlobBytes)

        assertEquals("track-abc-123", SongMediaItemConverter.parseIndexedDbTrackId("idb://offline_tracks/track-abc-123"))
        assertEquals("track-abc-123", SongMediaItemConverter.parseIndexedDbTrackId("zentryx-idb://offline_tracks/track-abc-123"))
    }

    @Test
    fun `OfflineBlobDataSource streams raw song, ZSONG container, JSON IndexedDB blob, and idb URI with byte-range seeking`() {
        val tempDir = File.createTempFile("zentryx_offline_test", "").apply {
            delete()
            mkdirs()
        }
        try {
            // 1. Legacy raw FLAC bytes saved as .song
            val flacPayload = fakeFlacPayload()
            val rawSongFile = File(tempDir, "legacy_track.song").apply { writeBytes(flacPayload) }
            val rawDs = OfflineBlobDataSource(null) { null }
            val rawLen = rawDs.openUriString("file://${rawSongFile.absolutePath}")
            assertEquals(flacPayload.size.toLong(), rawLen)
            assertEquals(listOf(MimeTypes.AUDIO_FLAC), rawDs.responseHeaders["Content-Type"])
            assertArrayEquals(flacPayload, readAllFromDataSource(rawDs))
            rawDs.close()

            // 2. Binary ZSONG container with range seek (position = 4, length = 10)
            val zsongBytes = buildZsongContainer("""{"id":"z1","title":"ZSong","mimeType":"audio/flac"}""", flacPayload)
            val zsongFile = File(tempDir, "framed.song").apply { writeBytes(zsongBytes) }
            val zsongDs = OfflineBlobDataSource(null) { null }
            val sliceLen = zsongDs.openUriString("file://${zsongFile.absolutePath}", position = 4L, length = 10L)
            assertEquals(10L, sliceLen)
            assertEquals(listOf(MimeTypes.AUDIO_FLAC), zsongDs.responseHeaders["Content-Type"])
            assertArrayEquals(flacPayload.copyOfRange(4, 14), readAllFromDataSource(zsongDs))
            zsongDs.close()

            // 3. IndexedDB JSON blob via idb://offline_tracks/<trackId>
            val mp3Payload = fakeMp3Id3Payload()
            val dataUrl = "data:audio/mpeg;base64,${Base64.getEncoder().encodeToString(mp3Payload)}"
            val idbJsonBytes = """{"id":"offline-42","mimeType":"audio/mpeg","dataUrl":"$dataUrl"}""".toByteArray(Charsets.UTF_8)
            val idbDs = OfflineBlobDataSource(null) { trackId ->
                if (trackId == "offline-42") OfflineBlobSource.MemoryBacked(idbJsonBytes, MimeTypes.AUDIO_MPEG) else null
            }
            val idbLen = idbDs.openUriString("idb://offline_tracks/offline-42")
            assertEquals(mp3Payload.size.toLong(), idbLen)
            assertEquals(listOf(MimeTypes.AUDIO_MPEG), idbDs.responseHeaders["Content-Type"])
            assertArrayEquals(mp3Payload, readAllFromDataSource(idbDs))
            idbDs.close()

            // 4. Direct inline data: URI
            val dataDs = OfflineBlobDataSource(null) { null }
            val dataLen = dataDs.openUriString(dataUrl, position = 3L, length = 8L)
            assertEquals(8L, dataLen)
            assertEquals(listOf(MimeTypes.AUDIO_MPEG), dataDs.responseHeaders["Content-Type"])
            assertArrayEquals(mp3Payload.copyOfRange(3, 11), readAllFromDataSource(dataDs))
            dataDs.close()
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
