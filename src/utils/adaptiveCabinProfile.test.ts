import test from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_ADAPTIVE_CABIN_CONFIG,
  INITIAL_ADAPTIVE_STATE,
  ZERO_ADAPTIVE_DELTA,
  AdaptiveCabinConfig,
  applyAdaptiveCabinOverlay,
  calculateAdaptiveDspDelta,
  calculateNoiseGainCalibrationDelta,
  estimateCabinNoiseDbFromPcm,
  evaluateAdaptiveCabinState,
  sanitizeAdaptiveCabinConfig,
  smoothAdaptiveDspDelta,
  smoothSpeedSample,
} from './adaptiveCabinProfile';
import { DEFAULT_DSP_SETTINGS } from './dspConsole';

test('adaptive profile stays quiet when disabled even at high speed and noise', () => {
  const config: AdaptiveCabinConfig = { ...DEFAULT_ADAPTIVE_CABIN_CONFIG, enabled: false };
  const next = evaluateAdaptiveCabinState(INITIAL_ADAPTIVE_STATE, config, {
    cabinNoiseDb: 82,
    speedKmH: 140,
    nowMs: 5000,
  });
  assert.equal(next.activeProfile, 'quiet');
  assert.equal(next.triggerReason, 'disabled');
});

test('escalates smoothly after dwell time on GPS speed threshold and de-escalates with hysteresis', () => {
  const config: AdaptiveCabinConfig = {
    ...DEFAULT_ADAPTIVE_CABIN_CONFIG,
    enabled: true,
    source: 'speed',
  };

  // First sample above 85 km/h starts pending dwell
  const s1 = evaluateAdaptiveCabinState(INITIAL_ADAPTIVE_STATE, config, {
    cabinNoiseDb: null,
    speedKmH: 95,
    nowMs: 1000,
  });
  assert.equal(s1.activeProfile, 'quiet');
  assert.equal(s1.pendingProfile, 'highway');

  // After 1500ms (>= 1400ms escalate dwell), switches to highway
  const s2 = evaluateAdaptiveCabinState(s1, config, {
    cabinNoiseDb: null,
    speedKmH: 96,
    nowMs: 2500,
  });
  assert.equal(s2.activeProfile, 'highway');
  assert.equal(s2.triggerReason, 'speed');

  // Small dip to 81 km/h stays in highway due to 7 km/h hysteresis (85 - 7 = 78 km/h)
  const s3 = evaluateAdaptiveCabinState(s2, config, {
    cabinNoiseDb: null,
    speedKmH: 81,
    nowMs: 7000,
  });
  assert.equal(s3.activeProfile, 'highway');
  assert.equal(s3.pendingProfile, null);

  // Sustained slow-down to 20 km/h brings smoothed speed below city threshold and de-escalates after dwell
  let state = s3;
  let now = 8000;
  for (let i = 0; i < 16; i++) {
    now += 400;
    state = evaluateAdaptiveCabinState(state, config, {
      cabinNoiseDb: null,
      speedKmH: 18,
      nowMs: now,
    });
  }
  assert.equal(state.activeProfile, 'quiet');
});

test('hybrid mode picks highest severity between cabin mic dB and GPS speed', () => {
  const config: AdaptiveCabinConfig = {
    ...DEFAULT_ADAPTIVE_CABIN_CONFIG,
    enabled: true,
    source: 'hybrid',
  };

  // Parked at 0 km/h but loud cabin (78 dB >= 76 dB extreme threshold)
  const s1 = evaluateAdaptiveCabinState(INITIAL_ADAPTIVE_STATE, config, {
    cabinNoiseDb: 78,
    speedKmH: 0,
    nowMs: 1000,
  });
  const s2 = evaluateAdaptiveCabinState(s1, config, {
    cabinNoiseDb: 79,
    speedKmH: 0,
    nowMs: 2600,
  });
  assert.equal(s2.activeProfile, 'high_noise');
  assert.equal(s2.triggerReason, 'microphone');
});

test('calculates directional balance, fader & gain deltas and applies non-destructive DSP overlay', () => {
  const lhdCfg: AdaptiveCabinConfig = {
    ...DEFAULT_ADAPTIVE_CABIN_CONFIG,
    enabled: true,
    cabinFocus: 'driver_lhd',
    sensitivity: 'balanced',
  };
  const rhdCfg: AdaptiveCabinConfig = { ...lhdCfg, cabinFocus: 'driver_rhd' };
  const centerCfg: AdaptiveCabinConfig = { ...lhdCfg, cabinFocus: 'front_center' };

  const lhdDelta = calculateAdaptiveDspDelta('highway', lhdCfg);
  assert.equal(lhdDelta.gainDb, 3);
  assert.ok(lhdDelta.balanceDelta < 0, 'LHD should shift balance slightly left toward driver');
  assert.ok(lhdDelta.faderDelta > 0, 'Highway should shift fader toward front stage');

  const rhdDelta = calculateAdaptiveDspDelta('highway', rhdCfg);
  assert.equal(rhdDelta.balanceDelta, -lhdDelta.balanceDelta);

  const centerDelta = calculateAdaptiveDspDelta('highway', centerCfg);
  assert.equal(centerDelta.balanceDelta, 0);
  assert.equal(centerDelta.faderDelta, lhdDelta.faderDelta);

  const base = { ...DEFAULT_DSP_SETTINGS, preampDb: -2, balance: 0, fader: 0 };
  const overlaid = applyAdaptiveCabinOverlay(base, lhdDelta);
  assert.equal(overlaid.preampDb, 1);
  assert.equal(overlaid.balance, lhdDelta.balanceDelta);
  assert.equal(overlaid.fader, lhdDelta.faderDelta);
  // Base object untouched
  assert.equal(base.preampDb, -2);
  assert.equal(base.balance, 0);
});

test('estimateCabinNoiseDbFromPcm scales monotonically with signal amplitude', () => {
  const quietSamples = new Float32Array(512).fill(0.0005);
  const loudSamples = new Float32Array(512).fill(0.12);
  const quietDb = estimateCabinNoiseDbFromPcm(quietSamples);
  const loudDb = estimateCabinNoiseDbFromPcm(loudSamples);
  assert.ok(quietDb < 45, `expected quiet < 45 dB, got ${quietDb}`);
  assert.ok(loudDb > 75, `expected loud > 75 dB, got ${loudDb}`);
});

test('sanitizeAdaptiveCabinConfig enforces monotonic ordered thresholds', () => {
  const sanitized = sanitizeAdaptiveCabinConfig({
    enabled: true,
    source: 'invalid_src',
    speedCityKmH: 70,
    speedHighwayKmH: 50,
    speedHighKmH: 40,
    noiseModerateDb: 68,
    noiseLoudDb: 60,
    noiseExtremeDb: 55,
  });
  assert.equal(sanitized.source, 'hybrid');
  assert.ok(sanitized.speedCityKmH < sanitized.speedHighwayKmH);
  assert.ok(sanitized.speedHighwayKmH < sanitized.speedHighKmH);
  assert.ok(sanitized.noiseModerateDb < sanitized.noiseLoudDb);
  assert.ok(sanitized.noiseLoudDb < sanitized.noiseExtremeDb);
});

test('smoothSpeedSample clamps transient GPS spikes and filters acceleration jitter', () => {
  // Single bogus GPS spike from 40 km/h -> 150 km/h in 250ms must not jump straight to highway/autobahn
  const afterSpike = smoothSpeedSample(40, 150, 250, false);
  assert.ok(afterSpike < 45, `expected spike to be heavily attenuated, got ${afterSpike}`);

  // Sustained acceleration from 40 -> 100 km/h over 5 seconds rises monotonically without overshoot
  let speed = 40;
  for (let i = 0; i < 25; i++) {
    const next = smoothSpeedSample(speed, 100, 200, false);
    assert.ok(next >= speed && next <= 100, `expected monotonic rise, got ${speed} -> ${next}`);
    speed = next;
  }
  assert.ok(speed > 88, `expected speed to converge near 100 km/h after 5s, got ${speed}`);
});

test('smoothAdaptiveDspDelta rate-limits gain and balance changes without abrupt step jumps', () => {
  const cfg: AdaptiveCabinConfig = {
    ...DEFAULT_ADAPTIVE_CABIN_CONFIG,
    enabled: true,
    source: 'speed',
    sensitivity: 'balanced',
    cabinFocus: 'driver_lhd',
  };
  const target = calculateAdaptiveDspDelta('high_noise', cfg); // +4.5 dB, -18 -> -20 bal, +25 fad

  let current = { ...ZERO_ADAPTIVE_DELTA };
  let prevGain = 0;
  // Ramp in 200ms ticks and verify each step obeys slew ceiling (<= 1.25 dB/s => <= 0.3 dB/tick)
  for (let i = 0; i < 40; i++) {
    current = smoothAdaptiveDspDelta(current, target, 200);
    const stepDelta = current.gainDb - prevGain;
    assert.ok(stepDelta >= 0 && stepDelta <= 0.35, `unexpected gain jump ${stepDelta} dB at tick ${i}`);
    prevGain = current.gainDb;
  }
  assert.equal(current.gainDb, target.gainDb);
  assert.equal(current.balanceDelta, target.balanceDelta);
  assert.equal(current.faderDelta, target.faderDelta);

  // Ramping back down to quiet applies non-zero decaying overlay cleanly until settled at 0
  const midRelease = smoothAdaptiveDspDelta(current, ZERO_ADAPTIVE_DELTA, 200);
  assert.equal(midRelease.profileId, 'quiet');
  assert.ok(midRelease.gainDb > 0 && midRelease.gainDb < current.gainDb);

  const base = { ...DEFAULT_DSP_SETTINGS, preampDb: 0 };
  const decayingOverlay = applyAdaptiveCabinOverlay(base, midRelease);
  assert.equal(decayingOverlay.preampDb, midRelease.gainDb);
});

test('calculateNoiseGainCalibrationDelta computes accurate noise-vs-DSP calibration deltas', () => {
  const cfg: AdaptiveCabinConfig = {
    ...DEFAULT_ADAPTIVE_CABIN_CONFIG,
    enabled: true,
    source: 'hybrid',
    noiseModerateDb: 58,
  };

  // Quiet floor below threshold
  const quiet = calculateNoiseGainCalibrationDelta(52.0, cfg, ZERO_ADAPTIVE_DELTA);
  assert.equal(quiet.noiseAboveFloorDb, -6.0);
  assert.equal(quiet.deltaDb, -6.0);
  assert.equal(quiet.status, 'quiet_floor');

  // Balanced cruising (62.5 dB noise -> +4.5 above floor, +1.5 dB gain -> +3.0 dB delta)
  const cityDelta = calculateAdaptiveDspDelta('city', cfg);
  const balanced = calculateNoiseGainCalibrationDelta(62.5, cfg, cityDelta);
  assert.equal(balanced.noiseAboveFloorDb, 4.5);
  assert.equal(balanced.appliedGainDb, 1.5);
  assert.equal(balanced.deltaDb, 3.0);
  assert.equal(balanced.effectiveNoiseDb, 61.0);
  assert.equal(balanced.status, 'balanced');

  // Under-compensated loud highway (75 dB noise -> +17 above floor, +1.5 dB gain -> +15.5 dB delta)
  const under = calculateNoiseGainCalibrationDelta(75.0, cfg, cityDelta);
  assert.equal(under.deltaDb, 15.5);
  assert.equal(under.status, 'under_compensated');

  // Over-compensated / speed-boosted in quiet cabin (55 dB noise -> -3 above floor, +3.0 dB gain -> -6.0 dB delta)
  const highwayDelta = calculateAdaptiveDspDelta('highway', cfg);
  const over = calculateNoiseGainCalibrationDelta(55.0, cfg, highwayDelta);
  assert.equal(over.deltaDb, -6.0);
  assert.equal(over.status, 'over_compensated');

  // Speed-only mode returns clean speed_only status without NaN
  const speedOnly = calculateNoiseGainCalibrationDelta(65.0, { ...cfg, source: 'speed' }, highwayDelta);
  assert.equal(speedOnly.deltaDb, null);
  assert.equal(speedOnly.appliedGainDb, 3.0);
  assert.equal(speedOnly.status, 'speed_only');
});
