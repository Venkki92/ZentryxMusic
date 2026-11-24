package com.zentryx.music.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DspModelsTest {

    @Test
    fun `all factory presets have 16 bands matching frequencies`() {
        assertEquals(16, DSP_16_FREQUENCIES.size)
        assertEquals(16, DSP_16_LABELS.size)
        FACTORY_DSP_PRESETS.forEach { preset ->
            assertEquals("Preset ${preset.id} must have 16 bands", 16, preset.bands16.size)
        }
    }

    @Test
    fun `pioneer ts e170ci preset applies anti-tin calibration and 60hz hpf`() {
        val pioneer = FACTORY_DSP_PRESETS.first { it.id == "pioneer_ts_e170ci" }
        val updated = applyPresetToDsp(DspSettings(), pioneer)
        assertEquals("pioneer_ts_e170ci", updated.selectedPresetId)
        assertEquals(HpfCutoffHz.HZ_60, updated.hpfCutoffHz)
        assertEquals(5.5f, updated.bassBoostDb, 0.01f)
        assertEquals(-6f, updated.preampDb, 0.01f)
        assertTrue(updated.dspEnabled)
        assertTrue(updated.bands16[8] < 0f) // -4.5 dB tweeter glare cut at 3.5 kHz
    }

    @Test
    fun `effectiveBandGainDb applies hpf rolloff below cutoff`() {
        val withHpf = DspSettings(
            bands16 = List(16) { 0f },
            bassBoostDb = 0f,
            loudnessContour = false,
            hpfCutoffHz = HpfCutoffHz.HZ_80
        )
        val band32Hz = effectiveBandGainDb(0, withHpf)
        val band1kHz = effectiveBandGainDb(5, withHpf)
        assertTrue("32 Hz should be attenuated below 80 Hz HPF", band32Hz < -10f)
        assertEquals(0f, band1kHz, 0.01f)
    }

    @Test
    fun `calculateChannelBalanceDb attenuates opposite channel up to 24 dB`() {
        val (left0, right0) = calculateChannelBalanceDb(0)
        assertEquals(0f, left0, 0.01f)
        assertEquals(0f, right0, 0.01f)

        val (leftR, rightR) = calculateChannelBalanceDb(100)
        assertEquals(-24f, leftR, 0.01f)
        assertEquals(0f, rightR, 0.01f)

        val (leftL, rightL) = calculateChannelBalanceDb(-50)
        assertEquals(0f, leftL, 0.01f)
        assertEquals(-12f, rightL, 0.01f)
    }

    @Test
    fun `dsp settings json serialize and deserialize roundtrip preserves all parameters`() {
        val original = DspSettings(
            dspEnabled = true,
            eqViewMode = EqViewMode.FIVE_BAND,
            selectedPresetId = "pioneer_ts_e170ci",
            bands16 = List(16) { (it - 8) * 0.5f },
            preampDb = -4.5f,
            bassBoostDb = 5.5f,
            bassBoostFreqHz = 80,
            hpfCutoffHz = HpfCutoffHz.HZ_80,
            loudnessContour = false,
            listeningStage = ListeningStagePreset.DRIVER_LHD,
            leftDelayMs = 1.4f,
            rightDelayMs = 0.0f,
            balance = -25,
            fader = -40,
            stereoWidthPercent = 125,
            compressorProfile = CompressorProfile.NIGHT_HIGHWAY,
            subPhaseInverted = true,
            subLpfHz = 100,
            subTrimDb = -3.5f,
            monoFoldDown = true,
            peakLimiterCeilingDb = -1.5f,
            autoGenrePresetEnabled = true,
            autoSwitchDevicePreset = true,
            devicePresetMap = mapOf(
                AudioOutputRoute.HEADUNIT_SPEAKERS.id to "w203_bose",
                AudioOutputRoute.BLUETOOTH_A2DP.id to "rock_metal",
                AudioOutputRoute.USB_DAC_AUX.id to "flat"
            )
        )
        val json = serializeDspSettings(original)
        val decoded = deserializeDspSettings(json)

        assertEquals(original.dspEnabled, decoded.dspEnabled)
        assertEquals(original.eqViewMode, decoded.eqViewMode)
        assertEquals(original.selectedPresetId, decoded.selectedPresetId)
        assertEquals(original.bands16, decoded.bands16)
        assertEquals(original.preampDb, decoded.preampDb, 0.01f)
        assertEquals(original.bassBoostDb, decoded.bassBoostDb, 0.01f)
        assertEquals(original.hpfCutoffHz, decoded.hpfCutoffHz)
        assertEquals(original.listeningStage, decoded.listeningStage)
        assertEquals(original.leftDelayMs, decoded.leftDelayMs, 0.01f)
        assertEquals(original.balance, decoded.balance)
        assertEquals(original.fader, decoded.fader)
        assertEquals(original.stereoWidthPercent, decoded.stereoWidthPercent)
        assertEquals(original.compressorProfile, decoded.compressorProfile)
        assertEquals(original.subPhaseInverted, decoded.subPhaseInverted)
        assertEquals(original.subLpfHz, decoded.subLpfHz)
        assertEquals(original.subTrimDb, decoded.subTrimDb, 0.01f)
        assertEquals(original.monoFoldDown, decoded.monoFoldDown)
        assertEquals(original.peakLimiterCeilingDb, decoded.peakLimiterCeilingDb, 0.01f)
        assertEquals(original.autoGenrePresetEnabled, decoded.autoGenrePresetEnabled)
        assertEquals(original.autoSwitchDevicePreset, decoded.autoSwitchDevicePreset)
        assertEquals("rock_metal", decoded.devicePresetMap[AudioOutputRoute.BLUETOOTH_A2DP.id])
        assertEquals("flat", decoded.devicePresetMap[AudioOutputRoute.USB_DAC_AUX.id])
    }

    @Test
    fun `calculateCabinStageGains combines left right balance and rear fader attenuation`() {
        val (centerL, centerR) = calculateCabinStageGains(0, 0)
        assertEquals(0f, centerL, 0.01f)
        assertEquals(0f, centerR, 0.01f)

        val (frontL, frontR) = calculateCabinStageGains(-50, 100)
        assertEquals(0f, frontL, 0.01f)
        assertEquals(-12f, frontR, 0.01f)

        val (rearL, rearR) = calculateCabinStageGains(0, -100)
        assertEquals(-4f, rearL, 0.01f)
        assertEquals(-4f, rearR, 0.01f)
    }

    @Test
    fun `matchPresetForTrack resolves genre and folder rules deterministically`() {
        assertEquals("podcast_clarity", matchPresetForTrack("Podcast", "/TalkShows", "Ep 12"))
        assertEquals("rock_metal", matchPresetForTrack("Hard Rock", "/Music/Metallica", "Track"))
        assertEquals("club_electronic", matchPresetForTrack(null, "/Music/Techno Sets", "Club Mix"))
        assertEquals("cabin_bass", matchPresetForTrack("Hip Hop", "/Music/Rap", "808s"))
        assertEquals("vocal_jazz", matchPresetForTrack("Jazz", "/Music/Acoustic", "Miles"))
        assertEquals(null, matchPresetForTrack("Pop", "/Music/Misc", "Track"))
    }

    @Test
    fun `effectiveBandGainDb reflects sub trim and rear fader warmth`() {
        val boostSub = DspSettings(
            bands16 = List(16) { 0f },
            bassBoostDb = 0f,
            loudnessContour = false,
            hpfCutoffHz = HpfCutoffHz.OFF,
            subLpfHz = 100,
            subTrimDb = 4f,
            fader = -100
        )
        assertEquals(4f, effectiveBandGainDb(1, boostSub), 0.01f) // 64 Hz inside 100Hz LPF
        assertTrue(effectiveBandGainDb(10, boostSub) < 0f) // 7kHz warmed when faded full rear
    }

    @Test
    fun `calculateRoadNoiseContourDb ramps smoothly above 50 kmh up to 130 kmh`() {
        assertEquals(0f to 0f, calculateRoadNoiseContourDb(45f, enabled = true))
        assertEquals(0f to 0f, calculateRoadNoiseContourDb(140f, enabled = false))

        val (midLow, midVoc) = calculateRoadNoiseContourDb(90f, enabled = true)
        assertTrue("Expected moderate bass lift at 90 km/h (was $midLow)", midLow in 1.5f..2.5f)
        assertTrue("Expected moderate vocal lift at 90 km/h (was $midVoc)", midVoc in 0.5f..1.0f)

        val (maxLow, maxVoc) = calculateRoadNoiseContourDb(140f, enabled = true)
        assertEquals(4.0f, maxLow, 0.05f)
        assertEquals(1.5f, maxVoc, 0.05f)
    }

    @Test
    fun `computeCabinRtaCorrections pulls boom peak down and clamps within plus minus 6 dB`() {
        val measured = MutableList(16) { 0f }
        measured[2] = 9f   // 125 Hz cabin resonance boom
        measured[14] = -10f // 15 kHz rolloff
        val corrections = computeCabinRtaCorrections(measured)
        assertEquals(16, corrections.size)
        assertTrue("125 Hz boom should receive negative cut (was ${corrections[2]})", corrections[2] < 0f)
        assertTrue("All corrections must clamp within -6..+6 dB", corrections.all { it in -6f..6f })
    }

    @Test
    fun `24 dB per oct LR4 slope attenuates sub-cutoff deeper than 12 dB per oct BW2`() {
        val bw2 = DspSettings(bands16 = List(16) { 0f }, bassBoostDb = 0f, loudnessContour = false, hpfCutoffHz = HpfCutoffHz.HZ_80, crossoverSlopeDb = 12)
        val lr4 = bw2.copy(crossoverSlopeDb = 24)
        // Band 1 is 64 Hz (0.32 octaves below 80 Hz cutoff): BW2 ~ -3.9 dB vs LR4 ~ -7.7 dB
        val gainBw2 = effectiveBandGainDb(1, bw2)
        val gainLr4 = effectiveBandGainDb(1, lr4)
        assertTrue("LR4 ($gainLr4) should attenuate 64Hz deeper than BW2 ($gainBw2)", gainLr4 < gainBw2 - 2f)
    }

    @Test
    fun `logic 7 and dolby virtual surround fields survive json roundtrip`() {
        val src = DspSettings(
            surroundMode = SurroundMode.DOLBY_ATMOS_VIRTUAL,
            surroundIntensity = 75,
            centerCoherence = 60,
            virtualHeight = 85,
            roadNoiseEqEnabled = false,
            crossoverSlopeDb = 24,
            subDelayMs = 8.5f
        )
        val decoded = deserializeDspSettings(serializeDspSettings(src))
        assertEquals(SurroundMode.DOLBY_ATMOS_VIRTUAL, decoded.surroundMode)
        assertEquals(75, decoded.surroundIntensity)
        assertEquals(60, decoded.centerCoherence)
        assertEquals(85, decoded.virtualHeight)
        assertEquals(false, decoded.roadNoiseEqEnabled)
        assertEquals(24, decoded.crossoverSlopeDb)
        assertEquals(8.5f, decoded.subDelayMs, 0.01f)
        assertTrue(SurroundMode.DOLBY_PRO_LOGIC_II.useAllPassPhase)
        assertTrue(SurroundMode.VIRTUAL_SURROUND_360.crosstalkCancelGain > 0.1f)

        val flatAtmos = DspSettings(
            bands16 = List(16) { 0f },
            bassBoostDb = 0f,
            loudnessContour = false,
            roadNoiseEqEnabled = false,
            surroundMode = SurroundMode.DOLBY_ATMOS_VIRTUAL,
            virtualHeight = 100
        )
        // Index 10 is 7000 Hz
        val heightGain = effectiveBandGainDb(10, flatAtmos)
        assertTrue("Expected Atmos 7kHz pinna height lift > 2.0 dB (got $heightGain)", heightGain > 2.0f)
    }

    @Test
    fun `applyPresetToDsp preserves active surround mode for factory EQ presets and applies surround fields when preset defines them`() {
        val activeLogic7 = DspSettings(
            dspEnabled = false,
            surroundMode = SurroundMode.LOGIC7_SURROUND,
            surroundIntensity = 75,
            centerCoherence = 65,
            virtualHeight = 50,
            monoFoldDown = false
        )
        val pioneer = FACTORY_DSP_PRESETS.first { it.id == "pioneer_ts_e170ci" }
        val afterFactory = applyPresetToDsp(activeLogic7, pioneer)
        assertTrue("applyPresetToDsp must enable DSP", afterFactory.dspEnabled)
        assertEquals(SurroundMode.LOGIC7_SURROUND, afterFactory.surroundMode)
        assertEquals(75, afterFactory.surroundIntensity)
        assertEquals(65, afterFactory.centerCoherence)
        assertEquals(50, afterFactory.virtualHeight)

        // Custom preset carrying explicit Dolby Atmos surround profile must update surround state and clear mono fold-down
        val customSurroundPreset = DspPreset16(
            id = "custom_atmos_cabin",
            name = "Atmos Cabin",
            description = "Saved surround preset",
            bands16 = List(16) { 1f },
            surroundModeId = SurroundMode.DOLBY_ATMOS_VIRTUAL.id,
            surroundIntensity = 85,
            centerCoherence = 70,
            virtualHeight = 90,
            isCustom = true
        )
        val monoBase = DspSettings(
            dspEnabled = false,
            surroundMode = SurroundMode.OFF,
            monoFoldDown = true
        )
        val afterCustom = applyPresetToDsp(monoBase, customSurroundPreset)
        assertTrue(afterCustom.dspEnabled)
        assertEquals(SurroundMode.DOLBY_ATMOS_VIRTUAL, afterCustom.surroundMode)
        assertEquals(85, afterCustom.surroundIntensity)
        assertEquals(70, afterCustom.centerCoherence)
        assertEquals(90, afterCustom.virtualHeight)
        assertEquals(false, afterCustom.monoFoldDown)

        // Custom preset JSON round-trip preserves surround parameters
        val roundTripped = deserializeCustomPresets(serializeCustomPresets(listOf(customSurroundPreset))).first()
        assertEquals(SurroundMode.DOLBY_ATMOS_VIRTUAL.id, roundTripped.surroundModeId)
        assertEquals(85, roundTripped.surroundIntensity)
        assertEquals(70, roundTripped.centerCoherence)
        assertEquals(90, roundTripped.virtualHeight)
    }

    @Test
    fun `buildSurroundMatrixDiagnostics outputs accurate parameters for every surround profile`() {
        for (mode in SurroundMode.entries) {
            val settings = DspSettings(
                dspEnabled = true,
                selectedPresetId = "w203_bose",
                surroundMode = mode,
                surroundIntensity = 80,
                centerCoherence = 60,
                virtualHeight = 75,
                stereoWidthPercent = 100,
                monoFoldDown = false
            )
            val diag = buildSurroundMatrixDiagnostics(
                trigger = "selectSurroundMode(${mode.id})",
                settings = settings,
                sampleRateHz = 48000,
                pcmConfigured = true,
                hardwareVirtualizerActive = mode != SurroundMode.OFF,
                hardwareVirtualizerStrengthPermille = if (mode != SurroundMode.OFF) 650 else 0
            )
            val formatted = formatSurroundMatrixDiagnosticLog(diag)

            assertTrue(formatted.contains("mode=${mode.id}"))
            assertTrue(formatted.contains("trigger=selectSurroundMode(${mode.id})"))
            if (mode == SurroundMode.OFF) {
                assertEquals(false, diag.surroundActive)
                assertEquals(100, diag.effectiveStereoWidthPercent)
                assertEquals(0f, diag.sideWetGain, 0.0001f)
            } else {
                assertTrue("Expected surroundActive=true for $mode", diag.surroundActive)
                assertTrue("Expected widened stereo width for $mode", diag.effectiveStereoWidthPercent > 100)
                assertTrue("Expected non-zero Haas delay for $mode", diag.haasLeftSamples > 0 && diag.haasRightSamples > 0)
                assertTrue("Expected positive sideWetGain for $mode", diag.sideWetGain > 0f)
                assertEquals(mode.useAllPassPhase, diag.allPassPhase90Active)
                if (mode.crosstalkCancelGain > 0f) {
                    assertTrue("Expected positive XTC gain for $mode", diag.xtcGain > 0f && diag.xtcDelaySamples > 0)
                }
            }
        }
    }
}
