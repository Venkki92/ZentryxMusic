package com.zentryx.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZentryxMediaBrowseHelperTest {

    @Test
    fun songMediaIdRoundTripPreservesIdWithoutDoublePrefix() {
        val raw = "track_123_abc"
        val prefixed = ZentryxMediaBrowseHelper.songMediaId(raw)
        assertEquals("song:track_123_abc", prefixed)
        assertEquals(prefixed, ZentryxMediaBrowseHelper.songMediaId(prefixed))
        assertEquals(raw, ZentryxMediaBrowseHelper.extractSongId(prefixed))
        assertEquals(raw, ZentryxMediaBrowseHelper.extractSongId(raw))
    }

    @Test
    fun albumAndArtistNodeIdsEncodeSpecialCharactersSafely() {
        val node = ZentryxMediaBrowseHelper.albumNodeId("AC/DC", "Back In Black & Gold")
        val parsed = ZentryxMediaBrowseHelper.parseAlbumNodeId(node)
        assertEquals("AC/DC" to "Back In Black & Gold", parsed)
        assertNull(ZentryxMediaBrowseHelper.parseAlbumNodeId("invalid_node"))

        val artistNode = ZentryxMediaBrowseHelper.artistNodeId("Miles Davis + Coltrane")
        assertEquals("Miles Davis + Coltrane", ZentryxMediaBrowseHelper.parseArtistNodeId(artistNode))
        assertNull(ZentryxMediaBrowseHelper.parseArtistNodeId("song:123"))
    }

    @Test
    fun slicePageHandlesBoundariesCleanly() {
        val items = (1..10).toList()
        assertEquals(listOf(1, 2, 3, 4), ZentryxMediaBrowseHelper.slicePage(items, 0, 4))
        assertEquals(listOf(9, 10), ZentryxMediaBrowseHelper.slicePage(items, 2, 4))
        assertEquals(emptyList<Int>(), ZentryxMediaBrowseHelper.slicePage(items, 3, 4))
        assertEquals(emptyList<Int>(), ZentryxMediaBrowseHelper.slicePage(items, -1, 4))
        assertEquals(emptyList<Int>(), ZentryxMediaBrowseHelper.slicePage(emptyList<Int>(), 0, 4))
    }

    @Test
    fun capForCarListEnforcesDistractionLimits() {
        val items = (1..120).toList()
        assertEquals(6, ZentryxMediaBrowseHelper.capForCarList(items, 4).size)
        assertEquals(12, ZentryxMediaBrowseHelper.capForCarList(items, 12).size)
        assertEquals(50, ZentryxMediaBrowseHelper.capForCarList(items, 200).size)
        assertEquals(6, ZentryxMediaBrowseHelper.capForCarList(items, 0).size)
    }

    @Test
    fun formatDurationMsFormatsMinutesAndSeconds() {
        assertEquals("0:00", ZentryxMediaBrowseHelper.formatDurationMs(0L))
        assertEquals("1:05", ZentryxMediaBrowseHelper.formatDurationMs(65_000L))
        assertEquals("4:05", ZentryxMediaBrowseHelper.formatDurationMs(245_000L))
    }
}
