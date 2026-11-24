import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  DSP_16_FREQUENCIES,
  FACTORY_DSP_PRESETS,
  DEFAULT_DSP_SETTINGS,
  DEFAULT_DEVICE_PRESET_MAP,
  SURROUND_MODES,
  interpolate5To16Bands,
  downsample16To5Bands,
  calculateLoudnessContourGains,
  calculateRoadNoiseContourDb,
  computeCabinRtaCorrections,
  calculateBalanceGains,
  calculateCabinStageGains,
  calculateStereoWidthMatrix,
  buildDspResponseCurvePoints,
  matchPresetForTrack,
  applyPresetToDsp,
  buildSurroundMatrixDiagnostics,
  formatSurroundMatrixDiagnosticLog,
  sanitizeDspSettings,
  sanitizeCustomPresets,
  dbToLinearGain,
  DspBandGains16,
  SurroundModeId,
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

  it('computes speed-sensitive Road-Noise Acoustic Contour smoothly above 50 km/h', () => {
    assert.deepEqual(calculateRoadNoiseContourDb(120, false), { subBassDb: 0, vocalPresenceDb: 0 });
    assert.deepEqual(calculateRoadNoiseContourDb(45, true), { subBassDb: 0, vocalPresenceDb: 0 });
    const cruise = calculateRoadNoiseContourDb(90, true);
    const highway = calculateRoadNoiseContourDb(130, true);
    assert.ok(cruise.subBassDb > 1.0 && cruise.subBassDb < highway.subBassDb);
    assert.ok(cruise.vocalPresenceDb > 0.4 && cruise.vocalPresenceDb < highway.vocalPresenceDb);
    assert.equal(highway.subBassDb, 4.0);
    assert.equal(highway.vocalPresenceDb, 1.5);
  });

  it('computes damped inverse Cabin RTA EQ corrections clamped to [-6, +6] dB', () => {
    // Simulate a harsh 3.5kHz cabin peak (+10 dB) and weak 64Hz bass (-10 dB) relative to -30 dB mids
    const measured = [
      -40, -40, -35, -30, -30, -30, -30, -30, -20, -22, -30, -30, -30, -30, -30, -30,
    ];
    const corr = computeCabinRtaCorrections(measured);
    assert.equal(corr.length, 16);
    assert.ok(corr[1] > 4, 'Weak 64Hz bass should receive positive boost');
    assert.ok(corr[8] <= -5, 'Harsh 3.5kHz peak should be attenuated');
    assert.ok(corr.every((g) => g >= -6 && g <= 6));
  });

  it('calculates L/R channel balance attenuation and 2D Cabin Stage gains cleanly', () => {
    assert.deepEqual(calculateBalanceGains(0), { leftGain: 1, rightGain: 1 });
    assert.deepEqual(calculateBalanceGains(-100), { leftGain: 1, rightGain: 0 });
    assert.deepEqual(calculateBalanceGains(100), { leftGain: 0, rightGain: 1 });
    assert.deepEqual(calculateBalanceGains(50), { leftGain: 0.5, rightGain: 1 });

    const centerStage = calculateCabinStageGains(0, 0);
    assert.deepEqual(centerStage, { leftGain: 1, rightGain: 1, faderHighShelfDb: 0 });

    const rearBias = calculateCabinStageGains(0, -100);
    assert.ok(rearBias.leftGain < 1 && rearBias.rightGain < 1);
    assert.equal(rearBias.faderHighShelfDb, -2.5);
  });

  it('computes transparent Mid/Side matrix coefficients for stereo width & exposes Logic 7 surround modes', () => {
    assert.deepEqual(calculateStereoWidthMatrix(100), { directGain: 1, crossGain: 0 });
    assert.deepEqual(calculateStereoWidthMatrix(0), { directGain: 0.5, crossGain: 0.5 });
    assert.deepEqual(calculateStereoWidthMatrix(150), { directGain: 1.25, crossGain: -0.25 });
    assert.ok(SURROUND_MODES.logic7_surround.haasLeftMs > 10);
    assert.ok(SURROUND_MODES.concert_hall.haasLeftMs > SURROUND_MODES.logic7_surround.haasLeftMs);
    assert.ok(SURROUND_MODES.studio_focused.centerBoostMaxDb > 0);
    assert.equal(SURROUND_MODES.dolby_pro_logic_ii.useAllPassPhase, true);
    assert.ok(SURROUND_MODES.dolby_atmos_virtual.heightBoostMaxDb >= 4.0);
    assert.ok(SURROUND_MODES.virtual_surround_360.crosstalkCancelGain > 0.1);
  });

  it('matches presets deterministically from track genre, folder path, and title', () => {
    assert.equal(matchPresetForTrack('Podcast', '/Audio/Shows', 'Episode 1'), 'podcast_clarity');
    assert.equal(matchPresetForTrack('Heavy Metal', '/Music/Iron', 'Track'), 'rock_metal');
    assert.equal(matchPresetForTrack(null, '/Music/Techno Sets', 'Live'), 'club_electronic');
    assert.equal(matchPresetForTrack('Hip-Hop', '/Music/Beats', '808'), 'cabin_bass');
    assert.equal(matchPresetForTrack('Acoustic Jazz', '/Music/Trio', 'Blue'), 'vocal_jazz');
    assert.equal(matchPresetForTrack('Pop', '/Music/Hits', 'Song'), null);

    const updated = applyPresetToDsp(DEFAULT_DSP_SETTINGS, FACTORY_DSP_PRESETS[5]);
    assert.equal(updated.selectedPresetId, FACTORY_DSP_PRESETS[5].id);
    assert.equal(updated.dspEnabled, true);
  });

  it('builds SVG response curve reflecting 12 vs 24 dB/oct HPF slope, Road-Noise Contour & Sub Trim', () => {
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

    const slope12 = buildDspResponseCurvePoints(
      {
        dspEnabled: true,
        bands16: new Array(16).fill(0) as DspBandGains16,
        bassBoostDb: 0,
        bassBoostFreqHz: 80,
        hpfCutoffHz: 100,
        crossoverSlopeDb: 12,
        loudnessContour: false,
      },
      85
    );
    const slope24 = buildDspResponseCurvePoints(
      {
        dspEnabled: true,
        bands16: new Array(16).fill(0) as DspBandGains16,
        bassBoostDb: 0,
        bassBoostFreqHz: 80,
        hpfCutoffHz: 100,
        crossoverSlopeDb: 24,
        loudnessContour: false,
      },
      85
    );
    // At 50 Hz (1 octave below 100 Hz HPF), 24 dB/oct cuts deeper than 12 dB/oct
    const pt12 = slope12.find((p) => p.freq >= 45 && p.freq <= 55)!;
    const pt24 = slope24.find((p) => p.freq >= 45 && p.freq <= 55)!;
    assert.ok(pt24.db < pt12.db, `Expected 24dB/oct (${pt24.db}) < 12dB/oct (${pt12.db})`);

    const highwayContour = buildDspResponseCurvePoints(
      {
        dspEnabled: true,
        bands16: new Array(16).fill(0) as DspBandGains16,
        bassBoostDb: 0,
        bassBoostFreqHz: 65,
        hpfCutoffHz: 0,
        loudnessContour: false,
        roadNoiseEqEnabled: true,
      },
      85,
      640,
      140,
      120
    );
    assert.ok(highwayContour[0].db > 2.5);

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

  it('sanitizes persisted DSP settings including Logic 7, 24dB/oct slope, sub delay & custom presets', () => {
    const sanitized = sanitizeDspSettings({
      dspEnabled: false,
      eqViewMode: '5band',
      preampDb: 99,
      bassBoostDb: -10,
      hpfCutoffHz: 999,
      crossoverSlopeDb: 24,
      leftDelayMs: 12,
      fader: -200,
      stereoWidthPercent: 200,
      compressorProfile: 'invalid',
      subPhaseInverted: true,
      subLpfHz: 120,
      subTrimDb: 20,
      subDelayMs: 9.4,
      monoFoldDown: true,
      peakLimiterCeilingDb: -2.9,
      autoGenrePresetEnabled: true,
      autoSwitchDevicePreset: true,
      devicePresetMap: { bluetooth_a2dp: 'rock_metal' },
      surroundMode: 'dolby_atmos_virtual',
      surroundIntensity: 140,
      centerCoherence: 73,
      virtualHeight: 112,
      roadNoiseEqEnabled: true,
      bands16: [25, -25, 3],
    });

    assert.equal(sanitized.dspEnabled, false);
    assert.equal(sanitized.eqViewMode, '5band');
    assert.equal(sanitized.preampDb, 6);
    assert.equal(sanitized.bassBoostDb, 0);
    assert.equal(sanitized.hpfCutoffHz, DEFAULT_DSP_SETTINGS.hpfCutoffHz);
    assert.equal(sanitized.crossoverSlopeDb, 24);
    assert.equal(sanitized.leftDelayMs, 5);
    assert.equal(sanitized.fader, -100);
    assert.equal(sanitized.stereoWidthPercent, 150);
    assert.equal(sanitized.compressorProfile, DEFAULT_DSP_SETTINGS.compressorProfile);
    assert.equal(sanitized.subPhaseInverted, true);
    assert.equal(sanitized.subLpfHz, 120);
    assert.equal(sanitized.subTrimDb, 6);
    assert.equal(sanitized.subDelayMs, 9.5);
    assert.equal(sanitized.monoFoldDown, true);
    assert.equal(sanitized.peakLimiterCeilingDb, -3.0);
    assert.equal(sanitized.autoGenrePresetEnabled, true);
    assert.equal(sanitized.autoSwitchDevicePreset, true);
    assert.equal(sanitized.devicePresetMap.bluetooth_a2dp, 'rock_metal');
    assert.equal(sanitized.devicePresetMap.headunit_speakers, DEFAULT_DEVICE_PRESET_MAP.headunit_speakers);
    assert.equal(sanitized.surroundMode, 'dolby_atmos_virtual');
    assert.equal(sanitized.surroundIntensity, 100);
    assert.equal(sanitized.centerCoherence, 75);
    assert.equal(sanitized.virtualHeight, 100);
    assert.equal(sanitized.roadNoiseEqEnabled, true);
    assert.equal(sanitized.bands16[0], 12);
    assert.equal(sanitized.bands16[1], -12);
    assert.equal(sanitized.bands16[2], 3);
    assert.equal(sanitized.bands16[15], 0);

    const atmosCurve = buildDspResponseCurvePoints(
      {
        dspEnabled: true,
        bands16: new Array(16).fill(0) as DspBandGains16,
        bassBoostDb: 0,
        bassBoostFreqHz: 65,
        hpfCutoffHz: 0,
        loudnessContour: false,
        surroundMode: 'dolby_atmos_virtual',
        centerCoherence: 50,
        virtualHeight: 100,
      },
      85
    );
    const heightPoint = atmosCurve.find((p) => p.freq >= 6500 && p.freq <= 8000)!;
    assert.ok(heightPoint.db > 2.0, `Expected Atmos virtual height lift at ~7.2 kHz, got ${heightPoint.db} dB`);

    const presets = sanitizeCustomPresets([
      null,
      { id: 'bad' },
      {
        id: 'ok',
        name: '  W203 Custom  ',
        bands16: [2, 3],
        preampDb: -3,
        bassBoostDb: 4,
        surroundMode: 'logic7_surround',
        surroundIntensity: 85,
        centerCoherence: 65,
        virtualHeight: 55,
      },
    ]);
    assert.equal(presets.length, 1);
    assert.equal(presets[0].name, 'W203 Custom');
    assert.equal(presets[0].isCustom, true);
    assert.equal(presets[0].preampDb, -3);
    assert.equal(presets[0].surroundMode, 'logic7_surround');
    assert.equal(presets[0].surroundIntensity, 85);
  });

  it('verifies applyPresetToDsp preserves Logic 7 for EQ presets and applies explicit surround presets', () => {
    const withLogic7 = {
      ...DEFAULT_DSP_SETTINGS,
      dspEnabled: false,
      surroundMode: 'logic7_surround' as SurroundModeId,
      surroundIntensity: 80,
      centerCoherence: 65,
      virtualHeight: 45,
    };

    const afterFactoryEq = applyPresetToDsp(withLogic7, FACTORY_DSP_PRESETS[1]);
    assert.equal(afterFactoryEq.dspEnabled, true);
    assert.equal(afterFactoryEq.selectedPresetId, FACTORY_DSP_PRESETS[1].id);
    assert.equal(afterFactoryEq.surroundMode, 'logic7_surround');
    assert.equal(afterFactoryEq.surroundIntensity, 80);

    const customSurroundPreset = {
      ...FACTORY_DSP_PRESETS[0],
      id: 'custom_atmos',
      name: 'Atmos Stage',
      isCustom: true,
      surroundMode: 'dolby_atmos_virtual' as SurroundModeId,
      surroundIntensity: 90,
      centerCoherence: 70,
      virtualHeight: 85,
    };
    const afterSurroundPreset = applyPresetToDsp(
      { ...withLogic7, monoFoldDown: true },
      customSurroundPreset
    );
    assert.equal(afterSurroundPreset.dspEnabled, true);
    assert.equal(afterSurroundPreset.monoFoldDown, false);
    assert.equal(afterSurroundPreset.surroundMode, 'dolby_atmos_virtual');
    assert.equal(afterSurroundPreset.surroundIntensity, 90);
    assert.equal(afterSurroundPreset.centerCoherence, 70);
    assert.equal(afterSurroundPreset.virtualHeight, 85);
  });

  it('builds and formats Surround Matrix diagnostic logs across all 7 surround modes', () => {
    const allModes = Object.keys(SURROUND_MODES) as SurroundModeId[];
    assert.equal(allModes.length, 7);

    for (const mode of allModes) {
      const settings = {
        ...DEFAULT_DSP_SETTINGS,
        dspEnabled: true,
        surroundMode: mode,
        surroundIntensity: 75,
        centerCoherence: 60,
        virtualHeight: 70,
      };
      const diag = buildSurroundMatrixDiagnostics('unit_test', settings, 48000);
      const formatted = formatSurroundMatrixDiagnosticLog(diag);

      assert.equal(diag.modeId, mode);
      assert.equal(diag.surroundActive, mode !== 'off');
      assert.ok(formatted.includes('[DSP Surround Diagnostics]'));
      assert.ok(formatted.includes(`mode=${mode}`));
      assert.ok(formatted.includes('width='));
      assert.ok(formatted.includes('haas='));

      if (mode === 'off') {
        assert.equal(diag.effectiveStereoWidthPercent, settings.stereoWidthPercent);
        assert.equal(diag.sideWetGain, 0);
      } else {
        assert.ok(diag.effectiveStereoWidthPercent >= settings.stereoWidthPercent);
        assert.ok(diag.sideWetGain > 0);
      }
      if (mode === 'dolby_pro_logic_ii') {
        assert.equal(diag.allPassPhase90Active, true);
      }
      if (mode === 'dolby_atmos_virtual') {
        assert.ok(diag.heightBoostDb > 2.0);
      }
      if (mode === 'virtual_surround_360') {
        assert.ok(diag.xtcGain > 0.08);
      }
    }
  });
});


