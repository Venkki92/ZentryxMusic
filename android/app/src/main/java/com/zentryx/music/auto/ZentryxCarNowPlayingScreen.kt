package com.zentryx.music.auto

import android.support.v4.media.session.MediaSessionCompat
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.annotations.RequiresCarApi
import androidx.car.app.media.model.MediaPlaybackTemplate
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.versioning.CarAppApiLevels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.zentryx.music.R
import com.zentryx.music.dsp.AndroidDspAudioEngine
import com.zentryx.music.dsp.DspSettings
import com.zentryx.music.dsp.FACTORY_DSP_PRESETS
import com.zentryx.music.models.PlaybackState
import com.zentryx.music.player.ZentryxMediaBrowseHelper
import com.zentryx.music.player.ZentryxMediaSessionHolder
import com.zentryx.music.player.ZentryxPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Android Auto Now Playing screen driven directly by ZentryxPlayerManager's Media3 state.
 * - Uses native MediaPlaybackTemplate on Car App API Level 8+ hosts once the MediaSessionCompat token is bound.
 * - Gracefully falls back to a distraction-compliant PaneTemplate on API 1–7 hosts.
 */
class ZentryxCarNowPlayingScreen(
    carContext: CarContext,
    private val playerManager: ZentryxPlayerManager,
    private val dspAudioEngine: AndroidDspAudioEngine,
    private val mediaSessionHolder: ZentryxMediaSessionHolder
) : Screen(carContext), DefaultLifecycleObserver {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        observerJob?.cancel()
        observerJob = screenScope.launch {
            combine(
                playerManager.playbackState,
                dspAudioEngine.dspSettings,
                mediaSessionHolder.sessionCompatToken
            ) { playback: PlaybackState, dsp: DspSettings, token: MediaSessionCompat.Token? ->
                listOf(
                    playback.currentSong?.id,
                    playback.currentSong?.isFavorite,
                    playback.isPlaying,
                    playback.isBuffering,
                    playback.currentPositionMs / 1000L,
                    playback.errorMessage,
                    dsp.selectedPresetId,
                    dsp.dspEnabled,
                    token != null
                )
            }
                .distinctUntilChanged()
                .collectLatest {
                    invalidate()
                }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        observerJob?.cancel()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        screenScope.cancel()
    }

    override fun onGetTemplate(): Template {
        val playback = playerManager.playbackState.value
        val dspSettings = dspAudioEngine.dspSettings.value
        val hasCompatToken = mediaSessionHolder.sessionCompatToken.value != null

        if (carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_8 && hasCompatToken) {
            return buildNativeMediaPlaybackTemplate(playback)
        }

        return buildCompatPaneTemplate(playback, dspSettings.selectedPresetId, dspSettings.dspEnabled)
    }

    @RequiresCarApi(CarAppApiLevels.LEVEL_8)
    private fun buildNativeMediaPlaybackTemplate(playback: PlaybackState): Template {
        val isFav = playback.currentSong?.isFavorite == true
        val header = Header.Builder()
            .setStartHeaderAction(Action.BACK)
            .setTitle(playback.currentSong?.title ?: "Zentryx Music")
            .addEndHeaderAction(
                Action.Builder()
                    .setIcon(
                        carDrawableIcon(
                            carContext,
                            if (isFav) R.drawable.ic_car_favorite_filled else R.drawable.ic_car_favorite_border
                        )
                    )
                    .setOnClickListener { playerManager.toggleFavoriteCurrentSong() }
                    .build()
            )
            .addEndHeaderAction(
                Action.Builder()
                    .setIcon(carDrawableIcon(carContext, R.drawable.ic_car_eq))
                    .setOnClickListener {
                        screenManager.push(ZentryxCarDspScreen(carContext, dspAudioEngine))
                    }
                    .build()
            )
            .build()

        return MediaPlaybackTemplate.Builder()
            .setHeader(header)
            .build()
    }

    private fun buildCompatPaneTemplate(
        playback: PlaybackState,
        selectedPresetId: String,
        dspEnabled: Boolean
    ): Template {
        val song = playback.currentSong
        val presetName = (FACTORY_DSP_PRESETS + dspAudioEngine.customPresets.value)
            .firstOrNull { it.id == selectedPresetId }
            ?.name ?: "Custom DSP"

        val paneBuilder = Pane.Builder()

        if (song != null) {
            val pos = ZentryxMediaBrowseHelper.formatDurationMs(playback.currentPositionMs)
            val dur = ZentryxMediaBrowseHelper.formatDurationMs(
                if (playback.durationMs > 0L) playback.durationMs else song.durationMs
            )
            val statusText = when {
                playback.isBuffering -> "Buffering…"
                playback.isPlaying -> "Playing"
                else -> "Paused"
            }

            paneBuilder.addRow(
                Row.Builder()
                    .setTitle(song.title)
                    .addText("${song.artist} — ${song.album}")
                    .addText("$statusText · $pos / $dur · ${song.format.name}")
                    .build()
            )

            val queueSummary = if (playback.queue.size > 1 && playback.currentQueueIndex >= 0) {
                "Queue ${playback.currentQueueIndex + 1}/${playback.queue.size}"
            } else {
                "Single Track"
            }
            val dspSummary = if (dspEnabled) "DSP: $presetName" else "DSP: Bypassed"
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("$queueSummary · $dspSummary")
                    .addText(playback.errorMessage ?: if (song.isDownloaded) "Source: Offline Cache" else "Source: Nextcloud WebDAV")
                    .build()
            )
        } else {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("No Active Track")
                    .addText(playback.errorMessage ?: "Choose a song or album from the Music Library.")
                    .build()
            )
        }

        // Car App Pane allows a maximum of 2 primary actions
        paneBuilder.addAction(
            Action.Builder()
                .setIcon(
                    carDrawableIcon(
                        carContext,
                        if (playback.isPlaying) R.drawable.ic_car_pause else R.drawable.ic_car_play
                    )
                )
                .setTitle(if (playback.isPlaying) "Pause" else "Play")
                .setOnClickListener { playerManager.togglePlayPause() }
                .build()
        )
        paneBuilder.addAction(
            Action.Builder()
                .setIcon(carDrawableIcon(carContext, R.drawable.ic_car_next))
                .setTitle("Next")
                .setOnClickListener { playerManager.skipToNext() }
                .build()
        )

        val isFav = song?.isFavorite == true
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(carDrawableIcon(carContext, R.drawable.ic_car_prev))
                    .setOnClickListener { playerManager.skipToPrevious() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(
                        carDrawableIcon(
                            carContext,
                            if (isFav) R.drawable.ic_car_favorite_filled else R.drawable.ic_car_favorite_border
                        )
                    )
                    .setOnClickListener { playerManager.toggleFavoriteCurrentSong() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(carDrawableIcon(carContext, R.drawable.ic_car_eq))
                    .setOnClickListener {
                        screenManager.push(ZentryxCarDspScreen(carContext, dspAudioEngine))
                    }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeaderAction(Action.BACK)
            .setTitle("Now Playing")
            .setActionStrip(actionStrip)
            .build()
    }
}
