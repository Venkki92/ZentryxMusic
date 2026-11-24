package com.zentryx.music.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Real-time Media3 PCM BaseAudioProcessor for Automotive DSP & Harman Logic 7 / Virtual 3D Surround:
 * 1. ReplayGain Loudness Normalization pre-trim gain stage.
 * 2. Selectable 12 dB/oct (BW2) or 24 dB/oct (Linkwitz-Riley LR4) High-Pass Filter (60/80/100/120 Hz).
 * 3. Phase-coherent Sub-Bass LPF Split (12 or 24 dB/oct) + 0°/180° Phase Flip + Sub Trim (-6..+6 dB)
 *    + Sub Delay Alignment (0.0..15.0 ms) to pull trunk bass forward onto the dash.
 * 4. Speed-Sensitive Road-Noise Acoustic Contour (115 Hz Low-Shelf + 2.8 kHz Vocal Presence Peak).
 * 5. Overhauled Multi-Stage Spatial Surround Matrix (Harman Logic 7 / Concert Hall / Studio / Dolby PLII / Atmos 3D / Virtual 360°):
 *    - Auto pre-matrix headroom & 20 ms parameter smoothing (click-free live mode switching)
 *    - Hybrid Side (L-R) + Band-Limited Mid (L+R) ambience extraction (works on centered/narrow mixes too!)
 *    - 4-Tap Asymmetric Decorrelated Cabin Early Reflections (L/R opposite polarity & distinct delay ratios)
 *    - Dual-Stage Cascaded Schroeder All-Pass Diffuser (smooth cabin wrap without metallic slap)
 *    - Damped Stereo Feedback Comb Hall Tank (4.5 kHz damping LPF for lush hall depth)
 *    - Dual-Stage 90° Quadrature All-Pass Phase Network (Dolby PLII / Virtual 3D)
 *    - Band-Limited 0.55 ms ITD Cross-Talk Cancellation (XTC, 280 Hz–5.5 kHz so bass stays tight)
 *    - 2.2 kHz Dash Center Vocal Lock + 180 Hz Cabin Warmth Shelf + Pinna Elevation Contour (Atmos 3D)
 * 6. Expanded Mid/Side Matrix Stereo Width Control (0%..200%) OR true Mono Fold-Down ((L+R)*0.5).
 * 7. True Millisecond L/R Time Alignment Delay Ring Buffer (0.0 – 5.0 ms).
 * 8. Soft-Knee Saturating Master Peak Limiter Ceiling (0.0 / -0.5 / -1.5 / -3.0 dBFS).
 */
@OptIn(UnstableApi::class)
@Singleton
class CarDspAudioProcessor @Inject constructor() : BaseAudioProcessor() {

    companion object {
        private const val MAX_STAGE_DELAY_MS = 6f
        private const val MAX_SUB_DELAY_MS = 16f
        private const val MAX_EARLY_DELAY_MS = 95f
        private const val SCHROEDER_AP1_MS = 4.7f
        private const val SCHROEDER_AP2_MS = 11.3f
        private const val HALL_COMB_L_MS = 39.7f
        private const val HALL_COMB_R_MS = 47.3f
        private const val SPECTRUM_BLOCK_SIZE = 512
        private const val SPECTRUM_BANDS = 16
    }

    @Volatile private var dspEnabled: Boolean = true
    @Volatile private var hpfHz: Int = 0
    @Volatile private var crossoverSlopeDb: Int = 12
    @Volatile private var subLpfHz: Int = 0
    @Volatile private var subPhaseInverted: Boolean = false
    @Volatile private var subTrimDb: Float = 0f
    @Volatile private var subDelayMs: Float = 0f
    @Volatile private var monoFoldDown: Boolean = false
    @Volatile private var peakLimiterCeilingDb: Float = -0.5f
    @Volatile private var leftDelayMs: Float = 0f
    @Volatile private var rightDelayMs: Float = 0f
    @Volatile private var stereoWidthPercent: Int = 100
    @Volatile private var surroundMode: SurroundMode = SurroundMode.OFF
    @Volatile private var surroundIntensity: Int = 75
    @Volatile private var centerCoherence: Int = 55
    @Volatile private var virtualHeight: Int = 60
    @Volatile private var roadNoiseEqEnabled: Boolean = true
    @Volatile private var currentSpeedKmH: Float = 0f
    @Volatile private var replayGainLinear: Float = 1.0f
    @Volatile private var coeffVersion: Int = 0

    @Volatile var activeSampleRateHz: Int = 44100
        private set
    @Volatile var activeChannelCount: Int = 2
        private set
    @Volatile var isPcmConfigured: Boolean = false
        private set
    @Volatile var activeEffectiveWidthPercent: Int = 100
        private set

    // Live PCM bitstream telemetry for SurroundMatrixDebugger (updated once per queueInput buffer)
    @Volatile private var processedBufferCount: Long = 0L
    @Volatile private var processedFrameCount: Long = 0L
    @Volatile private var lastBufferBypassed: Boolean = true
    @Volatile private var lastBufferSurroundApplied: Boolean = false
    @Volatile private var lastBufferModified: Boolean = false
    @Volatile private var lastBufferInputRms: Float = 0f
    @Volatile private var lastBufferOutputRms: Float = 0f
    @Volatile private var lastBufferSurroundDeltaRms: Float = 0f
    @Volatile private var lastBufferPeakDelta: Float = 0f
    @Volatile private var lastInputChecksum: Int = 0
    @Volatile private var lastOutputChecksum: Int = 0
    @Volatile private var lastProcessedTimestampMs: Long = 0L
    @Volatile private var activeCoeffVersion: Int = -1
    private var sampleRateHz: Int = 44100
    private var channelCount: Int = 2
    @Volatile private var inputEncoding: Int = C.ENCODING_PCM_16BIT

    // Cascaded 2nd-order HPF state (stage 1 always on when hpfHz > 0; stage 2 when slope == 24)
    private var hpfB0 = 1.0; private var hpfB1 = 0.0; private var hpfB2 = 0.0; private var hpfA1 = 0.0; private var hpfA2 = 0.0
    private var hpf1X1L = 0.0; private var hpf1X2L = 0.0; private var hpf1Y1L = 0.0; private var hpf1Y2L = 0.0
    private var hpf1X1R = 0.0; private var hpf1X2R = 0.0; private var hpf1Y1R = 0.0; private var hpf1Y2R = 0.0
    private var hpf2X1L = 0.0; private var hpf2X2L = 0.0; private var hpf2Y1L = 0.0; private var hpf2Y2L = 0.0
    private var hpf2X1R = 0.0; private var hpf2X2R = 0.0; private var hpf2Y1R = 0.0; private var hpf2Y2R = 0.0

    // Cascaded Sub-band LPF state (stage 1 + optional stage 2 for 24 dB/oct)
    private var subB0 = 0.0; private var subB1 = 0.0; private var subB2 = 0.0; private var subA1 = 0.0; private var subA2 = 0.0
    private var sub1X1L = 0.0; private var sub1X2L = 0.0; private var sub1Y1L = 0.0; private var sub1Y2L = 0.0
    private var sub1X1R = 0.0; private var sub1X2R = 0.0; private var sub1Y1R = 0.0; private var sub1Y2R = 0.0
    private var sub2X1L = 0.0; private var sub2X2L = 0.0; private var sub2Y1L = 0.0; private var sub2Y2L = 0.0
    private var sub2X1R = 0.0; private var sub2X2R = 0.0; private var sub2Y1R = 0.0; private var sub2Y2R = 0.0
    private var subGainSigned: Double = 1.0

    // Sub-band delay ring buffer (0..15 ms)
    private var subDelayBufferL = FloatArray(1024)
    private var subDelayBufferR = FloatArray(1024)
    private var subDelayWritePos = 0
    private var subDelaySamples = 0

    // Road-Noise Acoustic Contour: 115 Hz Low-Shelf + 2.8 kHz Vocal Presence Peak
    private var rnLowActive = false
    private var rnLowB0 = 1.0; private var rnLowB1 = 0.0; private var rnLowB2 = 0.0; private var rnLowA1 = 0.0; private var rnLowA2 = 0.0
    private var rnLowX1L = 0.0; private var rnLowX2L = 0.0; private var rnLowY1L = 0.0; private var rnLowY2L = 0.0
    private var rnLowX1R = 0.0; private var rnLowX2R = 0.0; private var rnLowY1R = 0.0; private var rnLowY2R = 0.0

    private var rnMidActive = false
    private var rnMidB0 = 1.0; private var rnMidB1 = 0.0; private var rnMidB2 = 0.0; private var rnMidA1 = 0.0; private var rnMidA2 = 0.0
    private var rnMidX1L = 0.0; private var rnMidX2L = 0.0; private var rnMidY1L = 0.0; private var rnMidY2L = 0.0
    private var rnMidX1R = 0.0; private var rnMidX2R = 0.0; private var rnMidY1R = 0.0; private var rnMidY2R = 0.0

    // Surround Matrix: Mid 2.2 kHz vocal lock + Hybrid Mid/Side ambience conditioning + Dual 90° All-Pass
    private var l7MidB0 = 0.0; private var l7MidB1 = 0.0; private var l7MidB2 = 0.0; private var l7MidA1 = 0.0; private var l7MidA2 = 0.0
    private var l7MidX1 = 0.0; private var l7MidX2 = 0.0; private var l7MidY1 = 0.0; private var l7MidY2 = 0.0

    // Wideband ambience band-limiter (220 Hz 1-pole HPF + 5.2 kHz 1-pole LPF)
    private var ambHpfAlpha = 0.97
    private var ambLpfAlpha = 0.48
    private var ambHpfPrevIn = 0.0
    private var ambHpfPrevOut = 0.0
    private var ambLpfState = 0.0

    // Dual-Stage 90° All-Pass Phase Quadrature Network (750 Hz + 2100 Hz)
    private var l7ApActive = false
    private var l7Ap1B0 = 1.0; private var l7Ap1B1 = 0.0; private var l7Ap1B2 = 0.0; private var l7Ap1A1 = 0.0; private var l7Ap1A2 = 0.0
    private var l7Ap1X1 = 0.0; private var l7Ap1X2 = 0.0; private var l7Ap1Y1 = 0.0; private var l7Ap1Y2 = 0.0
    private var l7Ap2B0 = 1.0; private var l7Ap2B1 = 0.0; private var l7Ap2B2 = 0.0; private var l7Ap2A1 = 0.0; private var l7Ap2A2 = 0.0
    private var l7Ap2X1 = 0.0; private var l7Ap2X2 = 0.0; private var l7Ap2Y1 = 0.0; private var l7Ap2Y2 = 0.0

    // 4-Tap Asymmetric Early Reflection Ring Buffer (up to 95 ms)
    private var earlyAmbBuffer = FloatArray(8192)
    private var earlyWritePos = 0
    private var tapL1 = 480; private var tapL2 = 780; private var tapL3 = 1140; private var tapL4 = 1510
    private var tapR1 = 740; private var tapR2 = 1140; private var tapR3 = 1660; private var tapR4 = 2180

    // Dual-Stage Schroeder All-Pass Diffuser (L & R channels decorrelated)
    private var diffAp1BufL = FloatArray(512)
    private var diffAp1BufR = FloatArray(512)
    private var diffAp1Pos = 0
    private var diffAp1LenL = 211
    private var diffAp1LenR = 239

    private var diffAp2BufL = FloatArray(1024)
    private var diffAp2BufR = FloatArray(1024)
    private var diffAp2Pos = 0
    private var diffAp2LenL = 509
    private var diffAp2LenR = 557
    private var diffuserG = 0.0

    // Damped Stereo Feedback Comb Hall Tank (4.5 kHz damping LPF)
    private var hallCombBufL = FloatArray(4096)
    private var hallCombBufR = FloatArray(4096)
    private var hallCombPosL = 0
    private var hallCombPosR = 0
    private var hallCombLenL = 1907
    private var hallCombLenR = 2269
    private var hallDampStateL = 0.0
    private var hallDampStateR = 0.0
    private var hallDampCoeff = 0.45
    private var hallReverbMixGain = 0.0
    private var hallDecayFeedback = 0.0

    // 180 Hz Cabin Warmth Low-Shelf (keeps cabin rich and full when stereo stage expands)
    private var warmthActive = false
    private var warmthB0 = 1.0; private var warmthB1 = 0.0; private var warmthB2 = 0.0; private var warmthA1 = 0.0; private var warmthA2 = 0.0
    private var warmthX1L = 0.0; private var warmthX2L = 0.0; private var warmthY1L = 0.0; private var warmthY2L = 0.0
    private var warmthX1R = 0.0; private var warmthX2R = 0.0; private var warmthY1R = 0.0; private var warmthY2R = 0.0

    // Surround gains & headroom
    private var l7CenterInjectGain = 0.0
    private var l7SideWetGain = 0.0
    private var midToAmbienceMix = 0.0
    private var targetHeadroomGain = 1.0
    private var smoothedHeadroomGain = 1.0
    private var computedEffectiveWidth = 1.0f
    private var smoothedWidth = 1.0
    private var smoothCoeff = 0.001

    // Dolby Virtual Atmos / 360° Overhead Pinna Height Elevation Filter (7.2 kHz peaking + subtle 4.2 kHz pinna notch)
    private var heightActive = false
    private var heightB0 = 1.0; private var heightB1 = 0.0; private var heightB2 = 0.0; private var heightA1 = 0.0; private var heightA2 = 0.0
    private var heightX1L = 0.0; private var heightX2L = 0.0; private var heightY1L = 0.0; private var heightY2L = 0.0
    private var heightX1R = 0.0; private var heightX2R = 0.0; private var heightY1R = 0.0; private var heightY2R = 0.0

    private var pinnaNotchActive = false
    private var notchB0 = 1.0; private var notchB1 = 0.0; private var notchB2 = 0.0; private var notchA1 = 0.0; private var notchA2 = 0.0
    private var notchX1L = 0.0; private var notchX2L = 0.0; private var notchY1L = 0.0; private var notchY2L = 0.0
    private var notchX1R = 0.0; private var notchX2R = 0.0; private var notchY1R = 0.0; private var notchY2R = 0.0

    // 360° Virtualizer Band-Limited Inter-Aural Cross-Talk Cancellation (0.55 ms ITD inverted cross-feed, 280 Hz–5.5 kHz)
    private var xtcBufferL = FloatArray(128)
    private var xtcBufferR = FloatArray(128)
    private var xtcWritePos = 0
    private var xtcDelaySamples = 24
    private var xtcGain = 0.0
    private var xtcHpfAlpha = 0.96
    private var xtcLpfAlpha = 0.50
    private var xtcHpfPrevInL = 0.0; private var xtcHpfPrevOutL = 0.0; private var xtcLpfStateL = 0.0
    private var xtcHpfPrevInR = 0.0; private var xtcHpfPrevOutR = 0.0; private var xtcLpfStateR = 0.0

    // L/R Stage Time Alignment Delay buffer
    private var delayBufferL = FloatArray(1024)
    private var delayBufferR = FloatArray(1024)
    private var delayWritePos = 0
    private var leftDelaySamples = 0
    private var rightDelaySamples = 0

    private var maxSampleAbs: Double = 32767.0

    // Real-time 16-band L/R Goertzel stereo frequency distribution analyzer (zero-allocation)
    private val spectrumGoertzelCoeff = DoubleArray(SPECTRUM_BANDS)
    private val spectrumWindow = FloatArray(SPECTRUM_BLOCK_SIZE) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (SPECTRUM_BLOCK_SIZE - 1)))).toFloat()
    }
    private val spectrumBufL = FloatArray(SPECTRUM_BLOCK_SIZE)
    private val spectrumBufR = FloatArray(SPECTRUM_BLOCK_SIZE)
    private var spectrumWritePos = 0
    private val smoothedBandsL = FloatArray(SPECTRUM_BANDS)
    private val smoothedBandsR = FloatArray(SPECTRUM_BANDS)
    @Volatile private var publishedBandsL = FloatArray(SPECTRUM_BANDS)
    @Volatile private var publishedBandsR = FloatArray(SPECTRUM_BANDS)
    @Volatile private var publishedMidRms: Float = 0f
    @Volatile private var publishedSideRms: Float = 0f
    @Volatile private var publishedStereoSepPct: Float = 0f
    @Volatile private var lastSpectrumTimestampMs: Long = 0L

    fun updateSettings(settings: DspSettings) {
        dspEnabled = settings.dspEnabled
        hpfHz = settings.hpfCutoffHz.hz
        crossoverSlopeDb = sanitizeCrossoverSlopeDb(settings.crossoverSlopeDb)
        subLpfHz = sanitizeSubLpfHz(settings.subLpfHz)
        subPhaseInverted = settings.subPhaseInverted
        subTrimDb = settings.subTrimDb.coerceIn(-6f, 6f)
        subDelayMs = settings.subDelayMs.coerceIn(0f, 15f)
        monoFoldDown = settings.monoFoldDown
        peakLimiterCeilingDb = sanitizeLimiterCeilingDb(settings.peakLimiterCeilingDb)
        leftDelayMs = settings.leftDelayMs.coerceIn(0f, 5f)
        rightDelayMs = settings.rightDelayMs.coerceIn(0f, 5f)
        stereoWidthPercent = settings.stereoWidthPercent.coerceIn(0, 150)
        surroundMode = settings.surroundMode
        surroundIntensity = settings.surroundIntensity.coerceIn(0, 100)
        centerCoherence = settings.centerCoherence.coerceIn(0, 100)
        virtualHeight = settings.virtualHeight.coerceIn(0, 100)
        roadNoiseEqEnabled = settings.roadNoiseEqEnabled
        activeEffectiveWidthPercent = computeEffectiveSurroundWidthPercent(settings)
        coeffVersion++
    }

    fun buildLiveSurroundDiagnostics(
        trigger: String,
        settings: DspSettings,
        hardwareVirtualizerActive: Boolean = false,
        hardwareVirtualizerStrengthPermille: Int = 0
    ): SurroundMatrixDiagnostics {
        return buildSurroundMatrixDiagnostics(
            trigger = trigger,
            settings = settings,
            sampleRateHz = activeSampleRateHz,
            pcmConfigured = isPcmConfigured,
            hardwareVirtualizerActive = hardwareVirtualizerActive,
            hardwareVirtualizerStrengthPermille = hardwareVirtualizerStrengthPermille
        )
    }

    private fun encodingLabel(encoding: Int): String = when (encoding) {
        C.ENCODING_PCM_16BIT -> "PCM_16BIT"
        C.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
        C.ENCODING_PCM_24BIT -> "PCM_24BIT"
        C.ENCODING_PCM_32BIT -> "PCM_32BIT"
        else -> "ENCODING_$encoding"
    }

    fun snapshotBitstreamTelemetry(): SurroundBitstreamTelemetry {
        return SurroundBitstreamTelemetry(
            pcmConfigured = isPcmConfigured,
            inputEncoding = if (isPcmConfigured) encodingLabel(inputEncoding) else "UNCONFIGURED",
            sampleRateHz = activeSampleRateHz,
            channelCount = activeChannelCount,
            coeffVersionTarget = coeffVersion,
            coeffVersionApplied = activeCoeffVersion,
            buffersProcessed = processedBufferCount,
            framesProcessed = processedFrameCount,
            fastPathBypassed = lastBufferBypassed,
            surroundStageExecuted = lastBufferSurroundApplied,
            bitstreamModified = lastBufferModified,
            inputRms = lastBufferInputRms,
            outputRms = lastBufferOutputRms,
            surroundDeltaRms = lastBufferSurroundDeltaRms,
            peakSampleDelta = lastBufferPeakDelta,
            inputChecksum = lastInputChecksum,
            outputChecksum = lastOutputChecksum,
            lastProcessedTimestampMs = lastProcessedTimestampMs
        )
    }

    fun snapshotStereoSpectrum(): StereoSpectrumSnapshot {
        val now = System.currentTimeMillis()
        val live = lastSpectrumTimestampMs > 0L && (now - lastSpectrumTimestampMs) <= 2000L
        return StereoSpectrumSnapshot(
            leftBands = publishedBandsL.copyOf(),
            rightBands = publishedBandsR.copyOf(),
            midRms = publishedMidRms,
            sideRms = publishedSideRms,
            stereoSeparationPercent = publishedStereoSepPct,
            isStreamingLive = live,
            sampleRateHz = activeSampleRateHz
        )
    }

    private fun updateSpectrumCoefficients() {
        val sr = sampleRateHz.toDouble().coerceAtLeast(8000.0)
        for (b in 0 until SPECTRUM_BANDS) {
            val freq = STEREO_SPECTRUM_FREQS_HZ[b].toDouble().coerceAtMost(sr * 0.46)
            val omega = 2.0 * PI * freq / sr
            spectrumGoertzelCoeff[b] = 2.0 * cos(omega)
        }
    }

    private fun pushSpectrumFrame(normL: Float, normR: Float) {
        spectrumBufL[spectrumWritePos] = normL
        spectrumBufR[spectrumWritePos] = normR
        spectrumWritePos++
        if (spectrumWritePos >= SPECTRUM_BLOCK_SIZE) {
            computeSpectrumBlock()
            spectrumWritePos = 0
        }
    }

    private fun computeSpectrumBlock() {
        var sumMidSq = 0.0
        var sumSideSq = 0.0
        for (i in 0 until SPECTRUM_BLOCK_SIZE) {
            val l = spectrumBufL[i].toDouble()
            val r = spectrumBufR[i].toDouble()
            val m = (l + r) * 0.5
            val s = (l - r) * 0.5
            sumMidSq += m * m
            sumSideSq += s * s
        }
        val midRms = sqrt(sumMidSq / SPECTRUM_BLOCK_SIZE).toFloat()
        val sideRms = sqrt(sumSideSq / SPECTRUM_BLOCK_SIZE).toFloat()
        val denom = midRms + sideRms
        val sepPct = if (denom > 0.0001f) ((sideRms / denom) * 100f).coerceIn(0f, 100f) else 0f

        val nextL = FloatArray(SPECTRUM_BANDS)
        val nextR = FloatArray(SPECTRUM_BANDS)
        val scaleNorm = 2.0 / (SPECTRUM_BLOCK_SIZE * 0.5)
        for (b in 0 until SPECTRUM_BANDS) {
            val coeff = spectrumGoertzelCoeff[b]
            var s1L = 0.0; var s2L = 0.0
            var s1R = 0.0; var s2R = 0.0
            for (i in 0 until SPECTRUM_BLOCK_SIZE) {
                val w = spectrumWindow[i].toDouble()
                val s0L = spectrumBufL[i].toDouble() * w + coeff * s1L - s2L
                s2L = s1L; s1L = s0L

                val s0R = spectrumBufR[i].toDouble() * w + coeff * s1R - s2R
                s2R = s1R; s1R = s0R
            }
            val powerL = (s1L * s1L + s2L * s2L - coeff * s1L * s2L).coerceAtLeast(0.0)
            val powerR = (s1R * s1R + s2R * s2R - coeff * s1R * s2R).coerceAtLeast(0.0)
            val magL = sqrt(powerL) * scaleNorm
            val magR = sqrt(powerR) * scaleNorm
            val dbL = 20.0 * log10(magL + 1e-6)
            val dbR = 20.0 * log10(magR + 1e-6)
            val normL = ((dbL + 56.0) / 56.0).toFloat().coerceIn(0f, 1f)
            val normR = ((dbR + 56.0) / 56.0).toFloat().coerceIn(0f, 1f)

            val prevL = smoothedBandsL[b]
            val prevR = smoothedBandsR[b]
            val smL = if (normL >= prevL) prevL + (normL - prevL) * 0.55f else prevL + (normL - prevL) * 0.25f
            val smR = if (normR >= prevR) prevR + (normR - prevR) * 0.55f else prevR + (normR - prevR) * 0.25f
            smoothedBandsL[b] = smL
            smoothedBandsR[b] = smR
            nextL[b] = smL
            nextR[b] = smR
        }
        publishedBandsL = nextL
        publishedBandsR = nextR
        publishedMidRms = midRms
        publishedSideRms = sideRms
        publishedStereoSepPct = sepPct
        lastSpectrumTimestampMs = System.currentTimeMillis()
    }

    fun setRoadNoiseSpeedKmH(speedKmH: Float) {
        val clamped = speedKmH.coerceIn(0f, 240f)
        if (abs(clamped - currentSpeedKmH) < 2f) return
        currentSpeedKmH = clamped
        coeffVersion++
    }

    fun setReplayGainLinear(linear: Float) {
        replayGainLinear = linear.coerceIn(0.18f, 2.0f)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val supportedEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_24BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_32BIT
        if (!supportedEncoding || inputAudioFormat.channelCount !in 1..2) {
            isPcmConfigured = false
            return AudioFormat.NOT_SET
        }
        inputEncoding = inputAudioFormat.encoding
        sampleRateHz = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        channelCount = inputAudioFormat.channelCount
        activeSampleRateHz = sampleRateHz
        activeChannelCount = channelCount
        isPcmConfigured = true

        val stageSize = ((sampleRateHz * MAX_STAGE_DELAY_MS / 1000f).toInt() + 16).coerceAtLeast(256)
        if (delayBufferL.size < stageSize) {
            delayBufferL = FloatArray(stageSize)
            delayBufferR = FloatArray(stageSize)
        }
        val subSize = ((sampleRateHz * MAX_SUB_DELAY_MS / 1000f).toInt() + 16).coerceAtLeast(512)
        if (subDelayBufferL.size < subSize) {
            subDelayBufferL = FloatArray(subSize)
            subDelayBufferR = FloatArray(subSize)
        }
        val earlySize = ((sampleRateHz * MAX_EARLY_DELAY_MS / 1000f).toInt() + 64).coerceAtLeast(2048)
        if (earlyAmbBuffer.size < earlySize) {
            earlyAmbBuffer = FloatArray(earlySize)
        }

        diffAp1LenL = ((SCHROEDER_AP1_MS / 1000f) * sampleRateHz).roundToInt().coerceAtLeast(32)
        diffAp1LenR = (((SCHROEDER_AP1_MS + 0.65f) / 1000f) * sampleRateHz).roundToInt().coerceAtLeast(37)
        val ap1BufSize = maxOf(diffAp1LenL, diffAp1LenR) + 16
        if (diffAp1BufL.size < ap1BufSize) {
            diffAp1BufL = FloatArray(ap1BufSize)
            diffAp1BufR = FloatArray(ap1BufSize)
        }

        diffAp2LenL = ((SCHROEDER_AP2_MS / 1000f) * sampleRateHz).roundToInt().coerceAtLeast(64)
        diffAp2LenR = (((SCHROEDER_AP2_MS + 1.15f) / 1000f) * sampleRateHz).roundToInt().coerceAtLeast(73)
        val ap2BufSize = maxOf(diffAp2LenL, diffAp2LenR) + 16
        if (diffAp2BufL.size < ap2BufSize) {
            diffAp2BufL = FloatArray(ap2BufSize)
            diffAp2BufR = FloatArray(ap2BufSize)
        }

        hallCombLenL = ((HALL_COMB_L_MS / 1000f) * sampleRateHz).roundToInt().coerceAtLeast(256)
        hallCombLenR = ((HALL_COMB_R_MS / 1000f) * sampleRateHz).roundToInt().coerceAtLeast(311)
        val hallBufSize = maxOf(hallCombLenL, hallCombLenR) + 16
        if (hallCombBufL.size < hallBufSize) {
            hallCombBufL = FloatArray(hallBufSize)
            hallCombBufR = FloatArray(hallBufSize)
        }

        val xtcSize = ((0.002 * sampleRateHz).toInt() + 16).coerceAtLeast(128)
        if (xtcBufferL.size < xtcSize) {
            xtcBufferL = FloatArray(xtcSize)
            xtcBufferR = FloatArray(xtcSize)
        }

        updateSpectrumCoefficients()
        recalculateCoefficients()
        smoothedWidth = computedEffectiveWidth.toDouble()
        smoothedHeadroomGain = targetHeadroomGain
        // Always output 16-bit PCM so DefaultAudioSink & Hardware DynamicsProcessing stay active
        return AudioFormat(sampleRateHz, channelCount, C.ENCODING_PCM_16BIT)
    }

    private fun recalculateCoefficients() {
        activeCoeffVersion = coeffVersion
        val sr = sampleRateHz.toDouble()
        // ~18 ms exponential smoothing coefficient for click-free mode switches
        smoothCoeff = 1.0 - exp(-1.0 / (0.018 * sr).coerceAtLeast(1.0))

        // 1. HPF (Butterworth Q=0.7071 for 12 dB/oct, cascaded Q=0.7071 twice for 24 dB/oct LR4)
        val cutoff = hpfHz
        if (cutoff <= 0 || cutoff >= sampleRateHz / 2) {
            hpfB0 = 1.0; hpfB1 = 0.0; hpfB2 = 0.0; hpfA1 = 0.0; hpfA2 = 0.0
        } else {
            val omega = 2.0 * PI * cutoff.toDouble() / sr
            val sn = sin(omega)
            val cs = cos(omega)
            val alpha = sn / sqrt(2.0)
            val a0 = 1.0 + alpha
            hpfB0 = ((1.0 + cs) / 2.0) / a0
            hpfB1 = (-(1.0 + cs)) / a0
            hpfB2 = ((1.0 + cs) / 2.0) / a0
            hpfA1 = (-2.0 * cs) / a0
            hpfA2 = (1.0 - alpha) / a0
        }

        // 2. Sub-band LPF
        val subSplitHz = if (subLpfHz > 0) subLpfHz else 100
        if (subSplitHz >= sampleRateHz / 2) {
            subB0 = 0.0; subB1 = 0.0; subB2 = 0.0; subA1 = 0.0; subA2 = 0.0
        } else {
            val omega = 2.0 * PI * subSplitHz.toDouble() / sr
            val sn = sin(omega)
            val cs = cos(omega)
            val alpha = sn / sqrt(2.0)
            val a0 = 1.0 + alpha
            subB0 = ((1.0 - cs) / 2.0) / a0
            subB1 = (1.0 - cs) / a0
            subB2 = ((1.0 - cs) / 2.0) / a0
            subA1 = (-2.0 * cs) / a0
            subA2 = (1.0 - alpha) / a0
        }

        val trimLin = 10.0.pow(subTrimDb.toDouble() / 20.0)
        subGainSigned = if (subPhaseInverted) -trimLin else trimLin
        subDelaySamples = ((subDelayMs / 1000f) * sampleRateHz).roundToInt().coerceIn(0, subDelayBufferL.size - 1)

        // 3. Road-Noise Acoustic Contour (Low-shelf @ 115 Hz + Vocal Presence Peak @ 2.8 kHz)
        val (rnLowDb, rnMidDb) = calculateRoadNoiseContourDb(currentSpeedKmH, roadNoiseEqEnabled)
        if (rnLowDb >= 0.08f && 115.0 < sr * 0.45) {
            rnLowActive = true
            val a = 10.0.pow(rnLowDb.toDouble() / 40.0)
            val w0 = 2.0 * PI * 115.0 / sr
            val cs = cos(w0)
            val sn = sin(w0)
            val alpha = sn / 2.0 * sqrt((a + 1.0 / a) * (1.0 / 0.7 - 1.0) + 2.0)
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
            val a0 = (a + 1.0) + (a - 1.0) * cs + twoSqrtAAlpha
            rnLowB0 = (a * ((a + 1.0) - (a - 1.0) * cs + twoSqrtAAlpha)) / a0
            rnLowB1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cs)) / a0
            rnLowB2 = (a * ((a + 1.0) - (a - 1.0) * cs - twoSqrtAAlpha)) / a0
            rnLowA1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cs)) / a0
            rnLowA2 = ((a + 1.0) + (a - 1.0) * cs - twoSqrtAAlpha) / a0
        } else {
            rnLowActive = false
        }

        if (rnMidDb >= 0.08f && 2800.0 < sr * 0.45) {
            rnMidActive = true
            val a = 10.0.pow(rnMidDb.toDouble() / 40.0)
            val w0 = 2.0 * PI * 2800.0 / sr
            val cs = cos(w0)
            val alpha = sin(w0) / (2.0 * 1.1)
            val a0 = 1.0 + alpha / a
            rnMidB0 = (1.0 + alpha * a) / a0
            rnMidB1 = (-2.0 * cs) / a0
            rnMidB2 = (1.0 - alpha * a) / a0
            rnMidA1 = (-2.0 * cs) / a0
            rnMidA2 = (1.0 - alpha / a) / a0
        } else {
            rnMidActive = false
        }

        // 4. Multi-Stage Spatial Surround Matrix (Harman Logic 7 / Concert Hall / Studio / Dolby PLII / Atmos 3D / Virtual 360°)
        val intensityNorm = surroundIntensity / 100.0
        val coherenceNorm = centerCoherence / 100.0
        val heightNorm = virtualHeight / 100.0

        val baseWidthPct = when {
            !dspEnabled -> 100f
            monoFoldDown -> 0f
            else -> stereoWidthPercent.toFloat()
        }
        val blendedWidthPct = if (dspEnabled && !monoFoldDown && surroundMode != SurroundMode.OFF) {
            val modeDelta = (surroundMode.sideWidthTarget - 100f) * intensityNorm.toFloat()
            (baseWidthPct + modeDelta).coerceIn(0f, 200f)
        } else {
            baseWidthPct
        }
        computedEffectiveWidth = blendedWidthPct / 100f
        activeEffectiveWidthPercent = blendedWidthPct.roundToInt()

        if (dspEnabled && !monoFoldDown && surroundMode != SurroundMode.OFF && 2200.0 < sr * 0.45) {
            // Gentle pre-matrix headroom (-0.4..-1.3 dB) keeps perceived cabin loudness consistent
            // with Stereo Direct while giving wide reflections room before the soft-knee limiter.
            val headroomDb = (0.4 + intensityNorm * 0.65 + surroundMode.hallReverbMix * 0.45).coerceIn(0.4, 1.35)
            targetHeadroomGain = 10.0.pow(-headroomDb / 20.0)

            // Center vocal coherence bandpass @ 2.2 kHz (Q = 0.85)
            val wMid = 2.0 * PI * 2200.0 / sr
            val aMid = sin(wMid) / (2.0 * 0.85)
            val a0Mid = 1.0 + aMid
            l7MidB0 = aMid / a0Mid
            l7MidB1 = 0.0
            l7MidB2 = -aMid / a0Mid
            l7MidA1 = (-2.0 * cos(wMid)) / a0Mid
            l7MidA2 = (1.0 - aMid) / a0Mid

            // Wideband ambience conditioner: 220 Hz 1-pole HPF (keeps bass tight) + 5.2 kHz 1-pole LPF (silky wrap)
            ambHpfAlpha = exp(-2.0 * PI * 220.0 / sr).coerceIn(0.85, 0.995)
            ambLpfAlpha = (1.0 - exp(-2.0 * PI * 5200.0 / sr)).coerceIn(0.15, 0.85)
            hallDampCoeff = (1.0 - exp(-2.0 * PI * 4200.0 / sr)).coerceIn(0.12, 0.78)

            // Dual-Stage 90° Quadrature All-Pass Phase Network (750 Hz + 2100 Hz)
            l7ApActive = surroundMode.useAllPassPhase && 2100.0 < sr * 0.45
            if (l7ApActive) {
                val w1 = 2.0 * PI * 750.0 / sr
                val cs1 = cos(w1)
                val al1 = sin(w1) / (2.0 * 0.7071)
                val a01 = 1.0 + al1
                l7Ap1B0 = (1.0 - al1) / a01
                l7Ap1B1 = (-2.0 * cs1) / a01
                l7Ap1B2 = (1.0 + al1) / a01
                l7Ap1A1 = (-2.0 * cs1) / a01
                l7Ap1A2 = (1.0 - al1) / a01

                val w2 = 2.0 * PI * 2100.0 / sr
                val cs2 = cos(w2)
                val al2 = sin(w2) / (2.0 * 0.7071)
                val a02 = 1.0 + al2
                l7Ap2B0 = (1.0 - al2) / a02
                l7Ap2B1 = (-2.0 * cs2) / a02
                l7Ap2B2 = (1.0 + al2) / a02
                l7Ap2A1 = (-2.0 * cs2) / a02
                l7Ap2A2 = (1.0 - al2) / a02
            }

            // 180 Hz Cabin Warmth Low-Shelf (Logic 7 / Hall / Virtual 360 full-bodied cabin wrap)
            val warmthDb = intensityNorm * surroundMode.cabinWarmthBoostDb.toDouble()
            if (warmthDb >= 0.15 && 180.0 < sr * 0.45) {
                warmthActive = true
                val aW = 10.0.pow(warmthDb / 40.0)
                val wW = 2.0 * PI * 180.0 / sr
                val csW = cos(wW)
                val snW = sin(wW)
                val alW = snW / 2.0 * sqrt((aW + 1.0 / aW) * (1.0 / 0.7 - 1.0) + 2.0)
                val twoSqrtW = 2.0 * sqrt(aW) * alW
                val a0W = (aW + 1.0) + (aW - 1.0) * csW + twoSqrtW
                warmthB0 = (aW * ((aW + 1.0) - (aW - 1.0) * csW + twoSqrtW)) / a0W
                warmthB1 = (2.0 * aW * ((aW - 1.0) - (aW + 1.0) * csW)) / a0W
                warmthB2 = (aW * ((aW + 1.0) - (aW - 1.0) * csW - twoSqrtW)) / a0W
                warmthA1 = (-2.0 * ((aW - 1.0) + (aW + 1.0) * csW)) / a0W
                warmthA2 = ((aW + 1.0) + (aW - 1.0) * csW - twoSqrtW) / a0W
            } else {
                warmthActive = false
            }

            // Overhead Pinna Height Elevation Filter @ 7.2 kHz + subtle 4.2 kHz pinna notch (Atmos 3D / Virtual 360)
            val heightDb = heightNorm * surroundMode.heightBoostMaxDb.toDouble()
            if (heightDb >= 0.1 && 7200.0 < sr * 0.45) {
                heightActive = true
                val aH = 10.0.pow(heightDb / 40.0)
                val wH = 2.0 * PI * 7200.0 / sr
                val csH = cos(wH)
                val alphaH = sin(wH) / (2.0 * 0.80)
                val a0H = 1.0 + alphaH / aH
                heightB0 = (1.0 + alphaH * aH) / a0H
                heightB1 = (-2.0 * csH) / a0H
                heightB2 = (1.0 - alphaH * aH) / a0H
                heightA1 = (-2.0 * csH) / a0H
                heightA2 = (1.0 - alphaH / aH) / a0H
            } else {
                heightActive = false
            }

            val notchDb = if (surroundMode == SurroundMode.DOLBY_ATMOS_VIRTUAL && heightNorm > 0.2) {
                -1.8 * heightNorm
            } else {
                0.0
            }
            if (notchDb <= -0.25 && 4200.0 < sr * 0.45) {
                pinnaNotchActive = true
                val aN = 10.0.pow(notchDb / 40.0)
                val wN = 2.0 * PI * 4200.0 / sr
                val csN = cos(wN)
                val alphaN = sin(wN) / (2.0 * 1.4)
                val a0N = 1.0 + alphaN / aN
                notchB0 = (1.0 + alphaN * aN) / a0N
                notchB1 = (-2.0 * csN) / a0N
                notchB2 = (1.0 - alphaN * aN) / a0N
                notchA1 = (-2.0 * csN) / a0N
                notchA2 = (1.0 - alphaN / aN) / a0N
            } else {
                pinnaNotchActive = false
            }

            // 4-Tap Asymmetric Early Reflection tap positions (distinct L vs R prime-like ratios)
            val maxEarly = earlyAmbBuffer.size - 2
            val baseL = ((surroundMode.haasLeftMs / 1000f) * sampleRateHz).roundToInt().coerceIn(8, maxEarly / 4)
            val baseR = ((surroundMode.haasRightMs / 1000f) * sampleRateHz).roundToInt().coerceIn(12, maxEarly / 4)
            tapL1 = baseL.coerceIn(1, maxEarly)
            tapL2 = (baseL * 1.63f).roundToInt().coerceIn(tapL1 + 1, maxEarly)
            tapL3 = (baseL * 2.39f).roundToInt().coerceIn(tapL2 + 1, maxEarly)
            tapL4 = (baseL * 3.17f).roundToInt().coerceIn(tapL3 + 1, maxEarly)

            tapR1 = baseR.coerceIn(1, maxEarly)
            tapR2 = (baseR * 1.54f).roundToInt().coerceIn(tapR1 + 1, maxEarly)
            tapR3 = (baseR * 2.26f).roundToInt().coerceIn(tapR2 + 1, maxEarly)
            tapR4 = (baseR * 2.97f).roundToInt().coerceIn(tapR3 + 1, maxEarly)

            val centerBoostDb = coherenceNorm * surroundMode.centerBoostMaxDb.toDouble()
            l7CenterInjectGain = if (centerBoostDb > 0.05) (10.0.pow(centerBoostDb / 20.0) - 1.0) else 0.0
            l7SideWetGain = intensityNorm * surroundMode.sideWetScale.toDouble()
            midToAmbienceMix = surroundMode.midToAmbienceMix.toDouble()
            diffuserG = (surroundMode.diffuserFeedback.toDouble() * (0.55 + 0.45 * intensityNorm)).coerceIn(0.0, 0.62)
            hallReverbMixGain = intensityNorm * surroundMode.hallReverbMix.toDouble()
            hallDecayFeedback = surroundMode.hallDecayFeedback.toDouble().coerceIn(0.0, 0.65)

            // Band-limited 0.55 ms ITD Cross-Talk Cancellation (280 Hz HPF + 5.5 kHz LPF)
            xtcDelaySamples = (0.00055 * sampleRateHz).roundToInt().coerceIn(1, xtcBufferL.size - 1)
            xtcGain = (intensityNorm * surroundMode.crosstalkCancelGain.toDouble()).coerceIn(0.0, 0.42)
            xtcHpfAlpha = exp(-2.0 * PI * 280.0 / sr).coerceIn(0.85, 0.995)
            xtcLpfAlpha = (1.0 - exp(-2.0 * PI * 5500.0 / sr)).coerceIn(0.15, 0.85)
        } else {
            targetHeadroomGain = 1.0
            l7CenterInjectGain = 0.0
            l7SideWetGain = 0.0
            midToAmbienceMix = 0.0
            diffuserG = 0.0
            hallReverbMixGain = 0.0
            hallDecayFeedback = 0.0
            l7ApActive = false
            warmthActive = false
            heightActive = false
            pinnaNotchActive = false
            xtcGain = 0.0
        }

        // 5. Limiter & L/R Stage Time Alignment
        val ceilingLin = 10.0.pow(peakLimiterCeilingDb.toDouble() / 20.0).coerceIn(0.5, 1.0)
        maxSampleAbs = 32767.0 * ceilingLin
        val maxIdx = delayBufferL.size - 1
        leftDelaySamples = ((leftDelayMs / 1000f) * sampleRateHz).roundToInt().coerceIn(0, maxIdx)
        rightDelaySamples = ((rightDelayMs / 1000f) * sampleRateHz).roundToInt().coerceIn(0, maxIdx)
    }

    private fun softLimitSample(sample: Double, ceiling: Double): Double {
        val absVal = abs(sample)
        val knee = ceiling * 0.88
        if (absVal <= knee) return sample
        val excess = (absVal - knee) / (ceiling - knee).coerceAtLeast(1.0)
        val limited = knee + (ceiling - knee) * tanh(excess)
        return if (sample >= 0.0) limited else -limited
    }

    private fun readSampleAsPcm16Double(buf: ByteBuffer): Double {
        return when (inputEncoding) {
            C.ENCODING_PCM_16BIT -> buf.getShort().toDouble()
            C.ENCODING_PCM_FLOAT -> (buf.getFloat().toDouble().coerceIn(-1.5, 1.5)) * 32767.0
            C.ENCODING_PCM_24BIT -> {
                val b0 = buf.get().toInt() and 0xFF
                val b1 = buf.get().toInt() and 0xFF
                val b2 = buf.get().toInt() // signed high byte
                val sample24 = (b2 shl 16) or (b1 shl 8) or b0
                sample24.toDouble() / 256.0
            }
            C.ENCODING_PCM_32BIT -> buf.getInt().toDouble() / 65536.0
            else -> buf.getShort().toDouble()
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (activeCoeffVersion != coeffVersion) {
            recalculateCoefficients()
        }

        val bytesPerInputSample = when (inputEncoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_32BIT -> 4
            else -> 2
        }
        val inFrameSize = channelCount * bytesPerInputSample
        val frameCount = remaining / inFrameSize
        if (frameCount <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val outBytes = frameCount * channelCount * 2
        val outBuffer = replaceOutputBuffer(outBytes)
        outBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val inRo = inputBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)

        val rg = replayGainLinear
        val useReplayGain = abs(rg - 1.0f) >= 0.005f
        val useSubStage = dspEnabled && (subPhaseInverted || subLpfHz > 0 || abs(subTrimDb) >= 0.1f || subDelaySamples > 0)
        val useRoadNoise = dspEnabled && (rnLowActive || rnMidActive)
        val useSurround = dspEnabled && channelCount == 2 && !monoFoldDown &&
            surroundMode != SurroundMode.OFF &&
            (l7CenterInjectGain > 0.002 || l7SideWetGain > 0.002 || hallReverbMixGain > 0.002 || heightActive || xtcGain > 0.002)
        val targetWidth = computedEffectiveWidth.toDouble()
        val useLimiterClamp = dspEnabled && peakLimiterCeilingDb < -0.05f
        val useDsp = dspEnabled &&
            (hpfHz > 0 || leftDelaySamples > 0 || rightDelaySamples > 0 ||
                abs(targetWidth - 1.0) > 0.005 || monoFoldDown || useSubStage ||
                useRoadNoise || useSurround || useLimiterClamp)

        if (!useDsp && !useReplayGain && inputEncoding == C.ENCODING_PCM_16BIT) {
            smoothedWidth = 1.0
            smoothedHeadroomGain = 1.0
            val probe = inRo.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            var sumSq = 0.0
            var hash = 17
            val totalSamples = frameCount * channelCount
            if (channelCount == 2) {
                for (f in 0 until frameCount) {
                    val sL = probe.getShort().toInt()
                    val sR = probe.getShort().toInt()
                    sumSq += sL.toDouble() * sL.toDouble() + sR.toDouble() * sR.toDouble()
                    hash = (hash * 31 + sL) * 31 + sR
                    pushSpectrumFrame(sL / 32767f, sR / 32767f)
                }
            } else {
                for (s in 0 until totalSamples) {
                    val sample = probe.getShort().toInt()
                    sumSq += sample.toDouble() * sample.toDouble()
                    hash = hash * 31 + sample
                    val norm = sample / 32767f
                    pushSpectrumFrame(norm, norm)
                }
            }
            val rms = if (totalSamples > 0) (sqrt(sumSq / totalSamples) / 32767.0).toFloat() else 0f
            processedBufferCount++
            processedFrameCount += frameCount
            lastBufferBypassed = true
            lastBufferSurroundApplied = false
            lastBufferModified = false
            lastBufferInputRms = rms
            lastBufferOutputRms = rms
            lastBufferSurroundDeltaRms = 0f
            lastBufferPeakDelta = 0f
            lastInputChecksum = hash
            lastOutputChecksum = hash
            lastProcessedTimestampMs = System.currentTimeMillis()

            outBuffer.put(inRo)
            inputBuffer.position(inputBuffer.limit())
            outBuffer.flip()
            return
        }

        val useHpf = dspEnabled && hpfHz > 0
        val useLr4 = crossoverSlopeDb >= 24
        val useSubDelay = subDelaySamples > 0
        val useDelay = dspEnabled && (leftDelaySamples > 0 || rightDelaySamples > 0)
        val bufLen = delayBufferL.size
        val subBufLen = subDelayBufferL.size
        val earlyLen = earlyAmbBuffer.size
        val ceiling = if (dspEnabled) maxSampleAbs else 32767.0
        val useSoftKnee = useSurround || useLimiterClamp

        var sumInSq = 0.0
        var sumOutSq = 0.0
        var sumSurroundDeltaSq = 0.0
        var maxSurroundDeltaAbs = 0.0
        var inChecksum = 17
        var outChecksum = 17

        if (channelCount == 2) {
            for (f in 0 until frameCount) {
                val rawL = readSampleAsPcm16Double(inRo)
                val rawR = readSampleAsPcm16Double(inRo)
                sumInSq += rawL * rawL + rawR * rawR
                inChecksum = inChecksum * 31 + rawL.roundToInt()
                inChecksum = inChecksum * 31 + rawR.roundToInt()

                var l = rawL * rg
                var r = rawR * rg

                if (useHpf) {
                    var yL = hpfB0 * l + hpfB1 * hpf1X1L + hpfB2 * hpf1X2L - hpfA1 * hpf1Y1L - hpfA2 * hpf1Y2L
                    hpf1X2L = hpf1X1L; hpf1X1L = l; hpf1Y2L = hpf1Y1L; hpf1Y1L = yL
                    if (useLr4) {
                        val yL2 = hpfB0 * yL + hpfB1 * hpf2X1L + hpfB2 * hpf2X2L - hpfA1 * hpf2Y1L - hpfA2 * hpf2Y2L
                        hpf2X2L = hpf2X1L; hpf2X1L = yL; hpf2Y2L = hpf2Y1L; hpf2Y1L = yL2
                        yL = yL2
                    }
                    l = yL

                    var yR = hpfB0 * r + hpfB1 * hpf1X1R + hpfB2 * hpf1X2R - hpfA1 * hpf1Y1R - hpfA2 * hpf1Y2R
                    hpf1X2R = hpf1X1R; hpf1X1R = r; hpf1Y2R = hpf1Y1R; hpf1Y1R = yR
                    if (useLr4) {
                        val yR2 = hpfB0 * yR + hpfB1 * hpf2X1R + hpfB2 * hpf2X2R - hpfA1 * hpf2Y1R - hpfA2 * hpf2Y2R
                        hpf2X2R = hpf2X1R; hpf2X1R = yR; hpf2Y2R = hpf2Y1R; hpf2Y1R = yR2
                        yR = yR2
                    }
                    r = yR
                }

                if (useSubStage) {
                    var subL = subB0 * l + subB1 * sub1X1L + subB2 * sub1X2L - subA1 * sub1Y1L - subA2 * sub1Y2L
                    sub1X2L = sub1X1L; sub1X1L = l; sub1Y2L = sub1Y1L; sub1Y1L = subL
                    if (useLr4) {
                        val subL2 = subB0 * subL + subB1 * sub2X1L + subB2 * sub2X2L - subA1 * sub2Y1L - subA2 * sub2Y2L
                        sub2X2L = sub2X1L; sub2X1L = subL; sub2Y2L = sub2Y1L; sub2Y1L = subL2
                        subL = subL2
                    }
                    val hiL = l - subL

                    var subR = subB0 * r + subB1 * sub1X1R + subB2 * sub1X2R - subA1 * sub1Y1R - subA2 * sub1Y2R
                    sub1X2R = sub1X1R; sub1X1R = r; sub1Y2R = sub1Y1R; sub1Y1R = subR
                    if (useLr4) {
                        val subR2 = subB0 * subR + subB1 * sub2X1R + subB2 * sub2X2R - subA1 * sub2Y1R - subA2 * sub2Y2R
                        sub2X2R = sub2X1R; sub2X1R = subR; sub2Y2R = sub2Y1R; sub2Y1R = subR2
                        subR = subR2
                    }
                    val hiR = r - subR

                    if (useSubDelay) {
                        subDelayBufferL[subDelayWritePos] = subL.toFloat()
                        subDelayBufferR[subDelayWritePos] = subR.toFloat()
                        val readPos = (subDelayWritePos - subDelaySamples + subBufLen) % subBufLen
                        subL = subDelayBufferL[readPos].toDouble()
                        subR = subDelayBufferR[readPos].toDouble()
                        subDelayWritePos = (subDelayWritePos + 1) % subBufLen
                    }

                    l = hiL + subL * subGainSigned
                    r = hiR + subR * subGainSigned
                }

                if (useRoadNoise) {
                    if (rnLowActive) {
                        val yL = rnLowB0 * l + rnLowB1 * rnLowX1L + rnLowB2 * rnLowX2L - rnLowA1 * rnLowY1L - rnLowA2 * rnLowY2L
                        rnLowX2L = rnLowX1L; rnLowX1L = l; rnLowY2L = rnLowY1L; rnLowY1L = yL
                        l = yL

                        val yR = rnLowB0 * r + rnLowB1 * rnLowX1R + rnLowB2 * rnLowX2R - rnLowA1 * rnLowY1R - rnLowA2 * rnLowY2R
                        rnLowX2R = rnLowX1R; rnLowX1R = r; rnLowY2R = rnLowY1R; rnLowY1R = yR
                        r = yR
                    }
                    if (rnMidActive) {
                        val yL = rnMidB0 * l + rnMidB1 * rnMidX1L + rnMidB2 * rnMidX2L - rnMidA1 * rnMidY1L - rnMidA2 * rnMidY2L
                        rnMidX2L = rnMidX1L; rnMidX1L = l; rnMidY2L = rnMidY1L; rnMidY1L = yL
                        l = yL

                        val yR = rnMidB0 * r + rnMidB1 * rnMidX1R + rnMidB2 * rnMidX2R - rnMidA1 * rnMidY1R - rnMidA2 * rnMidY2R
                        rnMidX2R = rnMidX1R; rnMidX1R = r; rnMidY2R = rnMidY1R; rnMidY1R = yR
                        r = yR
                    }
                }

                val preSurroundL = l
                val preSurroundR = r

                // Smoothly track headroom and stereo width to avoid zipper clicks on mode change
                smoothedHeadroomGain += (targetHeadroomGain - smoothedHeadroomGain) * smoothCoeff
                smoothedWidth += (targetWidth - smoothedWidth) * smoothCoeff

                if (useSurround) {
                    val hl = l * smoothedHeadroomGain
                    val hr = r * smoothedHeadroomGain

                    val midIn = (hl + hr) * 0.5
                    val sideIn = (hl - hr) * 0.5

                    // 1. Center Vocal Coherence Bandpass @ 2.2 kHz
                    val midBp = l7MidB0 * midIn + l7MidB1 * l7MidX1 + l7MidB2 * l7MidX2 - l7MidA1 * l7MidY1 - l7MidA2 * l7MidY2
                    l7MidX2 = l7MidX1; l7MidX1 = midIn; l7MidY2 = l7MidY1; l7MidY1 = midBp

                    // 2. Hybrid Ambience Extraction (Side + band-limited Mid -> works on centered & narrow mixes!)
                    val rawAmb = sideIn + midIn * midToAmbienceMix
                    val ambHp = ambHpfAlpha * (ambHpfPrevOut + rawAmb - ambHpfPrevIn)
                    ambHpfPrevIn = rawAmb
                    ambHpfPrevOut = ambHp
                    ambLpfState += ambLpfAlpha * (ambHp - ambLpfState)
                    var ambBus = ambLpfState

                    // 3. Dual-Stage 90° Quadrature All-Pass Phase Network (Dolby PLII / Logic 7 / Atmos / Virtual 360)
                    if (l7ApActive) {
                        val ap1 = l7Ap1B0 * ambBus + l7Ap1B1 * l7Ap1X1 + l7Ap1B2 * l7Ap1X2 - l7Ap1A1 * l7Ap1Y1 - l7Ap1A2 * l7Ap1Y2
                        l7Ap1X2 = l7Ap1X1; l7Ap1X1 = ambBus; l7Ap1Y2 = l7Ap1Y1; l7Ap1Y1 = ap1
                        val ap2 = l7Ap2B0 * ap1 + l7Ap2B1 * l7Ap2X1 + l7Ap2B2 * l7Ap2X2 - l7Ap2A1 * l7Ap2Y1 - l7Ap2A2 * l7Ap2Y2
                        l7Ap2X2 = l7Ap2X1; l7Ap2X1 = ap1; l7Ap2Y2 = l7Ap2Y1; l7Ap2Y1 = ap2
                        ambBus = ap2
                    }

                    // 4. 4-Tap Asymmetric Early Cabin Reflection Matrix (decorrelated L vs R delays & polarities)
                    earlyAmbBuffer[earlyWritePos] = ambBus.toFloat()
                    var idx = earlyWritePos - tapL1; if (idx < 0) idx += earlyLen
                    val eL1 = earlyAmbBuffer[idx].toDouble()
                    idx = earlyWritePos - tapL2; if (idx < 0) idx += earlyLen
                    val eL2 = earlyAmbBuffer[idx].toDouble()
                    idx = earlyWritePos - tapL3; if (idx < 0) idx += earlyLen
                    val eL3 = earlyAmbBuffer[idx].toDouble()
                    idx = earlyWritePos - tapL4; if (idx < 0) idx += earlyLen
                    val eL4 = earlyAmbBuffer[idx].toDouble()

                    idx = earlyWritePos - tapR1; if (idx < 0) idx += earlyLen
                    val eR1 = earlyAmbBuffer[idx].toDouble()
                    idx = earlyWritePos - tapR2; if (idx < 0) idx += earlyLen
                    val eR2 = earlyAmbBuffer[idx].toDouble()
                    idx = earlyWritePos - tapR3; if (idx < 0) idx += earlyLen
                    val eR3 = earlyAmbBuffer[idx].toDouble()
                    idx = earlyWritePos - tapR4; if (idx < 0) idx += earlyLen
                    val eR4 = earlyAmbBuffer[idx].toDouble()

                    earlyWritePos++
                    if (earlyWritePos >= earlyLen) earlyWritePos = 0

                    var wetL = eL1 * 0.42 - eL2 * 0.28 + eL3 * 0.20 - eL4 * 0.14
                    var wetR = -eR1 * 0.40 + eR2 * 0.28 - eR3 * 0.20 + eR4 * 0.14

                    // 5. Dual-Stage Cascaded Schroeder All-Pass Diffuser (silky cabin surround wrap)
                    if (diffuserG > 0.02) {
                        val g = diffuserG
                        val d1L = diffAp1BufL[diffAp1Pos % diffAp1LenL].toDouble()
                        val v1L = wetL + g * d1L
                        diffAp1BufL[diffAp1Pos % diffAp1LenL] = v1L.toFloat()
                        val out1L = d1L - g * v1L

                        val d1R = diffAp1BufR[diffAp1Pos % diffAp1LenR].toDouble()
                        val v1R = wetR + g * d1R
                        diffAp1BufR[diffAp1Pos % diffAp1LenR] = v1R.toFloat()
                        val out1R = d1R - g * v1R
                        diffAp1Pos = (diffAp1Pos + 1) and 0x3FFFFFFF

                        val d2L = diffAp2BufL[diffAp2Pos % diffAp2LenL].toDouble()
                        val v2L = out1L + g * 0.85 * d2L
                        diffAp2BufL[diffAp2Pos % diffAp2LenL] = v2L.toFloat()
                        wetL = d2L - g * 0.85 * v2L

                        val d2R = diffAp2BufR[diffAp2Pos % diffAp2LenR].toDouble()
                        val v2R = out1R + g * 0.85 * d2R
                        diffAp2BufR[diffAp2Pos % diffAp2LenR] = v2R.toFloat()
                        wetR = d2R - g * 0.85 * v2R
                        diffAp2Pos = (diffAp2Pos + 1) and 0x3FFFFFFF
                    }

                    // 6. Damped Stereo Feedback Comb Hall Tank (natural acoustic room / hall decay)
                    var hallL = 0.0
                    var hallR = 0.0
                    if (hallReverbMixGain > 0.005) {
                        val delayedL = hallCombBufL[hallCombPosL].toDouble()
                        hallDampStateL += hallDampCoeff * (delayedL - hallDampStateL)
                        hallCombBufL[hallCombPosL] = (wetL + hallDampStateL * hallDecayFeedback).toFloat()
                        hallCombPosL++
                        if (hallCombPosL >= hallCombLenL) hallCombPosL = 0
                        hallL = hallDampStateL

                        val delayedR = hallCombBufR[hallCombPosR].toDouble()
                        hallDampStateR += hallDampCoeff * (delayedR - hallDampStateR)
                        hallCombBufR[hallCombPosR] = (wetR + hallDampStateR * hallDecayFeedback).toFloat()
                        hallCombPosR++
                        if (hallCombPosR >= hallCombLenR) hallCombPosR = 0
                        hallR = hallDampStateR
                    }

                    val centerLift = midBp * l7CenterInjectGain

                    // Widen the direct stereo bed first so reflections & hall tank wrap around
                    // the widened stage instead of comb-cancelling or getting over-widened.
                    val directSide = (hl - hr) * 0.5 * smoothedWidth
                    val directWideL = midIn + directSide
                    val directWideR = midIn - directSide

                    var outL = directWideL + centerLift + wetL * l7SideWetGain + hallL * hallReverbMixGain
                    var outR = directWideR + centerLift + wetR * l7SideWetGain + hallR * hallReverbMixGain

                    // 7. Band-Limited 0.55 ms ITD Inter-Aural Cross-Talk Cancellation (280 Hz–5.5 kHz)
                    if (xtcGain > 0.002) {
                        val hpL = xtcHpfAlpha * (xtcHpfPrevOutL + hl - xtcHpfPrevInL)
                        xtcHpfPrevInL = hl; xtcHpfPrevOutL = hpL
                        xtcLpfStateL += xtcLpfAlpha * (hpL - xtcLpfStateL)

                        val hpR = xtcHpfAlpha * (xtcHpfPrevOutR + hr - xtcHpfPrevInR)
                        xtcHpfPrevInR = hr; xtcHpfPrevOutR = hpR
                        xtcLpfStateR += xtcLpfAlpha * (hpR - xtcLpfStateR)

                        val xtcLen = xtcBufferL.size
                        xtcBufferL[xtcWritePos] = xtcLpfStateL.toFloat()
                        xtcBufferR[xtcWritePos] = xtcLpfStateR.toFloat()
                        val xtcRead = (xtcWritePos - xtcDelaySamples + xtcLen) % xtcLen
                        outL -= xtcBufferR[xtcRead].toDouble() * xtcGain
                        outR -= xtcBufferL[xtcRead].toDouble() * xtcGain
                        xtcWritePos++
                        if (xtcWritePos >= xtcLen) xtcWritePos = 0
                    }

                    // 8. 180 Hz Cabin Warmth Shelf
                    if (warmthActive) {
                        val wL = warmthB0 * outL + warmthB1 * warmthX1L + warmthB2 * warmthX2L - warmthA1 * warmthY1L - warmthA2 * warmthY2L
                        warmthX2L = warmthX1L; warmthX1L = outL; warmthY2L = warmthY1L; warmthY1L = wL
                        outL = wL

                        val wR = warmthB0 * outR + warmthB1 * warmthX1R + warmthB2 * warmthX2R - warmthA1 * warmthY1R - warmthA2 * warmthY2R
                        warmthX2R = warmthX1R; warmthX1R = outR; warmthY2R = warmthY1R; warmthY1R = wR
                        outR = wR
                    }

                    // 9. Atmos / Virtual 360° Pinna Elevation Contour (4.2 kHz subtle notch + 7.2 kHz overhead air)
                    if (pinnaNotchActive) {
                        val nL = notchB0 * outL + notchB1 * notchX1L + notchB2 * notchX2L - notchA1 * notchY1L - notchA2 * notchY2L
                        notchX2L = notchX1L; notchX1L = outL; notchY2L = notchY1L; notchY1L = nL
                        outL = nL

                        val nR = notchB0 * outR + notchB1 * notchX1R + notchB2 * notchX2R - notchA1 * notchY1R - notchA2 * notchY2R
                        notchX2R = notchX1R; notchX1R = outR; notchY2R = notchY1R; notchY1R = nR
                        outR = nR
                    }

                    if (heightActive) {
                        val hL = heightB0 * outL + heightB1 * heightX1L + heightB2 * heightX2L - heightA1 * heightY1L - heightA2 * heightY2L
                        heightX2L = heightX1L; heightX1L = outL; heightY2L = heightY1L; heightY1L = hL
                        outL = hL

                        val hR = heightB0 * outR + heightB1 * heightX1R + heightB2 * heightX2R - heightA1 * heightY1R - heightA2 * heightY2R
                        heightX2R = heightX1R; heightX1R = outR; heightY2R = heightY1R; heightY1R = hR
                        outR = hR
                    }

                    l = outL
                    r = outR
                } else {
                    // Standalone Mid/Side Width Control or Mono Fold-Down when Surround Matrix is OFF
                    val activeWidth = if (monoFoldDown) 0.0 else smoothedWidth
                    if (abs(activeWidth - 1.0) > 0.005) {
                        val mid = (l + r) * 0.5
                        val side = (l - r) * 0.5 * activeWidth
                        l = mid + side
                        r = mid - side
                    }
                }

                val dL = l - preSurroundL
                val dR = r - preSurroundR
                sumSurroundDeltaSq += dL * dL + dR * dR
                val absDL = abs(dL)
                val absDR = abs(dR)
                if (absDL > maxSurroundDeltaAbs) maxSurroundDeltaAbs = absDL
                if (absDR > maxSurroundDeltaAbs) maxSurroundDeltaAbs = absDR

                if (useDelay) {
                    delayBufferL[delayWritePos] = l.toFloat()
                    delayBufferR[delayWritePos] = r.toFloat()
                    val readL = (delayWritePos - leftDelaySamples + bufLen) % bufLen
                    val readR = (delayWritePos - rightDelaySamples + bufLen) % bufLen
                    l = delayBufferL[readL].toDouble()
                    r = delayBufferR[readR].toDouble()
                    delayWritePos = (delayWritePos + 1) % bufLen
                }

                val safeL = if (useSoftKnee) softLimitSample(l, ceiling) else l
                val safeR = if (useSoftKnee) softLimitSample(r, ceiling) else r
                val clampedL = safeL.coerceIn(-ceiling, ceiling).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                val clampedR = safeR.coerceIn(-ceiling, ceiling).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                sumOutSq += clampedL.toDouble() * clampedL.toDouble() + clampedR.toDouble() * clampedR.toDouble()
                outChecksum = outChecksum * 31 + clampedL.toInt()
                outChecksum = outChecksum * 31 + clampedR.toInt()
                pushSpectrumFrame(clampedL / 32767f, clampedR / 32767f)
                outBuffer.putShort(clampedL)
                outBuffer.putShort(clampedR)
            }
        } else {
            for (f in 0 until frameCount) {
                val rawM = readSampleAsPcm16Double(inRo)
                sumInSq += rawM * rawM
                inChecksum = inChecksum * 31 + rawM.roundToInt()
                var m = rawM * rg
                if (useHpf) {
                    var yM = hpfB0 * m + hpfB1 * hpf1X1L + hpfB2 * hpf1X2L - hpfA1 * hpf1Y1L - hpfA2 * hpf1Y2L
                    hpf1X2L = hpf1X1L; hpf1X1L = m; hpf1Y2L = hpf1Y1L; hpf1Y1L = yM
                    if (useLr4) {
                        val yM2 = hpfB0 * yM + hpfB1 * hpf2X1L + hpfB2 * hpf2X2L - hpfA1 * hpf2Y1L - hpfA2 * hpf2Y2L
                        hpf2X2L = hpf2X1L; hpf2X1L = yM; hpf2Y2L = hpf2Y1L; hpf2Y1L = yM2
                        yM = yM2
                    }
                    m = yM
                }
                if (useSubStage) {
                    var subM = subB0 * m + subB1 * sub1X1L + subB2 * sub1X2L - subA1 * sub1Y1L - subA2 * sub1Y2L
                    sub1X2L = sub1X1L; sub1X1L = m; sub1Y2L = sub1Y1L; sub1Y1L = subM
                    if (useLr4) {
                        val subM2 = subB0 * subM + subB1 * sub2X1L + subB2 * sub2X2L - subA1 * sub2Y1L - subA2 * sub2Y2L
                        sub2X2L = sub2X1L; sub2X1L = subM; sub2Y2L = sub2Y1L; sub2Y1L = subM2
                        subM = subM2
                    }
                    val hiM = m - subM
                    if (useSubDelay) {
                        subDelayBufferL[subDelayWritePos] = subM.toFloat()
                        val readPos = (subDelayWritePos - subDelaySamples + subBufLen) % subBufLen
                        subM = subDelayBufferL[readPos].toDouble()
                        subDelayWritePos = (subDelayWritePos + 1) % subBufLen
                    }
                    m = hiM + subM * subGainSigned
                }
                val clampedM = m.coerceIn(-ceiling, ceiling).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                sumOutSq += clampedM.toDouble() * clampedM.toDouble()
                outChecksum = outChecksum * 31 + clampedM.toInt()
                outBuffer.putShort(clampedM)
            }
        }

        val totalSamples = (frameCount * channelCount).coerceAtLeast(1)
        processedBufferCount++
        processedFrameCount += frameCount
        lastBufferBypassed = false
        lastBufferSurroundApplied = useSurround
        lastBufferInputRms = (sqrt(sumInSq / totalSamples) / 32767.0).toFloat()
        lastBufferOutputRms = (sqrt(sumOutSq / totalSamples) / 32767.0).toFloat()
        lastBufferSurroundDeltaRms = (sqrt(sumSurroundDeltaSq / totalSamples) / 32767.0).toFloat()
        lastBufferPeakDelta = (maxSurroundDeltaAbs / 32767.0).toFloat()
        lastInputChecksum = inChecksum
        lastOutputChecksum = outChecksum
        lastBufferModified = (inChecksum != outChecksum) || lastBufferSurroundDeltaRms > 0.00001f
        lastProcessedTimestampMs = System.currentTimeMillis()

        inputBuffer.position(inputBuffer.limit())
        outBuffer.flip()
    }

    override fun onFlush() {
        hpf1X1L = 0.0; hpf1X2L = 0.0; hpf1Y1L = 0.0; hpf1Y2L = 0.0
        hpf1X1R = 0.0; hpf1X2R = 0.0; hpf1Y1R = 0.0; hpf1Y2R = 0.0
        hpf2X1L = 0.0; hpf2X2L = 0.0; hpf2Y1L = 0.0; hpf2Y2L = 0.0
        hpf2X1R = 0.0; hpf2X2R = 0.0; hpf2Y1R = 0.0; hpf2Y2R = 0.0

        sub1X1L = 0.0; sub1X2L = 0.0; sub1Y1L = 0.0; sub1Y2L = 0.0
        sub1X1R = 0.0; sub1X2R = 0.0; sub1Y1R = 0.0; sub1Y2R = 0.0
        sub2X1L = 0.0; sub2X2L = 0.0; sub2Y1L = 0.0; sub2Y2L = 0.0
        sub2X1R = 0.0; sub2X2R = 0.0; sub2Y1R = 0.0; sub2Y2R = 0.0

        rnLowX1L = 0.0; rnLowX2L = 0.0; rnLowY1L = 0.0; rnLowY2L = 0.0
        rnLowX1R = 0.0; rnLowX2R = 0.0; rnLowY1R = 0.0; rnLowY2R = 0.0
        rnMidX1L = 0.0; rnMidX2L = 0.0; rnMidY1L = 0.0; rnMidY2L = 0.0
        rnMidX1R = 0.0; rnMidX2R = 0.0; rnMidY1R = 0.0; rnMidY2R = 0.0

        l7MidX1 = 0.0; l7MidX2 = 0.0; l7MidY1 = 0.0; l7MidY2 = 0.0
        ambHpfPrevIn = 0.0; ambHpfPrevOut = 0.0; ambLpfState = 0.0
        l7Ap1X1 = 0.0; l7Ap1X2 = 0.0; l7Ap1Y1 = 0.0; l7Ap1Y2 = 0.0
        l7Ap2X1 = 0.0; l7Ap2X2 = 0.0; l7Ap2Y1 = 0.0; l7Ap2Y2 = 0.0
        warmthX1L = 0.0; warmthX2L = 0.0; warmthY1L = 0.0; warmthY2L = 0.0
        warmthX1R = 0.0; warmthX2R = 0.0; warmthY1R = 0.0; warmthY2R = 0.0
        heightX1L = 0.0; heightX2L = 0.0; heightY1L = 0.0; heightY2L = 0.0
        heightX1R = 0.0; heightX2R = 0.0; heightY1R = 0.0; heightY2R = 0.0
        notchX1L = 0.0; notchX2L = 0.0; notchY1L = 0.0; notchY2L = 0.0
        notchX1R = 0.0; notchX2R = 0.0; notchY1R = 0.0; notchY2R = 0.0
        xtcHpfPrevInL = 0.0; xtcHpfPrevOutL = 0.0; xtcLpfStateL = 0.0
        xtcHpfPrevInR = 0.0; xtcHpfPrevOutR = 0.0; xtcLpfStateR = 0.0
        hallDampStateL = 0.0; hallDampStateR = 0.0

        subDelayBufferL.fill(0f)
        subDelayBufferR.fill(0f)
        subDelayWritePos = 0

        earlyAmbBuffer.fill(0f)
        earlyWritePos = 0

        diffAp1BufL.fill(0f)
        diffAp1BufR.fill(0f)
        diffAp1Pos = 0
        diffAp2BufL.fill(0f)
        diffAp2BufR.fill(0f)
        diffAp2Pos = 0

        hallCombBufL.fill(0f)
        hallCombBufR.fill(0f)
        hallCombPosL = 0
        hallCombPosR = 0

        xtcBufferL.fill(0f)
        xtcBufferR.fill(0f)
        xtcWritePos = 0

        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        delayWritePos = 0

        spectrumBufL.fill(0f)
        spectrumBufR.fill(0f)
        spectrumWritePos = 0
        smoothedBandsL.fill(0f)
        smoothedBandsR.fill(0f)

        recalculateCoefficients()
        smoothedWidth = computedEffectiveWidth.toDouble()
        smoothedHeadroomGain = targetHeadroomGain
    }

    override fun onReset() {
        onFlush()
    }
}
