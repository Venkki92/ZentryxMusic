package com.zentryx.music.player

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Automotive Headunit Hardware Receiver:
 * - ACC / Ignition Boot Auto-Resume (ACTION_BOOT_COMPLETED)
 * - Bluetooth A2DP / ACL Auto-Play Trigger
 * - CAN-Bus / MCU Reverse-Gear Parking Sensor Audio Ducking
 *   (supports Topway/AutoChips com.tw.reverse, Microntek com.microntek.reverse, generic REVERSE_GEAR)
 */
@AndroidEntryPoint
class BootAndHardwareReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ZentryxBootHwReceiver"
        const val ACTION_MICRONTEK_REVERSE = "com.microntek.reverse"
        const val ACTION_TOPWAY_REVERSE = "com.tw.reverse"
        const val ACTION_GENERIC_REVERSE = "android.intent.action.REVERSE_GEAR"
    }

    @Inject
    lateinit var settingsPreferences: SettingsPreferences

    @Inject
    lateinit var playerManager: ZentryxPlayerManager

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        scope.launch {
            try {
                when (action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                        val autoResume = settingsPreferences.autoResumeOnBootFlow.firstOrNull() ?: true
                        if (autoResume) {
                            Log.i(TAG, "Ignition boot completed — resuming last playback state")
                            playerManager.resumeOrStartOnBluetooth()
                        }
                    }

                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val autoPlayBt = settingsPreferences.autoPlayOnBluetoothFlow.firstOrNull() ?: true
                        if (autoPlayBt) {
                            Log.i(TAG, "Bluetooth ACL connected — triggering auto-play")
                            playerManager.resumeOrStartOnBluetooth()
                        }
                    }

                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                        val autoPlayBt = settingsPreferences.autoPlayOnBluetoothFlow.firstOrNull() ?: true
                        if (state == BluetoothProfile.STATE_CONNECTED && autoPlayBt) {
                            Log.i(TAG, "Bluetooth A2DP connected — triggering auto-play")
                            playerManager.resumeOrStartOnBluetooth()
                        }
                    }

                    ACTION_MICRONTEK_REVERSE,
                    ACTION_TOPWAY_REVERSE,
                    ACTION_GENERIC_REVERSE -> {
                        val duckEnabled = settingsPreferences.duckOnReverseGearFlow.firstOrNull() ?: true
                        if (!duckEnabled) {
                            playerManager.setReverseGearDucking(false)
                        } else {
                            val inReverse = parseReverseGearExtra(intent)
                            Log.i(TAG, "Reverse gear broadcast ($action): active=$inReverse")
                            playerManager.setReverseGearDucking(inReverse)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Hardware broadcast handling failed for $action: ${t.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun parseReverseGearExtra(intent: Intent): Boolean {
        val extras = intent.extras ?: return true
        for (key in listOf("reverse", "backcar", "status", "state", "isReverse")) {
            if (!extras.containsKey(key)) continue
            val value = extras.get(key)
            return when (value) {
                is Boolean -> value
                is Int -> value != 0
                is String -> value.equals("true", true) || value.equals("on", true) || value == "1"
                else -> false
            }
        }
        return false
    }
}
