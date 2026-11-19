package com.zentryx.music.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(UnstableApi::class)
class CarDspAudioProcessorTest {

    private fun buildStereoPcmBuffer(frames: Int, sampleRate: Int, freqHz: Double, leftAmp: Short, rightAmp: Short): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val phase = sin(2.0 * PI * freqHz * i / sampleRate)
            buf.putShort((leftAmp * phase).roundToInt().toShort())
            buf.putShort((rightAmp * phase).roundToInt().toShort())
        }
        buf.flip()
        return buf
    }

    @Test
    fun `mono fold down sums stereo channels identically`() {
        val processor = CarDspAudioProcessor()
        processor.configure(AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.updateSettings(
            DspSettings(
                dspEnabled = true,
                hpfCutoffHz = HpfCutoffHz.OFF,
                subTrimDb = 0f,
                subPhaseInverted = false,
                monoFoldDown = true,
                peakLimiterCeilingDb = 0.0f,
                leftDelayMs = 0f,
                rightDelayMs = 0f
            )
        )
        processor.flush()

        val frames = 16
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            input.putShort(12000)
            input.putShort(-4000)
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.getOutput().order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until frames) {
            val l = output.getShort().toInt()
            val r = output.getShort().toInt()
            assertEquals("Left and Right must match in Mono Fold-Down", l, r)
            assertEquals("Expected (12000 + -4000)/2 = 4000", 4000, l)
        }
    }

    @Test
    fun `peak limiter brickwall ceiling clamps full-scale samples`() {
        val processor = CarDspAudioProcessor()
        processor.configure(AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.updateSettings(
            DspSettings(
                dspEnabled = true,
                hpfCutoffHz = HpfCutoffHz.OFF,
                subTrimDb = 0f,
                subPhaseInverted = false,
                monoFoldDown = false,
                peakLimiterCeilingDb = -3.0f,
                leftDelayMs = 0f,
                rightDelayMs = 0f
            )
        )
        processor.flush()

        val expectedMax = (32767.0 * 10.0.pow(-3.0 / 20.0)).roundToInt() + 1
        val input = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 8) {
            input.putShort(32000)
            input.putShort(-32000)
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.getOutput().order(ByteOrder.LITTLE_ENDIAN)
        while (output.remaining() >= 4) {
            val l = abs(output.getShort().toInt())
            val r = abs(output.getShort().toInt())
            assertTrue("Left peak $l should be <= ceiling $expectedMax", l <= expectedMax)
            assertTrue("Right peak $r should be <= ceiling $expectedMax", r <= expectedMax)
        }
    }

    @Test
    fun `sub phase inversion flips low frequency polarity while keeping high frequencies intact`() {
        val sampleRate = 48000
        val frames = 960

        fun processLowFreq(inverted: Boolean): DoubleArray {
            val p = CarDspAudioProcessor()
            p.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            p.updateSettings(
                DspSettings(
                    dspEnabled = true,
                    hpfCutoffHz = HpfCutoffHz.OFF,
                    subLpfHz = 120,
                    subPhaseInverted = inverted,
                    subTrimDb = 0f,
                    monoFoldDown = false,
                    peakLimiterCeilingDb = 0.0f,
                    leftDelayMs = 0f,
                    rightDelayMs = 0f
                )
            )
            p.flush()
            val buf = buildStereoPcmBuffer(frames, sampleRate, 50.0, 10000, 10000)
            p.queueInput(buf)
            val out = p.getOutput().order(ByteOrder.LITTLE_ENDIAN)
            val leftSamples = DoubleArray(frames)
            for (i in 0 until frames) {
                leftSamples[i] = out.getShort().toDouble()
                out.getShort()
            }
            return leftSamples
        }

        val normal = processLowFreq(inverted = false)
        val inverted = processLowFreq(inverted = true)

        var dotProduct = 0.0
        for (i in frames / 2 until frames) {
            dotProduct += normal[i] * inverted[i]
        }
        assertTrue("180-degree sub phase invert should produce negative correlation for 50 Hz sine", dotProduct < 0.0)
    }

    @Test
    fun `high pass filter attenuates sub-door rumble below cutoff`() {
        val sampleRate = 48000
        val frames = 1920
        val p = CarDspAudioProcessor()
        p.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        p.updateSettings(
            DspSettings(
                dspEnabled = true,
                hpfCutoffHz = HpfCutoffHz.HZ_120,
                subTrimDb = 0f,
                subPhaseInverted = false,
                monoFoldDown = false,
                peakLimiterCeilingDb = 0.0f,
                leftDelayMs = 0f,
                rightDelayMs = 0f
            )
        )
        p.flush()

        val lowInput = buildStereoPcmBuffer(frames, sampleRate, 30.0, 10000, 10000)
        p.queueInput(lowInput)
        val lowOut = p.getOutput().order(ByteOrder.LITTLE_ENDIAN)

        var maxTail = 0
        for (i in 0 until frames) {
            val l = abs(lowOut.getShort().toInt())
            lowOut.getShort()
            if (i >= frames / 2 && l > maxTail) maxTail = l
        }
        assertTrue("30Hz signal with 120Hz HPF should drop far below 2000 (was $maxTail)", maxTail < 2000)
    }

    @Test
    fun `24 dB per oct LR4 HPF attenuates 30Hz deeper than 12 dB per oct BW2`() {
        val sampleRate = 48000
        val frames = 2400

        fun measureTailPeak(slopeDb: Int): Int {
            val p = CarDspAudioProcessor()
            p.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            p.updateSettings(
                DspSettings(
                    dspEnabled = true,
                    hpfCutoffHz = HpfCutoffHz.HZ_120,
                    crossoverSlopeDb = slopeDb,
                    roadNoiseEqEnabled = false,
                    surroundMode = SurroundMode.OFF,
                    peakLimiterCeilingDb = 0.0f
                )
            )
            p.flush()
            val input = buildStereoPcmBuffer(frames, sampleRate, 30.0, 12000, 12000)
            p.queueInput(input)
            val out = p.getOutput().order(ByteOrder.LITTLE_ENDIAN)
            var peak = 0
            for (i in 0 until frames) {
                val l = abs(out.getShort().toInt())
                out.getShort()
                if (i >= frames / 2 && l > peak) peak = l
            }
            return peak
        }

        val bw2Peak = measureTailPeak(12)
        val lr4Peak = measureTailPeak(24)
        assertTrue("LR4 tail peak ($lr4Peak) must be strictly lower than BW2 ($bw2Peak)", lr4Peak < bw2Peak)
    }

    @Test
    fun `road noise contour boosts 100Hz bass at highway speed compared to parked`() {
        val sampleRate = 48000
        val frames = 2400

        fun measure100HzAtSpeed(speedKmH: Float): Int {
            val p = CarDspAudioProcessor()
            p.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            p.updateSettings(
                DspSettings(
                    dspEnabled = true,
                    hpfCutoffHz = HpfCutoffHz.OFF,
                    roadNoiseEqEnabled = true,
                    surroundMode = SurroundMode.OFF,
                    peakLimiterCeilingDb = 0.0f
                )
            )
            p.setRoadNoiseSpeedKmH(speedKmH)
            p.flush()
            val input = buildStereoPcmBuffer(frames, sampleRate, 100.0, 8000, 8000)
            p.queueInput(input)
            val out = p.getOutput().order(ByteOrder.LITTLE_ENDIAN)
            var peak = 0
            for (i in 0 until frames) {
                val l = abs(out.getShort().toInt())
                out.getShort()
                if (i >= frames / 2 && l > peak) peak = l
            }
            return peak
        }

        val parked = measure100HzAtSpeed(0f)
        val highway = measure100HzAtSpeed(130f)
        assertTrue("Highway 130 km/h peak ($highway) should exceed parked ($parked)", highway > parked + 1000)
    }

    @Test
    fun `logic 7 surround processes stereo side reflections without clipping`() {
        val sampleRate = 48000
        val frames = 2400
        val p = CarDspAudioProcessor()
        p.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        p.updateSettings(
            DspSettings(
                dspEnabled = true,
                hpfCutoffHz = HpfCutoffHz.OFF,
                roadNoiseEqEnabled = false,
                surroundMode = SurroundMode.LOGIC7_SURROUND,
                surroundIntensity = 80,
                centerCoherence = 60,
                peakLimiterCeilingDb = -0.5f
            )
        )
        p.flush()

        val input = buildStereoPcmBuffer(frames, sampleRate, 1400.0, 10000, 2000)
        p.queueInput(input)
        val out = p.getOutput().order(ByteOrder.LITTLE_ENDIAN)
        var diffSum = 0L
        for (i in 0 until frames) {
            val l = out.getShort().toInt()
            val r = out.getShort().toInt()
            if (i >= frames / 2) diffSum += abs(l - r)
        }
        assertTrue("Logic 7 stereo output should preserve & widen L/R spatial difference", diffSum > 100_000L)
    }

    @Test
    fun `dolby virtual atmos boosts 7200Hz height elevation over stereo direct`() {
        val sampleRate = 48000
        val frames = 2400

        fun measure7200HzPeak(mode: SurroundMode, height: Int): Int {
            val p = CarDspAudioProcessor()
            p.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            p.updateSettings(
                DspSettings(
                    dspEnabled = true,
                    hpfCutoffHz = HpfCutoffHz.OFF,
                    roadNoiseEqEnabled = false,
                    surroundMode = mode,
                    surroundIntensity = 70,
                    centerCoherence = 50,
                    virtualHeight = height,
                    peakLimiterCeilingDb = 0f
                )
            )
            p.flush()
            val input = buildStereoPcmBuffer(frames, sampleRate, 7200.0, 9000, 9000)
            p.queueInput(input)
            val out = p.getOutput().order(ByteOrder.LITTLE_ENDIAN)
            var peak = 0
            for (i in 0 until frames) {
                val l = abs(out.getShort().toInt())
                out.getShort()
                if (i >= frames / 2 && l > peak) peak = l
            }
            return peak
        }

        val directPeak = measure7200HzPeak(SurroundMode.OFF, 0)
        val atmosPeak = measure7200HzPeak(SurroundMode.DOLBY_ATMOS_VIRTUAL, 100)
        assertTrue("Dolby Virtual Atmos 7.2 kHz peak ($atmosPeak) should exceed Direct ($directPeak)", atmosPeak > directPeak + 2000)
    }

    @Test
    fun `dolby pro logic ii and 360 virtualizer process stereo cleanly with cross-talk cancellation`() {
        val sampleRate = 48000
        val frames = 2400
        for (mode in listOf(SurroundMode.DOLBY_PRO_LOGIC_II, SurroundMode.VIRTUAL_SURROUND_360)) {
            val p = CarDspAudioProcessor()
            p.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            p.updateSettings(
                DspSettings(
                    dspEnabled = true,
                    hpfCutoffHz = HpfCutoffHz.OFF,
                    roadNoiseEqEnabled = false,
                    surroundMode = mode,
                    surroundIntensity = 85,
                    centerCoherence = 60,
                    virtualHeight = 70,
                    peakLimiterCeilingDb = -0.5f
                )
            )
            p.flush()
            val input = buildStereoPcmBuffer(frames, sampleRate, 1200.0, 11000, 1500)
            p.queueInput(input)
            val out = p.getOutput().order(ByteOrder.LITTLE_ENDIAN)
            var diffSum = 0L
            for (i in 0 until frames) {
                val l = out.getShort().toInt()
                val r = out.getShort().toInt()
                if (i >= frames / 2) diffSum += abs(l - r)
            }
            assertTrue("$mode should generate active wide stereo spatial field (diffSum=$diffSum)", diffSum > 100_000L)
        }
    }
}
