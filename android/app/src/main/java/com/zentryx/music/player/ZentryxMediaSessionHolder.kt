package com.zentryx.music.player

import android.support.v4.media.session.MediaSessionCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the active Media3 MediaLibrarySession's MediaSessionCompat.Token from
 * ZentryxMediaService to ZentryxCarSession (Android Auto CarAppLibrary MediaPlaybackManager).
 */
@Singleton
class ZentryxMediaSessionHolder @Inject constructor() {

    private val _sessionCompatToken = MutableStateFlow<MediaSessionCompat.Token?>(null)
    val sessionCompatToken: StateFlow<MediaSessionCompat.Token?> = _sessionCompatToken.asStateFlow()

    fun setSessionCompatToken(token: MediaSessionCompat.Token?) {
        _sessionCompatToken.value = token
    }
}
