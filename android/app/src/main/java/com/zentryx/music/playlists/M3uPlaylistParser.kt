package com.zentryx.music.playlists

import com.zentryx.music.models.Song

/**
 * Parses and serializes UTF-8 Extended M3U (.m3u8) playlists compatible with
 * Nextcloud Music, VLC, and standard car headunit playlist files.
 */
object M3uPlaylistParser {

    fun serializeM3u8(playlistName: String, songs: List<Song>): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#PLAYLIST:").append(playlistName.replace("\n", " ").trim()).append('\n')
        for (song in songs) {
            val sec = (song.durationMs / 1000L).coerceAtLeast(0L)
            sb.append("#EXTINF:").append(sec).append(',')
                .append(song.artist).append(" - ").append(song.title).append('\n')
            sb.append(song.relativePath).append('\n')
        }
        return sb.toString()
    }

    fun parseM3u8Paths(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        val paths = mutableListOf<String>()
        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            paths.add(line.trimStart('/'))
        }
        return paths
    }
}
