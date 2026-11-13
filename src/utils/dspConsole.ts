export const DSP_16_FREQUENCIES = [
  32, 64, 125, 250, 500, 1000, 1600, 2500, 3500, 5000, 7000, 9000, 11000, 13000, 15000, 16000,
] as const;

export type DspBandGains16 = [
  number, number, number, number,
  number, number, number, number,
  number, number, number, number,
  number, number, number, number
];

export type HpfCutoffHz = 0 | 60 | 80 | 100 | 120;
export type ListeningStagePreset = 'all' | 'driver' | 'driver_rhd' | 'center' | 'custom';
export type CompressorProfileId = 'off' | 'balanced' | 'night_highway' | 'club_punch';

export interface DspSettings {
  dspEnabled: boolean;
  eqViewMode: '5band' | '16band';
  selectedPresetId: string;
  bands16: DspBandGains16;
  preampDb: number; // -12 to +6 dB
  bassBoostDb: number; // 0 to +12 dB
  bassBoostFreqHz: number; // 40 to 100 Hz
  hpfCutoffHz: HpfCutoffHz; // 0 (off), 60, 80, 100, 120
  loudnessContour: boolean; // Fletcher-Munson dynamic low-volume boost
  listeningStage: ListeningStagePreset;
  leftDelayMs: number; // 0.0 to 5.0 ms
  rightDelayMs: number; // 0.0 to 5.0 ms
  balance: number; // -100 (full L) to +100 (full R)
  stereoWidthPercent: number; // 0 (mono) to 150 (wide)
  compressorProfile: CompressorProfileId;
}

export interface DspPreset16 {
  id: string;
  name: string;
  description: string;
  bands16: DspBandGains16;
  bassBoostDb?: number;
  bassBoostFreqHz?: number;
  preampDb?: number;
  hpfCutoffHz?: HpfCutoffHz;
  loudnessContour?: boolean;
  compressorProfile?: CompressorProfileId;
  isCustom?: boolean;
}

export const ZERO_BANDS_16: DspBandGains16 = [
  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
];

export const FACTORY_DSP_PRESETS: DspPreset16[] = [
  {
    id: 'flat',
    name: 'Flat / Studio Direct',
    description: 'Neutral reference response with zero coloration',
    bands16: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    bassBoostDb: 0,
    preampDb: 0,
  },
  {
    id: 'w203_bose',
    name: 'W203 Sound System',
    description: 'Mercedes cabin signature — tight door kick, rich mids, smooth silk dome highs',
    bands16: [3, 4.5, 3.5, 1.5, 0, 0.5, 1.5, 2, 1.5, 1, 2, 3, 3.5, 3, 2, 1.5],
    bassBoostDb: 3,
    preampDb: -2,
  },
  {
    id: 'cabin_bass',
    name: 'Cabin Bass Slam',
    description: 'Deep sub-bass punch engineered for trunk subwoofers & door woofers',
    bands16: [7.5, 8, 6, 3.5, 1, -0.5, -1, 0, 1, 2, 2.5, 3, 3.5, 3, 2.5, 2],
    bassBoostDb: 6,
    preampDb: -3,
  },
  {
    id: 'highway_cruiser',
    name: 'Highway Cruiser',
    description: 'Lifts bass and crisp presence over tire roar at 100+ km/h',
    bands16: [5, 5.5, 4, 2, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4, 3.5, 3, 2.5, 2],
    bassBoostDb: 4,
    preampDb: -2,
  },
  {
    id: 'podcast_clarity',
    name: 'Vocal & Podcast',
    description: 'Suppresses road boom and sharpens spoken vocal intelligibility',
    bands16: [-6, -5, -3.5, -1.5, 1, 3, 4.5, 5, 4, 2.5, 1.5, 1, 0, -1, -1.5, -2],
    bassBoostDb: 0,
    preampDb: 0,
  },
  {
    id: 'rock_metal',
    name: 'Rock & Guitars',
    description: 'Snappy snare punch, forward guitar bite, airy cymbal shimmer',
    bands16: [4, 4.5, 3, 1.5, -1, -1.5, 0.5, 2.5, 3.5, 4, 4.5, 5, 4.5, 4, 3, 2.5],
    bassBoostDb: 3,
    preampDb: -2,
  },
  {
    id: 'club_electronic',
    name: 'Club & Electronic',
    description: 'Maximum sub extension and sparkling synth air',
    bands16: [8, 7.5, 5, 2, 0, -1, 0, 1.5, 2.5, 3.5, 4.5, 5.5, 6, 6, 5, 4.5],
    bassBoostDb: 6,
    preampDb: -3,
  },
  {
    id: 'vocal_jazz',
    name: 'Acoustic & Jazz',
    description: 'Warm upright bass body and natural brass/vocal staging',
    bands16: [2, 3, 2.5, 1.5, 1, 1.5, 2, 2, 1.5, 2, 2.5, 3, 3, 2.5, 2, 1.5],
    bassBoostDb: 1,
    preampDb: -1,
  },
  {
    id: 'pioneer_ts_e170ci',
    name: 'Pioneer TS-E170Ci Kit',
    description: 'Anti-tin cure: 60Hz HPF + 80Hz door-bass boost + -4.5dB tweeter glare cut',
    bands16: [3.5, 6.5, 6, 3, 1, -0.5, -1.5, -3.5, -4.5, -4, -3.5, -2.5, -1.5, -0.5, 0.5, 1],
    bassBoostDb: 5.5,
    bassBoostFreqHz: 80,
    preampDb: -6,
    hpfCutoffHz: 60,
    loudnessContour: true,
    compressorProfile: 'balanced',
  },
];

export const STAGE_PRESETS: Record<
  Exclude<ListeningStagePreset, 'custom'>,
  { label: string; desc: string; leftDelayMs: number; rightDelayMs: number }
> = {
  all: {
    label: 'All Cabin',
    desc: '0.0 ms symmetric',
    leftDelayMs: 0.0,
    rightDelayMs: 0.0,
  },
  driver: {
    label: 'Driver (LHD)',
    desc: '+1.4 ms L delay',
    leftDelayMs: 1.4,
    rightDelayMs: 0.0,
  },
  driver_rhd: {
    label: 'Driver (RHD)',
    desc: '+1.4 ms R delay',
    leftDelayMs: 0.0,
    rightDelayMs: 1.4,
  },
  center: {
    label: 'Front Center',
    desc: '+0.4 ms front align',
    leftDelayMs: 0.4,
    rightDelayMs: 0.4,
  },
};

export const COMPRESSOR_PROFILES: Record<
  CompressorProfileId,
  {
    label: string;
    desc: string;
    threshold: number;
    knee: number;
    ratio: number;
    attack: number;
    release: number;
  }
> = {
  off: {
    label: 'Bypass',
    desc: 'Full dynamic range untouched',
    threshold: 0,
    knee: 0,
    ratio: 1,
    attack: 0.003,
    release: 0.25,
  },
  balanced: {
    label: 'Balanced',
    desc: 'Gentle cabin loudness leveling',
    threshold: -18,
    knee: 18,
    ratio: 3,
    attack: 0.012,
    release: 0.22,
  },
  night_highway: {
    label: 'Night / Highway',
    desc: 'Lifts quiet details over road noise',
    threshold: -26,
    knee: 24,
    ratio: 5.5,
    attack: 0.008,
    release: 0.28,
  },
  club_punch: {
    label: 'Club Punch',
    desc: 'Fast recovery with peak protection',
    threshold: -14,
    knee: 10,
    ratio: 4,
    attack: 0.025,
    release: 0.14,
  },
};

export const DEFAULT_DSP_SETTINGS: DspSettings = {
  dspEnabled: true,
  eqViewMode: '16band',
  selectedPresetId: 'w203_bose',
  bands16: [...FACTORY_DSP_PRESETS[1].bands16] as DspBandGains16,
  preampDb: -2,
  bassBoostDb: 3,
  bassBoostFreqHz: 65,
  hpfCutoffHz: 0,
  loudnessContour: true,
  listeningStage: 'all',
  leftDelayMs: 0,
  rightDelayMs: 0,
  balance: 0,
  stereoWidthPercent: 100,
  compressorProfile: 'balanced',
};

export function clampNumber(val: number, min: number, max: number, step = 0.5): number {
  if (!Number.isFinite(val)) return min;
  const clamped = Math.max(min, Math.min(max, val));
  return Math.round(clamped / step) * step;
}

export function sanitizeBands16(input: unknown): DspBandGains16 {
  if (!Array.isArray(input)) return [...ZERO_BANDS_16] as DspBandGains16;
  const out = new Array(16).fill(0) as DspBandGains16;
  for (let i = 0; i < 16; i++) {
    out[i] = clampNumber(Number(input[i] ?? 0), -12, 12, 0.5);
  }
  return out;
}

/**
 * Maps 5 coarse anchor frequencies [60, 250, 1k, 4k, 12k] into smooth 16-band curve.
 */
export function interpolate5To16Bands(bands5: readonly number[]): DspBandGains16 {
  const anchors = [
    { freq: 60, gain: Number(bands5[0] ?? 0) },
    { freq: 250, gain: Number(bands5[1] ?? 0) },
    { freq: 1000, gain: Number(bands5[2] ?? 0) },
    { freq: 4000, gain: Number(bands5[3] ?? 0) },
    { freq: 12000, gain: Number(bands5[4] ?? 0) },
  ];

  const out = new Array(16).fill(0) as DspBandGains16;
  for (let i = 0; i < DSP_16_FREQUENCIES.length; i++) {
    const f = DSP_16_FREQUENCIES[i];
    if (f <= anchors[0].freq) {
      out[i] = clampNumber(anchors[0].gain, -12, 12, 0.5);
      continue;
    }
    if (f >= anchors[anchors.length - 1].freq) {
      out[i] = clampNumber(anchors[anchors.length - 1].gain, -12, 12, 0.5);
      continue;
    }
    for (let j = 0; j < anchors.length - 1; j++) {
      const a = anchors[j];
      const b = anchors[j + 1];
      if (f >= a.freq && f <= b.freq) {
        const logRatio = (Math.log10(f) - Math.log10(a.freq)) / (Math.log10(b.freq) - Math.log10(a.freq));
        out[i] = clampNumber(a.gain + (b.gain - a.gain) * logRatio, -12, 12, 0.5);
        break;
      }
    }
  }
  return out;
}

/**
 * Extracts 5 representative bands [64Hz, 250Hz, 1kHz, 3.5kHz/5kHz, 11kHz] from 16 bands.
 */
export function downsample16To5Bands(bands16: DspBandGains16): [number, number, number, number, number] {
  return [
    clampNumber(bands16[1], -12, 12, 0.5),
    clampNumber(bands16[3], -12, 12, 0.5),
    clampNumber(bands16[5], -12, 12, 0.5),
    clampNumber((bands16[8] + bands16[9]) / 2, -12, 12, 0.5),
    clampNumber(bands16[12], -12, 12, 0.5),
  ];
}

export function dbToLinearGain(db: number): number {
  if (!Number.isFinite(db)) return 1;
  return Math.pow(10, db / 20);
}

/**
 * Fletcher-Munson Loudness Contour: gently boosts sub-bass & highs at low listening volumes,
 * tapering back to 0 dB above ~80% master volume.
 */
export function calculateLoudnessContourGains(
  enabled: boolean,
  volumePercent: number
): { lowShelfDb: number; highShelfDb: number } {
  if (!enabled) return { lowShelfDb: 0, highShelfDb: 0 };
  const clampedVol = Math.max(0, Math.min(100, volumePercent));
  if (clampedVol >= 80) return { lowShelfDb: 0, highShelfDb: 0 };

  const factor = (80 - clampedVol) / 80; // 1.0 at 0%, 0.0 at 80%
  const shaped = Math.pow(factor, 1.15);
  return {
    lowShelfDb: Math.round(6.5 * shaped * 10) / 10,
    highShelfDb: Math.round(3.5 * shaped * 10) / 10,
  };
}

/**
 * Computes L/R balance gains for -100..+100 slider (0 = centered 1.0 / 1.0).
 */
export function calculateBalanceGains(balance: number): { leftGain: number; rightGain: number } {
  const b = Math.max(-100, Math.min(100, balance));
  return {
    leftGain: b > 0 ? Math.round((1 - b / 100) * 1000) / 1000 : 1,
    rightGain: b < 0 ? Math.round((1 + b / 100) * 1000) / 1000 : 1,
  };
}

/**
 * Computes transparent Mid/Side matrix coefficients for 3D stereo width (0%..150%).
 * 100% => direct = 1, cross = 0 (bit-exact passthrough).
 * 0% => direct = 0.5, cross = 0.5 (clean mono).
 * 150% => direct = 1.25, cross = -0.25 (wide soundstage).
 */
export function calculateStereoWidthMatrix(widthPercent: number): {
  directGain: number;
  crossGain: number;
} {
  const w = Math.max(0, Math.min(150, widthPercent)) / 100;
  return {
    directGain: Math.round(((1 + w) / 2) * 1000) / 1000,
    crossGain: Math.round(((1 - w) / 2) * 1000) / 1000,
  };
}

/**
 * Computes high-resolution frequency curve dB points (for interactive SVG curve overlay)
 * incorporating HPF rolloff + Bass Boost + 16 EQ bands + Loudness Contour.
 */
export function buildDspResponseCurvePoints(
  settings: Pick<
    DspSettings,
    'dspEnabled' | 'bands16' | 'bassBoostDb' | 'bassBoostFreqHz' | 'hpfCutoffHz' | 'loudnessContour'
  >,
  volumePercent: number,
  width = 640,
  height = 140
): Array<{ x: number; y: number; freq: number; db: number }> {
  const minLog = Math.log10(25);
  const maxLog = Math.log10(18000);
  const loudness = calculateLoudnessContourGains(
    settings.dspEnabled && settings.loudnessContour,
    volumePercent
  );

  const pts: Array<{ x: number; y: number; freq: number; db: number }> = [];
  const sampleCount = 48;

  for (let i = 0; i < sampleCount; i++) {
    const t = i / (sampleCount - 1);
    const freq = Math.pow(10, minLog + t * (maxLog - minLog));
    let db = 0;

    if (settings.dspEnabled) {
      // Smooth bell summation across the 16 bands in log-frequency space
      for (let b = 0; b < DSP_16_FREQUENCIES.length; b++) {
        const bandFreq = DSP_16_FREQUENCIES[b];
        const distOct = (Math.log2(freq) - Math.log2(bandFreq)) / 0.55;
        db += (settings.bands16[b] || 0) * Math.exp(-0.5 * distOct * distOct);
      }

      // Low-shelf bass boost rolloff
      if (settings.bassBoostDb > 0) {
        const shelfRatio = 1 / (1 + Math.pow(freq / Math.max(35, settings.bassBoostFreqHz), 2.2));
        db += settings.bassBoostDb * shelfRatio;
      }

      // Dynamic Fletcher-Munson loudness shelves
      if (loudness.lowShelfDb > 0) {
        db += loudness.lowShelfDb / (1 + Math.pow(freq / 95, 1.8));
      }
      if (loudness.highShelfDb > 0) {
        db += loudness.highShelfDb / (1 + Math.pow(9000 / freq, 1.8));
      }

      // 2nd-order High-Pass Filter (door speaker protection) rolloff
      if (settings.hpfCutoffHz > 0 && freq < settings.hpfCutoffHz * 1.4) {
        const ratio = freq / settings.hpfCutoffHz;
        const mag = (ratio * ratio) / Math.sqrt(Math.pow(1 - ratio * ratio, 2) + 2 * ratio * ratio);
        const hpfDb = 20 * Math.log10(Math.max(0.02, mag));
        db += Math.max(-18, hpfDb);
      }
    }

    const clampedDb = Math.max(-15, Math.min(15, db));
    const x = Math.round(t * width * 10) / 10;
    const y = Math.round(((15 - clampedDb) / 30) * height * 10) / 10;
    pts.push({ x, y, freq: Math.round(freq), db: Math.round(clampedDb * 10) / 10 });
  }

  return pts;
}

export function sanitizeDspSettings(raw: unknown): DspSettings {
  if (!raw || typeof raw !== 'object') return { ...DEFAULT_DSP_SETTINGS };
  const obj = raw as Record<string, unknown>;
  const validHpf: HpfCutoffHz[] = [0, 60, 80, 100, 120];
  const validStages: ListeningStagePreset[] = ['all', 'driver', 'driver_rhd', 'center', 'custom'];
  const validComp: CompressorProfileId[] = ['off', 'balanced', 'night_highway', 'club_punch'];

  return {
    dspEnabled: typeof obj.dspEnabled === 'boolean' ? obj.dspEnabled : DEFAULT_DSP_SETTINGS.dspEnabled,
    eqViewMode: obj.eqViewMode === '5band' || obj.eqViewMode === '16band' ? obj.eqViewMode : '16band',
    selectedPresetId:
      typeof obj.selectedPresetId === 'string' && obj.selectedPresetId.trim()
        ? obj.selectedPresetId.trim()
        : DEFAULT_DSP_SETTINGS.selectedPresetId,
    bands16: sanitizeBands16(obj.bands16),
    preampDb: clampNumber(Number(obj.preampDb ?? DEFAULT_DSP_SETTINGS.preampDb), -12, 6, 0.5),
    bassBoostDb: clampNumber(Number(obj.bassBoostDb ?? DEFAULT_DSP_SETTINGS.bassBoostDb), 0, 12, 0.5),
    bassBoostFreqHz: clampNumber(Number(obj.bassBoostFreqHz ?? DEFAULT_DSP_SETTINGS.bassBoostFreqHz), 40, 100, 5),
    hpfCutoffHz: validHpf.includes(obj.hpfCutoffHz as HpfCutoffHz)
      ? (obj.hpfCutoffHz as HpfCutoffHz)
      : DEFAULT_DSP_SETTINGS.hpfCutoffHz,
    loudnessContour:
      typeof obj.loudnessContour === 'boolean' ? obj.loudnessContour : DEFAULT_DSP_SETTINGS.loudnessContour,
    listeningStage: validStages.includes(obj.listeningStage as ListeningStagePreset)
      ? (obj.listeningStage as ListeningStagePreset)
      : DEFAULT_DSP_SETTINGS.listeningStage,
    leftDelayMs: clampNumber(Number(obj.leftDelayMs ?? 0), 0, 5, 0.1),
    rightDelayMs: clampNumber(Number(obj.rightDelayMs ?? 0), 0, 5, 0.1),
    balance: clampNumber(Number(obj.balance ?? 0), -100, 100, 5),
    stereoWidthPercent: clampNumber(Number(obj.stereoWidthPercent ?? 100), 0, 150, 5),
    compressorProfile: validComp.includes(obj.compressorProfile as CompressorProfileId)
      ? (obj.compressorProfile as CompressorProfileId)
      : DEFAULT_DSP_SETTINGS.compressorProfile,
  };
}

export function sanitizeCustomPresets(raw: unknown): DspPreset16[] {
  if (!Array.isArray(raw)) return [];
  const out: DspPreset16[] = [];
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue;
    const obj = item as Record<string, unknown>;
    if (typeof obj.id !== 'string' || typeof obj.name !== 'string' || !obj.name.trim()) continue;
    out.push({
      id: obj.id,
      name: obj.name.trim().slice(0, 32),
      description: typeof obj.description === 'string' ? obj.description : 'Custom Headunit DSP Tune',
      bands16: sanitizeBands16(obj.bands16),
      bassBoostDb: clampNumber(Number(obj.bassBoostDb ?? 0), 0, 12, 0.5),
      preampDb: clampNumber(Number(obj.preampDb ?? 0), -12, 6, 0.5),
      isCustom: true,
    });
  }
  return out;
}
