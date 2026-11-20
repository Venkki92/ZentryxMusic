package com.zentryx.music.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveCabinProfileEngineTest {

    @Test
    fun staysQuietWhenDisabledEvenAtHighNoiseAndSpeed() {
        val cfg = AdaptiveCabinConfig(enabled = false)
        val res = evaluateAdaptiveCabinState(
            prevState = AdaptiveEvaluationState(),
            config = cfg,
            cabinNoiseDb = 84f,
            speedKmH = 140f,
            nowMs = 5000L
        )
        assertEquals(AdaptiveCabinProfile.QUIET, res.activeProfile)
        assertEquals("disabled", res.triggerReason)
    }

    @Test
    fun escalatesOnSpeedThresholdWithDwellAndHoldsWithHysteresis() {
        val cfg = AdaptiveCabinConfig(enabled = true, source = AdaptiveCabinSource.SPEED)
        val s1 = evaluateAdaptiveCabinState(AdaptiveEvaluationState(), cfg, null, 95f, 1000L)
        assertEquals(AdaptiveCabinProfile.QUIET, s1.activeProfile)
        assertEquals(AdaptiveCabinProfile.HIGHWAY, s1.pendingProfile)

        val s2 = evaluateAdaptiveCabinState(s1, cfg, null, 96f, 2500L)
        assertEquals(AdaptiveCabinProfile.HIGHWAY, s2.activeProfile)
        assertEquals("speed", s2.triggerReason)

        // Small dip to 80 km/h stays in HIGHWAY because 85 - 7 = 78 km/h hysteresis
        val s3 = evaluateAdaptiveCabinState(s2, cfg, null, 80f, 6000L)
        assertEquals(AdaptiveCabinProfile.HIGHWAY, s3.activeProfile)

        // Sustained slow-down to 15 km/h brings smoothed speed below city threshold and de-escalates after dwell
        var state = s3
        var now = 7000L
        repeat(16) {
            now += 400L
            state = evaluateAdaptiveCabinState(state, cfg, null, 14f, now)
        }
        assertEquals(AdaptiveCabinProfile.QUIET, state.activeProfile)
    }

    @Test
    fun hybridModeSwitchesOnCabinMicNoiseEvenWhenParked() {
        val cfg = AdaptiveCabinConfig(enabled = true, source = AdaptiveCabinSource.HYBRID)
        val s1 = evaluateAdaptiveCabinState(AdaptiveEvaluationState(), cfg, 79f, 0f, 1000L)
        val s2 = evaluateAdaptiveCabinState(s1, cfg, 78f, 0f, 2600L)
        assertEquals(AdaptiveCabinProfile.HIGH_NOISE, s2.activeProfile)
        assertEquals("microphone", s2.triggerReason)
    }

    @Test
    fun appliesDirectionalBalanceFaderAndGainOverlayNonDestructively() {
        val lhdCfg = AdaptiveCabinConfig(
            enabled = true,
            sensitivity = AdaptiveCabinSensitivity.BALANCED,
            cabinFocus = AdaptiveCabinFocus.DRIVER_LHD
        )
        val rhdCfg = lhdCfg.copy(cabinFocus = AdaptiveCabinFocus.DRIVER_RHD)
        val centerCfg = lhdCfg.copy(cabinFocus = AdaptiveCabinFocus.FRONT_CENTER)

        val lhdDelta = calculateAdaptiveDspDelta(AdaptiveCabinProfile.HIGHWAY, lhdCfg)
        assertEquals(3.0f, lhdDelta.gainDb, 0.01f)
        assertTrue(lhdDelta.balanceDelta < 0)
        assertTrue(lhdDelta.faderDelta > 0)

        val rhdDelta = calculateAdaptiveDspDelta(AdaptiveCabinProfile.HIGHWAY, rhdCfg)
        assertEquals(-lhdDelta.balanceDelta, rhdDelta.balanceDelta)

        val centerDelta = calculateAdaptiveDspDelta(AdaptiveCabinProfile.HIGHWAY, centerCfg)
        assertEquals(0, centerDelta.balanceDelta)

        val base = DspSettings(preampDb = -2f, balance = 0, fader = 0)
        val overlaid = applyAdaptiveCabinOverlay(base, lhdDelta)
        assertEquals(1.0f, overlaid.preampDb, 0.01f)
        assertEquals(lhdDelta.balanceDelta, overlaid.balance)
        assertEquals(lhdDelta.faderDelta, overlaid.fader)
        assertEquals(-2f, base.preampDb, 0.01f)
    }

    @Test
    fun pcmNoiseEstimatorScalesWithSignalAmplitude() {
        val quiet = ShortArray(512) { 18 }
        val loud = ShortArray(512) { 4500 }
        val quietDb = estimateCabinNoiseDbFromPcm16(quiet)
        val loudDb = estimateCabinNoiseDbFromPcm16(loud)
        assertTrue("Expected quiet < 45 dB, got $quietDb", quietDb < 45f)
        assertTrue("Expected loud > 75 dB, got $loudDb", loudDb > 75f)
    }

    @Test
    fun smoothSpeedSampleAttenuatesTransientSpikesAndConvergesMonotonically() {
        val afterSpike = smoothSpeedSample(40f, 150f, 250L, isInitialSample = false)
        assertTrue("Expected spike attenuation < 45 km/h, got $afterSpike", afterSpike < 45f)

        var speed = 40f
        repeat(25) {
            val next = smoothSpeedSample(speed, 100f, 200L, isInitialSample = false)
            assertTrue("Expected monotonic climb $speed -> $next", next >= speed && next <= 100f)
            speed = next
        }
        assertTrue("Expected convergence > 88 km/h, got $speed", speed > 88f)
    }

    @Test
    fun smoothAdaptiveDspDeltaRateLimitsGainAndDecaysCleanly() {
        val cfg = AdaptiveCabinConfig(enabled = true, source = AdaptiveCabinSource.SPEED)
        val target = calculateAdaptiveDspDelta(AdaptiveCabinProfile.HIGH_NOISE, cfg)
        var current = AdaptiveDspDelta()
        var prevGain = 0f

        repeat(40) {
            current = smoothAdaptiveDspDelta(current, target, 200L)
            val step = current.gainDb - prevGain
            assertTrue("Unexpected gain jump $step dB", step >= 0f && step <= 0.35f)
            prevGain = current.gainDb
        }
        assertEquals(target.gainDb, current.gainDb, 0.01f)
        assertEquals(target.balanceDelta, current.balanceDelta)
        assertEquals(target.faderDelta, current.faderDelta)

        val midRelease = smoothAdaptiveDspDelta(current, AdaptiveDspDelta(), 200L)
        assertEquals(AdaptiveCabinProfile.QUIET, midRelease.profile)
        assertTrue(midRelease.gainDb > 0f && midRelease.gainDb < current.gainDb)
        val overlaid = applyAdaptiveCabinOverlay(DspSettings(preampDb = 0f), midRelease)
        assertEquals(midRelease.gainDb, overlaid.preampDb, 0.01f)
    }

    @Test
    fun calibrationDeltaTracksCabinNoiseVersusDspGain() {
        val cfg = AdaptiveCabinConfig(enabled = true, source = AdaptiveCabinSource.HYBRID, noiseModerateDb = 58)

        val quiet = calculateNoiseGainCalibrationDelta(52.0f, cfg, AdaptiveDspDelta())
        assertEquals(-6.0f, quiet.deltaDb!!, 0.01f)
        assertEquals(CalibrationBalanceStatus.QUIET_FLOOR, quiet.status)

        val cityDelta = calculateAdaptiveDspDelta(AdaptiveCabinProfile.CITY, cfg)
        val balanced = calculateNoiseGainCalibrationDelta(62.5f, cfg, cityDelta)
        assertEquals(3.0f, balanced.deltaDb!!, 0.01f)
        assertEquals(CalibrationBalanceStatus.BALANCED, balanced.status)

        val under = calculateNoiseGainCalibrationDelta(75.0f, cfg, cityDelta)
        assertEquals(15.5f, under.deltaDb!!, 0.01f)
        assertEquals(CalibrationBalanceStatus.UNDER_COMPENSATED, under.status)

        val highwayDelta = calculateAdaptiveDspDelta(AdaptiveCabinProfile.HIGHWAY, cfg)
        val over = calculateNoiseGainCalibrationDelta(55.0f, cfg, highwayDelta)
        assertEquals(-6.0f, over.deltaDb!!, 0.01f)
        assertEquals(CalibrationBalanceStatus.OVER_COMPENSATED, over.status)

        val speedOnly = calculateNoiseGainCalibrationDelta(65.0f, cfg.copy(source = AdaptiveCabinSource.SPEED), highwayDelta)
        assertEquals(null, speedOnly.deltaDb)
        assertEquals(CalibrationBalanceStatus.SPEED_ONLY, speedOnly.status)
    }
}
