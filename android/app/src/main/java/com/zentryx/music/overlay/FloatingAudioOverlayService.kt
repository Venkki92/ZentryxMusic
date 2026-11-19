package com.zentryx.music.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.player.ZentryxPlayerManager
import com.zentryx.music.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * Draggable Floating Translucent Cockpit Audio Bar (SYSTEM_ALERT_WINDOW)
 * designed to float cleanly over Google Maps / Waze on Android headunits.
 */
@AndroidEntryPoint
class FloatingAudioOverlayService : Service() {

    companion object {
        fun startOverlay(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                return false
            }
            return runCatching {
                context.startService(Intent(context, FloatingAudioOverlayService::class.java))
                true
            }.getOrDefault(false)
        }

        fun stopOverlay(context: Context) {
            runCatching {
                context.stopService(Intent(context, FloatingAudioOverlayService::class.java))
            }
        }
    }

    @Inject
    lateinit var playerManager: ZentryxPlayerManager

    @Inject
    lateinit var settingsPreferences: SettingsPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var titleText: TextView? = null
    private var subtitleText: TextView? = null
    private var playPauseBtn: TextView? = null
    private var duckBtn: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlayView == null) {
            attachFloatingBar()
        }
        return START_STICKY
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun attachFloatingBar() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(48)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(228, 10, 14, 24))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.argb(170, 163, 230, 53))
            }
            elevation = dp(10).toFloat()
        }

        val metaColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(170), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(8)
            }
        }

        val title = TextView(this).apply {
            text = "Zentryx Music"
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "Tap to open cockpit"
            setTextColor(Color.rgb(163, 230, 53))
            textSize = 10f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        metaColumn.addView(title)
        metaColumn.addView(subtitle)
        metaColumn.setOnClickListener {
            val launch = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(launch)
        }

        fun makeActionChip(label: String, textColor: Int = Color.WHITE, bgColor: Int = Color.argb(180, 30, 41, 59), onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                setTextColor(textColor)
                textSize = 13f
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(bgColor)
                    cornerRadius = dp(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(38)).apply {
                    marginEnd = dp(6)
                }
                setOnClickListener { onClick() }
            }
        }

        val prevBtn = makeActionChip("⏮") { playerManager.skipToPrevious() }
        val playBtn = makeActionChip("▶", Color.BLACK, Color.rgb(163, 230, 53)) { playerManager.togglePlayPause() }
        val nextBtn = makeActionChip("⏭") { playerManager.skipToNext() }
        val talkDuck = makeActionChip("-20", Color.rgb(245, 158, 11)) { playerManager.toggleAttenuation() }
        val closeBtn = makeActionChip("✕", Color.rgb(248, 113, 113), Color.argb(150, 69, 10, 10)) {
            serviceScope.launch { settingsPreferences.setFloatingOverlayEnabled(false) }
            stopSelf()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = 0
        }

        container.addView(metaColumn)
        container.addView(prevBtn)
        container.addView(playBtn)
        container.addView(nextBtn)
        container.addView(talkDuck)
        container.addView(closeBtn)

        // Smooth drag-to-reposition anywhere on the headunit screen
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragging = false
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) {
                        dragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        runCatching { wm.updateViewLayout(container, params) }
                    }
                    dragging
                }
                else -> false
            }
        }

        titleText = title
        subtitleText = subtitle
        playPauseBtn = playBtn
        duckBtn = talkDuck
        overlayView = container

        runCatching { wm.addView(container, params) }

        serviceScope.launch {
            playerManager.playbackState.collectLatest { state ->
                val song = state.currentSong
                titleText?.text = song?.title ?: "Zentryx Music"
                subtitleText?.text = song?.artist ?: "Ready to Drive"
                playPauseBtn?.text = if (state.isPlaying) "⏸" else "▶"
            }
        }
        serviceScope.launch {
            playerManager.isAttenuated.collectLatest { ducked ->
                duckBtn?.alpha = if (ducked) 1.0f else 0.72f
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
        super.onDestroy()
    }
}
