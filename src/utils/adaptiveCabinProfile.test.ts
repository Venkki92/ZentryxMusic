import test from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_ADAPTIVE_CABIN_CONFIG,
  INITIAL_ADAPTIVE_STATE,
  AdaptiveCabinConfig,
  applyAdaptiveCabinOverlay,
  calculateAdaptiveDspDelta,
  estimateCabinNoiseDbFromPcm,
  evaluateAdaptiveCabinState,
  sanitizeAdaptiveCabinConfig,
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

  // Drop to 20 km/h starts de-escalate dwell, switches after >= 3200ms
  const s4 = evaluateAdaptiveCabinState(s3, config, {
    cabinNoiseDb: null,
    speedKmH: 20,
    nowMs: 8000,
  });
  assert.equal(s4.activeProfile, 'highway');
  assert.equal(s4.pendingProfile, 'quiet');

  const s5 = evaluateAdaptiveCabinState(s4, config, {
    cabinNoiseDb: null,
    speedKmH: 18,
    nowMs: 11500,
  });
  assert.equal(s5.activeProfile, 'quiet');
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
