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
import kotlin.math.log10
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
    val peakLevel: Float = 0f,
    val bands32: FloatArray = FloatArray(32),
    val peaks32: FloatArray = FloatArray(32),
    val waveform64: FloatArray = FloatArray(64),
    val isLiveAudio: Boolean = false
)

/**
 * High-responsiveness FFT + Waveform audio telemetry capturer directly bound to ExoPlayer's audioSessionId.
 * Uses calibrated logarithmic dB perception scaling, rolling AGC normalization, fast transient attack (0.72),
 * and spectral-flux kick detection so needle & bars react crisply on 1024x600 displays at any volume.
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

    // Rolling AGC & transient envelope state
    private var agcPeak = 0.22f
    private var rollingBassAvg = 0.15f
    private var smoothBass = 0f
    private var smoothMid = 0f
    private var smoothTreble = 0f
    private var beatEnvelope = 0f
    private var prevBass = 0f
    private var peakHoldOverall = 0f
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
                val captureSize = 1024.coerceIn(range[0], range[1])
                vis.captureSize = captureSize
                vis.measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
                vis.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
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
                    Visualizer.getMaxCaptureRate().coerceIn(15000, 30000),
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
            if (!active) {
                _frame.value = _frame.value.copy(
                    subBass = smoothBass * 0.2f,
                    mid = smoothMid * 0.2f,
                    treble = smoothTreble * 0.2f,
                    beatPulse = 0f
                )
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

    private fun toPerceptualLevel(rawNorm: Float): Float {
        if (rawNorm <= 0.002f) return 0f
        // Map 42 dB dynamic window smoothly to [0..1]
        val db = 20f * log10(rawNorm.coerceAtLeast(0.004f))
        return ((db + 42f) / 42f).coerceIn(0f, 1f).pow(1.15f)
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

        // Fast-adapting rolling AGC normalization
        agcPeak = max(0.14f, agcPeak * 0.990f)
        if (maxFrameMag > agcPeak) agcPeak = agcPeak * 0.35f + maxFrameMag * 0.65f
        val scale = 1f / agcPeak

        var bassSum = 0f
        var midSum = 0f
        var trebleSum = 0f
        val bassEnd = (numBins * 0.07f).toInt().coerceAtLeast(4)
        val midEnd = (numBins * 0.34f).toInt().coerceAtLeast(bassEnd + 6)

        for (i in 1 until numBins) {
            val norm = toPerceptualLevel((mags[i] * scale).coerceIn(0f, 1.5f))
            when {
                i < bassEnd -> bassSum += norm
                i < midEnd -> midSum += norm
                else -> trebleSum += norm
            }
        }

        val rawBass = (bassSum / max(1, bassEnd - 1) * 1.25f).coerceIn(0f, 1f)
        val rawMid = (midSum / max(1, midEnd - bassEnd) * 1.35f).coerceIn(0f, 1f)
        val rawTreble = (trebleSum / max(1, numBins - midEnd) * 1.65f).coerceIn(0f, 1f)

        // Responsive fast-attack / musical-decay envelopes
        smoothBass = envelope(smoothBass, rawBass, 0.72f, 0.24f)
        smoothMid = envelope(smoothMid, rawMid, 0.65f, 0.26f)
        smoothTreble = envelope(smoothTreble, rawTreble, 0.68f, 0.28f)

        // Spectral-flux kick/beat transient detector
        rollingBassAvg = rollingBassAvg * 0.92f + rawBass * 0.08f
        val delta = rawBass - prevBass
        prevBass = rawBass
        beatEnvelope = if (delta > 0.09f && rawBass > rollingBassAvg * 1.18f) {
            1f
        } else {
            (beatEnvelope * 0.80f).coerceAtLeast(0f)
        }

        // Map 32 logarithmic frequency bands + gravity peaks
        for (b in 0 until 32) {
            val lo = ((b.toFloat() / 32f).pow(1.75f) * (numBins - 1)).toInt().coerceIn(1, numBins - 1)
            val hi = (((b + 1).toFloat() / 32f).pow(1.75f) * (numBins - 1)).toInt().coerceIn(lo + 1, numBins)
            var bandPeak = 0f
            for (k in lo until hi) {
                val v = mags[k] * scale
                if (v > bandPeak) bandPeak = v
            }
            val perceived = toPerceptualLevel(bandPeak * (0.95f + b * 0.022f))
            smoothBands[b] = envelope(smoothBands[b], perceived, 0.74f, 0.25f)
            holdPeaks[b] = if (smoothBands[b] >= holdPeaks[b]) {
                smoothBands[b]
            } else {
                (holdPeaks[b] - 0.022f).coerceAtLeast(0f)
            }
        }

        val overall = (smoothBass * 0.55f + smoothMid * 0.30f + smoothTreble * 0.15f).coerceIn(0f, 1f)
        peakHoldOverall = if (overall >= peakHoldOverall) overall else (peakHoldOverall - 0.014f).coerceAtLeast(0f)

        _frame.value = VisualizerFrame(
            subBass = smoothBass,
            mid = smoothMid,
            treble = smoothTreble,
            beatPulse = beatEnvelope,
            peakLevel = peakHoldOverall,
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
