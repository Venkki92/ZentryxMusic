package com.zentryx.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import com.zentryx.music.R
import com.zentryx.music.models.PlaybackState
import com.zentryx.music.player.ZentryxPlayerManager
import com.zentryx.music.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

/**
 * 4x2 Automotive Headunit Quick-Dock Home Screen Widget.
 * Gives direct one-tap access to Prev / Play-Pause / Next / -20dB Talk Attenuation
 * on the headunit launcher dashboard without opening the full app.
 */
@AndroidEntryPoint
class ZentryxWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var playerManager: ZentryxPlayerManager

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.zentryx.music.widget.ACTION_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.zentryx.music.widget.ACTION_NEXT"
        const val ACTION_WIDGET_PREV = "com.zentryx.music.widget.ACTION_PREV"
        const val ACTION_WIDGET_DUCK = "com.zentryx.music.widget.ACTION_DUCK"

        fun updateWidgetFromState(
            context: Context,
            playbackState: PlaybackState,
            isAttenuated: Boolean
        ) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, ZentryxWidgetProvider::class.java)
                val widgetIds = manager.getAppWidgetIds(component)
                if (widgetIds.isEmpty()) return

                val views = buildRemoteViews(context, playbackState, isAttenuated)
                manager.updateAppWidget(widgetIds, views)
            } catch (_: Throwable) {
                // Safe guard if launcher has no widget host
            }
        }

        private fun buildRemoteViews(
            context: Context,
            state: PlaybackState,
            isAttenuated: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_zentryx_player)
            val song = state.currentSong

            views.setTextViewText(
                R.id.widget_title,
                song?.title ?: "Zentryx Music"
            )
            views.setTextViewText(
                R.id.widget_artist,
                if (song != null) "${song.artist} · ${song.album}" else "Tap to open cockpit library"
            )
            views.setTextViewText(
                R.id.widget_brand,
                state.signalPathBadge ?: "ZENTRYX MBUX AUDIO"
            )

            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (state.isPlaying) R.drawable.ic_car_pause else R.drawable.ic_car_play
            )

            // Artwork bitmap if cached locally on filesystem
            val artPath = song?.artworkPath
            if (artPath != null && File(artPath).exists()) {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    val bmp = BitmapFactory.decodeFile(artPath, opts)
                    if (bmp != null) {
                        views.setImageViewBitmap(R.id.widget_artwork, bmp)
                    } else {
                        views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_car_music)
                    }
                } catch (_: Throwable) {
                    views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_car_music)
                }
            } else {
                views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_car_music)
            }

            // Highlight -20dB Duck button when attenuated
            views.setTextViewText(
                R.id.widget_btn_duck,
                if (isAttenuated) "DUCK ON" else "-20dB"
            )
            views.setInt(
                R.id.widget_btn_duck,
                "setBackgroundColor",
                if (isAttenuated) Color.parseColor("#78350F") else Color.parseColor("#151E32")
            )

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPi = PendingIntent.getActivity(
                context,
                100,
                launchIntent,
                pendingIntentFlags()
            )
            views.setOnClickPendingIntent(R.id.widget_artwork, openAppPi)
            views.setOnClickPendingIntent(R.id.widget_title, openAppPi)

            views.setOnClickPendingIntent(
                R.id.widget_btn_prev,
                actionPendingIntent(context, ACTION_WIDGET_PREV, 101)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_play_pause,
                actionPendingIntent(context, ACTION_WIDGET_PLAY_PAUSE, 102)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_next,
                actionPendingIntent(context, ACTION_WIDGET_NEXT, 103)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_duck,
                actionPendingIntent(context, ACTION_WIDGET_DUCK, 104)
            )

            return views
        }

        private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, ZentryxWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(context, requestCode, intent, pendingIntentFlags())
        }

        private fun pendingIntentFlags(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgetFromState(
            context,
            playerManager.playbackState.value,
            playerManager.isAttenuated.value
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_PLAY_PAUSE -> playerManager.togglePlayPause()
            ACTION_WIDGET_NEXT -> playerManager.skipToNext()
            ACTION_WIDGET_PREV -> playerManager.skipToPrevious()
            ACTION_WIDGET_DUCK -> playerManager.toggleAttenuation()
        }
        if (intent.action != null && intent.action?.startsWith("com.zentryx.music.widget.") == true) {
            updateWidgetFromState(
                context,
                playerManager.playbackState.value,
                playerManager.isAttenuated.value
            )
        }
    }
}
