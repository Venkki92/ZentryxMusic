package com.zentryx.music.models

/**
 * Supported audio formats for Nextcloud streaming.
 * Includes common extensions and standard MIME types.
 */
enum class AudioFormat(val extension: String, val mimeType: String, val displayName: String) {
    MP3("mp3", "audio/mpeg", "MP3"),
    FLAC("flac", "audio/flac", "FLAC"),
    AAC("aac", "audio/aac", "AAC"),
    M4A("m4a", "audio/mp4", "M4A"),
    OGG("ogg", "audio/ogg", "OGG Vorbis"),
    OPUS("opus", "audio/opus", "Opus"),
    WAV("wav", "audio/wav", "WAV");

    companion object {
        private val extensionMap = entries.associateBy { it.extension.lowercase() }

        fun fromExtension(ext: String?): AudioFormat? {
            if (ext.isNullOrBlank()) return null
            return extensionMap[ext.lowercase().removePrefix(".")]
        }

        fun isSupported(fileName: String): Boolean {
            val ext = fileName.substringAfterLast('.', "")
            return extensionMap.containsKey(ext.lowercase())
        }
    }
}
