package com.zentryx.music.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared state holder tracking currently mounted removable USB OTG / MicroSD volumes
 * so SettingsScreen and Library can show detected drives live.
 */
@Singleton
class UsbVolumeMonitor @Inject constructor(
    private val localMediaScanner: LocalMediaScanner
) {
    private val _mountedVolumes = MutableStateFlow<List<String>>(emptyList())
    val mountedVolumes: StateFlow<List<String>> = _mountedVolumes.asStateFlow()

    private val _lastEventMessage = MutableStateFlow<String?>(null)
    val lastEventMessage: StateFlow<String?> = _lastEventMessage.asStateFlow()

    init {
        refreshVolumes()
    }

    fun refreshVolumes(): List<String> {
        val list = localMediaScanner.listMountedRemovableVolumes()
        _mountedVolumes.value = list
        return list
    }

    fun reportEvent(message: String) {
        _lastEventMessage.value = message
    }
}

/**
 * Watches Android ACTION_MEDIA_MOUNTED / UNMOUNTED / EJECT intents for USB OTG sticks
 * and MicroSD cards plugged into the car headunit, auto-indexing local audio tracks.
 */
@AndroidEntryPoint
class UsbStorageWatcher : BroadcastReceiver() {

    companion object {
        private const val TAG = "ZentryxUsbWatcher"
    }

    @Inject
    lateinit var localMediaScanner: LocalMediaScanner

    @Inject
    lateinit var usbVolumeMonitor: UsbVolumeMonitor

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val volumes = usbVolumeMonitor.refreshVolumes()
                when (action) {
                    Intent.ACTION_MEDIA_MOUNTED -> {
                        val path = intent.data?.path ?: "USB Drive"
                        Log.i(TAG, "USB/SD mounted at $path (volumes=${volumes.size}); auto-scanning audio")
                        val count = localMediaScanner.scanLocalAudio()
                        usbVolumeMonitor.reportEvent("USB mounted ($path) · Indexed $count local tracks")
                    }
                    Intent.ACTION_MEDIA_UNMOUNTED,
                    Intent.ACTION_MEDIA_EJECT,
                    Intent.ACTION_MEDIA_REMOVED -> {
                        Log.i(TAG, "USB/SD removed; refreshing local index")
                        val count = localMediaScanner.scanLocalAudio()
                        usbVolumeMonitor.reportEvent("USB removed · $count local tracks remaining")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "USB watcher error: ${t.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
