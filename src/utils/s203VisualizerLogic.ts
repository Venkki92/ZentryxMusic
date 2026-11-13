// Mercedes-Benz S203 / AMG / MBUX Audio Visualizer Logic & Pure Math Helpers

export type S203ColorwayId = 'amber' | 'avantgarde' | 'amg_gt' | 'ambient';
export type S203DisplayMode =
  | 'gauge'
  | 'star_pulse'
  | 'amg_telemetry'
  | 'burmester_wave'
  | 'comand'
  | 'mfd_amber';

export interface S203Colorway {
  id: S203ColorwayId;
  name: string;
  description: string;
  primaryHex: string;
  secondaryHex: string;
  glowHex: string;
  needleHex: string;
  bezelTrim: string;
  dialBg: string;
  mfdBg: string;
}

export interface VisualizerModeMeta {
  id: S203DisplayMode;
  shortLabel: string;
  name: string;
  subtitle: string;
}

export const MERCEDES_VISUALIZER_MODES: VisualizerModeMeta[] = [
  {
    id: 'gauge',
    shortLabel: 'Cluster',
    name: 'W203 VDO Cluster',
    subtitle: 'Classic instrument dial with spring-damped needle & twin fullscreen telemetry',
  },
  {
    id: 'star_pulse',
    shortLabel: 'Starlight',
    name: 'Emblem Starlight',
    subtitle: '3-pointed chrome emblem inside rotating 48-ray harmonic spectrum rings',
  },
  {
    id: 'amg_telemetry',
    shortLabel: 'AMG Track',
    name: 'AMG Track Pace',
    subtitle: 'Motorsport G-force dynamics, scrolling torque trace & redline telemetry',
  },
  {
    id: 'burmester_wave',
    shortLabel: 'Burmester',
    name: 'Burmester 3D Wave',
    subtitle: 'Layered acoustic silk ribbons over perforated surround-sound grille',
  },
  {
    id: 'comand',
    shortLabel: 'COMAND',
    name: 'COMAND Logic 7',
    subtitle: 'Harman/Kardon 24-bit DSP multi-band analyzer with gravity peak-hold',
  },
  {
    id: 'mfd_amber',
    shortLabel: 'MFD',
    name: 'VDO Dot-Matrix',
    subtitle: 'Authentic amber phosphor cluster matrix & vehicle status lamp bar',
  },
];

export const S203_COLORWAYS: Record<S203ColorwayId, S203Colorway> = {
  amber: {
    id: 'amber',
    name: 'Classic Amber (Pre-Facelift)',
    description: 'Iconic warm amber phosphor dot-matrix illumination of the original W203/S203 cluster',
    primaryHex: '#FF9500',
    secondaryHex: '#FFB84D',
    glowHex: 'rgba(255, 149, 0, 0.45)',
    needleHex: '#FF5E00',
    bezelTrim: '#71717A',
    dialBg: '#090704',
    mfdBg: '#180E02',
  },
  avantgarde: {
    id: 'avantgarde',
    name: 'Avantgarde Silver (Facelift)',
    description: 'Crisp white-backlit dials with matte brushed aluminum rings from 2004-2007 Sport models',
    primaryHex: '#F8FAFC',
    secondaryHex: '#93C5FD',
    glowHex: 'rgba(147, 197, 253, 0.35)',
    needleHex: '#EF4444',
    bezelTrim: '#CBD5E1',
    dialBg: '#080B11',
    mfdBg: '#0B1120',
  },
  amg_gt: {
    id: 'amg_gt',
    name: 'AMG Track Red & Titanium',
    description: 'Affalterbach crimson needles paired with brushed titanium typography and cold-white ticks',
    primaryHex: '#EF4444',
    secondaryHex: '#FCA5A5',
    glowHex: 'rgba(239, 68, 68, 0.4)',
    needleHex: '#F97316',
    bezelTrim: '#94A3B8',
    dialBg: '#090608',
    mfdBg: '#1A080C',
  },
  ambient: {
    id: 'ambient',
    name: 'Cockpit MBUX Sync',
    description: 'Harmonizes dial needles and spectrum bars with your active vehicle ambient lighting',
    primaryHex: '#A3E635',
    secondaryHex: '#BEF264',
    glowHex: 'rgba(163, 230, 53, 0.35)',
    needleHex: '#F97316',
    bezelTrim: '#64748B',
    dialBg: '#060A10',
    mfdBg: '#0B1220',
  },
};

export interface DialTick {
  angleDeg: number;
  isMajor: boolean;
  isRedline: boolean;
  label?: string;
}

export interface BandEnergies {
  bass: number;
  mid: number;
  treble: number;
  overall: number;
}

export interface StarVertex {
  x: number;
  y: number;
  type: 'tip' | 'valley';
}

export function generateS203DialTicks(
  totalTicks = 25,
  startAngleDeg = 135,
  endAngleDeg = 405,
  maxScaleValue = 240
): DialTick[] {
  const ticks: DialTick[] = [];
  const span = endAngleDeg - startAngleDeg;
  const step = span / Math.max(1, totalTicks - 1);

  for (let i = 0; i < totalTicks; i++) {
    const angleDeg = startAngleDeg + i * step;
    const isMajor = i % 4 === 0;
    const isRedline = i >= Math.floor(totalTicks * 0.8);

    let label: string | undefined;
    if (isMajor) {
      const val = Math.round((i / Math.max(1, totalTicks - 1)) * maxScaleValue);
      label = val.toString();
    }

    ticks.push({
      angleDeg,
      isMajor,
      isRedline,
      label,
    });
  }

  return ticks;
}

export function calculateNeedleAngle(
  energy: number,
  startAngleDeg = 135,
  endAngleDeg = 405
): number {
  const clamped = Math.max(0, Math.min(1, energy));
  const ballistic = Math.pow(clamped, 0.72);
  const span = endAngleDeg - startAngleDeg;
  return startAngleDeg + ballistic * span;
}

export function calculatePeakHold(
  currentPeak: number,
  newValue: number,
  decayRate = 0.03
): number {
  if (newValue >= currentPeak) {
    return Math.min(1, newValue);
  }
  return Math.max(0, currentPeak - decayRate);
}

export function formatS203ClusterText(
  title?: string,
  artist?: string,
  maxLength = 22
): string {
  if (!title) return 'AUDIO OFF';
  let formatted = title.trim();
  if (artist && artist !== 'Unknown Artist' && artist !== 'Unknown') {
    formatted = `${title} - ${artist}`;
  }
  formatted = formatted.toUpperCase();
  if (formatted.length <= maxLength) {
    return formatted;
  }
  return formatted.slice(0, maxLength - 1) + '…';
}

/**
 * Asymmetric envelope smoothing: fast attack on transients/beats, smooth release on decay.
 */
export function applyAttackReleaseEnvelope(
  current: number,
  target: number,
  attackRate = 0.65,
  releaseRate = 0.16
): number {
  const rate = target >= current ? attackRate : releaseRate;
  return current + (target - current) * rate;
}

/**
 * Tracks a rolling loudness peak (AGC) and returns normalized scale factor
 * so quiet tracks & medium headunit volumes still drive full meter excursions.
 */
export function updateRollingAgcPeak(
  fftData: Uint8Array,
  currentPeak: number,
  decayPerFrame = 0.992,
  minFloor = 0.25
): { nextPeak: number; frameMaxNorm: number; hasSignal: boolean } {
  let maxByte = 0;
  for (let i = 0; i < fftData.length; i++) {
    if (fftData[i] > maxByte) maxByte = fftData[i];
  }
  const frameMaxNorm = maxByte / 255;
  const decayed = currentPeak * decayPerFrame;
  const nextPeak = Math.max(minFloor, Math.max(frameMaxNorm, decayed));
  return {
    nextPeak,
    frameMaxNorm,
    hasSignal: maxByte > 2,
  };
}

/**
 * Maps linear FFT bins logarithmically across perceptual frequency bands (bass -> presence -> air)
 * into target display bars using AGC peak normalization for crisp, independent bar motion.
 */
export function computeLogSpectrumTargets(
  fftData: Uint8Array,
  outTargets: Float32Array,
  agcPeak = 1.0
): void {
  const numBars = outTargets.length;
  const usableBins = Math.max(8, Math.floor(fftData.length * 0.72));
  const safePeak = Math.max(0.25, agcPeak);
  const minLog = Math.log(1);
  const maxLog = Math.log(usableBins);

  for (let i = 0; i < numBars; i++) {
    const t0 = i / numBars;
    const t1 = (i + 1) / numBars;
    const startBin = Math.min(usableBins - 1, Math.floor(Math.exp(minLog + (maxLog - minLog) * t0)) - 1);
    const endBin = Math.max(
      startBin + 1,
      Math.min(usableBins, Math.ceil(Math.exp(minLog + (maxLog - minLog) * t1)))
    );

    let peak = 0;
    let sum = 0;
    const count = endBin - startBin;
    for (let b = startBin; b < endBin; b++) {
      const v = fftData[b];
      if (v > peak) peak = v;
      sum += v;
    }

    // Blend average + bin peak so narrow high-frequency spikes still show cleanly
    const rawNorm = (peak * 0.6 + (sum / count) * 0.4) / 255;
    // Slight high-frequency compensation (+0..+35%) since natural music rolls off -3dB/octave
    const tiltBoost = 1 + t0 * 0.35;
    const scaled = Math.min(1, (rawNorm / safePeak) * tiltBoost);
    outTargets[i] = Math.max(0.03, Math.pow(scaled, 0.82));
  }
}

/**
 * Computes instantaneous beat transient pulse (0..1) when bass surges above recent moving average.
 */
export function updateBeatTransient(
  instantBass: number,
  avgBass: number,
  currentPulse: number
): { nextAvgBass: number; nextBeatPulse: number } {
  const nextAvgBass = avgBass * 0.9 + instantBass * 0.1;
  const delta = instantBass - nextAvgBass * 1.08;
  const hit = delta > 0.035 ? Math.min(1, delta * 4.5) : 0;
  const nextBeatPulse = Math.max(hit, currentPulse * 0.82);
  return { nextAvgBass, nextBeatPulse };
}

/**
 * Normalizes raw 0..255 Web Audio time-domain bytes into -1..+1 waveform samples.
 */
export function normalizeTimeDomainBuffer(rawBytes: Uint8Array, outNormalized: Float32Array): number {
  const len = Math.min(rawBytes.length, outNormalized.length);
  let rmsSum = 0;
  for (let i = 0; i < len; i++) {
    const sample = (rawBytes[i] - 128) / 128;
    outNormalized[i] = sample;
    rmsSum += sample * sample;
  }
  return len > 0 ? Math.sqrt(rmsSum / len) : 0;
}

/**
 * Extracts normalized Bass, Mid, Treble, and Overall audio energies (0..1)
 * from real FFT bins (with AGC scaling) or rhythmic fallback when no audio stream is active.
 */
export function extractBandEnergies(
  fftData: Uint8Array | null,
  hasRealAudio: boolean,
  isPlaying: boolean,
  phase: number,
  agcPeak = 1.0
): BandEnergies {
  if (!isPlaying) {
    const idlePulse = 0.04 + (Math.sin(phase * 0.5) + 1) * 0.02;
    return {
      bass: idlePulse,
      mid: idlePulse * 0.85,
      treble: idlePulse * 0.7,
      overall: idlePulse,
    };
  }

  if (hasRealAudio && fftData && fftData.length >= 16) {
    const len = fftData.length;
    const safePeak = Math.max(0.25, agcPeak);
    // Tuned for fftSize=512 (256 bins @ ~86Hz/bin) or fftSize=128 (64 bins)
    const bassEnd = Math.max(2, Math.floor(len * 0.05)); // ~0 - 430 Hz (kick & sub-bass)
    const midEnd = Math.max(bassEnd + 4, Math.floor(len * 0.24)); // ~430 - 3,800 Hz (vocals/snare)
    const trebleEnd = Math.max(midEnd + 6, Math.floor(len * 0.62)); // ~3.8k - 13k Hz (hi-hats/air)

    let bassSum = 0;
    let bassMax = 0;
    for (let i = 0; i < bassEnd; i++) {
      bassSum += fftData[i];
      if (fftData[i] > bassMax) bassMax = fftData[i];
    }

    let midSum = 0;
    for (let i = bassEnd; i < midEnd; i++) midSum += fftData[i];

    let trebleSum = 0;
    for (let i = midEnd; i < trebleEnd; i++) trebleSum += fftData[i];

    const rawBass = ((bassSum / bassEnd) * 0.45 + bassMax * 0.55) / 255;
    const rawMid = midSum / ((midEnd - bassEnd) * 255);
    const rawTreble = trebleSum / ((trebleEnd - midEnd) * 255);

    const bass = Math.min(1, Math.max(0.04, Math.pow(rawBass / safePeak, 0.82)));
    const mid = Math.min(1, Math.max(0.04, Math.pow((rawMid * 1.25) / safePeak, 0.85)));
    const treble = Math.min(1, Math.max(0.04, Math.pow((rawTreble * 1.55) / safePeak, 0.88)));
    const overall = Math.min(1, bass * 0.48 + mid * 0.34 + treble * 0.18);

    return { bass, mid, treble, overall };
  }

  // Fallback musical harmonic pulse when playing without an accessible audio buffer
  const kickWave = Math.pow((Math.sin(phase * 1.45) + 1) * 0.5, 1.6);
  const subHarmonic = (Math.cos(phase * 0.72) + 1) * 0.5;
  const bass = Math.min(0.98, Math.max(0.14, kickWave * 0.65 + subHarmonic * 0.25 + 0.08));

  const midWave1 = (Math.sin(phase * 2.1 + 0.9) + 1) * 0.5;
  const midWave2 = (Math.cos(phase * 1.15 - 0.4) + 1) * 0.5;
  const mid = Math.min(0.95, Math.max(0.12, midWave1 * 0.5 + midWave2 * 0.35 + 0.08));

  const hiWave = Math.pow((Math.sin(phase * 3.4 + 1.7) + 1) * 0.5, 1.2);
  const shimmer = (Math.cos(phase * 4.8) + 1) * 0.25;
  const treble = Math.min(0.92, Math.max(0.1, hiWave * 0.55 + shimmer + 0.08));

  const overall = Math.min(1, bass * 0.48 + mid * 0.34 + treble * 0.18);
  return { bass, mid, treble, overall };
}

export function calculateThreePointedStarVertices(
  cx: number,
  cy: number,
  outerRadius: number,
  innerRadiusRatio = 0.22,
  rotationRad = 0
): StarVertex[] {
  const innerRadius = outerRadius * innerRadiusRatio;
  const vertices: StarVertex[] = [];
  const baseAngle = -Math.PI / 2 + rotationRad;

  for (let i = 0; i < 6; i++) {
    const angle = baseAngle + (i * Math.PI) / 3;
    const isTip = i % 2 === 0;
    const r = isTip ? outerRadius : innerRadius;
    vertices.push({
      x: cx + Math.cos(angle) * r,
      y: cy + Math.sin(angle) * r,
      type: isTip ? 'tip' : 'valley',
    });
  }

  return vertices;
}

export function pushRollingTelemetrySample(buffer: Float32Array, nextValue: number): void {
  if (buffer.length === 0) return;
  for (let i = 0; i < buffer.length - 1; i++) {
    buffer[i] = buffer[i + 1];
  }
  buffer[buffer.length - 1] = Math.max(0, Math.min(1, nextValue));
}

export function getVisualizerCanvasResolution(
  cssWidth: number,
  cssHeight: number,
  devicePixelRatio: number,
  ecoMode: boolean
): { pixelWidth: number; pixelHeight: number; scale: number } {
  const safeW = Math.max(120, Math.round(cssWidth));
  const safeH = Math.max(80, Math.round(cssHeight));
  const rawDpr = Number.isFinite(devicePixelRatio) && devicePixelRatio > 0 ? devicePixelRatio : 1;
  const scale = ecoMode ? 1 : Math.min(2, Math.max(1, rawDpr));

  return {
    pixelWidth: Math.round(safeW * scale),
    pixelHeight: Math.round(safeH * scale),
    scale,
  };
}
