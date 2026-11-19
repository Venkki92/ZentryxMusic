package com.zentryx.music.dsp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

enum class CabinMicMonitorStatus(val label: String) {
    OFF("Standby"),
    ACTIVE("Live Mic"),
    PERMISSION_REQUIRED("Mic Permission Required"),
    UNAVAILABLE("No Mic Input")
}

/**
 * Low-overhead background cabin ambient noise monitor using Android AudioRecord.
 * Automatically checks RECORD_AUDIO permission and releases mic hardware immediately when stopped.
 */
@Singleton
class CabinNoiseMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val SAMPLE_RATE = 16000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    private val _cabinNoiseDb = MutableStateFlow<Float?>(null)
    val cabinNoiseDb: StateFlow<Float?> = _cabinNoiseDb.asStateFlow()

    private val _status = MutableStateFlow(CabinMicMonitorStatus.OFF)
    val status: StateFlow<CabinMicMonitorStatus> = _status.asStateFlow()

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (monitorJob?.isActive == true) return
        if (!hasRecordAudioPermission()) {
            _cabinNoiseDb.value = null
            _status.value = CabinMicMonitorStatus.PERMISSION_REQUIRED
            return
        }

        monitorJob = scope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var record: AudioRecord? = null
            val sources = listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)
            for (src in sources) {
                try {
                    val candidate = AudioRecord(
                        src,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf * 2
                    )
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        candidate.startRecording()
                        record = candidate
                        break
                    } else {
                        candidate.release()
                    }
                } catch (_: Throwable) {
                    // Try fallback audio source
                }
            }

            if (record == null) {
                _cabinNoiseDb.value = null
                _status.value = CabinMicMonitorStatus.UNAVAILABLE
                return@launch
            }

            _status.value = CabinMicMonitorStatus.ACTIVE
            val buffer = ShortArray(1024)
            var emaDb: Float? = null

            try {
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read >= 128) {
                        val instant = estimateCabinNoiseDbFromPcm16(buffer, read)
                        emaDb = if (emaDb == null) instant else (emaDb * 0.72f + instant * 0.28f)
                        _cabinNoiseDb.value = ((emaDb * 10f).roundToInt() / 10f)
                    }
                    delay(200L)
                }
            } finally {
                try { record.stop() } catch (_: Throwable) {}
                try { record.release() } catch (_: Throwable) {}
                _cabinNoiseDb.value = null
                if (_status.value == CabinMicMonitorStatus.ACTIVE) {
                    _status.value = CabinMicMonitorStatus.OFF
                }
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        _cabinNoiseDb.value = null
        _status.value = CabinMicMonitorStatus.OFF
    }
}
