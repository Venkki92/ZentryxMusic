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

    @Test
    fun `pcm float decoder input is processed by surround 3d instead of bypassed`() {
        val sampleRate = 48000
        val frames = 1600
        val p = CarDspAudioProcessor()
        val configured = p.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_FLOAT))
        assertEquals(C.ENCODING_PCM_16BIT, configured.encoding)
        assertTrue("CarDspAudioProcessor must report active PCM configuration", p.isPcmConfigured)

        p.updateSettings(
            DspSettings(
                dspEnabled = true,
                hpfCutoffHz = HpfCutoffHz.OFF,
                roadNoiseEqEnabled = false,
                surroundMode = SurroundMode.LOGIC7_SURROUND,
                surroundIntensity = 80,
                centerCoherence = 50,
                peakLimiterCeilingDb = -0.5f
            )
        )
        p.flush()

        val floatInput = ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val phase = sin(2.0 * PI * 1100.0 * i / sampleRate).toFloat()
            floatInput.putFloat(0.45f * phase)
            floatInput.putFloat(0.10f * phase)
        }
        floatInput.flip()

        p.queueInput(floatInput)
        val out = p.getOutput().order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(frames * 4, out.remaining())

        var diffSum = 0L
        for (i in 0 until frames) {
            val l = out.getShort().toInt()
            val r = out.getShort().toInt()
            if (i >= frames / 2) diffSum += abs(l - r)
        }
        assertTrue("PCM_FLOAT Logic 7 output must be active and spatially wide (diffSum=$diffSum)", diffSum > 100_000L)
        assertTrue("Effective surround width should exceed 100% (was ${p.activeEffectiveWidthPercent}%)", p.activeEffectiveWidthPercent > 100)
    }

    @Test
    fun `all surround matrix modes measurably widen stereo image compared to stereo direct`() {
        val sampleRate = 48000
        val frames = 2400

        fun measureSideDiff(mode: SurroundMode): Long {
            val p = CarDspAudioProcessor()
            p.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            p.updateSettings(
                DspSettings(
                    dspEnabled = true,
                    hpfCutoffHz = HpfCutoffHz.OFF,
                    roadNoiseEqEnabled = false,
                    surroundMode = mode,
                    surroundIntensity = 80,
                    centerCoherence = 40,
                    virtualHeight = 60,
                    stereoWidthPercent = 100,
                    peakLimiterCeilingDb = 0f
                )
            )
            p.flush()
            val input = buildStereoPcmBuffer(frames, sampleRate, 1350.0, 9000, 3000)
            p.queueInput(input)
            val out = p.getOutput().order(ByteOrder.LITTLE_ENDIAN)
            var diffSum = 0L
            for (i in 0 until frames) {
                val l = out.getShort().toInt()
                val r = out.getShort().toInt()
                if (i >= frames / 2) diffSum += abs(l - r)
            }
            return diffSum
        }

        val directDiff = measureSideDiff(SurroundMode.OFF)
        for (mode in listOf(
            SurroundMode.LOGIC7_SURROUND,
            SurroundMode.CONCERT_HALL,
            SurroundMode.DOLBY_PRO_LOGIC_II,
            SurroundMode.DOLBY_ATMOS_VIRTUAL,
            SurroundMode.VIRTUAL_SURROUND_360
        )) {
            val modeDiff = measureSideDiff(mode)
            assertTrue(
                "$mode side energy ($modeDiff) should measurably exceed Stereo Direct ($directDiff)",
                modeDiff > directDiff
            )
        }
    }

    @Test
    fun `applyPresetToDsp updates CarDspAudioProcessor live width and surround diagnostics immediately`() {
        val processor = CarDspAudioProcessor()
        processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        val w203Preset = FACTORY_DSP_PRESETS.first { it.id == "w203_bose" }

        for (mode in SurroundMode.entries) {
            val base = DspSettings(
                dspEnabled = false,
                surroundMode = mode,
                surroundIntensity = 80,
                centerCoherence = 65,
                virtualHeight = 70,
                stereoWidthPercent = 100
            )
            val updated = applyPresetToDsp(base, w203Preset)
            processor.updateSettings(updated)

            val diag = processor.buildLiveSurroundDiagnostics("applyPresetToDsp(${w203Preset.id})", updated)
            assertEquals(updated.surroundMode, diag.mode)
            assertEquals(processor.activeEffectiveWidthPercent, diag.effectiveStereoWidthPercent)
            assertTrue("PCM processor should remain configured", diag.pcmConfigured)
            if (mode == SurroundMode.OFF) {
                assertEquals(100, processor.activeEffectiveWidthPercent)
            } else {
                assertTrue(
                    "Expected effective width > 100% immediately on updateSettings for $mode (was ${processor.activeEffectiveWidthPercent}%)",
                    processor.activeEffectiveWidthPercent > 100
                )
            }
        }
    }

    @Test
    fun `surround bitstream telemetry proves OFF passes dry PCM while every surround profile mutates bitstream`() {
        val sampleRate = 48000
        val frames = 1920
        val processor = CarDspAudioProcessor()
        processor.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))

        // 1. Direct / OFF with flat DSP -> fast-path dry bitstream
        val drySettings = DspSettings(
            dspEnabled = true,
            hpfCutoffHz = HpfCutoffHz.OFF,
            roadNoiseEqEnabled = false,
            surroundMode = SurroundMode.OFF,
            stereoWidthPercent = 100,
            peakLimiterCeilingDb = 0.0f
        )
        processor.updateSettings(drySettings)
        processor.flush()
        processor.queueInput(buildStereoPcmBuffer(frames, sampleRate, 1300.0, 10000, 3000))
        processor.getOutput()

        val dryTelemetry = processor.snapshotBitstreamTelemetry()
        assertTrue("Dry stereo with 0dB ceiling should hit fast-path bypass", dryTelemetry.fastPathBypassed)
        assertEquals(false, dryTelemetry.surroundStageExecuted)
        assertEquals(false, dryTelemetry.bitstreamModified)
        assertEquals(dryTelemetry.inputChecksum, dryTelemetry.outputChecksum)
        assertEquals(0f, dryTelemetry.surroundDeltaRms, 0.00001f)

        val dryJson = formatSurroundDebugJson(
            SurroundDebugSnapshot(
                diagnostics = processor.buildLiveSurroundDiagnostics("test_off", drySettings),
                bitstream = dryTelemetry
            )
        )
        assertTrue(dryJson.contains("\"verdict\": \"DRY_DIRECT_STEREO\""))
        assertTrue(dryJson.contains("\"bitstreamModified\": false"))

        // 2. Every active SurroundMode must execute surround stage & visibly mutate PCM samples
        for (mode in SurroundMode.entries.filter { it != SurroundMode.OFF }) {
            val activeSettings = drySettings.copy(
                surroundMode = mode,
                surroundIntensity = 80,
                centerCoherence = 60,
                virtualHeight = 70
            )
            processor.updateSettings(activeSettings)
            processor.flush()
            processor.queueInput(buildStereoPcmBuffer(frames, sampleRate, 1300.0, 10000, 3000))
            processor.getOutput()

            val tel = processor.snapshotBitstreamTelemetry()
            assertEquals("Mode $mode must not hit dry fast-path bypass", false, tel.fastPathBypassed)
            assertEquals("Mode $mode must execute surround PCM stage", true, tel.surroundStageExecuted)
            assertEquals("Mode $mode must mutate output bitstream", true, tel.bitstreamModified)
            assertTrue(
                "Mode $mode surroundDeltaRms should be > 0.001 (was ${tel.surroundDeltaRms})",
                tel.surroundDeltaRms > 0.001f
            )
            assertTrue(
                "Mode $mode output checksum should differ from input checksum",
                tel.inputChecksum != tel.outputChecksum
            )

            val json = formatSurroundDebugJson(
                SurroundDebugSnapshot(
                    diagnostics = processor.buildLiveSurroundDiagnostics("test_${mode.id}", activeSettings),
                    bitstream = tel
                )
            )
            assertTrue("JSON for $mode should report SURROUND_MODIFYING_BITSTREAM", json.contains("\"verdict\": \"SURROUND_MODIFYING_BITSTREAM\""))
            assertTrue("JSON for $mode should include modeId", json.contains("\"modeId\": \"${mode.id}\""))
            assertTrue("JSON for $mode should report bitstreamModified: true", json.contains("\"bitstreamModified\": true"))
        }
    }

    @Test
    fun `centered mono-like signal gets genuine stereo decorrelation and audible surround delta`() {
        val sampleRate = 48000
        val frames = 9600 // 200 ms so early reflections + Schroeder diffuser + hall comb tank fully engage

        // Build multi-harmonic centered track (L == R) simulating pop/rock vocal & synth center mix
        fun buildCenteredComplexMix(): ByteBuffer {
            val buf = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until frames) {
                val t = i.toDouble() / sampleRate
                val s = 0.45 * sin(2.0 * PI * 440.0 * t) +
                    0.35 * sin(2.0 * PI * 1180.0 * t) +
                    0.20 * sin(2.0 * PI * 2750.0 * t)
                val sample = (s * 12000.0).roundToInt().toShort()
                buf.putShort(sample)
                buf.putShort(sample)
            }
            buf.flip()
            return buf
        }

        val testedModes = listOf(
            SurroundMode.LOGIC7_SURROUND,
            SurroundMode.CONCERT_HALL,
            SurroundMode.DOLBY_PRO_LOGIC_II,
            SurroundMode.DOLBY_ATMOS_VIRTUAL,
            SurroundMode.VIRTUAL_SURROUND_360
        )

        for (mode in testedModes) {
            val processor = CarDspAudioProcessor()
            processor.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            processor.updateSettings(
                DspSettings(
                    dspEnabled = true,
                    hpfCutoffHz = HpfCutoffHz.OFF,
                    roadNoiseEqEnabled = false,
                    surroundMode = mode,
                    surroundIntensity = 75,
                    centerCoherence = 55,
                    virtualHeight = 60,
                    stereoWidthPercent = 100,
                    peakLimiterCeilingDb = -0.5f
                )
            )
            processor.flush()
            processor.queueInput(buildCenteredComplexMix())
            val out = processor.getOutput().order(ByteOrder.LITTLE_ENDIAN)

            var lrDiffSum = 0L
            for (i in 0 until frames) {
                val l = out.getShort().toInt()
                val r = out.getShort().toInt()
                if (i >= frames / 2) {
                    lrDiffSum += abs(l - r)
                }
            }

            val tel = processor.snapshotBitstreamTelemetry()
            assertTrue(
                "$mode must synthesize audible L/R stereo decorrelation from pure centered L==R mix (lrDiffSum=$lrDiffSum)",
                lrDiffSum > 500_000L
            )
            assertTrue(
                "$mode must produce unmistakable surroundDeltaPercent >= 12% on centered mix (was ${tel.surroundDeltaPercent}%)",
                tel.surroundDeltaPercent >= 12f
            )
        }
    }

    @Test
    fun `concert hall exhibits longer reverb tail than studio focused and soft limiter stays clean on hot signals`() {
        val sampleRate = 48000
        val burstFrames = 2400 // 50 ms impulse burst
        val tailFrames = 4800  // 100 ms silence tail to measure reverb decay

        fun measureTailEnergy(mode: SurroundMode): Long {
            val processor = CarDspAudioProcessor()
            processor.configure(AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            processor.updateSettings(
                DspSettings(
                    dspEnabled = true,
                    hpfCutoffHz = HpfCutoffHz.OFF,
                    roadNoiseEqEnabled = false,
                    surroundMode = mode,
                    surroundIntensity = 80,
                    centerCoherence = 55,
                    virtualHeight = 60,
                    peakLimiterCeilingDb = -0.5f
                )
            )
            processor.flush()

            // Feed hot multi-tone burst (near full scale) to also verify soft-knee limiter safety
            val burst = ByteBuffer.allocateDirect(burstFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until burstFrames) {
                val t = i.toDouble() / sampleRate
                val sL = (0.65 * sin(2.0 * PI * 680.0 * t) + 0.35 * sin(2.0 * PI * 1950.0 * t)) * 29000.0
                val sR = (0.45 * sin(2.0 * PI * 680.0 * t) - 0.35 * sin(2.0 * PI * 1950.0 * t)) * 29000.0
                burst.putShort(sL.roundToInt().coerceIn(-32767, 32767).toShort())
                burst.putShort(sR.roundToInt().coerceIn(-32767, 32767).toShort())
            }
            burst.flip()
            processor.queueInput(burst)
            val burstOut = processor.getOutput().order(ByteOrder.LITTLE_ENDIAN)
            val ceilingLimit = (32767.0 * 10.0.pow(-0.5 / 20.0)).roundToInt() + 2
            while (burstOut.remaining() >= 4) {
                val l = abs(burstOut.getShort().toInt())
                val r = abs(burstOut.getShort().toInt())
                assertTrue("$mode burst left $l must obey soft limiter ceiling $ceilingLimit", l <= ceilingLimit)
                assertTrue("$mode burst right $r must obey soft limiter ceiling $ceilingLimit", r <= ceilingLimit)
            }

            // Now feed pure silence and measure tail decay energy
            val silence = ByteBuffer.allocateDirect(tailFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until tailFrames) {
                silence.putShort(0)
                silence.putShort(0)
            }
            silence.flip()
            processor.queueInput(silence)
            val tailOut = processor.getOutput().order(ByteOrder.LITTLE_ENDIAN)
            var tailEnergy = 0L
            for (i in 0 until tailFrames) {
                val l = abs(tailOut.getShort().toInt())
                val r = abs(tailOut.getShort().toInt())
                if (i >= 1200) { // After initial 25ms Haas taps, measure true hall reverb tail
                    tailEnergy += l + r
                }
            }
            return tailEnergy
        }

        val studioTail = measureTailEnergy(SurroundMode.STUDIO_FOCUSED)
        val hallTail = measureTailEnergy(SurroundMode.CONCERT_HALL)
        assertTrue(
            "Concert Hall reverb tail ($hallTail) must significantly exceed Studio Focused ($studioTail)",
            hallTail > studioTail * 2L && hallTail > 200_000L
        )
    }
}
