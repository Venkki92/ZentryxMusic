import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  DSP_16_FREQUENCIES,
  FACTORY_DSP_PRESETS,
  DEFAULT_DSP_SETTINGS,
  interpolate5To16Bands,
  downsample16To5Bands,
  calculateLoudnessContourGains,
  calculateBalanceGains,
  calculateStereoWidthMatrix,
  buildDspResponseCurvePoints,
  sanitizeDspSettings,
  sanitizeCustomPresets,
  dbToLinearGain,
  DspBandGains16,
} from './dspConsole';

describe('DSP Console Engine Math & Validation', () => {
  it('exposes 16 ISO center frequencies and 9 factory presets with 16 bands each', () => {
    assert.equal(DSP_16_FREQUENCIES.length, 16);
    assert.equal(FACTORY_DSP_PRESETS.length, 9);
    for (const preset of FACTORY_DSP_PRESETS) {
      assert.equal(preset.bands16.length, 16);
    }
    const pioneer = FACTORY_DSP_PRESETS.find((p) => p.id === 'pioneer_ts_e170ci');
    assert.ok(pioneer);
    assert.equal(pioneer.hpfCutoffHz, 60);
    assert.equal(pioneer.bassBoostFreqHz, 80);
    assert.ok((pioneer.bassBoostDb ?? 0) >= 5.5);
    assert.ok((pioneer.preampDb ?? 0) < 0);
    assert.ok(pioneer.bands16[1] > 0 && pioneer.bands16[2] > 0);
    assert.ok(pioneer.bands16[8] < 0 && pioneer.bands16[9] < 0);
  });

  it('interpolates 5-band curve cleanly into 16 bands and downsamples back accurately', () => {
    const bassV = [8, 2, -2, 3, 6] as const;
    const expanded = interpolate5To16Bands(bassV);
    assert.equal(expanded.length, 16);
    assert.equal(expanded[0], 8);
    assert.equal(expanded[3], 2);
    assert.equal(expanded[5], -2);
    assert.ok(expanded[15] === 6);

    const compact = downsample16To5Bands(expanded);
    assert.equal(compact.length, 5);
    assert.equal(compact[1], 2);
    assert.equal(compact[2], -2);
  });

  it('computes Fletcher-Munson loudness contour inversely with volume and stops >= 80%', () => {
    const disabled = calculateLoudnessContourGains(false, 15);
    assert.deepEqual(disabled, { lowShelfDb: 0, highShelfDb: 0 });

    const lowVol = calculateLoudnessContourGains(true, 15);
    const midVol = calculateLoudnessContourGains(true, 50);
    const highVol = calculateLoudnessContourGains(true, 85);

    assert.ok(lowVol.lowShelfDb > midVol.lowShelfDb);
    assert.ok(lowVol.highShelfDb > midVol.highShelfDb);
    assert.deepEqual(highVol, { lowShelfDb: 0, highShelfDb: 0 });
  });

  it('calculates L/R channel balance attenuation cleanly', () => {
    assert.deepEqual(calculateBalanceGains(0), { leftGain: 1, rightGain: 1 });
    assert.deepEqual(calculateBalanceGains(-100), { leftGain: 1, rightGain: 0 });
    assert.deepEqual(calculateBalanceGains(100), { leftGain: 0, rightGain: 1 });
    assert.deepEqual(calculateBalanceGains(50), { leftGain: 0.5, rightGain: 1 });
  });

  it('computes transparent Mid/Side matrix coefficients for stereo width', () => {
    assert.deepEqual(calculateStereoWidthMatrix(100), { directGain: 1, crossGain: 0 });
    assert.deepEqual(calculateStereoWidthMatrix(0), { directGain: 0.5, crossGain: 0.5 });
    assert.deepEqual(calculateStereoWidthMatrix(150), { directGain: 1.25, crossGain: -0.25 });
  });

  it('builds SVG response curve reflecting HPF rolloff and Bass Boost lift', () => {
    const flatPoints = buildDspResponseCurvePoints(
      {
        dspEnabled: true,
        bands16: new Array(16).fill(0) as DspBandGains16,
        bassBoostDb: 0,
        bassBoostFreqHz: 60,
        hpfCutoffHz: 0,
        loudnessContour: false,
      },
      85
    );
    assert.equal(flatPoints.length, 48);
    assert.ok(flatPoints.every((p) => p.db === 0));

    const hpfAndBoost = buildDspResponseCurvePoints(
      {
        dspEnabled: true,
        bands16: new Array(16).fill(0) as DspBandGains16,
        bassBoostDb: 6,
        bassBoostFreqHz: 80,
        hpfCutoffHz: 100,
        loudnessContour: false,
      },
      85
    );
    assert.ok(hpfAndBoost[0].db < 0);

    const bypassed = buildDspResponseCurvePoints(
      {
        dspEnabled: false,
        bands16: new Array(16).fill(8) as DspBandGains16,
        bassBoostDb: 10,
        bassBoostFreqHz: 80,
        hpfCutoffHz: 120,
        loudnessContour: true,
      },
      20
    );
    assert.ok(bypassed.every((p) => p.db === 0));
  });

  it('converts dB to linear gain cleanly', () => {
    assert.equal(Math.round(dbToLinearGain(0) * 1000) / 1000, 1);
    assert.ok(Math.abs(dbToLinearGain(6) - 1.995) < 0.01);
    assert.ok(Math.abs(dbToLinearGain(-6) - 0.501) < 0.01);
  });

  it('sanitizes persisted DSP settings & custom presets safely', () => {
    const sanitized = sanitizeDspSettings({
      dspEnabled: false,
      eqViewMode: '5band',
      preampDb: 99,
      bassBoostDb: -10,
      hpfCutoffHz: 999,
      leftDelayMs: 12,
      stereoWidthPercent: 200,
      compressorProfile: 'invalid',
      bands16: [25, -25, 3],
    });

    assert.equal(sanitized.dspEnabled, false);
    assert.equal(sanitized.eqViewMode, '5band');
    assert.equal(sanitized.preampDb, 6);
    assert.equal(sanitized.bassBoostDb, 0);
    assert.equal(sanitized.hpfCutoffHz, DEFAULT_DSP_SETTINGS.hpfCutoffHz);
    assert.equal(sanitized.leftDelayMs, 5);
    assert.equal(sanitized.stereoWidthPercent, 150);
    assert.equal(sanitized.compressorProfile, DEFAULT_DSP_SETTINGS.compressorProfile);
    assert.equal(sanitized.bands16[0], 12);
    assert.equal(sanitized.bands16[1], -12);
    assert.equal(sanitized.bands16[2], 3);
    assert.equal(sanitized.bands16[15], 0);

    const presets = sanitizeCustomPresets([
      null,
      { id: 'bad' },
      { id: 'ok', name: '  W203 Custom  ', bands16: [2, 3], preampDb: -3, bassBoostDb: 4 },
    ]);
    assert.equal(presets.length, 1);
    assert.equal(presets[0].name, 'W203 Custom');
    assert.equal(presets[0].isCustom, true);
    assert.equal(presets[0].preampDb, -3);
  });
});
