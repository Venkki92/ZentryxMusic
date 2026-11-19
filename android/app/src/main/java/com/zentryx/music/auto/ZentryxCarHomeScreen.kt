package com.zentryx.music.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.zentryx.music.R
import com.zentryx.music.dsp.AndroidDspAudioEngine
import com.zentryx.music.dsp.FACTORY_DSP_PRESETS
import com.zentryx.music.models.PlaybackState
import com.zentryx.music.player.ZentryxMediaSessionHolder
import com.zentryx.music.player.ZentryxPlayerManager
import com.zentryx.music.repository.MusicRepository
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
 * Primary Android Auto dashboard screen for Zentryx Music.
 * Uses sectioned ListTemplate (Car App API Level 1+) for maximum compatibility across all
 * Android Auto headunits and displays live Media3 playback status, quick controls, DSP presets,
 * search, and library categories.
 */
class ZentryxCarHomeScreen(
    carContext: CarContext,
    private val playerManager: ZentryxPlayerManager,
    private val musicRepository: MusicRepository,
    private val dspAudioEngine: AndroidDspAudioEngine,
    private val mediaSessionHolder: ZentryxMediaSessionHolder
) : Screen(carContext), DefaultLifecycleObserver {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateObserverJob: Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        stateObserverJob?.cancel()
        stateObserverJob = screenScope.launch {
            combine(
                playerManager.playbackState,
                dspAudioEngine.dspSettings
            ) { playback, dsp ->
                Triple(
                    playback.currentSong?.id,
                    playback.isPlaying to playback.isBuffering,
                    dsp.selectedPresetId to dsp.dspEnabled
                )
            }
                .distinctUntilChanged()
                .collectLatest {
                    invalidate()
                }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        stateObserverJob?.cancel()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        screenScope.cancel()
    }

    override fun onGetTemplate(): Template {
        val playback: PlaybackState = playerManager.playbackState.value
        val dspSettings = dspAudioEngine.dspSettings.value
        val customPresets = dspAudioEngine.customPresets.value

        val activePresetName = (FACTORY_DSP_PRESETS + customPresets)
            .firstOrNull { it.id == dspSettings.selectedPresetId }
            ?.name ?: "Custom Calibration"

        val nowPlayingRow = buildNowPlayingRow(playback)
        val dspRow = Row.Builder()
            .setTitle("Audio DSP Profile")
            .addText(
                if (dspSettings.dspEnabled) "Active: $activePresetName"
                else "DSP Bypassed · Tap to enable"
            )
            .setImage(carDrawableIcon(carContext, R.drawable.ic_car_eq))
            .setBrowsable(true)
            .setOnClickListener {
                screenManager.push(
                    ZentryxCarDspScreen(carContext, dspAudioEngine)
                )
            }
            .build()

        val statusList = ItemList.Builder()
            .addItem(nowPlayingRow)
            .addItem(dspRow)
            .build()

        val libraryList = ItemList.Builder()
            .addItem(
                categoryRow("All Songs", "Browse complete catalog") {
                    pushBrowse(CarBrowseCategory.SONGS)
                }
            )
            .addItem(
                categoryRow("Albums", "Browse by album") {
                    pushBrowse(CarBrowseCategory.ALBUMS)
                }
            )
            .addItem(
                categoryRow("Artists", "Browse by artist") {
                    pushBrowse(CarBrowseCategory.ARTISTS)
                }
            )
            .addItem(
                categoryRow("Favorites", "Starred tracks") {
                    pushBrowse(CarBrowseCategory.FAVORITES)
                }
            )
            .addItem(
                categoryRow("Offline Downloads", "Locally cached lossless & MP3 tracks") {
                    pushBrowse(CarBrowseCategory.DOWNLOADED)
                }
            )
            .build()

        val actionStripBuilder = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(
                        carDrawableIcon(
                            carContext,
                            if (playback.isPlaying) R.drawable.ic_car_pause else R.drawable.ic_car_play
                        )
                    )
                    .setOnClickListener { playerManager.togglePlayPause() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(carDrawableIcon(carContext, R.drawable.ic_car_next))
                    .setOnClickListener { playerManager.skipToNext() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(carDrawableIcon(carContext, R.drawable.ic_car_search))
                    .setOnClickListener {
                        screenManager.push(
                            ZentryxCarSearchScreen(
                                carContext = carContext,
                                playerManager = playerManager,
                                musicRepository = musicRepository,
                                dspAudioEngine = dspAudioEngine,
                                mediaSessionHolder = mediaSessionHolder
                            )
                        )
                    }
                    .build()
            )

        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStripBuilder.build())
            .addSectionedList(SectionedItemList.create(statusList, "Now Playing & Audio"))
            .addSectionedList(SectionedItemList.create(libraryList, "Music Library"))
            .build()
    }

    private fun buildNowPlayingRow(playback: PlaybackState): Row {
        val song = playback.currentSong
        val title = song?.title ?: "Nothing Playing"
        val subtitle = if (song != null) {
            val stateLabel = when {
                playback.isBuffering -> "Buffering…"
                playback.isPlaying -> "Playing"
                else -> "Paused"
            }
            "$stateLabel · ${song.artist} · ${song.format.name}"
        } else {
            playback.errorMessage ?: "Select a track from your Nextcloud catalog"
        }

        return Row.Builder()
            .setTitle(title)
            .addText(subtitle)
            .setImage(
                carDrawableIcon(
                    carContext,
                    if (playback.isPlaying) R.drawable.ic_car_pause else R.drawable.ic_car_music
                )
            )
            .setBrowsable(true)
            .setOnClickListener {
                screenManager.push(
                    ZentryxCarNowPlayingScreen(
                        carContext = carContext,
                        playerManager = playerManager,
                        dspAudioEngine = dspAudioEngine,
                        mediaSessionHolder = mediaSessionHolder
                    )
                )
            }
            .build()
    }

    private fun categoryRow(title: String, subtitle: String, onClick: () -> Unit): Row {
        return Row.Builder()
            .setTitle(title)
            .addText(subtitle)
            .setBrowsable(true)
            .setOnClickListener(onClick)
            .build()
    }

    private fun pushBrowse(category: CarBrowseCategory) {
        screenManager.push(
            ZentryxCarBrowseScreen(
                carContext = carContext,
                category = category,
                playerManager = playerManager,
                musicRepository = musicRepository,
                dspAudioEngine = dspAudioEngine,
                mediaSessionHolder = mediaSessionHolder
            )
        )
    }
}
