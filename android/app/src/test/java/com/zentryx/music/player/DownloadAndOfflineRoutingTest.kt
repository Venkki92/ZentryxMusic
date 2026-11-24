package com.zentryx.music.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.zentryx.music.download.DownloadManager
import com.zentryx.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLEncoder

@OptIn(UnstableApi::class)
class DownloadAndOfflineRoutingTest {

    private fun sampleSong(
        id: String = "song-01",
        title: String = "Night Drive (Remix)",
        relativePath: String = "Music/Electronic/Night Drive.flac",
        fileUrl: String = "https://cloud.zentryx.bg/remote.php/dav/files/user/Music/Night%20Drive.flac",
        mimeType: String = "audio/flac",
        localFilePath: String? = null
    ): Song = Song(
        id = id,
        title = title,
        artist = "Zentryx Artist",
        album = "Autobahn Sessions",
        durationMs = 215_000L,
        fileUrl = fileUrl,
        relativePath = relativePath,
        fileSize = 24_000_000L,
        mimeType = mimeType,
        isDownloaded = localFilePath != null,
        localFilePath = localFilePath
    )

    @Test
    fun `resolveAudioExtensionAndMime preserves authentic audio extensions and concrete MIME types`() {
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
    fun `buildDownloadFilenames produces clean FAT32-safe name with authentic extension and extensionless SAF base`() {
        val song = sampleSong(
            id = "abc/123:def",
            title = "Autobahn: Midnight / Run?*",
            relativePath = "Synth/Autobahn.mp3",
            fileUrl = "https://ex/Synth/Autobahn.mp3",
            mimeType = "audio/mpeg"
        )
        val (fullName, safBaseName) = DownloadManager.buildDownloadFilenames(song)
        assertTrue("Full filename should end with .mp3 (was $fullName)", fullName.endsWith(".mp3"))
        assertFalse("SAF base name must omit extension to prevent double-extension (was $safBaseName)", safBaseName.endsWith(".mp3"))
        assertFalse("Full filename must not contain .song or .audio", fullName.endsWith(".song") || fullName.endsWith(".audio"))
        assertFalse("Full filename must not contain invalid FAT32 characters", Regex("[/:*?\"<>|]").containsMatchIn(fullName))
    }

    @Test
    fun `isRemoteHttpScheme routes http and https to remote cache and local schemes to native DefaultDataSource`() {
        assertTrue(CacheDataSourceFactory.isRemoteHttpScheme("http"))
        assertTrue(CacheDataSourceFactory.isRemoteHttpScheme("https"))
        assertTrue(CacheDataSourceFactory.isRemoteHttpScheme("HTTPS"))
        assertFalse(CacheDataSourceFactory.isRemoteHttpScheme("file"))
        assertFalse(CacheDataSourceFactory.isRemoteHttpScheme("content"))
        assertFalse(CacheDataSourceFactory.isRemoteHttpScheme("asset"))
        assertFalse(CacheDataSourceFactory.isRemoteHttpScheme(null))
    }

    @Test
    fun `resolveLocalFile decodes percent-encoded file URIs with spaces and parentheses`() {
        val tempDir = File.createTempFile("zentryx_native_dl_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val mp3File = File(tempDir, "Night Drive (Remix).mp3").apply {
                writeBytes(byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x00, 0x00, 0x00))
            }
            val encodedName = URLEncoder.encode(mp3File.name, "UTF-8").replace("+", "%20")
            val fileUri = "file://${tempDir.absolutePath}/$encodedName"

            val resolvedFromUri = ZentryxPlayerManager.resolveLocalFile(fileUri)
            assertTrue("Expected resolved file to exist for $fileUri", resolvedFromUri.exists())
            assertEquals(mp3File.canonicalPath, resolvedFromUri.canonicalPath)

            val resolvedFromRaw = ZentryxPlayerManager.resolveLocalFile(mp3File.absolutePath)
            assertTrue(resolvedFromRaw.exists())
            assertEquals(mp3File.canonicalPath, resolvedFromRaw.canonicalPath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `resolveConcreteMimeType resolves authentic MIME types from local file extension or metadata`() {
        assertEquals(
            MimeTypes.AUDIO_MPEG,
            ZentryxPlayerManager.resolveConcreteMimeType(
                sampleSong(
                    relativePath = "Music/Track.mp3",
                    fileUrl = "https://ex/Track.mp3",
                    mimeType = "audio/*",
                    localFilePath = "/storage/usb/Track.mp3"
                )
            )
        )
        assertEquals(
            MimeTypes.AUDIO_FLAC,
            ZentryxPlayerManager.resolveConcreteMimeType(
                sampleSong(
                    relativePath = "Music/Lossless.flac",
                    fileUrl = "https://ex/Lossless.flac",
                    mimeType = "audio/x-flac",
                    localFilePath = "/storage/usb/Lossless.flac"
                )
            )
        )
        assertEquals(
            MimeTypes.AUDIO_MP4,
            ZentryxPlayerManager.resolveConcreteMimeType(
                sampleSong(
                    relativePath = "Music/Cruising.m4a",
                    fileUrl = "https://ex/Cruising.m4a",
                    mimeType = "audio/x-m4a"
                )
            )
        )
    }
}
