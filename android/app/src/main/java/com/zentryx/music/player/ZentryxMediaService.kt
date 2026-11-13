package com.zentryx.music.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zentryx.music.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Android Media3 MediaSessionService managing foreground audio streaming, system notifications,
 * lock screen controls, Bluetooth AVRCP commands, and automotive CAN-Bus steering wheel controls.
 *
 * Specific optimizations for AutoChips AC8259P / Topway TSKJ.G5 headunit in Mercedes-Benz S203:
 * 1. Broadcasts track metadata intents (com.android.music.metachanged) to feed the S203 instrument cluster MFD.
 * 2. Remains alive in the foreground during navigation or reverse-gear camera display.
 * 3. Gracefully responds to media key events forwarded from the CAN-Bus decoder box.
 */
@AndroidEntryPoint
class ZentryxMediaService : MediaSessionService() {

    @Inject
    lateinit var playerManager: ZentryxPlayerManager

    private var mediaSession: MediaSession? = null

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

        val sessionCallback = object : MediaSession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> {
                return com.google.common.util.concurrent.Futures.immediateFuture(mediaItems)
            }
        }

        mediaSession = MediaSession.Builder(this, playerManager.exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(sessionCallback)
            .setId("ZentryxMediaSession")
            .build()

        // Listen for track transitions to broadcast metadata to the vehicle's CAN-Bus & MFD cluster
        playerManager.exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                broadcastClusterMetadata(mediaItem)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                broadcastPlaybackState(isPlaying)
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
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
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
