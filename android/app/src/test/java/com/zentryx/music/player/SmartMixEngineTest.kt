package com.zentryx.music.player

import com.zentryx.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartMixEngineTest {

    private fun song(
        id: String,
        artist: String = "Artist",
        genre: String? = null,
        relativePath: String = "Music/$id.mp3",
        isFavorite: Boolean = false,
        playCount: Int = 0,
        lastPlayedAt: Long = 0L
    ) = Song(
        id = id,
        title = "Track $id",
        artist = artist,
        album = "Album",
        durationMs = 210_000L,
        fileUrl = "https://cloud.example/$relativePath",
        relativePath = relativePath,
        mimeType = "audio/mpeg",
        genre = genre,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt
    )

    @Test
    fun resolveSongGenre_prioritizesTagThenMeaningfulFolderThenUnclassified() {
        assertEquals("Synthwave", SmartMixEngine.resolveSongGenre(song("1", genre = "synthwave", relativePath = "Music/Rock/a.mp3")))
        assertEquals("EDM", SmartMixEngine.resolveSongGenre(song("2", genre = "EDM", relativePath = "Music/Local/a.mp3")))
        assertEquals("DKTN", SmartMixEngine.resolveSongGenre(song("3", genre = "Unknown", relativePath = "Music/WoW Radio/DKTN/track.mp3")))
        assertEquals("Deep House", SmartMixEngine.resolveSongGenre(song("4", genre = "", relativePath = "Music/deep_house/track.mp3")))
        assertEquals("Unclassified", SmartMixEngine.resolveSongGenre(song("5", genre = "Other", relativePath = "Music/Local/Downloads/track.mp3")))
    }

    @Test
    fun sortAndShuffleSongsByGenre_keepsGenresAlphabeticalWithUnclassifiedLast() {
        val library = listOf(
            song("r1", artist = "Muse", genre = "Rock"),
            song("r2", artist = "Muse", genre = "Rock"),
            song("r3", artist = "Arctic Monkeys", genre = "Rock"),
            song("a1", artist = "Tycho", genre = "Ambient"),
            song("a2", artist = "Brian Eno", genre = "Ambient"),
            song("u1", artist = "Mystery", relativePath = "Music/Local/u1.mp3"),
            song("e1", artist = "Daft Punk", genre = "Electronic"),
            song("e2", artist = "Justice", genre = "Electronic")
        )

        val passA = SmartMixEngine.sortAndShuffleSongsByGenre(library, Random(11))
        val passB = SmartMixEngine.sortAndShuffleSongsByGenre(library, Random(99))

        val expectedGenres = listOf("Ambient", "Ambient", "Electronic", "Electronic", "Rock", "Rock", "Rock", "Unclassified")
        assertEquals(expectedGenres, passA.map { SmartMixEngine.resolveSongGenre(it) })
        assertEquals(expectedGenres, passB.map { SmartMixEngine.resolveSongGenre(it) })
        assertNotEquals(passA.map { it.id }, passB.map { it.id })

        val rockBlock = passA.subList(4, 7)
        assertTrue(rockBlock[0].artist != rockBlock[1].artist || rockBlock[1].artist != rockBlock[2].artist)
    }

    @Test
    fun buildSmartMixSongs_supportsCruisingReshuffleFavoritesAndDeepCuts() {
        val library = listOf(
            song("t1", artist = "Artist A", genre = "House", isFavorite = true, playCount = 12, lastPlayedAt = 500L),
            song("t2", artist = "Artist B", genre = "Ambient", playCount = 8, lastPlayedAt = 400L),
            song("t3", artist = "Artist C", genre = "Techno", isFavorite = true),
            song("t4", artist = "Artist D", genre = "Ambient", isFavorite = true),
            song("t5", artist = "Artist E", genre = "House"),
            song("t6", artist = "Artist F", relativePath = "Music/t6.mp3")
        )

        val summary = SmartMixEngine.summarizeMixGenres(library)
        assertEquals(4, summary.genreCount)
        assertEquals(
            listOf(
                GenreCount("Ambient", 2),
                GenreCount("House", 2),
                GenreCount("Techno", 1),
                GenreCount("Unclassified", 1)
            ),
            summary.genres
        )

        val cruising = SmartMixEngine.buildSmartMixSongs(
            songs = library,
            mixType = SmartMixType.CRUISING,
            limit = library.size,
            reshuffle = true,
            sortByGenre = true,
            random = Random(42)
        )
        assertEquals(library.size, cruising.size)
        assertEquals(
            listOf("Ambient", "Ambient", "House", "House", "Techno", "Unclassified"),
            cruising.map { SmartMixEngine.resolveSongGenre(it) }
        )

        val favRadio = SmartMixEngine.buildSmartMixSongs(
            songs = library,
            mixType = SmartMixType.FAVORITES_RADIO,
            limit = 10,
            reshuffle = true,
            sortByGenre = true,
            random = Random(7)
        )
        assertEquals(listOf("t4", "t1", "t3"), favRadio.map { it.id })

        val deepCuts = SmartMixEngine.buildSmartMixSongs(
            songs = library,
            mixType = SmartMixType.DEEP_CUTS,
            limit = 3,
            reshuffle = false,
            sortByGenre = false
        )
        assertEquals(3, deepCuts.size)
        assertTrue(deepCuts.all { it.playCount == 0 })
    }
}
