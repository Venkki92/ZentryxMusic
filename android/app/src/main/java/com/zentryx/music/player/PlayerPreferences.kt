package com.zentryx.music.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playerDataStore by preferencesDataStore(name = "zentryx_player_prefs")

/**
 * Persists and restores playback state across app and vehicle ignition restarts.
 * Remembers the last played track ID, seek position, shuffle, and repeat modes.
 */
@Singleton
class PlayerPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_LAST_SONG_ID = stringPreferencesKey("last_song_id")
        private val KEY_LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        private val KEY_REPEAT_MODE = stringPreferencesKey("repeat_mode")
        private val KEY_SHUFFLE_ENABLED = stringPreferencesKey("shuffle_enabled")
    }

    suspend fun savePlaybackState(songId: String?, positionMs: Long) {
        context.playerDataStore.edit { prefs ->
            if (songId != null) {
                prefs[KEY_LAST_SONG_ID] = songId
                prefs[KEY_LAST_POSITION_MS] = positionMs
            }
        }
    }

    suspend fun getLastPlaybackState(): Pair<String?, Long> {
        val prefs = context.playerDataStore.data.firstOrNull() ?: return Pair(null, 0L)
        val songId = prefs[KEY_LAST_SONG_ID]
        val position = prefs[KEY_LAST_POSITION_MS] ?: 0L
        return Pair(songId, position)
    }

    suspend fun savePlayerModes(repeatMode: String, shuffle: Boolean) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_REPEAT_MODE] = repeatMode
            prefs[KEY_SHUFFLE_ENABLED] = shuffle.toString()
        }
    }
}
