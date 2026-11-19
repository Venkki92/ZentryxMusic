package com.zentryx.music.dsp

import com.zentryx.music.models.Song
import com.zentryx.music.player.ZentryxPlayerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGainConversionTest {

    @Test
    fun replayGainDbToLinearConvertsAndClampsSafely() {
        assertEquals(1.0f, ZentryxPlayerManager.replayGainDbToLinear(null), 0.0001f)
        assertEquals(1.0f, ZentryxPlayerManager.replayGainDbToLinear(Float.NaN), 0.0001f)
        assertEquals(1.0f, ZentryxPlayerManager.replayGainDbToLinear(0f), 0.0001f)
        assertEquals(0.5012f, ZentryxPlayerManager.replayGainDbToLinear(-6f), 0.001f)
        assertEquals(1.9953f, ZentryxPlayerManager.replayGainDbToLinear(6f), 0.001f)

        // Clamped extremes: [-15dB, +6dB] -> linear in [0.18, 2.0]
        assertEquals(0.18f, ZentryxPlayerManager.replayGainDbToLinear(-30f), 0.005f)
        assertEquals(1.9953f, ZentryxPlayerManager.replayGainDbToLinear(20f), 0.005f)
    }

    @Test
    fun buildSignalPathBadgeFormatsSampleRateBitDepthLocalTagAndReplayGain() {
        assertNull(ZentryxPlayerManager.buildSignalPathBadge(null, 48000, null))

        val flacLocal = Song(
            id = "s1",
            title = "Studio Master",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            fileUrl = "content://usb/master.flac",
            relativePath = "Local/USB/master.flac",
            mimeType = "audio/flac",
            sourceType = "LOCAL",
            sampleRateHz = 96000,
            bitsPerSample = 24,
            bitrateKbps = 2780,
            replayGainDb = -4.2f
        )

        val badge = ZentryxPlayerManager.buildSignalPathBadge(flacLocal, 48000, -4.2f)
        assertEquals("FLAC · 24-bit/96 kHz · 2780 kbps · USB/LOCAL · RG -4.2dB", badge)

        val mp3Remote = Song(
            id = "s2",
            title = "Track",
            artist = "Artist",
            album = "Album",
            durationMs = 120_000L,
            fileUrl = "https://cloud.example.com/dav/track.mp3",
            relativePath = "track.mp3",
            mimeType = "audio/mpeg",
            sourceType = "WEBDAV",
            bitrateKbps = 320
        )
        val remoteBadge = ZentryxPlayerManager.buildSignalPathBadge(mp3Remote, 44100, null)
        assertTrue(remoteBadge?.contains("MP3") == true)
        assertTrue(remoteBadge?.contains("44.1 kHz") == true)
        assertTrue(remoteBadge?.contains("320 kbps") == true)
    }
}
