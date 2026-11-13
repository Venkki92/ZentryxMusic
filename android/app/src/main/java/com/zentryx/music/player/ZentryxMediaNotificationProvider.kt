package com.zentryx.music.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.zentryx.music.R
import com.zentryx.music.ui.MainActivity

/**
 * Custom Media3 notification provider tailored for automotive Android headunits.
 * Ensures large touch targets in the notification shade and guarantees Android 9 (API 28)
 * notification channel compatibility.
 */
@OptIn(UnstableApi::class)
class ZentryxMediaNotificationProvider(
    private val context: Context
) : MediaNotification.Provider {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "zentryx_media_playback_channel"
        const val NOTIFICATION_ID = 1001
    }

    private val defaultProvider = DefaultMediaNotificationProvider.Builder(context)
        .setChannelId(NOTIFICATION_CHANNEL_ID)
        .setChannelName(R.string.app_name)
        .build()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existingChannel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Audio Playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Music playback controls for Zentryx Music"
                    setShowBadge(false)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val notification = defaultProvider.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            onNotificationChangedCallback
        )

        // Attach intent to open MainActivity when clicking notification on headunit
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        notification.notification.contentIntent = contentIntent
        return notification
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: android.os.Bundle
    ): Boolean = false
}
