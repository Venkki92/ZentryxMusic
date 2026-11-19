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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time Media3 PCM BaseAudioProcessor for Automotive DSP & Harman Logic 7 Surround stages:
 * 1. ReplayGain Loudness Normalization pre-trim gain stage.
 * 2. Selectable 12 dB/oct (BW2) or 24 dB/oct (Linkwitz-Riley LR4) High-Pass Filter (60/80/100/120 Hz).
 * 3. Phase-coherent Sub-Bass LPF Split (12 or 24 dB/oct) + 0°/180° Phase Flip + Sub Trim (-6..+6 dB)
 *    + Sub Delay Alignment (0.0..15.0 ms) to pull trunk bass forward onto the dash.
 * 4. Speed-Sensitive Road-Noise Acoustic Contour (115 Hz Low-Shelf + 2.8 kHz Vocal Presence Peak).
 * 5. Harman Logic 7 / Concert Hall / Studio Focused Surround Matrix:
 *    - Mid (L+R) 2.2 kHz center vocal coherence boost
 *    - Decorrelated Side (L-R) stereo Haas ambient reflections (+L / -R)
 * 6. Mid/Side Matrix Stereo Width Control (0%..150%) OR true Mono Fold-Down ((L+R)*0.5).
 * 7. True Millisecond L/R Time Alignment Delay Ring Buffer (0.0 – 5.0 ms).
 * 8. Master Peak Limiter Brickwall Ceiling (0.0 / -0.5 / -1.5 / -3.0 dBFS).
 */
@OptIn(UnstableApi::class)
@Singleton
class CarDspAudioProcessor @Inject constructor() : BaseAudioProcessor() {

    companion object {
        private const val MAX_STAGE_DELAY_MS = 6f
        private const val MAX_SUB_DELAY_MS = 16f
        private const val MAX_HAAS_DELAY_MS = 35f
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
    @Volatile private var surroundIntensity: Int = 55
    @Volatile private var centerCoherence: Int = 45
    @Volatile private var virtualHeight: Int = 45
    @Volatile private var roadNoiseEqEnabled: Boolean = true
    @Volatile private var currentSpeedKmH: Float = 0f
    @Volatile private var replayGainLinear: Float = 1.0f
    @Volatile private var coeffVersion: Int = 0

    @Volatile var activeSampleRateHz: Int = 44100
        private set
    @Volatile var activeChannelCount: Int = 2
        private set

    private var activeCoeffVersion: Int = -1
    private var sampleRateHz: Int = 44100
    private var channelCount: Int = 2

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

    // Surround Matrix: Mid 2.2 kHz bandpass + Side bandpass + Dolby PLII 90° All-Pass + Haas + Atmos Height + 360° XTC
    private var l7MidB0 = 0.0; private var l7MidB1 = 0.0; private var l7MidB2 = 0.0; private var l7MidA1 = 0.0; private var l7MidA2 = 0.0
    private var l7MidX1 = 0.0; private var l7MidX2 = 0.0; private var l7MidY1 = 0.0; private var l7MidY2 = 0.0
    private var l7SideB0 = 0.0; private var l7SideB1 = 0.0; private var l7SideB2 = 0.0; private var l7SideA1 = 0.0; private var l7SideA2 = 0.0
    private var l7SideX1 = 0.0; private var l7SideX2 = 0.0; private var l7SideY1 = 0.0; private var l7SideY2 = 0.0
    private var l7ApActive = false
    private var l7ApB0 = 1.0; private var l7ApB1 = 0.0; private var l7ApB2 = 0.0; private var l7ApA1 = 0.0; private var l7ApA2 = 0.0
    private var l7ApX1 = 0.0; private var l7ApX2 = 0.0; private var l7ApY1 = 0.0; private var l7ApY2 = 0.0
    private var haasSideBuffer = FloatArray(2048)
    private var haasWritePos = 0
    private var haasDelayLeftSamples = 0
    private var haasDelayRightSamples = 0
    private var l7CenterInjectGain = 0.0
    private var l7SideWetGain = 0.0

    // Dolby Virtual Atmos / 360° Overhead Pinna Height Elevation Filter (7.2 kHz peaking)
    private var heightActive = false
    private var heightB0 = 1.0; private var heightB1 = 0.0; private var heightB2 = 0.0; private var heightA1 = 0.0; private var heightA2 = 0.0
    private var heightX1L = 0.0; private var heightX2L = 0.0; private var heightY1L = 0.0; private var heightY2L = 0.0
    private var heightX1R = 0.0; private var heightX2R = 0.0; private var heightY1R = 0.0; private var heightY2R = 0.0

    // 360° Virtualizer Inter-Aural Cross-Talk Cancellation (0.55 ms ITD inverted cross-feed)
    private var xtcBufferL = FloatArray(128)
    private var xtcBufferR = FloatArray(128)
    private var xtcWritePos = 0
    private var xtcDelaySamples = 24
    private var xtcGain = 0.0

    // L/R Stage Time Alignment Delay buffer
    private var delayBufferL = FloatArray(1024)
    private var delayBufferR = FloatArray(1024)
    private var delayWritePos = 0
    private var leftDelaySamples = 0
    private var rightDelaySamples = 0

    private var maxSampleAbs: Double = 32767.0

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
        coeffVersion++
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
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            return AudioFormat.NOT_SET
        }
        sampleRateHz = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        channelCount = inputAudioFormat.channelCount
        activeSampleRateHz = sampleRateHz
        activeChannelCount = channelCount

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
        val haasSize = ((sampleRateHz * MAX_HAAS_DELAY_MS / 1000f).toInt() + 32).coerceAtLeast(1024)
        if (haasSideBuffer.size < haasSize) {
            haasSideBuffer = FloatArray(haasSize)
        }

        recalculateCoefficients()
        return inputAudioFormat
    }

    private fun recalculateCoefficients() {
        activeCoeffVersion = coeffVersion
        val sr = sampleRateHz.toDouble()

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

        // 4. Harman Logic 7 / Dolby Pro Logic II / Dolby Virtual Atmos / 360° Virtualizer Matrix
        if (surroundMode != SurroundMode.OFF && 2200.0 < sr * 0.45) {
            val wMid = 2.0 * PI * 2200.0 / sr
            val aMid = sin(wMid) / (2.0 * 1.0)
            val a0Mid = 1.0 + aMid
            l7MidB0 = aMid / a0Mid
            l7MidB1 = 0.0
            l7MidB2 = -aMid / a0Mid
            l7MidA1 = (-2.0 * cos(wMid)) / a0Mid
            l7MidA2 = (1.0 - aMid) / a0Mid

            val sideHz = surroundMode.sideBandpassHz.toDouble().coerceIn(800.0, sr * 0.35)
            val wSide = 2.0 * PI * sideHz / sr
            val aSide = sin(wSide) / (2.0 * 0.62)
            val a0Side = 1.0 + aSide
            l7SideB0 = aSide / a0Side
            l7SideB1 = 0.0
            l7SideB2 = -aSide / a0Side
            l7SideA1 = (-2.0 * cos(wSide)) / a0Side
            l7SideA2 = (1.0 - aSide) / a0Side

            // Dolby Pro Logic II / Virtual 3D 90-degree All-Pass Phase Shift @ 1.1 kHz
            l7ApActive = surroundMode.useAllPassPhase && 1100.0 < sr * 0.45
            if (l7ApActive) {
                val wAp = 2.0 * PI * 1100.0 / sr
                val csAp = cos(wAp)
                val alphaAp = sin(wAp) / (2.0 * 0.7071)
                val a0Ap = 1.0 + alphaAp
                l7ApB0 = (1.0 - alphaAp) / a0Ap
                l7ApB1 = (-2.0 * csAp) / a0Ap
                l7ApB2 = (1.0 + alphaAp) / a0Ap
                l7ApA1 = (-2.0 * csAp) / a0Ap
                l7ApA2 = (1.0 - alphaAp) / a0Ap
            }

            // Overhead Pinna Height Elevation Filter @ 7.2 kHz (Atmos 3D / Virtual 360)
            val heightDb = (virtualHeight / 100.0) * surroundMode.heightWeight * 4.2
            if (heightDb >= 0.1 && 7200.0 < sr * 0.45) {
                heightActive = true
                val aH = 10.0.pow(heightDb / 40.0)
                val wH = 2.0 * PI * 7200.0 / sr
                val csH = cos(wH)
                val alphaH = sin(wH) / (2.0 * 0.85)
                val a0H = 1.0 + alphaH / aH
                heightB0 = (1.0 + alphaH * aH) / a0H
                heightB1 = (-2.0 * csH) / a0H
                heightB2 = (1.0 - alphaH * aH) / a0H
                heightA1 = (-2.0 * csH) / a0H
                heightA2 = (1.0 - alphaH / aH) / a0H
            } else {
                heightActive = false
            }

            val maxHaas = haasSideBuffer.size - 1
            haasDelayLeftSamples = ((surroundMode.haasLeftMs / 1000f) * sampleRateHz).roundToInt().coerceIn(1, maxHaas)
            haasDelayRightSamples = ((surroundMode.haasRightMs / 1000f) * sampleRateHz).roundToInt().coerceIn(1, maxHaas)
            l7CenterInjectGain = (centerCoherence / 100.0) * surroundMode.centerWeight * 0.42
            l7SideWetGain = (surroundIntensity / 100.0) * surroundMode.sideWeight * 0.65

            xtcDelaySamples = (0.00055 * sampleRateHz).roundToInt().coerceIn(1, xtcBufferL.size - 1)
            xtcGain = (surroundIntensity / 100.0) * surroundMode.crosstalkCancelGain.toDouble()
        } else {
            l7CenterInjectGain = 0.0
            l7SideWetGain = 0.0
            l7ApActive = false
            heightActive = false
            xtcGain = 0.0
        }

        // 5. Limiter & L/R Stage Time Alignment
        val ceilingLin = 10.0.pow(peakLimiterCeilingDb.toDouble() / 20.0).coerceIn(0.5, 1.0)
        maxSampleAbs = 32767.0 * ceilingLin
        val maxIdx = delayBufferL.size - 1
        leftDelaySamples = ((leftDelayMs / 1000f) * sampleRateHz).roundToInt().coerceIn(0, maxIdx)
        rightDelaySamples = ((rightDelayMs / 1000f) * sampleRateHz).roundToInt().coerceIn(0, maxIdx)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (activeCoeffVersion != coeffVersion) {
            recalculateCoefficients()
        }

        val rg = replayGainLinear
        val useReplayGain = abs(rg - 1.0f) >= 0.005f
        val useSubStage = dspEnabled && (subPhaseInverted || subLpfHz > 0 || abs(subTrimDb) >= 0.1f || subDelaySamples > 0)
        val useRoadNoise = dspEnabled && (rnLowActive || rnMidActive)
        val useSurround = dspEnabled && channelCount == 2 && !monoFoldDown &&
            surroundMode != SurroundMode.OFF &&
            (l7CenterInjectGain > 0.002 || l7SideWetGain > 0.002 || heightActive || xtcGain > 0.002)
        val useLimiterClamp = dspEnabled && peakLimiterCeilingDb < -0.05f
        val useDsp = dspEnabled &&
            (hpfHz > 0 || leftDelaySamples > 0 || rightDelaySamples > 0 ||
                stereoWidthPercent != 100 || monoFoldDown || useSubStage ||
                useRoadNoise || useSurround || useLimiterClamp)

        val outBuffer = replaceOutputBuffer(remaining)
        outBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val inRo = inputBuffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)

        if (!useDsp && !useReplayGain) {
            outBuffer.put(inRo)
            inputBuffer.position(inputBuffer.limit())
            outBuffer.flip()
            return
        }

        val frameSize = channelCount * 2
        val frameCount = remaining / frameSize
        val useHpf = dspEnabled && hpfHz > 0
        val useLr4 = crossoverSlopeDb >= 24
        val useSubDelay = subDelaySamples > 0
        val useDelay = dspEnabled && (leftDelaySamples > 0 || rightDelaySamples > 0)
        val effectiveWidth = when {
            !dspEnabled -> 1.0f
            monoFoldDown -> 0.0f
            else -> stereoWidthPercent / 100f
        }
        val bufLen = delayBufferL.size
        val subBufLen = subDelayBufferL.size
        val haasLen = haasSideBuffer.size
        val ceiling = if (dspEnabled) maxSampleAbs else 32767.0

        if (channelCount == 2) {
            for (f in 0 until frameCount) {
                var l = inRo.getShort().toDouble() * rg
                var r = inRo.getShort().toDouble() * rg

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

                if (useSurround) {
                    val midIn = (l + r) * 0.5
                    val sideIn = (l - r) * 0.5

                    val midBp = l7MidB0 * midIn + l7MidB1 * l7MidX1 + l7MidB2 * l7MidX2 - l7MidA1 * l7MidY1 - l7MidA2 * l7MidY2
                    l7MidX2 = l7MidX1; l7MidX1 = midIn; l7MidY2 = l7MidY1; l7MidY1 = midBp

                    var sideBp = l7SideB0 * sideIn + l7SideB1 * l7SideX1 + l7SideB2 * l7SideX2 - l7SideA1 * l7SideY1 - l7SideA2 * l7SideY2
                    l7SideX2 = l7SideX1; l7SideX1 = sideIn; l7SideY2 = l7SideY1; l7SideY1 = sideBp

                    if (l7ApActive) {
                        val apOut = l7ApB0 * sideBp + l7ApB1 * l7ApX1 + l7ApB2 * l7ApX2 - l7ApA1 * l7ApY1 - l7ApA2 * l7ApY2
                        l7ApX2 = l7ApX1; l7ApX1 = sideBp; l7ApY2 = l7ApY1; l7ApY1 = apOut
                        sideBp = apOut
                    }

                    haasSideBuffer[haasWritePos] = sideBp.toFloat()
                    val haasL = haasSideBuffer[(haasWritePos - haasDelayLeftSamples + haasLen) % haasLen].toDouble()
                    val haasR = haasSideBuffer[(haasWritePos - haasDelayRightSamples + haasLen) % haasLen].toDouble()
                    haasWritePos = (haasWritePos + 1) % haasLen

                    val centerLift = midBp * l7CenterInjectGain
                    var outL = l + centerLift + haasL * l7SideWetGain
                    var outR = r + centerLift - haasR * l7SideWetGain

                    if (xtcGain > 0.002) {
                        val xtcLen = xtcBufferL.size
                        xtcBufferL[xtcWritePos] = l.toFloat()
                        xtcBufferR[xtcWritePos] = r.toFloat()
                        val xtcRead = (xtcWritePos - xtcDelaySamples + xtcLen) % xtcLen
                        outL -= xtcBufferR[xtcRead].toDouble() * xtcGain
                        outR -= xtcBufferL[xtcRead].toDouble() * xtcGain
                        xtcWritePos = (xtcWritePos + 1) % xtcLen
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
                }

                if (effectiveWidth != 1.0f) {
                    val mid = (l + r) * 0.5
                    val side = (l - r) * 0.5 * effectiveWidth
                    l = mid + side
                    r = mid - side
                }

                if (useDelay) {
                    delayBufferL[delayWritePos] = l.toFloat()
                    delayBufferR[delayWritePos] = r.toFloat()
                    val readL = (delayWritePos - leftDelaySamples + bufLen) % bufLen
                    val readR = (delayWritePos - rightDelaySamples + bufLen) % bufLen
                    l = delayBufferL[readL].toDouble()
                    r = delayBufferR[readR].toDouble()
                    delayWritePos = (delayWritePos + 1) % bufLen
                }

                val clampedL = l.coerceIn(-ceiling, ceiling).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                val clampedR = r.coerceIn(-ceiling, ceiling).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                outBuffer.putShort(clampedL)
                outBuffer.putShort(clampedR)
            }
        } else {
            for (f in 0 until frameCount) {
                var m = inRo.getShort().toDouble() * rg
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
                outBuffer.putShort(clampedM)
            }
        }

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
        l7SideX1 = 0.0; l7SideX2 = 0.0; l7SideY1 = 0.0; l7SideY2 = 0.0
        l7ApX1 = 0.0; l7ApX2 = 0.0; l7ApY1 = 0.0; l7ApY2 = 0.0
        heightX1L = 0.0; heightX2L = 0.0; heightY1L = 0.0; heightY2L = 0.0
        heightX1R = 0.0; heightX2R = 0.0; heightY1R = 0.0; heightY2R = 0.0

        subDelayBufferL.fill(0f)
        subDelayBufferR.fill(0f)
        subDelayWritePos = 0

        haasSideBuffer.fill(0f)
        haasWritePos = 0

        xtcBufferL.fill(0f)
        xtcBufferR.fill(0f)
        xtcWritePos = 0

        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        delayWritePos = 0
    }

    override fun onReset() {
        onFlush()
    }
}
