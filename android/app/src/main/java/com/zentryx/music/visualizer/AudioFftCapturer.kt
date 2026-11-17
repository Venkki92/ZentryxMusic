package com.zentryx.music.visualizer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

enum class VisualizerMode(
    val id: String,
    val shortLabel: String,
    val title: String,
    val subtitle: String
) {
    GAUGE("gauge", "Cluster", "W203 VDO Cluster", "Spring-damped instrument dial & redline needles"),
    STAR_PULSE("star_pulse", "Starlight", "Emblem Starlight", "3-pointed emblem inside 48-ray harmonic spectrum"),
    AMG_TELEMETRY("amg_telemetry", "AMG Track", "AMG Track Pace", "Motorsport torque trace & real-time G telemetry"),
    BURMESTER_WAVE("burmester_wave", "Burmester", "Burmester 3D Wave", "Layered acoustic silk ribbons on grille matrix"),
    COMAND("comand", "COMAND", "COMAND Logic 7", "32-band Harman/Kardon spectrum with peak hold"),
    MFD_AMBER("mfd_amber", "MFD", "W203 MFD Dot Matrix", "VDO amber cockpit matrix FFT readout");

    companion object {
        fun fromId(id: String): VisualizerMode = entries.firstOrNull { it.id == id } ?: GAUGE
    }
}

enum class VisualizerColorway(
    val id: String,
    val displayName: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val needleHex: Long,
    val dialBgHex: Long
) {
    AMBER("amber", "W203 Solar Amber", 0xFFF59E0B, 0xFFFBBF24, 0xFFFF5500, 0xFF0A0805),
    AVANTGARDE("avantgarde", "Zentryx Lime", 0xFFA3E635, 0xFF38BDF8, 0xFFA3E635, 0xFF030712),
    AMG_GT("amg_gt", "AMG Solar Red", 0xFFEF4444, 0xFFF97316, 0xFFFFFFFF, 0xFF090406),
    AMBIENT("ambient", "MBUX Cyan Ice", 0xFF38BDF8, 0xFF818CF8, 0xFF22D3EE, 0xFF020B18);

    companion object {
        fun fromId(id: String): VisualizerColorway = entries.firstOrNull { it.id == id } ?: AMBER
    }
}

data class VisualizerFrame(
    val subBass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val beatPulse: Float = 0f,
    val bands32: FloatArray = FloatArray(32),
    val peaks32: FloatArray = FloatArray(32),
    val waveform64: FloatArray = FloatArray(64),
    val isLiveAudio: Boolean = false
)

/**
 * Captures real-time FFT + Waveform audio frames directly from ExoPlayer's audioSessionId
 * via android.media.audiofx.Visualizer with rolling AGC peak tracking so quiet/mastered tracks stay lively.
 */
@Singleton
class AudioFftCapturer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ZentryxVisualizerFFT"
    }

    private val _frame = MutableStateFlow(VisualizerFrame())
    val frame: StateFlow<VisualizerFrame> = _frame.asStateFlow()

    private var visualizer: Visualizer? = null
    private var attachedSessionId: Int = 0

    // Rolling AGC & envelope state
    private var agcPeak = 0.25f
    private var smoothBass = 0f
    private var smoothMid = 0f
    private var smoothTreble = 0f
    private var beatEnvelope = 0f
    private var prevBass = 0f
    private val smoothBands = FloatArray(32)
    private val holdPeaks = FloatArray(32)
    private val latestWave = FloatArray(64)

    fun hasAudioCapturePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun attachAudioSession(sessionId: Int, isPlaying: Boolean) {
        if (sessionId <= 0) return
        if (!hasAudioCapturePermission()) {
            release()
            return
        }
        if (attachedSessionId != sessionId || visualizer == null) {
            release()
            try {
                val vis = Visualizer(sessionId)
                val range = Visualizer.getCaptureSizeRange()
                val captureSize = 512.coerceIn(range[0], range[1])
                vis.captureSize = captureSize
                vis.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                            if (waveform != null && waveform.isNotEmpty()) {
                                processWaveform(waveform)
                            }
                        }

                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            if (fft != null && fft.size >= 4) {
                                processFft(fft)
                            }
                        }
                    },
                    (Visualizer.getMaxCaptureRate() / 2).coerceIn(10000, 20000),
                    true,
                    true
                )
                vis.enabled = isPlaying
                visualizer = vis
                attachedSessionId = sessionId
            } catch (t: Throwable) {
                Log.w(TAG, "Hardware Visualizer attach skipped: ${t.message}")
                release()
            }
        } else {
            setCaptureActive(isPlaying)
        }
    }

    fun setCaptureActive(active: Boolean) {
        try {
            visualizer?.let {
                if (it.enabled != active) {
                    it.enabled = active
                }
            }
        } catch (_: Throwable) {}
    }

    private fun processWaveform(waveform: ByteArray) {
        val step = max(1, waveform.size / 64)
        for (i in 0 until 64) {
            val byteVal = (waveform[(i * step).coerceAtMost(waveform.size - 1)].toInt() and 0xFF) - 128
            latestWave[i] = (byteVal / 128f).coerceIn(-1f, 1f)
        }
    }

    private fun processFft(fft: ByteArray) {
        val numBins = fft.size / 2
        val mags = FloatArray(numBins)
        var maxFrameMag = 0f

        for (i in 1 until numBins) {
            val re = fft[2 * i].toFloat()
            val im = fft[2 * i + 1].toFloat()
            val mag = hypot(re, im) / 128f
            mags[i] = mag
            if (mag > maxFrameMag) maxFrameMag = mag
        }

        // Rolling AGC normalization
        agcPeak = max(0.18f, agcPeak * 0.992f)
        if (maxFrameMag > agcPeak) agcPeak = maxFrameMag
        val scale = 1f / agcPeak

        // Extract Sub-Bass, Mid, Treble bands
        var bassSum = 0f
        var midSum = 0f
        var trebleSum = 0f
        val bassEnd = (numBins * 0.08f).toInt().coerceAtLeast(3)
        val midEnd = (numBins * 0.36f).toInt().coerceAtLeast(bassEnd + 4)

        for (i in 1 until numBins) {
            val norm = (mags[i] * scale).coerceIn(0f, 1f)
            when {
                i < bassEnd -> bassSum += norm
                i < midEnd -> midSum += norm
                else -> trebleSum += norm
            }
        }

        val rawBass = (bassSum / max(1, bassEnd - 1) * 1.4f).coerceIn(0f, 1f)
        val rawMid = (midSum / max(1, midEnd - bassEnd) * 1.5f).coerceIn(0f, 1f)
        val rawTreble = (trebleSum / max(1, numBins - midEnd) * 1.8f).coerceIn(0f, 1f)

        smoothBass = envelope(smoothBass, rawBass, 0.45f, 0.16f)
        smoothMid = envelope(smoothMid, rawMid, 0.40f, 0.18f)
        smoothTreble = envelope(smoothTreble, rawTreble, 0.42f, 0.22f)

        // Beat transient detector
        val delta = rawBass - prevBass
        prevBass = rawBass
        beatEnvelope = if (delta > 0.12f && rawBass > 0.35f) 1f else (beatEnvelope * 0.86f)

        // Map 32 logarithmic bars + gravity peaks
        for (b in 0 until 32) {
            val lo = ((b.toFloat() / 32f).pow(1.65f) * (numBins - 1)).toInt().coerceIn(1, numBins - 1)
            val hi = (((b + 1).toFloat() / 32f).pow(1.65f) * (numBins - 1)).toInt().coerceIn(lo + 1, numBins)
            var peak = 0f
            for (k in lo until hi) {
                val v = mags[k] * scale
                if (v > peak) peak = v
            }
            val boosted = (peak * (0.95f + b * 0.015f)).coerceIn(0f, 1f)
            smoothBands[b] = envelope(smoothBands[b], boosted, 0.55f, 0.20f)
            holdPeaks[b] = if (smoothBands[b] >= holdPeaks[b]) smoothBands[b] else (holdPeaks[b] - 0.018f).coerceAtLeast(0f)
        }

        _frame.value = VisualizerFrame(
            subBass = smoothBass,
            mid = smoothMid,
            treble = smoothTreble,
            beatPulse = beatEnvelope,
            bands32 = smoothBands.copyOf(),
            peaks32 = holdPeaks.copyOf(),
            waveform64 = latestWave.copyOf(),
            isLiveAudio = true
        )
    }

    private fun envelope(prev: Float, target: Float, attack: Float, release: Float): Float {
        val k = if (target > prev) attack else release
        return prev + (target - prev) * k
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {}
        visualizer = null
        attachedSessionId = 0
        _frame.value = VisualizerFrame(isLiveAudio = false)
    }
}
