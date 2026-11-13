package com.zentryx.music.swc

import android.view.KeyEvent
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.player.ZentryxPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class SwcAction(val displayName: String) {
    NEXT_TRACK("Next Track"),
    PREV_TRACK("Previous Track"),
    PLAY_PAUSE("Play / Pause"),
    SEEK_FORWARD_15("Seek +15 Seconds"),
    SEEK_BACKWARD_15("Seek -15 Seconds"),
    TOGGLE_FAVORITE("Toggle Favorite (Heart)"),
    NEXT_ALBUM("Next Album / Folder"),
    MUTE("Mute Audio")
}

/**
 * Feature 4: Steering Wheel Controls (CAN-Bus SWC) for Mercedes-Benz W203 / S203.
 * Distinguishes single-tap, double-tap, and long-press from steering wheel buttons (KEY1/KEY2).
 */
@Singleton
class SteeringWheelManager @Inject constructor(
    private val settingsPreferences: SettingsPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var playerManager: ZentryxPlayerManager? = null

    private var longPressAction = SwcAction.SEEK_FORWARD_15
    private var doublePressAction = SwcAction.TOGGLE_FAVORITE

    private var keyDownTimestamp = 0L
    private var pendingClickJob: Job? = null
    private var lastClickTimestamp = 0L
    private var isLongPressTriggered = false

    init {
        scope.launch {
            settingsPreferences.swcLongPressActionFlow.collectLatest { actionName ->
                longPressAction = try {
                    SwcAction.valueOf(actionName)
                } catch (e: Exception) {
                    SwcAction.SEEK_FORWARD_15
                }
            }
        }

        scope.launch {
            settingsPreferences.swcDoublePressActionFlow.collectLatest { actionName ->
                doublePressAction = try {
                    SwcAction.valueOf(actionName)
                } catch (e: Exception) {
                    SwcAction.TOGGLE_FAVORITE
                }
            }
        }
    }

    fun attachPlayerManager(manager: ZentryxPlayerManager) {
        this.playerManager = manager
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (event?.repeatCount == 0) {
                    keyDownTimestamp = System.currentTimeMillis()
                    isLongPressTriggered = false

                    // Schedule long-press check at 600ms
                    scope.launch {
                        delay(600L)
                        if (keyDownTimestamp > 0 && !isLongPressTriggered) {
                            isLongPressTriggered = true
                            executeAction(longPressAction)
                        }
                    }
                }
                return true
            }
        }
        return false
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                val pressDuration = System.currentTimeMillis() - keyDownTimestamp
                keyDownTimestamp = 0L

                if (isLongPressTriggered) {
                    // Long press was already executed during key down
                    return true
                }

                val now = System.currentTimeMillis()
                if (now - lastClickTimestamp < 350L) {
                    // Double click detected!
                    pendingClickJob?.cancel()
                    lastClickTimestamp = 0L
                    executeAction(doublePressAction)
                } else {
                    lastClickTimestamp = now
                    pendingClickJob = scope.launch {
                        delay(350L)
                        // Single click executed
                        when (keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT -> playerManager?.skipToNext()
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> playerManager?.skipToPrevious()
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_HEADSETHOOK -> playerManager?.togglePlayPause()
                        }
                    }
                }
                return true
            }
        }
        return false
    }

    private fun executeAction(action: SwcAction) {
        val player = playerManager ?: return
        when (action) {
            SwcAction.NEXT_TRACK -> player.skipToNext()
            SwcAction.PREV_TRACK -> player.skipToPrevious()
            SwcAction.PLAY_PAUSE -> player.togglePlayPause()
            SwcAction.SEEK_FORWARD_15 -> {
                val current = player.playbackState.value.currentPositionMs
                player.seekTo(current + 15000L)
            }
            SwcAction.SEEK_BACKWARD_15 -> {
                val current = player.playbackState.value.currentPositionMs
                player.seekTo(kotlin.math.max(0L, current - 15000L))
            }
            SwcAction.TOGGLE_FAVORITE -> {
                // Toggled via repository/viewModel
            }
            SwcAction.NEXT_ALBUM -> {
                player.skipToNext()
            }
            SwcAction.MUTE -> {
                // Toggles mute
            }
        }
    }
}
