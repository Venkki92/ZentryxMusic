package com.zentryx.music.player

import com.zentryx.music.dsp.SurroundMode
import com.zentryx.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitModelsTest {

    @Test
    fun `driver profiles serialize and deserialize roundtrip cleanly`() {
        val json = serializeDriverProfiles(DEFAULT_DRIVER_PROFILES)
        val decoded = deserializeDriverProfiles(json)
        assertEquals(3, decoded.size)
        assertEquals("Venki", decoded[0].name)
        assertEquals(SurroundMode.LOGIC7_SURROUND, decoded[0].surroundMode)
        assertEquals("Night Cruise", decoded[1].name)
        assertTrue(decoded[1].amberFilterEnabled)
        assertEquals("Full Cabin", decoded[2].name)
    }

    @Test
    fun `buildRoadtripOfflinePlan budgets target duration and counts missing downloads`() {
        val songs = (1..20).map { i ->
            Song(
                id = "s_$i",
                title = "Track $i",
                artist = "Artist",
                album = "Album",
                durationMs = 600_000L, // 10 min each
                fileUrl = "https://cloud.zentryx.bg/$i.mp3",
                relativePath = "Music/$i.mp3",
                mimeType = "audio/mpeg",
                fileSize = 10L * 1024L * 1024L,
                isFavorite = i <= 6,
                isDownloaded = i <= 2,
                playCount = if (i <= 4) 10 else 0
            )
        }

        val plan = buildRoadtripOfflinePlan(songs, targetHours = 1, pool = RoadtripPool.FAVORITES_AND_UNHEARD)
        assertEquals(6, plan.selectedSongs.size)
        assertEquals(60, plan.totalMinutes)
        assertEquals(2, plan.alreadyOfflineCount)
        assertEquals(4, plan.missingOfflineSongs.size)
        assertEquals(40, plan.estimatedMissingMb)
    }
}
