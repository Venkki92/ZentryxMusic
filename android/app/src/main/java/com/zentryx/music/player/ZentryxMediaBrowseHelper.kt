package com.zentryx.music.player

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Pure helper utilities for Media3 MediaLibraryService browse tree IDs, paging,
 * and Android Auto distraction-list item limits.
 */
object ZentryxMediaBrowseHelper {
    const val ROOT_ID = "zentryx_root"
    const val SONGS_ID = "zentryx_songs"
    const val ALBUMS_ID = "zentryx_albums"
    const val ARTISTS_ID = "zentryx_artists"
    const val FAVORITES_ID = "zentryx_favorites"
    const val DOWNLOADS_ID = "zentryx_downloads"
    const val RECENTLY_PLAYED_ID = "zentryx_recently_played"
    const val MOST_PLAYED_ID = "zentryx_most_played"
    const val RESUME_ID = "zentryx_resume"
    const val FOLDERS_ID = "zentryx_folders"
    const val PLAYLISTS_ID = "zentryx_playlists"

    const val SONG_PREFIX = "song:"
    const val ALBUM_PREFIX = "album:"
    const val ARTIST_PREFIX = "artist:"
    const val FOLDER_PREFIX = "folder:"
    const val PLAYLIST_PREFIX = "playlist:"

    const val DEFAULT_CAR_LIST_LIMIT = 50
    const val MIN_CAR_LIST_LIMIT = 6

    fun songMediaId(songId: String): String =
        if (songId.startsWith(SONG_PREFIX)) songId else "$SONG_PREFIX$songId"

    fun extractSongId(mediaId: String): String =
        if (mediaId.startsWith(SONG_PREFIX)) mediaId.removePrefix(SONG_PREFIX) else mediaId

    fun albumNodeId(artist: String, album: String): String {
        val encArtist = URLEncoder.encode(artist, StandardCharsets.UTF_8.name())
        val encAlbum = URLEncoder.encode(album, StandardCharsets.UTF_8.name())
        return "$ALBUM_PREFIX$encArtist|$encAlbum"
    }

    fun parseAlbumNodeId(nodeId: String): Pair<String, String>? {
        if (!nodeId.startsWith(ALBUM_PREFIX)) return null
        val payload = nodeId.removePrefix(ALBUM_PREFIX)
        val sep = payload.indexOf('|')
        if (sep <= 0 && sep != 0) return null
        val encArtist = payload.substring(0, sep)
        val encAlbum = payload.substring(sep + 1)
        return try {
            URLDecoder.decode(encArtist, StandardCharsets.UTF_8.name()) to
                URLDecoder.decode(encAlbum, StandardCharsets.UTF_8.name())
        } catch (_: Throwable) {
            null
        }
    }

    fun artistNodeId(artist: String): String {
        val encArtist = URLEncoder.encode(artist, StandardCharsets.UTF_8.name())
        return "$ARTIST_PREFIX$encArtist"
    }

    fun parseArtistNodeId(nodeId: String): String? {
        if (!nodeId.startsWith(ARTIST_PREFIX)) return null
        val encArtist = nodeId.removePrefix(ARTIST_PREFIX)
        return try {
            URLDecoder.decode(encArtist, StandardCharsets.UTF_8.name())
        } catch (_: Throwable) {
            null
        }
    }

    fun folderNodeId(folderPath: String): String =
        "$FOLDER_PREFIX${URLEncoder.encode(folderPath, StandardCharsets.UTF_8.name())}"

    fun parseFolderNodeId(nodeId: String): String? {
        if (!nodeId.startsWith(FOLDER_PREFIX)) return null
        return runCatching {
            URLDecoder.decode(nodeId.removePrefix(FOLDER_PREFIX), StandardCharsets.UTF_8.name())
        }.getOrNull()
    }

    fun playlistNodeId(playlistId: String): String = "$PLAYLIST_PREFIX$playlistId"

    fun parsePlaylistNodeId(nodeId: String): String? =
        if (nodeId.startsWith(PLAYLIST_PREFIX)) nodeId.removePrefix(PLAYLIST_PREFIX) else null

    fun <T> slicePage(items: List<T>, page: Int, pageSize: Int): List<T> {
        if (items.isEmpty() || page < 0 || pageSize <= 0) return emptyList()
        val fromIndex = page.toLong() * pageSize.toLong()
        if (fromIndex >= items.size) return emptyList()
        val toIndex = (fromIndex + pageSize.toLong()).coerceAtMost(items.size.toLong()).toInt()
        return items.subList(fromIndex.toInt(), toIndex)
    }

    fun <T> capForCarList(items: List<T>, hostContentLimit: Int): List<T> {
        val safeCap = hostContentLimit
            .takeIf { it > 0 }
            ?.coerceIn(MIN_CAR_LIST_LIMIT, DEFAULT_CAR_LIST_LIMIT)
            ?: MIN_CAR_LIST_LIMIT
        return if (items.size <= safeCap) items else items.subList(0, safeCap)
    }

    fun formatDurationMs(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format("%d:%02d", minutes, seconds)
    }
}
