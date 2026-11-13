import test from 'node:test';
import assert from 'node:assert/strict';
import {
  calculateSpeedVolumeBoost,
  clampEqGain,
  calculateAttenuationMultiplier,
  calculateSleepTimerFade,
  EQ_PRESETS,
} from './audioEqualizer';

test('calculateSpeedVolumeBoost: zero boost when off or speed <= 15 km/h', () => {
  const res1 = calculateSpeedVolumeBoost(10, 'medium');
  assert.equal(res1.dbBoost, 0);
  assert.equal(res1.linearMultiplier, 1.0);

  const res2 = calculateSpeedVolumeBoost(100, 'off');
  assert.equal(res2.dbBoost, 0);
  assert.equal(res2.linearMultiplier, 1.0);
});

test('calculateSpeedVolumeBoost: boosts correctly at highway speed and caps at max', () => {
  // 75 km/h with low (+1 dB per 30 km/h after 15 km/h -> 60 / 30 * 1 = +2 dB)
  const resLow = calculateSpeedVolumeBoost(75, 'low');
  assert.equal(resLow.dbBoost, 2);

  // 135 km/h with high (+3 dB per 30 km/h after 15 -> 120 / 30 * 3 = 12, capped at 9 dB)
  const resHigh = calculateSpeedVolumeBoost(135, 'high');
  assert.equal(resHigh.dbBoost, 9);
  assert.ok(resHigh.linearMultiplier > 2.0);
});

test('clampEqGain: clamps within -12 and +12', () => {
  assert.equal(clampEqGain(-20), -12);
  assert.equal(clampEqGain(18), 12);
  assert.equal(clampEqGain(4.2), 4);
});

test('calculateAttenuationMultiplier: attenuates by approx 0.1 at -20dB', () => {
  assert.equal(calculateAttenuationMultiplier(false), 1.0);
  const ducked = calculateAttenuationMultiplier(true, -20);
  assert.ok(Math.abs(ducked - 0.1) < 0.01);
});

test('calculateSleepTimerFade: smooth fade-out during last 10 seconds', () => {
  assert.equal(calculateSleepTimerFade(30), 1.0);
  assert.equal(calculateSleepTimerFade(10), 1.0);
  assert.equal(calculateSleepTimerFade(5), 0.5);
  assert.equal(calculateSleepTimerFade(0), 0.0);
  assert.equal(calculateSleepTimerFade(null), 0.0);
});

test('EQ_PRESETS: has valid presets and 5 bands each', () => {
  assert.ok(EQ_PRESETS.length >= 5);
  for (const preset of EQ_PRESETS) {
    assert.equal(preset.bands.length, 5);
  }
});
