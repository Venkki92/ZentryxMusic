package com.zentryx.music.player

import com.zentryx.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class W203CockpitUtilsTest {

    private fun makeSong(
        id: String,
        title: String,
        album: String = "Test Album",
        artist: String = "Test Artist",
        relativePath: String = "Music/Electronic/Track.flac",
        trackNumber: Int? = null,
        isDownloaded: Boolean = false,
        fileSize: Long = 10L * 1024L * 1024L,
        sourceType: String = "WEBDAV"
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = 210_000L,
        fileUrl = "https://cloud.example.com/$relativePath",
        relativePath = relativePath,
        mimeType = "audio/flac",
        trackNumber = trackNumber,
        fileSize = fileSize,
        isDownloaded = isDownloaded,
        sourceType = sourceType
    )

    @Test
    fun `extractFolderKey normalizes slashes and falls back to album when flat`() {
        val nested = makeSong("1", "Track 1", relativePath = "/Music/House/2025/01.flac")
        assertEquals("Music/House/2025", extractFolderKey(nested))

        val backslash = makeSong("2", "Track 2", relativePath = "Music\\Rock\\02.mp3")
        assertEquals("Music/Rock", extractFolderKey(backslash))

        val flat = makeSong("3", "Track 3", album = "Night Drive", relativePath = "03.mp3")
        assertEquals("Night Drive", extractFolderKey(flat))
    }

    @Test
    fun `buildFolderSwapGroups groups deterministically counts cached tracks and reflects pins`() {
        val songs = listOf(
            makeSong("s1", "Track B", relativePath = "Music/Synthwave/02.flac", trackNumber = 2, isDownloaded = true),
            makeSong("s2", "Track A", relativePath = "Music/Synthwave/01.flac", trackNumber = 1, isDownloaded = false),
            makeSong("s3", "Jazz 1", relativePath = "Music/Jazz/01.flac", trackNumber = 1, isDownloaded = true)
        )

        val groups = buildFolderSwapGroups(
            songs = songs,
            pinnedFolders = setOf("music/synthwave")
        )
        assertEquals(2, groups.size)
        assertEquals("Music/Jazz", groups[0].id)
        assertEquals("Jazz", groups[0].name)
        assertEquals(1, groups[0].cachedCount)
        assertTrue(groups[0].isFullyCached)
        assertFalse(groups[0].isPinnedForWifiCache)

        assertEquals("Music/Synthwave", groups[1].id)
        assertEquals("Synthwave", groups[1].name)
        assertEquals("s2", groups[1].songs.first().id) // trackNumber 1 first
        assertEquals(1, groups[1].cachedCount)
        assertFalse(groups[1].isFullyCached)
        assertTrue(groups[1].isPinnedForWifiCache)

        val offlineGroups = buildFolderSwapGroups(songs = songs, offlineOnly = true)
        assertEquals(2, offlineGroups.size)
        assertEquals(1, offlineGroups[1].trackCount)
    }

    @Test
    fun `findAdjacentSwapGroup cycles forward and backward across folders and albums`() {
        val songs = listOf(
            makeSong("a1", "One", album = "Alpha", artist = "Band A", relativePath = "Music/A/01.flac"),
            makeSong("b1", "Two", album = "Beta", artist = "Band B", relativePath = "Music/B/01.flac"),
            makeSong("c1", "Three", album = "Gamma", artist = "Band C", relativePath = "Music/C/01.flac")
        )
        val folders = buildFolderSwapGroups(songs)
        val nextFromLast = findAdjacentSwapGroup(folders, songs[2], SwapGroupMode.FOLDERS, +1)
        assertNotNull(nextFromLast)
        assertEquals("Music/A", nextFromLast!!.first.id)
        assertEquals(0, nextFromLast.second)

        val prevFromFirst = findAdjacentSwapGroup(folders, songs[0], SwapGroupMode.FOLDERS, -1)
        assertNotNull(prevFromFirst)
        assertEquals("Music/C", prevFromFirst!!.first.id)
        assertEquals(2, prevFromFirst.second)

        val albums = buildAlbumSwapGroups(songs)
        val nextAlbum = findAdjacentSwapGroup(albums, songs[0], SwapGroupMode.ALBUMS, +1)
        assertNotNull(nextAlbum)
        assertEquals("Beta", nextAlbum!!.first.name)
        assertEquals(1, nextAlbum.second)
    }

    @Test
    fun `isW203NightWindow and resolveW203DimmerState honor schedule overrides and amber night palette`() {
        assertTrue(isW203NightWindow(hourOfDay = 21, dayStartHour = 7, nightStartHour = 19))
        assertTrue(isW203NightWindow(hourOfDay = 4, dayStartHour = 7, nightStartHour = 19))
        assertFalse(isW203NightWindow(hourOfDay = 12, dayStartHour = 7, nightStartHour = 19))
        assertTrue(isW203NightWindow(hourOfDay = 19, dayStartHour = 7, nightStartHour = 19))
        assertFalse(isW203NightWindow(hourOfDay = 7, dayStartHour = 7, nightStartHour = 19))

        // Auto mode during custom night window enables W203 Amber Night
        val autoNight = resolveW203DimmerState(
            themeMode = CockpitThemeMode.AUTO,
            hourOfDay = 20,
            dayStartHour = 6,
            nightStartHour = 18,
            isSolarDaylight = true, // custom schedule takes precedence
            autoAmberAtNight = true,
            manualAmberFilter = false
        )
        assertFalse(autoNight.useDayMode)
        assertTrue(autoNight.isAmberNightActive)

        // Forced Day mode suppresses auto amber unless manualAmberFilter is active
        val forcedDay = resolveW203DimmerState(
            themeMode = CockpitThemeMode.DAY,
            hourOfDay = 22,
            dayStartHour = 7,
            nightStartHour = 19,
            autoAmberAtNight = true,
            manualAmberFilter = false
        )
        assertTrue(forcedDay.useDayMode)
        assertFalse(forcedDay.isAmberNightActive)

        // Forced Night mode with autoAmberAtNight=false stays OLED dark without amber
        val forcedNightNoAmber = resolveW203DimmerState(
            themeMode = CockpitThemeMode.NIGHT,
            hourOfDay = 14,
            autoAmberAtNight = false,
            manualAmberFilter = false
        )
        assertFalse(forcedNightNoAmber.useDayMode)
        assertFalse(forcedNightNoAmber.isAmberNightActive)
    }

    @Test
    fun `selectTracksForWifiAutoCache filters pinned WebDAV uncached tracks and obeys storage cap`() {
        val mb = 1024L * 1024L
        val songs = listOf(
            makeSong("1", "Pinned 1", relativePath = "Music/Roadtrip/01.flac", trackNumber = 1, isDownloaded = false, fileSize = 20 * mb),
            makeSong("2", "Pinned 2", relativePath = "Music/Roadtrip/02.flac", trackNumber = 2, isDownloaded = false, fileSize = 25 * mb),
            makeSong("3", "Pinned 3", relativePath = "Music/Roadtrip/03.flac", trackNumber = 3, isDownloaded = false, fileSize = 30 * mb),
            makeSong("4", "Already Cached", relativePath = "Music/Roadtrip/04.flac", trackNumber = 4, isDownloaded = true, fileSize = 10 * mb),
            makeSong("5", "Local USB", relativePath = "Music/Roadtrip/05.flac", trackNumber = 5, isDownloaded = false, fileSize = 10 * mb, sourceType = "LOCAL"),
            makeSong("6", "Unpinned", relativePath = "Music/Other/01.flac", trackNumber = 1, isDownloaded = false, fileSize = 10 * mb)
        )

        // Cap = 100 MB, currently cached = 50 MB -> room for 50 MB -> tracks 1 (20MB) + 2 (25MB) = 45MB fit; track 3 (30MB) stops
        val selected = selectTracksForWifiAutoCache(
            songs = songs,
            pinnedFolders = setOf("Music/Roadtrip"),
            maxCacheMb = 100,
            currentCachedBytes = 50 * mb
        )
        assertEquals(listOf("1", "2"), selected.map { it.id })

        // Unlimited cap (0) selects all 3 uncached WebDAV tracks in pinned folder
        val unlimited = selectTracksForWifiAutoCache(
            songs = songs,
            pinnedFolders = setOf("music/roadtrip"),
            maxCacheMb = 0,
            currentCachedBytes = 50 * mb
        )
        assertEquals(listOf("1", "2", "3"), unlimited.map { it.id })
    }
}
