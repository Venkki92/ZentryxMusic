package com.zentryx.music.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.zentryx.music.R
import com.zentryx.music.dsp.AndroidDspAudioEngine
import com.zentryx.music.models.Album
import com.zentryx.music.models.Artist
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class CarBrowseCategory {
    SONGS,
    ALBUMS,
    ARTISTS,
    FAVORITES,
    DOWNLOADED,
    ALBUM_TRACKS,
    ARTIST_TRACKS
}

/**
 * Android Auto browse screen for Songs, Albums, Artists, Favorites, and Offline Downloads.
 * Enforces ConstraintManager list limits so templates always pass host distraction validation.
 */
class ZentryxCarBrowseScreen(
    carContext: CarContext,
    private val category: CarBrowseCategory,
    private val playerManager: ZentryxPlayerManager,
    private val musicRepository: MusicRepository,
    private val dspAudioEngine: AndroidDspAudioEngine,
    private val mediaSessionHolder: ZentryxMediaSessionHolder,
    private val filterArtist: String = "",
    private val filterAlbum: String = ""
) : Screen(carContext), DefaultLifecycleObserver {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null

    private var isLoading = true
    private var songs: List<Song> = emptyList()
    private var albums: List<Album> = emptyList()
    private var artists: List<Artist> = emptyList()

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        loadJob?.cancel()
        loadJob = screenScope.launch {
            when (category) {
                CarBrowseCategory.SONGS -> {
                    musicRepository.getAllSongs().collectLatest { list ->
                        songs = list.filter { playerManager.isSongPlayable(it) }
                        isLoading = false
                        invalidate()
                    }
                }
                CarBrowseCategory.FAVORITES -> {
                    musicRepository.getFavoriteSongs().collectLatest { list ->
                        songs = list.filter { playerManager.isSongPlayable(it) }
                        isLoading = false
                        invalidate()
                    }
                }
                CarBrowseCategory.DOWNLOADED -> {
                    musicRepository.getDownloadedSongs().collectLatest { list ->
                        songs = list
                        isLoading = false
                        invalidate()
                    }
                }
                CarBrowseCategory.ALBUM_TRACKS -> {
                    musicRepository.getSongsByAlbum(filterAlbum, filterArtist).collectLatest { list ->
                        songs = list.filter { playerManager.isSongPlayable(it) }
                        isLoading = false
                        invalidate()
                    }
                }
                CarBrowseCategory.ARTIST_TRACKS -> {
                    musicRepository.getSongsByArtist(filterArtist).collectLatest { list ->
                        songs = list.filter { playerManager.isSongPlayable(it) }
                        isLoading = false
                        invalidate()
                    }
                }
                CarBrowseCategory.ALBUMS -> {
                    musicRepository.getAlbums().collectLatest { list ->
                        albums = list
                        isLoading = false
                        invalidate()
                    }
                }
                CarBrowseCategory.ARTISTS -> {
                    musicRepository.getArtists().collectLatest { list ->
                        artists = list
                        isLoading = false
                        invalidate()
                    }
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        loadJob?.cancel()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        screenScope.cancel()
    }

    override fun onGetTemplate(): Template {
        val title = when (category) {
            CarBrowseCategory.SONGS -> "All Songs"
            CarBrowseCategory.ALBUMS -> "Albums"
            CarBrowseCategory.ARTISTS -> "Artists"
            CarBrowseCategory.FAVORITES -> "Favorites"
            CarBrowseCategory.DOWNLOADED -> "Offline Downloads"
            CarBrowseCategory.ALBUM_TRACKS -> filterAlbum.ifBlank { "Album" }
            CarBrowseCategory.ARTIST_TRACKS -> filterArtist.ifBlank { "Artist" }
        }

        val builder = ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
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
                    .build()
            )

        if (isLoading) {
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
                when (category) {
                    CarBrowseCategory.DOWNLOADED -> "No downloaded tracks cached locally"
                    CarBrowseCategory.FAVORITES -> "No favorite tracks marked yet"
                    else -> "No tracks indexed — run Nextcloud scan in app"
                }
            )

        when (category) {
            CarBrowseCategory.ALBUMS -> {
                ZentryxMediaBrowseHelper.capForCarList(albums, hostLimit).forEach { album ->
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(album.name)
                            .addText("${album.artist} · ${album.songCount} tracks")
                            .setBrowsable(true)
                            .setOnClickListener {
                                screenManager.push(
                                    ZentryxCarBrowseScreen(
                                        carContext = carContext,
                                        category = CarBrowseCategory.ALBUM_TRACKS,
                                        playerManager = playerManager,
                                        musicRepository = musicRepository,
                                        dspAudioEngine = dspAudioEngine,
                                        mediaSessionHolder = mediaSessionHolder,
                                        filterArtist = album.artist,
                                        filterAlbum = album.name
                                    )
                                )
                            }
                            .build()
                    )
                }
            }

            CarBrowseCategory.ARTISTS -> {
                ZentryxMediaBrowseHelper.capForCarList(artists, hostLimit).forEach { artist ->
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle(artist.name)
                            .addText("${artist.albumCount} albums · ${artist.songCount} tracks")
                            .setBrowsable(true)
                            .setOnClickListener {
                                screenManager.push(
                                    ZentryxCarBrowseScreen(
                                        carContext = carContext,
                                        category = CarBrowseCategory.ARTIST_TRACKS,
                                        playerManager = playerManager,
                                        musicRepository = musicRepository,
                                        dspAudioEngine = dspAudioEngine,
                                        mediaSessionHolder = mediaSessionHolder,
                                        filterArtist = artist.name
                                    )
                                )
                            }
                            .build()
                    )
                }
            }

            else -> {
                val activeSongId = playerManager.playbackState.value.currentSong?.id
                ZentryxMediaBrowseHelper.capForCarList(songs, hostLimit).forEach { song ->
                    val prefix = if (song.id == activeSongId) "▶ " else ""
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle("$prefix${song.title}")
                            .addText("${song.artist} · ${song.formattedDuration} · ${song.format.name}")
                            .setOnClickListener {
                                playerManager.playSong(song, songs)
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
            }
        }

        return builder.setSingleList(listBuilder.build()).build()
    }
}
