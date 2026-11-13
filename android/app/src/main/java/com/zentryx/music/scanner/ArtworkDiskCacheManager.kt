package com.zentryx.music.scanner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages caching of extracted embedded album artwork to internal disk storage.
 * Deduplicates art files by album and artist name so identical cover art across
 * multiple tracks in the same album is saved only once.
 * Ensures Room stores strictly file paths, keeping SQLite memory light.
 */
@Singleton
class ArtworkDiskCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val artDir: File by lazy {
        File(context.filesDir, "album_art").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Saves raw image bytes to disk and returns the absolute local file path string.
     * Returns existing file path if this album's art was already cached.
     */
    fun saveArtwork(artist: String, album: String, rawBytes: ByteArray): String? {
        if (rawBytes.isEmpty()) return null

        val key = "${artist.trim().lowercase()}_${album.trim().lowercase()}"
        val hash = md5(key)
        val file = File(artDir, "art_$hash.jpg")

        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }

        return try {
            FileOutputStream(file).use { out ->
                out.write(rawBytes)
                out.flush()
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if artwork for this album is already cached on disk.
     */
    fun getExistingArtworkPath(artist: String, album: String): String? {
        val key = "${artist.trim().lowercase()}_${album.trim().lowercase()}"
        val hash = md5(key)
        val file = File(artDir, "art_$hash.jpg")
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
