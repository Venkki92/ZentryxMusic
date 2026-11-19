package com.zentryx.music.swc

import android.view.KeyEvent
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.player.ZentryxPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

enum class SwcAction(val displayName: String) {
    NEXT_TRACK("Next Track"),
    PREV_TRACK("Previous Track"),
    PLAY_PAUSE("Play / Pause"),
    SEEK_FORWARD_15("Seek +15s"),
    SEEK_BACKWARD_15("Seek -15s"),
    TOGGLE_FAVORITE("Favorite ♥"),
    NEXT_ALBUM("Next Album"),
    MUTE("Mute Audio"),
    TALK_DUCK("Talk Duck -20dB"),
    CRUISING_MIX("Cruising Mix"),
    CYCLE_SURROUND("Cycle Logic 7"),
    CYCLE_EQ_PRESET("Cycle EQ Preset");

    companion object {
        fun fromPreference(raw: String, fallback: SwcAction): SwcAction {
            return when (raw.trim().uppercase()) {
                "SEEK_15S", "SEEK_FORWARD_15" -> SEEK_FORWARD_15
                "SEEK_BACK_15S", "SEEK_BACKWARD_15" -> SEEK_BACKWARD_15
                "FAVORITE", "TOGGLE_FAVORITE" -> TOGGLE_FAVORITE
                "NEXT_ALBUM" -> NEXT_ALBUM
                "MUTE" -> MUTE
                "TALK_DUCK" -> TALK_DUCK
                "CRUISING_MIX" -> CRUISING_MIX
                "CYCLE_SURROUND" -> CYCLE_SURROUND
                "CYCLE_EQ_PRESET" -> CYCLE_EQ_PRESET
                "NEXT_TRACK" -> NEXT_TRACK
                "PREV_TRACK" -> PREV_TRACK
                "PLAY_PAUSE" -> PLAY_PAUSE
                else -> fallback
            }
        }
    }
}

enum class SwcPressType { SHORT, LONG }

data class SwcLearnTarget(
    val action: SwcAction,
    val pressType: SwcPressType
)

data class SwcCustomKeyMap(
    val shortPressMap: Map<Int, SwcAction> = emptyMap(),
    val longPressMap: Map<Int, SwcAction> = emptyMap()
) {
    fun toJson(): String = JSONObject().apply {
        val shortObj = JSONObject()
        shortPressMap.forEach { (k, v) -> shortObj.put(k.toString(), v.name) }
        val longObj = JSONObject()
        longPressMap.forEach { (k, v) -> longObj.put(k.toString(), v.name) }
        put("short", shortObj)
        put("long", longObj)
    }.toString()

    companion object {
        fun fromJson(json: String?): SwcCustomKeyMap {
            if (json.isNullOrBlank()) return SwcCustomKeyMap()
            return try {
                val obj = JSONObject(json)
                val shortMap = mutableMapOf<Int, SwcAction>()
                val longMap = mutableMapOf<Int, SwcAction>()
                obj.optJSONObject("short")?.let { s ->
                    s.keys().forEach { k ->
                        val code = k.toIntOrNull()
                        val act = SwcAction.fromPreference(s.optString(k), SwcAction.PLAY_PAUSE)
                        if (code != null) shortMap[code] = act
                    }
                }
                obj.optJSONObject("long")?.let { l ->
                    l.keys().forEach { k ->
                        val code = k.toIntOrNull()
                        val act = SwcAction.fromPreference(l.optString(k), SwcAction.SEEK_FORWARD_15)
                        if (code != null) longMap[code] = act
                    }
                }
                SwcCustomKeyMap(shortMap, longMap)
            } catch (_: Throwable) {
                SwcCustomKeyMap()
            }
        }
    }
}

/**
 * Steering Wheel Controls (CAN-Bus SWC) for Mercedes-Benz W203 / S203.
 * Supports single-tap, double-tap, long-press, AND interactive hardware KeyCode remapping
 * for custom CANBUS / resistive KEY1 & KEY2 headunit buttons (Short-Press & Long-Press).
 */
@Singleton
class SteeringWheelManager @Inject constructor(
    private val settingsPreferences: SettingsPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playerManager: ZentryxPlayerManager? = null

    private var longPressAction = SwcAction.SEEK_FORWARD_15
    private var doublePressAction = SwcAction.TOGGLE_FAVORITE

    private val _customKeyMap = MutableStateFlow(SwcCustomKeyMap())
    val customKeyMap: StateFlow<SwcCustomKeyMap> = _customKeyMap.asStateFlow()

    private val _learningTarget = MutableStateFlow<SwcLearnTarget?>(null)
    val learningTarget: StateFlow<SwcLearnTarget?> = _learningTarget.asStateFlow()

    private val _lastCapturedKeyLabel = MutableStateFlow<String?>(null)
    val lastCapturedKeyLabel: StateFlow<String?> = _lastCapturedKeyLabel.asStateFlow()

    private var keyDownTimestamp = 0L
    private var pendingClickJob: Job? = null
    private var longPressJob: Job? = null
    private var lastClickTimestamp = 0L
    private var lastClickKeyCode = -1
    private var isLongPressTriggered = false

    init {
        scope.launch {
            settingsPreferences.swcLongPressActionFlow.collectLatest { actionName ->
                longPressAction = SwcAction.fromPreference(actionName, SwcAction.SEEK_FORWARD_15)
            }
        }
        scope.launch {
            settingsPreferences.swcDoublePressActionFlow.collectLatest { actionName ->
                doublePressAction = SwcAction.fromPreference(actionName, SwcAction.TOGGLE_FAVORITE)
            }
        }
        scope.launch {
            settingsPreferences.swcCustomKeyMapJsonFlow.collectLatest { json ->
                _customKeyMap.value = SwcCustomKeyMap.fromJson(json)
            }
        }
    }

    fun attachPlayerManager(manager: ZentryxPlayerManager) {
        this.playerManager = manager
    }

    fun startLearning(action: SwcAction, pressType: SwcPressType) {
        _learningTarget.value = SwcLearnTarget(action, pressType)
        _lastCapturedKeyLabel.value = "Press any steering wheel button for ${action.displayName} (${pressType.name})…"
    }

    fun cancelLearning() {
        _learningTarget.value = null
    }

    fun clearCustomKeyMap() {
        val empty = SwcCustomKeyMap()
        _customKeyMap.value = empty
        _lastCapturedKeyLabel.value = "Custom SWC mappings cleared"
        scope.launch { settingsPreferences.saveSwcCustomKeyMapJson(empty.toJson()) }
    }

    private fun isDefaultMediaKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK
    }

    private fun isHandledKey(keyCode: Int): Boolean {
        val map = _customKeyMap.value
        return isDefaultMediaKey(keyCode) ||
            map.shortPressMap.containsKey(keyCode) ||
            map.longPressMap.containsKey(keyCode)
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Interactive CANBUS / SWC Key Learner capture mode
        val learn = _learningTarget.value
        if (learn != null && keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_HOME) {
            if (event?.repeatCount == 0) {
                val cur = _customKeyMap.value
                val updated = if (learn.pressType == SwcPressType.SHORT) {
                    cur.copy(shortPressMap = cur.shortPressMap + (keyCode to learn.action))
                } else {
                    cur.copy(longPressMap = cur.longPressMap + (keyCode to learn.action))
                }
                _customKeyMap.value = updated
                _learningTarget.value = null
                _lastCapturedKeyLabel.value = "Mapped ${KeyEvent.keyCodeToString(keyCode)} (${learn.pressType.name}) → ${learn.action.displayName}"
                scope.launch { settingsPreferences.saveSwcCustomKeyMapJson(updated.toJson()) }
            }
            return true
        }

        if (!isHandledKey(keyCode)) return false

        if (event?.repeatCount == 0) {
            keyDownTimestamp = System.currentTimeMillis()
            isLongPressTriggered = false
            longPressJob?.cancel()

            val resolvedLongAction = _customKeyMap.value.longPressMap[keyCode] ?: longPressAction
            longPressJob = scope.launch {
                delay(600L)
                if (keyDownTimestamp > 0 && !isLongPressTriggered) {
                    isLongPressTriggered = true
                    executeAction(resolvedLongAction)
                }
            }
        }
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!isHandledKey(keyCode)) return false

        longPressJob?.cancel()
        keyDownTimestamp = 0L

        if (isLongPressTriggered) {
            return true
        }

        val now = System.currentTimeMillis()
        if (now - lastClickTimestamp < 350L && lastClickKeyCode == keyCode) {
            pendingClickJob?.cancel()
            lastClickTimestamp = 0L
            lastClickKeyCode = -1
            executeAction(doublePressAction)
        } else {
            lastClickTimestamp = now
            lastClickKeyCode = keyCode
            val customShort = _customKeyMap.value.shortPressMap[keyCode]
            pendingClickJob = scope.launch {
                delay(350L)
                if (customShort != null) {
                    executeAction(customShort)
                } else {
                    when (keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> playerManager?.skipToNext()
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> playerManager?.skipToPrevious()
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> playerManager?.togglePlayPause()
                    }
                }
            }
        }
        return true
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
                player.seekTo(max(0L, current - 15000L))
            }
            SwcAction.TOGGLE_FAVORITE -> player.toggleFavoriteCurrentSong()
            SwcAction.NEXT_ALBUM -> player.skipToNextAlbum()
            SwcAction.MUTE -> player.toggleMute()
            SwcAction.TALK_DUCK -> player.toggleAttenuation()
            SwcAction.CRUISING_MIX -> player.startCruisingMix()
            SwcAction.CYCLE_SURROUND -> { player.cycleSurroundMode() }
            SwcAction.CYCLE_EQ_PRESET -> { player.cycleEqPreset() }
        }
    }
}
