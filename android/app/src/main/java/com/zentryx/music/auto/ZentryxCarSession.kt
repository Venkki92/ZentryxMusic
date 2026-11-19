package com.zentryx.music.auto

import android.content.ComponentName
import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.annotations.RequiresCarApi
import androidx.car.app.media.MediaPlaybackManager
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.zentryx.music.dsp.AndroidDspAudioEngine
import com.zentryx.music.player.ZentryxMediaService
import com.zentryx.music.player.ZentryxMediaSessionHolder
import com.zentryx.music.player.ZentryxPlayerManager
import com.zentryx.music.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Manages the Android Auto CarApp session lifecycle, connects cleanly to ZentryxMediaService
 * via Media3 MediaBrowser, and registers the live MediaSessionCompat.Token with
 * CarAppLibrary's MediaPlaybackManager on API Level 8+ hosts.
 */
class ZentryxCarSession(
    private val playerManager: ZentryxPlayerManager,
    private val musicRepository: MusicRepository,
    private val dspAudioEngine: AndroidDspAudioEngine,
    private val mediaSessionHolder: ZentryxMediaSessionHolder
) : Session(), DefaultLifecycleObserver {

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tokenObserverJob: Job? = null
    private var mediaBrowserFuture: ListenableFuture<MediaBrowser>? = null
    private var lastRegisteredToken: MediaSessionCompat.Token? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return ZentryxCarHomeScreen(
            carContext = carContext,
            playerManager = playerManager,
            musicRepository = musicRepository,
            dspAudioEngine = dspAudioEngine,
            mediaSessionHolder = mediaSessionHolder
        )
    }

    override fun onCreate(owner: LifecycleOwner) {
        val sessionToken = SessionToken(
            carContext,
            ComponentName(carContext, ZentryxMediaService::class.java)
        )
        mediaBrowserFuture = MediaBrowser.Builder(carContext, sessionToken)
            .buildAsync()
            .also { future ->
                future.addListener(
                    { /* MediaLibraryService bound and active */ },
                    ContextCompat.getMainExecutor(carContext)
                )
            }

        tokenObserverJob?.cancel()
        tokenObserverJob = sessionScope.launch {
            mediaSessionHolder.sessionCompatToken.collectLatest { compatToken ->
                if (compatToken != null && compatToken != lastRegisteredToken) {
                    if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_8) {
                        registerTokenWithCarHost(compatToken)
                        lastRegisteredToken = compatToken
                    }
                }
            }
        }
    }

    @RequiresCarApi(CarAppApiLevels.LEVEL_8)
    private fun registerTokenWithCarHost(token: MediaSessionCompat.Token) {
        try {
            val playbackManager = carContext.getCarService(MediaPlaybackManager::class.java)
            playbackManager.registerMediaPlaybackToken(token)
        } catch (_: Throwable) {
            // Host does not support MediaPlaybackManager despite API level declaration;
            // ZentryxCarNowPlayingScreen falls back safely to PaneTemplate.
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        tokenObserverJob?.cancel()
        lastRegisteredToken = null
        mediaBrowserFuture?.let { MediaBrowser.releaseFuture(it) }
        mediaBrowserFuture = null
        sessionScope.cancel()
    }
}
