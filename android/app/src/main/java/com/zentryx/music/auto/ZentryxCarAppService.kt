package com.zentryx.music.auto

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.zentryx.music.dsp.AndroidDspAudioEngine
import com.zentryx.music.player.ZentryxMediaSessionHolder
import com.zentryx.music.player.ZentryxPlayerManager
import com.zentryx.music.repository.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Jetpack Car App Library entry point exposing Zentryx Music as a native Android Auto
 * media application while sharing the Hilt-managed Media3 ZentryxPlayerManager,
 * CarDspAudioProcessor / Hardware DSP engine, and Room Nextcloud catalog.
 */
@AndroidEntryPoint
class ZentryxCarAppService : CarAppService() {

    @Inject
    lateinit var playerManager: ZentryxPlayerManager

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var dspAudioEngine: AndroidDspAudioEngine

    @Inject
    lateinit var mediaSessionHolder: ZentryxMediaSessionHolder

    override fun createHostValidator(): HostValidator {
        return if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return ZentryxCarSession(
            playerManager = playerManager,
            musicRepository = musicRepository,
            dspAudioEngine = dspAudioEngine,
            mediaSessionHolder = mediaSessionHolder
        )
    }

    override fun onCreateSession(): Session {
        return ZentryxCarSession(
            playerManager = playerManager,
            musicRepository = musicRepository,
            dspAudioEngine = dspAudioEngine,
            mediaSessionHolder = mediaSessionHolder
        )
    }
}
