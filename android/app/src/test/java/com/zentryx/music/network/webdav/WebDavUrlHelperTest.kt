package com.zentryx.music.network.webdav

import com.zentryx.music.models.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavUrlHelperTest {

    private val helper = WebDavUrlHelper()

    @Test
    fun `encodePath preserves slashes and encodes spaces and German umlauts cleanly`() {
        val encoded = helper.encodePath("Car Music/Münchner Freiheit/Ohne Dich (Live).flac")
        assertEquals("Car%20Music/M%C3%BCnchner%20Freiheit/Ohne%20Dich%20(Live).flac", encoded)
    }

    @Test
    fun `decodePath preserves literal plus signs in track filenames`() {
        val decoded = helper.decodePath("/Music/DJ+Mix%202026%20%26%20Bass.mp3")
        assertEquals("/Music/DJ+Mix 2026 & Bass.mp3", decoded)
    }

    @Test
    fun `resolveHrefUrl avoids double-encoding already percent-encoded SabreDAV hrefs`() {
        val resolved = helper.resolveHrefUrl(
            "https://cloud.zentryx.bg",
            "/remote.php/dav/files/kris/Music/90s%20Hits/Track%2001.flac"
        )
        assertEquals(
            "https://cloud.zentryx.bg/remote.php/dav/files/kris/Music/90s%20Hits/Track%2001.flac",
            resolved
        )
    }

    @Test
    fun `extractRelativePath strips dav files user prefix and decodes UTF-8 segments`() {
        val rel1 = helper.extractRelativePath(
            "/remote.php/dav/files/kris/Music/Elektronisch/Klangk%C3%BCnstler%20-%20Weltschmerz.flac",
            "kris"
        )
        assertEquals("Music/Elektronisch/Klangkünstler - Weltschmerz.flac", rel1)

        val rel2 = helper.extractRelativePath(
            "https://cloud.zentryx.bg/remote.php/webdav/Music/Album%20One",
            "kris"
        )
        assertEquals("Music/Album One", rel2)
    }

    @Test
    fun `serverConfig builds RFC 3986 encoded Nextcloud WebDAV endpoints`() {
        val config = ServerConfig(
            serverUrl = "https://cloud.zentryx.bg/",
            username = "kris user",
            tokenOrPassword = "secret",
            musicFolderPath = "/Car Music/FLAC Hits"
        )
        assertEquals("https://cloud.zentryx.bg/remote.php/dav/files/kris%20user", config.webDavBaseUrl)
        assertEquals(
            "https://cloud.zentryx.bg/remote.php/dav/files/kris%20user/Car%20Music/FLAC%20Hits",
            config.musicWebDavUrl
        )
    }

    @Test
    fun `resolveHrefUrl handles mixed percent escapes and spaces without invalid URL chars`() {
        val resolved = helper.resolveHrefUrl(
            "https://cloud.zentryx.bg/nextcloud",
            "/nextcloud/remote.php/dav/files/user/My%20Music/01 Track [Live].mp3"
        )
        assertEquals(
            "https://cloud.zentryx.bg/nextcloud/remote.php/dav/files/user/My%20Music/01%20Track%20%5BLive%5D.mp3",
            resolved
        )
    }
}
