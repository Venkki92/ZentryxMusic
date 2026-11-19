package com.zentryx.music.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.zentryx.music.R
import com.zentryx.music.models.Song
import com.zentryx.music.repository.MusicRepository
import com.zentryx.music.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Android Media3 MediaLibraryService managing background audio streaming, system notifications,
 * Android Auto MediaBrowserService hierarchy, voice search, Bluetooth AVRCP, and CAN-Bus SWC keys.
 *
 * Bridges its MediaSessionCompat.Token into ZentryxMediaSessionHolder so the Jetpack Car App
 * Library (MediaPlaybackManager / MediaPlaybackTemplate) leverages the exact same ExoPlayer
 * and playback state without duplicating player instances.
 */
@AndroidEntryPoint
class ZentryxMediaService : MediaLibraryService() {

    companion object {
        const val ACTION_TOGGLE_FAVORITE = "com.zentryx.music.ACTION_TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_SHUFFLE = "com.zentryx.music.ACTION_TOGGLE_SHUFFLE"
        const val ACTION_CYCLE_EQ_PRESET = "com.zentryx.music.ACTION_CYCLE_EQ_PRESET"
        const val ACTION_CYCLE_SURROUND = "com.zentryx.music.ACTION_CYCLE_SURROUND"
    }

    @Inject
    lateinit var playerManager: ZentryxPlayerManager

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var mediaSessionHolder: ZentryxMediaSessionHolder

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Configure automotive notification provider
        setMediaNotificationProvider(ZentryxMediaNotificationProvider(this))

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val libraryCallback = ZentryxMediaLibraryCallback()

        mediaLibrarySession = MediaLibrarySession.Builder(this, playerManager.exoPlayer, libraryCallback)
            .setSessionActivity(sessionActivityPendingIntent)
            .setId("ZentryxMediaSession")
            .build()

        mediaLibrarySession?.let { session ->
            updateCustomLayout(session)
            mediaSessionHolder.setSessionCompatToken(session.sessionCompatToken)
        }

        // Listen for track/favorite/shuffle transitions to refresh Android Auto custom actions and MFD cluster
        playerManager.exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                broadcastClusterMetadata(mediaItem)
                mediaLibrarySession?.let { updateCustomLayout(it) }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                broadcastPlaybackState(isPlaying)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                mediaLibrarySession?.let { updateCustomLayout(it) }
            }
        })
    }

    @OptIn(UnstableApi::class)
    private fun updateCustomLayout(session: MediaLibrarySession) {
        val currentSong = playerManager.playbackState.value.currentSong
        val isFav = currentSong?.isFavorite == true
        val favButton = CommandButton.Builder(
            if (isFav) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(if (isFav) "Unfavorite" else "Favorite")
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
            .setCustomIconResId(
                if (isFav) R.drawable.ic_car_favorite_filled else R.drawable.ic_car_favorite_border
            )
            .build()

        val shuffleEnabled = playerManager.exoPlayer.shuffleModeEnabled
        val shuffleButton = CommandButton.Builder(
            if (shuffleEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setDisplayName(if (shuffleEnabled) "Shuffle On" else "Shuffle Off")
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
            .build()

        val eqButton = CommandButton.Builder(CommandButton.ICON_EQUALIZER)
            .setDisplayName("Cycle EQ")
            .setSessionCommand(SessionCommand(ACTION_CYCLE_EQ_PRESET, Bundle.EMPTY))
            .build()

        session.setCustomLayout(ImmutableList.of(favButton, shuffleButton, eqButton))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    private fun <T> launchFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                future.set(block())
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }

    private fun buildFolderMediaItem(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildBrowsableSongItem(song: Song): MediaItem {
        val base = playerManager.buildMediaItem(song)
        return base.buildUpon()
            .setMediaId(ZentryxMediaBrowseHelper.songMediaId(song.id))
            .build()
    }

    private inner class ZentryxMediaLibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_CYCLE_EQ_PRESET, Bundle.EMPTY))
                .add(SessionCommand(ACTION_CYCLE_SURROUND, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                ACTION_TOGGLE_FAVORITE -> {
                    playerManager.toggleFavoriteCurrentSong()
                    mediaLibrarySession?.let { updateCustomLayout(it) }
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_TOGGLE_SHUFFLE -> {
                    playerManager.setShuffleMode(!playerManager.exoPlayer.shuffleModeEnabled)
                    mediaLibrarySession?.let { updateCustomLayout(it) }
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_CYCLE_EQ_PRESET -> {
                    playerManager.cycleEqPreset()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_CYCLE_SURROUND -> {
                    playerManager.cycleSurroundMode()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = buildFolderMediaItem(
                mediaId = ZentryxMediaBrowseHelper.ROOT_ID,
                title = getString(R.string.app_name),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
            )
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = launchFuture {
            val songId = ZentryxMediaBrowseHelper.extractSongId(mediaId)
            val song = musicRepository.getSongById(songId)
            if (song != null) {
                LibraryResult.ofItem(buildBrowsableSongItem(song), null)
            } else {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = launchFuture {
            val effectiveSize = pageSize.coerceIn(1, 200)
            val items: List<MediaItem> = when {
                parentId == ZentryxMediaBrowseHelper.ROOT_ID -> {
                    val rootNodes = listOf(
                        buildFolderMediaItem(
                            ZentryxMediaBrowseHelper.RECENTLY_PLAYED_ID,
                            "Recently Played",
                            "Jump straight back into recent tracks",
                            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                        ),
                        buildFolderMediaItem(
                            ZentryxMediaBrowseHelper.MOST_PLAYED_ID,
                            "Most Played",
                            "Your top driving rotation",
                            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                        ),
                        buildFolderMediaItem(
                            ZentryxMediaBrowseHelper.RESUME_ID,
                            "Resume Sets & Podcasts",
                            "Pick up mid-track where you left off",
                            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                        ),
                        buildFolderMediaItem(
                            ZentryxMediaBrowseHelper.FAVORITES_ID,
                            "Favorites",
                            "Liked tracks",
                            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                        ),
                        buildFolderMediaItem(
                            ZentryxMediaBrowseHelper.DOWNLOADS_ID,
                            "Offline Downloads",
                            "Cached locally for tunnels & no-signal areas",
                            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                        ),
                        buildFolderMediaItem(
                            ZentryxMediaBrowseHelper.PLAYLISTS_ID,
                            "Playlists",
                            "Synced Nextcloud .m3u8 & local lists",
                            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                        ),
                        buildFolderMediaItem(
                            ZentryxMediaBrowseHelper.FOLDERS_ID,
                            "Folders",
                            "Browse WebDAV & USB folder hierarchy",
                            MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                        ),
                        buildFolderMediaItem(
                            ZentryxMediaBrowseHelper.SONGS_ID,
                            getString(R.string.tab_songs),
                            "Browse all indexed tracks",
                            MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                        ),
                        buildFolderMediaItem(
                            ZentryxMediaBrowseHelper.ALBUMS_ID,
                            getString(R.string.tab_albums),
                            "Browse by album",
                            MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                        ),
                        buildFolderMediaItem(
                            ZentryxMediaBrowseHelper.ARTISTS_ID,
                            getString(R.string.tab_artists),
                            "Browse by artist",
                            MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
                        )
                    )
                    ZentryxMediaBrowseHelper.slicePage(rootNodes, page, effectiveSize)
                }

                parentId == ZentryxMediaBrowseHelper.RECENTLY_PLAYED_ID -> {
                    val recents = musicRepository.getRecentlyPlayedSongs().firstOrNull().orEmpty()
                        .filter { playerManager.isSongPlayable(it) }
                    ZentryxMediaBrowseHelper.slicePage(recents, page, effectiveSize)
                        .map { buildBrowsableSongItem(it) }
                }

                parentId == ZentryxMediaBrowseHelper.MOST_PLAYED_ID -> {
                    val most = musicRepository.getMostPlayedSongs().firstOrNull().orEmpty()
                        .filter { playerManager.isSongPlayable(it) }
                    ZentryxMediaBrowseHelper.slicePage(most, page, effectiveSize)
                        .map { buildBrowsableSongItem(it) }
                }

                parentId == ZentryxMediaBrowseHelper.RESUME_ID -> {
                    val resumable = musicRepository.getResumableSongs().firstOrNull().orEmpty()
                        .filter { playerManager.isSongPlayable(it) }
                    ZentryxMediaBrowseHelper.slicePage(resumable, page, effectiveSize)
                        .map { buildBrowsableSongItem(it) }
                }

                parentId == ZentryxMediaBrowseHelper.PLAYLISTS_ID -> {
                    val playlists = musicRepository.getPlaylists().firstOrNull().orEmpty()
                    ZentryxMediaBrowseHelper.slicePage(playlists, page, effectiveSize).map { pl ->
                        buildFolderMediaItem(
                            mediaId = ZentryxMediaBrowseHelper.playlistNodeId(pl.id),
                            title = pl.name,
                            subtitle = "${pl.songCount} tracks",
                            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
                        )
                    }
                }

                parentId.startsWith(ZentryxMediaBrowseHelper.PLAYLIST_PREFIX) -> {
                    val plId = ZentryxMediaBrowseHelper.parsePlaylistNodeId(parentId)
                        ?: return@launchFuture LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    val songs = musicRepository.getPlaylistSongs(plId).firstOrNull().orEmpty()
                        .filter { playerManager.isSongPlayable(it) }
                    ZentryxMediaBrowseHelper.slicePage(songs, page, effectiveSize)
                        .map { buildBrowsableSongItem(it) }
                }

                parentId == ZentryxMediaBrowseHelper.FOLDERS_ID -> {
                    val paths = musicRepository.getAllRelativePaths().firstOrNull().orEmpty()
                    val topFolders = paths.mapNotNull { rel ->
                        val cleaned = rel.trimStart('/')
                        val dir = cleaned.substringBeforeLast('/', "")
                        dir.ifBlank { null }
                    }.distinct().sorted()
                    ZentryxMediaBrowseHelper.slicePage(topFolders, page, effectiveSize).map { folder ->
                        buildFolderMediaItem(
                            mediaId = ZentryxMediaBrowseHelper.folderNodeId(folder),
                            title = folder.substringAfterLast('/'),
                            subtitle = folder,
                            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                        )
                    }
                }

                parentId.startsWith(ZentryxMediaBrowseHelper.FOLDER_PREFIX) -> {
                    val folderPrefix = ZentryxMediaBrowseHelper.parseFolderNodeId(parentId)
                        ?: return@launchFuture LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    val songs = musicRepository.getSongsByFolderPrefix(folderPrefix).firstOrNull().orEmpty()
                        .filter { playerManager.isSongPlayable(it) }
                    ZentryxMediaBrowseHelper.slicePage(songs, page, effectiveSize)
                        .map { buildBrowsableSongItem(it) }
                }

                parentId == ZentryxMediaBrowseHelper.SONGS_ID -> {
                    val songs = musicRepository.getAllSongs().firstOrNull().orEmpty()
                        .filter { playerManager.isSongPlayable(it) }
                    ZentryxMediaBrowseHelper.slicePage(songs, page, effectiveSize)
                        .map { buildBrowsableSongItem(it) }
                }

                parentId == ZentryxMediaBrowseHelper.FAVORITES_ID -> {
                    val favs = musicRepository.getFavoriteSongs().firstOrNull().orEmpty()
                        .filter { playerManager.isSongPlayable(it) }
                    ZentryxMediaBrowseHelper.slicePage(favs, page, effectiveSize)
                        .map { buildBrowsableSongItem(it) }
                }

                parentId == ZentryxMediaBrowseHelper.DOWNLOADS_ID -> {
                    val downloads = musicRepository.getDownloadedSongs().firstOrNull().orEmpty()
                    ZentryxMediaBrowseHelper.slicePage(downloads, page, effectiveSize)
                        .map { buildBrowsableSongItem(it) }
                }

                parentId == ZentryxMediaBrowseHelper.ALBUMS_ID -> {
                    val albums = musicRepository.getAlbums().firstOrNull().orEmpty()
                    ZentryxMediaBrowseHelper.slicePage(albums, page, effectiveSize).map { album ->
                        buildFolderMediaItem(
                            mediaId = ZentryxMediaBrowseHelper.albumNodeId(album.artist, album.name),
                            title = album.name,
                            subtitle = "${album.artist} · ${album.songCount} tracks",
                            mediaType = MediaMetadata.MEDIA_TYPE_ALBUM
                        )
                    }
                }

                parentId == ZentryxMediaBrowseHelper.ARTISTS_ID -> {
                    val artists = musicRepository.getArtists().firstOrNull().orEmpty()
                    ZentryxMediaBrowseHelper.slicePage(artists, page, effectiveSize).map { artist ->
                        buildFolderMediaItem(
                            mediaId = ZentryxMediaBrowseHelper.artistNodeId(artist.name),
                            title = artist.name,
                            subtitle = "${artist.albumCount} albums · ${artist.songCount} tracks",
                            mediaType = MediaMetadata.MEDIA_TYPE_ARTIST
                        )
                    }
                }

                parentId.startsWith(ZentryxMediaBrowseHelper.ALBUM_PREFIX) -> {
                    val (artist, album) = ZentryxMediaBrowseHelper.parseAlbumNodeId(parentId)
                        ?: return@launchFuture LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    val songs = musicRepository.getSongsByAlbum(album, artist).firstOrNull().orEmpty()
                        .filter { playerManager.isSongPlayable(it) }
                    ZentryxMediaBrowseHelper.slicePage(songs, page, effectiveSize)
                        .map { buildBrowsableSongItem(it) }
                }

                parentId.startsWith(ZentryxMediaBrowseHelper.ARTIST_PREFIX) -> {
                    val artist = ZentryxMediaBrowseHelper.parseArtistNodeId(parentId)
                        ?: return@launchFuture LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    val songs = musicRepository.getSongsByArtist(artist).firstOrNull().orEmpty()
                        .filter { playerManager.isSongPlayable(it) }
                    ZentryxMediaBrowseHelper.slicePage(songs, page, effectiveSize)
                        .map { buildBrowsableSongItem(it) }
                }

                else -> return@launchFuture LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            }

            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = launchFuture {
            val results = musicRepository.searchSongs(query).firstOrNull().orEmpty()
                .filter { playerManager.isSongPlayable(it) }
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid(params)
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = launchFuture {
            val effectiveSize = pageSize.coerceIn(1, 100)
            val results = musicRepository.searchSongs(query).firstOrNull().orEmpty()
                .filter { playerManager.isSongPlayable(it) }
            val items = ZentryxMediaBrowseHelper.slicePage(results, page, effectiveSize)
                .map { buildBrowsableSongItem(it) }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = launchFuture {
            val resolvedSongs = mutableListOf<Song>()
            val resolvedItems = mutableListOf<MediaItem>()

            for (item in mediaItems) {
                val songId = ZentryxMediaBrowseHelper.extractSongId(item.mediaId)
                val song = musicRepository.getSongById(songId)
                if (song != null && playerManager.isSongPlayable(song)) {
                    resolvedSongs.add(song)
                    resolvedItems.add(playerManager.buildMediaItem(song))
                } else if (item.localConfiguration != null) {
                    resolvedItems.add(item)
                }
            }

            if (resolvedSongs.isNotEmpty()) {
                playerManager.syncExternalQueue(resolvedSongs, 0)
            }
            resolvedItems
        }
    }

    /**
     * Broadcasts standard Android Music intents recognized by Topway / AutoChips CAN-Bus decoders
     * to update the Mercedes-Benz W203/S203 instrument cluster Multi-Function Display (MFD).
     */
    private fun broadcastClusterMetadata(mediaItem: MediaItem?) {
        val metadata = mediaItem?.mediaMetadata ?: return
        val title = metadata.title?.toString() ?: "Unknown Title"
        val artist = metadata.artist?.toString() ?: "Unknown Artist"
        val album = metadata.albumTitle?.toString() ?: "Unknown Album"

        val intent = Intent("com.android.music.metachanged").apply {
            putExtra("id", mediaItem.mediaId)
            putExtra("artist", artist)
            putExtra("album", album)
            putExtra("track", title)
            putExtra("playing", playerManager.exoPlayer.isPlaying)
            putExtra("package", packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastPlaybackState(isPlaying: Boolean) {
        val intent = Intent("com.android.music.playstatechanged").apply {
            putExtra("playing", isPlaying)
            putExtra("package", packageName)
        }
        sendBroadcast(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSessionHolder.setSessionCompatToken(null)
        serviceScope.cancel()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }
}
