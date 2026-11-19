package com.zentryx.music.scanner

import com.zentryx.music.models.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigFolderScanTest {

    @Test
    fun defaultConfigIncludesWoWRadioAndMusic() {
        val config = ServerConfig(
            serverUrl = "https://cloud.zentryx.bg",
            username = "venki",
            tokenOrPassword = "secret"
        )
        assertEquals(listOf("WoW Radio", "Music"), config.configuredMusicFolders())
        assertEquals(
            listOf(
                "https://cloud.zentryx.bg/remote.php/dav/files/venki/WoW%20Radio",
                "https://cloud.zentryx.bg/remote.php/dav/files/venki/Music"
            ),
            config.musicWebDavUrls
        )
    }

    @Test
    fun parseFolderListSupportsMultipleSeparatorsAndDeduplicates() {
        val folders = ServerConfig.parseFolderList(
            raw = "/WoW Radio/, /Music ; Podcasts\nDJ Sets",
            extraFolders = listOf("music", "Audiobooks/Car")
        )
        assertEquals(
            listOf("WoW Radio", "Music", "Podcasts", "DJ Sets", "Audiobooks/Car"),
            folders
        )
    }

    @Test
    fun buildCandidateRootsEnqueuesAllDefaultAndExtraFoldersAsPrimaryBeforeFallback() {
        val config = ServerConfig(
            serverUrl = "https://cloud.zentryx.bg",
            username = "venki",
            tokenOrPassword = "secret"
        )
        val candidates = LibraryScanWorker.buildCandidateRootsStatic(
            config = config,
            extraFolders = listOf("Podcasts", "Live Sets")
        )

        val primary = candidates.filter { !it.isFallback }
        val fallbacks = candidates.filter { it.isFallback }

        assertEquals(4, primary.size)
        assertEquals("https://cloud.zentryx.bg/remote.php/dav/files/venki/WoW%20Radio", primary[0].url)
        assertEquals("https://cloud.zentryx.bg/remote.php/dav/files/venki/Music", primary[1].url)
        assertEquals("https://cloud.zentryx.bg/remote.php/dav/files/venki/Podcasts", primary[2].url)
        assertEquals("https://cloud.zentryx.bg/remote.php/dav/files/venki/Live%20Sets", primary[3].url)

        assertTrue(fallbacks.any { it.url == "https://cloud.zentryx.bg/remote.php/dav/files/venki" })
        assertTrue(fallbacks.any { it.url == "https://cloud.zentryx.bg/remote.php/webdav" })
    }

    @Test
    fun buildCandidateRootsTargetsSingleExplicitFolderInAdditiveScanMode() {
        val config = ServerConfig(
            serverUrl = "https://cloud.zentryx.bg",
            username = "venki",
            tokenOrPassword = "secret"
        )
        val candidates = LibraryScanWorker.buildCandidateRootsStatic(
            config = config,
            explicitTargetFolder = "/Soundtracks/Azeroth"
        )

        val primary = candidates.filter { !it.isFallback }
        assertEquals(1, primary.size)
        assertEquals("https://cloud.zentryx.bg/remote.php/dav/files/venki/Soundtracks/Azeroth", primary[0].url)
        assertFalse(candidates.any { it.url.endsWith("/WoW%20Radio") })
        assertFalse(candidates.any { it.url.endsWith("/Music") })
    }
}
