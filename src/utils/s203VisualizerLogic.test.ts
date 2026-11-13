import test from 'node:test';
import assert from 'node:assert/strict';
import {
  generateS203DialTicks,
  calculateNeedleAngle,
  calculatePeakHold,
  formatS203ClusterText,
  S203_COLORWAYS,
  MERCEDES_VISUALIZER_MODES,
  extractBandEnergies,
  calculateThreePointedStarVertices,
  pushRollingTelemetrySample,
  getVisualizerCanvasResolution,
  applyAttackReleaseEnvelope,
  updateRollingAgcPeak,
  computeLogSpectrumTargets,
  updateBeatTransient,
  normalizeTimeDomainBuffer,
} from './s203VisualizerLogic';

test('S203_COLORWAYS: contains amber, avantgarde, amg_gt, and ambient color schemes', () => {
  assert.equal(typeof S203_COLORWAYS.amber, 'object');
  assert.equal(S203_COLORWAYS.amber.primaryHex, '#FF9500');
  assert.equal(S203_COLORWAYS.avantgarde.primaryHex, '#F8FAFC');
  assert.equal(S203_COLORWAYS.amg_gt.primaryHex, '#EF4444');
  assert.equal(typeof S203_COLORWAYS.ambient.name, 'string');
});

test('MERCEDES_VISUALIZER_MODES: defines all 6 Mercedes-themed visualizers', () => {
  const ids = MERCEDES_VISUALIZER_MODES.map((m) => m.id);
  assert.deepEqual(ids, [
    'gauge',
    'star_pulse',
    'amg_telemetry',
    'burmester_wave',
    'comand',
    'mfd_amber',
  ]);
});

test('generateS203DialTicks: produces 25 ticks with major markers and redline', () => {
  const ticks = generateS203DialTicks(25, 135, 405);
  assert.equal(ticks.length, 25);
  assert.equal(ticks[0].angleDeg, 135);
  assert.equal(ticks[24].angleDeg, 405);
  assert.equal(ticks[0].isMajor, true);
  assert.equal(ticks[0].isRedline, false);
  assert.equal(ticks[23].isRedline, true);
  assert.equal(ticks[24].isRedline, true);
});

test('calculateNeedleAngle: maps energy 0 to startAngle and 1 to endAngle', () => {
  const minAngle = 135;
  const maxAngle = 405;

  const atZero = calculateNeedleAngle(0, minAngle, maxAngle);
  assert.equal(atZero, 135);

  const atFull = calculateNeedleAngle(1, minAngle, maxAngle);
  assert.equal(atFull, 405);

  const mid = calculateNeedleAngle(0.5, minAngle, maxAngle);
  assert.ok(mid > minAngle && mid < maxAngle);
});

test('calculatePeakHold: tracks peak value and smoothly decays with gravity', () => {
  let peak = 0;
  peak = calculatePeakHold(peak, 0.8, 0.05);
  assert.equal(peak, 0.8);

  peak = calculatePeakHold(peak, 0.2, 0.05);
  assert.equal(peak, 0.75);

  peak = calculatePeakHold(peak, 0.95, 0.05);
  assert.equal(peak, 0.95);
});

test('formatS203ClusterText: formats track and artist for VDO dot matrix', () => {
  assert.equal(formatS203ClusterText(''), 'AUDIO OFF');
  assert.equal(formatS203ClusterText('Blue Plz S1E03', 'Totalbiscuit'), 'BLUE PLZ S1E03 - TOTA…');
  assert.equal(formatS203ClusterText('Radio Active', 'Unknown'), 'RADIO ACTIVE');
});

test('extractBandEnergies: isolates sub-bass, mid, and treble from 256-bin FFT with AGC scaling', () => {
  const paused = extractBandEnergies(null, false, false, 1.0);
  assert.ok(paused.overall < 0.1);

  const fft = new Uint8Array(256);
  fft.fill(240, 0, 12); // Strong bass (first 5% bins)
  fft.fill(110, 12, 61); // Mid (5%..24%)
  fft.fill(50, 61, 158); // Treble (24%..62%)
  const real = extractBandEnergies(fft, true, true, 0, 0.94);
  assert.ok(real.bass > 0.85, `Expected strong bass, got ${real.bass}`);
  assert.ok(real.mid > 0.4 && real.mid < 0.85, `Expected moderate mid, got ${real.mid}`);
  assert.ok(real.treble > 0.2 && real.treble < real.bass, `Expected treble below bass, got ${real.treble}`);
});

test('applyAttackReleaseEnvelope: snaps faster on rising transients than falling decay', () => {
  const rising = applyAttackReleaseEnvelope(0.2, 0.9, 0.65, 0.15);
  const falling = applyAttackReleaseEnvelope(0.9, 0.2, 0.65, 0.15);
  assert.ok(rising - 0.2 > 0.4); // Fast jump up
  assert.ok(0.9 - falling < 0.15); // Smooth release down
});

test('updateRollingAgcPeak & computeLogSpectrumTargets: normalizes quiet tracks across 32 log bars', () => {
  const quietFft = new Uint8Array(256);
  quietFft[1] = 100; // Bass peak ~0.39
  quietFft[20] = 80; // Mid ~0.31
  quietFft.fill(50, 55, 110); // High-frequency band

  const agc = updateRollingAgcPeak(quietFft, 0.35);
  assert.equal(agc.hasSignal, true);
  assert.ok(agc.nextPeak >= 0.39 && agc.nextPeak <= 0.41);

  const out = new Float32Array(32);
  computeLogSpectrumTargets(quietFft, out, agc.nextPeak);
  assert.ok(out[0] > 0.5 || out[1] > 0.5);
  assert.ok(out[25] > 0.25);
});

test('updateBeatTransient: spikes on kick drum transient and decays smoothly', () => {
  const quiet = updateBeatTransient(0.2, 0.2, 0);
  assert.equal(quiet.nextBeatPulse, 0);

  const kick = updateBeatTransient(0.85, 0.22, 0);
  assert.ok(kick.nextBeatPulse > 0.7, `Expected beat pulse spike on kick, got ${kick.nextBeatPulse}`);

  const afterKick = updateBeatTransient(0.3, kick.nextAvgBass, kick.nextBeatPulse);
  assert.ok(afterKick.nextBeatPulse < kick.nextBeatPulse && afterKick.nextBeatPulse > 0.4);
});

test('normalizeTimeDomainBuffer: converts 0..255 bytes into centered -1..+1 waveform & RMS', () => {
  const raw = new Uint8Array([128, 255, 128, 0]);
  const out = new Float32Array(4);
  const rms = normalizeTimeDomainBuffer(raw, out);
  assert.equal(out[0], 0);
  assert.ok(out[1] > 0.98);
  assert.equal(out[2], 0);
  assert.equal(out[3], -1);
  assert.ok(rms > 0.65 && rms < 0.75);
});

test('calculateThreePointedStarVertices: returns alternating 6-point Mercedes emblem vertices', () => {
  const verts = calculateThreePointedStarVertices(100, 100, 50, 0.2, 0);
  assert.equal(verts.length, 6);
  assert.equal(verts[0].type, 'tip');
  assert.equal(verts[1].type, 'valley');
  assert.ok(Math.abs(verts[0].x - 100) < 0.001);
  assert.ok(Math.abs(verts[0].y - 50) < 0.001);
});

test('pushRollingTelemetrySample: shifts buffer in-place and clamps input', () => {
  const buf = new Float32Array([0.1, 0.2, 0.3, 0.4]);
  pushRollingTelemetrySample(buf, 0.85);
  assert.ok(Math.abs(buf[0] - 0.2) < 0.0001);
  assert.ok(Math.abs(buf[2] - 0.4) < 0.0001);
  assert.ok(Math.abs(buf[3] - 0.85) < 0.0001);

  pushRollingTelemetrySample(buf, 1.8);
  assert.equal(buf[3], 1);
});

test('getVisualizerCanvasResolution: respects MediaTek ecoMode DPR clamp', () => {
  const eco = getVisualizerCanvasResolution(1200, 600, 2.5, true);
  assert.deepEqual(eco, { pixelWidth: 1200, pixelHeight: 600, scale: 1 });

  const hq = getVisualizerCanvasResolution(1200, 600, 2.5, false);
  assert.deepEqual(hq, { pixelWidth: 2400, pixelHeight: 1200, scale: 2 });
});
