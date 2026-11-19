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

        // Drop to 15 km/h de-escalates after >= 3200 ms
        val s4 = evaluateAdaptiveCabinState(s3, cfg, null, 15f, 7000L)
        val s5 = evaluateAdaptiveCabinState(s4, cfg, null, 12f, 10500L)
        assertEquals(AdaptiveCabinProfile.QUIET, s5.activeProfile)
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
}
