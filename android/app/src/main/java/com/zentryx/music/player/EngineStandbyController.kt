package com.zentryx.music.player

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature 7: Engine Standby & Fade Transition for Mercedes-Benz S203 C220 CDI.
 * Handles ACC ignition power cuts, deep sleep, and starter cranking voltage drops.
 * Prevents speaker pops by fading volume to 0 over 800ms on shutdown,
 * and waits 1500ms after ignition ON for alternator stabilization before smooth fade-in.
 */
@Singleton
class EngineStandbyController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPreferences: SettingsPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var exoPlayer: ExoPlayer? = null
    private var isIgnitionFadeEnabled = true
    private var originalVolume = 1.0f
    private var fadeAnimator: ValueAnimator? = null

    private val ignitionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SHUTDOWN,
                "com.microntek.bootcheck",
                "com.forfan.acc_off",
                "com.topway.acc_off" -> {
                    onIgnitionOff()
                }
                Intent.ACTION_SCREEN_ON,
                "com.forfan.acc_on",
                "com.topway.acc_on" -> {
                    onIgnitionOn()
                }
            }
        }
    }

    init {
        scope.launch {
            settingsPreferences.ignitionFadeEnabledFlow.collectLatest { enabled ->
                isIgnitionFadeEnabled = enabled
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SHUTDOWN)
            addAction("com.microntek.bootcheck")
            addAction("com.forfan.acc_off")
            addAction("com.forfan.acc_on")
            addAction("com.topway.acc_off")
            addAction("com.topway.acc_on")
        }
        try {
            context.registerReceiver(ignitionReceiver, filter)
        } catch (e: Exception) {
            // Context register fallback
        }
    }

    fun attachPlayer(player: ExoPlayer) {
        this.exoPlayer = player
        this.originalVolume = player.volume
    }

    fun onIgnitionOff() {
        if (!isIgnitionFadeEnabled) {
            exoPlayer?.pause()
            return
        }

        val player = exoPlayer ?: return
        if (!player.isPlaying) return

        fadeAnimator?.cancel()
        originalVolume = player.volume

        fadeAnimator = ValueAnimator.ofFloat(originalVolume, 0f).apply {
            duration = 800L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                player.volume = anim.animatedValue as Float
            }
            start()
        }

        scope.launch {
            delay(850L)
            player.pause()
            player.volume = originalVolume
        }
    }

    fun onIgnitionOn() {
        if (!isIgnitionFadeEnabled) return
        val player = exoPlayer ?: return

        scope.launch {
            // Wait 1.5s for Mercedes OM646 starter crank & alternator voltage to stabilize
            delay(1500L)
            player.volume = 0f
            player.play()

            fadeAnimator?.cancel()
            fadeAnimator = ValueAnimator.ofFloat(0f, originalVolume).apply {
                duration = 1200L
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    player.volume = anim.animatedValue as Float
                }
                start()
            }
        }
    }
}
