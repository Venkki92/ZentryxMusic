package com.zentryx.music.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.zentryx.music.dsp.AndroidDspAudioEngine
import com.zentryx.music.models.Song
import com.zentryx.music.player.ZentryxMediaBrowseHelper
import com.zentryx.music.player.ZentryxMediaSessionHolder
import com.zentryx.music.player.ZentryxPlayerManager
import com.zentryx.music.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Android Auto FTS4 search screen querying Room locally while respecting Offline Mode.
 */
class ZentryxCarSearchScreen(
    carContext: CarContext,
    private val playerManager: ZentryxPlayerManager,
    private val musicRepository: MusicRepository,
    private val dspAudioEngine: AndroidDspAudioEngine,
    private val mediaSessionHolder: ZentryxMediaSessionHolder
) : Screen(carContext), DefaultLifecycleObserver {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var searchJob: Job? = null

    private var currentQuery: String = ""
    private var isSearching: Boolean = false
    private var results: List<Song> = emptyList()

    init {
        lifecycle.addObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        searchJob?.cancel()
        screenScope.cancel()
    }

    private fun runSearch(query: String) {
        val trimmed = query.trim()
        currentQuery = trimmed
        searchJob?.cancel()

        if (trimmed.isEmpty()) {
            results = emptyList()
            isSearching = false
            invalidate()
            return
        }

        isSearching = true
        invalidate()
        searchJob = screenScope.launch {
            val found = musicRepository.searchSongs(trimmed).firstOrNull().orEmpty()
                .filter { playerManager.isSongPlayable(it) }
            results = found
            isSearching = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val callback = object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                runSearch(searchText)
            }

            override fun onSearchSubmitted(searchText: String) {
                runSearch(searchText)
            }
        }

        val builder = SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint("Search Nextcloud library…")
            .setInitialSearchText(currentQuery)

        if (isSearching) {
            return builder.setLoading(true).build()
        }

        val hostLimit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Throwable) {
            ZentryxMediaBrowseHelper.DEFAULT_CAR_LIST_LIMIT
        }

        val listBuilder = ItemList.Builder()
            .setNoItemsMessage(
                if (currentQuery.isBlank()) "Type a song, artist, or album"
                else "No matching tracks for \"$currentQuery\""
            )

        ZentryxMediaBrowseHelper.capForCarList(results, hostLimit).forEach { song ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(song.title)
                    .addText("${song.artist} · ${song.album} · ${song.formattedDuration}")
                    .setOnClickListener {
                        playerManager.playSong(song, results)
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
            )
        }

        return builder.setItemList(listBuilder.build()).build()
    }
}
