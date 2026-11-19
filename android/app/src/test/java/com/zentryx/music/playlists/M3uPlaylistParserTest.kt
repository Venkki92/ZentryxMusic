package com.zentryx.music.playlists

import com.zentryx.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistParserTest {

    @Test
    fun serializeAndParseM3u8PreservesTracksAndIgnoresMetadataLines() {
        val songs = listOf(
            Song(
                id = "1",
                title = "Nightcall",
                artist = "Kavinsky",
                album = "OutRun",
                durationMs = 258_000L,
                fileUrl = "https://cloud.example.com/dav/Synth/Nightcall.flac",
                relativePath = "Synth/Nightcall.flac",
                mimeType = "audio/flac"
            ),
            Song(
                id = "2",
                title = "Resonance",
                artist = "HOME",
                album = "Odyssey",
                durationMs = 212_000L,
                fileUrl = "https://cloud.example.com/dav/Synth/Resonance.mp3",
                relativePath = "Synth/Resonance.mp3",
                mimeType = "audio/mpeg"
            )
        )

        val serialized = M3uPlaylistParser.serializeM3u8("Midnight Drive\nVIP", songs)
        assertTrue(serialized.startsWith("#EXTM3U\n#PLAYLIST:Midnight Drive VIP\n"))
        assertTrue(serialized.contains("#EXTINF:258,Kavinsky - Nightcall"))

        val parsed = M3uPlaylistParser.parseM3u8Paths(
            """
            $serialized
            # Extra comment line

            /LeadingSlash/Track Three.ogg
            """.trimIndent()
        )
        assertEquals(
            listOf("Synth/Nightcall.flac", "Synth/Resonance.mp3", "LeadingSlash/Track Three.ogg"),
            parsed
        )
    }

    @Test
    fun parseBlankContentReturnsEmptyList() {
        assertTrue(M3uPlaylistParser.parseM3u8Paths("").isEmpty())
        assertTrue(M3uPlaylistParser.parseM3u8Paths("   \n#EXTM3U\n ").isEmpty())
    }
}
