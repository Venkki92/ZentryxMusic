package com.zentryx.music.player

import com.zentryx.music.models.Song
import java.net.URLDecoder
import java.util.Locale
import kotlin.random.Random

enum class SmartMixType(val label: String) {
    CRUISING("Cruising Mix"),
    DEEP_CUTS("Deep Cuts"),
    FAVORITES_RADIO("Favorites Radio"),
    RECENT("Recently Played"),
    MOST_PLAYED("Most Played")
}

data class GenreCount(
    val genre: String,
    val count: Int
)

data class MixGenreSummary(
    val genreCount: Int,
    val genres: List<GenreCount>
)

/**
 * Shared Smart Mix & Genre-Sorted Shuffle engine mirroring Web carConvenience.ts:
 * - Resolves clean display genres from ID3/metadata tags with smart folder-segment fallback.
 * - Keeps tracks grouped and sorted A-Z by genre ("Unclassified" last) while shuffling
 *   and de-clustering consecutive identical artists inside each genre block.
 */
object SmartMixEngine {
    const val UNCLASSIFIED_GENRE = "Unclassified"

    private val GENERIC_GENRE_VALUES = setOf(
        "",
        "unknown",
        "unknown genre",
        "other",
        "none",
        "misc",
        "miscellaneous",
        "undefined",
        "null",
        "n/a"
    )

    private val GENERIC_FOLDER_SEGMENTS = setOf(
        "",
        "music",
        "audio",
        "tracks",
        "songs",
        "local",
        "downloads",
        "download",
        "root",
        "webdav",
        "remote.php",
        "files",
        "library",
        "media"
    )

    private fun toDisplayGenre(raw: String): String {
        val cleaned = raw
            .replace(Regex("[_-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return UNCLASSIFIED_GENRE
        return cleaned.split(' ').joinToString(" ") { word ->
            if (word.length <= 4 && word == word.uppercase(Locale.US)) {
                word
            } else {
                word.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
            }
        }
    }

    fun resolveSongGenre(song: Song): String {
        val rawGenre = song.genre?.trim().orEmpty()
        if (rawGenre.isNotEmpty() && rawGenre.lowercase(Locale.US) !in GENERIC_GENRE_VALUES) {
            return toDisplayGenre(rawGenre)
        }

        val relPath = song.relativePath.trim()
        if (relPath.isNotEmpty()) {
            val segments = relPath
                .split('/')
                .dropLast(1) // Drop filename; inspect folder segments only
                .map { seg ->
                    runCatching { URLDecoder.decode(seg, "UTF-8").trim() }.getOrElse { seg.trim() }
                }
                .filter { it.isNotEmpty() }

            for (i in segments.indices.reversed()) {
                val seg = segments[i]
                if (seg.lowercase(Locale.US) !in GENERIC_FOLDER_SEGMENTS) {
                    return toDisplayGenre(seg)
                }
            }
        }

        return UNCLASSIFIED_GENRE
    }

    private fun declusterByArtist(items: List<Song>, limit: Int): List<Song> {
        if (items.isEmpty() || limit <= 0) return emptyList()
        val pool = items.toMutableList()
        val result = ArrayList<Song>(minOf(items.size, limit))
        var lastArtist = ""

        while (pool.isNotEmpty() && result.size < limit) {
            val pickIdx = pool.indexOfFirst { !it.artist.equals(lastArtist, ignoreCase = true) }
            val picked = pool.removeAt(if (pickIdx >= 0) pickIdx else 0)
            result.add(picked)
            lastArtist = picked.artist
        }
        return result
    }

    private fun sortGenreKeys(keys: Collection<String>): List<String> {
        return keys.sortedWith(
            compareBy<String> { if (it == UNCLASSIFIED_GENRE) 1 else 0 }
                .thenBy { it.lowercase(Locale.US) }
        )
    }

    /**
     * Groups songs alphabetically by genre ("Unclassified" last) and shuffles cleanly
     * inside each genre block while avoiding consecutive identical artists.
     */
    fun sortAndShuffleSongsByGenre(
        songs: List<Song>,
        random: Random = Random.Default
    ): List<Song> {
        if (songs.size <= 1) return songs.toList()
        val grouped = songs.groupBy { resolveSongGenre(it) }
        val sortedGenres = sortGenreKeys(grouped.keys)
        return sortedGenres.flatMap { genre ->
            val bucket = grouped[genre].orEmpty().shuffled(random)
            declusterByArtist(bucket, bucket.size)
        }
    }

    fun summarizeMixGenres(songs: List<Song>): MixGenreSummary {
        val counts = LinkedHashMap<String, Int>()
        for (song in songs) {
            val genre = resolveSongGenre(song)
            counts[genre] = (counts[genre] ?: 0) + 1
        }
        val sorted = sortGenreKeys(counts.keys).map { g -> GenreCount(g, counts[g] ?: 0) }
        return MixGenreSummary(genreCount = sorted.size, genres = sorted)
    }

    fun buildSmartMixSongs(
        songs: List<Song>,
        mixType: SmartMixType,
        limit: Int = 40,
        reshuffle: Boolean = false,
        sortByGenre: Boolean = false,
        random: Random = Random.Default
    ): List<Song> {
        if (songs.isEmpty() || limit <= 0) return emptyList()

        return when (mixType) {
            SmartMixType.RECENT -> songs
                .filter { it.lastPlayedAt > 0L }
                .sortedByDescending { it.lastPlayedAt }
                .take(limit)

            SmartMixType.MOST_PLAYED -> songs
                .filter { it.playCount > 0 }
                .sortedWith(
                    compareByDescending<Song> { it.playCount }
                        .thenByDescending { it.lastPlayedAt }
                )
                .take(limit)

            SmartMixType.FAVORITES_RADIO -> {
                val favs = songs.filter { it.isFavorite }
                if (favs.isEmpty()) return emptyList()
                if (sortByGenre) {
                    sortAndShuffleSongsByGenre(favs, random).take(limit)
                } else {
                    val ordered = if (reshuffle) favs.shuffled(random) else favs
                    declusterByArtist(ordered, limit)
                }
            }

            SmartMixType.DEEP_CUTS -> {
                val byLeastPlayed = songs.sortedWith(
                    compareBy<Song> { it.playCount }
                        .thenBy { it.lastPlayedAt }
                )
                val poolSize = minOf(songs.size, maxOf(limit * 2, limit))
                val candidatePool = byLeastPlayed.take(poolSize)
                if (sortByGenre) {
                    val picked = candidatePool.shuffled(random).take(limit)
                    sortAndShuffleSongsByGenre(picked, random)
                } else {
                    val ordered = if (reshuffle) candidatePool.shuffled(random) else candidatePool
                    declusterByArtist(ordered, limit)
                }
            }

            SmartMixType.CRUISING -> {
                if (reshuffle) {
                    val pool = if (songs.size <= limit) songs else songs.shuffled(random).take(limit)
                    if (sortByGenre) {
                        sortAndShuffleSongsByGenre(pool, random)
                    } else {
                        declusterByArtist(pool.shuffled(random), limit)
                    }
                } else {
                    val scored = songs.sortedByDescending { song ->
                        (if (song.isFavorite) 50 else 0) +
                            minOf(30, song.playCount * 5) +
                            ((song.id.firstOrNull()?.code ?: 0) % 11)
                    }
                    val selected = declusterByArtist(scored, limit)
                    if (sortByGenre) {
                        val grouped = selected.groupBy { resolveSongGenre(it) }
                        val sortedGenres = sortGenreKeys(grouped.keys)
                        sortedGenres.flatMap { genre ->
                            val bucket = grouped[genre].orEmpty()
                            declusterByArtist(bucket, bucket.size)
                        }
                    } else {
                        selected
                    }
                }
            }
        }
    }
}
