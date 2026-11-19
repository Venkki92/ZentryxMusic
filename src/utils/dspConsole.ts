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
export type SubLpfHz = 0 | 80 | 100 | 120 | 160;
export type CrossoverSlopeDb = 12 | 24;
export type LimiterCeilingDb = 0 | -0.5 | -1.5 | -3.0;
export type ListeningStagePreset = 'all' | 'driver' | 'driver_rhd' | 'center' | 'custom';
export type CompressorProfileId = 'off' | 'balanced' | 'night_highway' | 'club_punch';
export type AudioOutputRouteId = 'headunit_speakers' | 'bluetooth_a2dp' | 'usb_dac_aux';
export type SurroundModeId =
  | 'off'
  | 'logic7_surround'
  | 'concert_hall'
  | 'studio_focused'
  | 'dolby_pro_logic_ii'
  | 'dolby_atmos_virtual'
  | 'virtual_surround_360';

export const SURROUND_MODE_ORDER: readonly SurroundModeId[] = [
  'off',
  'logic7_surround',
  'concert_hall',
  'studio_focused',
  'dolby_pro_logic_ii',
  'dolby_atmos_virtual',
  'virtual_surround_360',
];

export const SURROUND_MODES: Record<
  SurroundModeId,
  {
    label: string;
    shortLabel: string;
    badge: string;
    family: 'harman' | 'dolby_virtual';
    tagline: string;
    haasLeftMs: number;
    haasRightMs: number;
    sideWetScale: number;
    centerBoostMaxDb: number;
    sideWidthTarget: number;
    sideBandpassHz: number;
    sideBandpassQ: number;
    heightBoostMaxDb: number;
    useAllPassPhase: boolean;
    crosstalkCancelGain: number;
  }
> = {
  off: {
    label: 'Stereo Direct',
    shortLabel: 'Direct',
    badge: '2.0 DIRECT',
    family: 'harman',
    tagline: 'Pure uncolored 2-channel stereo path',
    haasLeftMs: 0,
    haasRightMs: 0,
    sideWetScale: 0,
    centerBoostMaxDb: 0,
    sideWidthTarget: 100,
    sideBandpassHz: 1600,
    sideBandpassQ: 0.55,
    heightBoostMaxDb: 0,
    useAllPassPhase: false,
    crosstalkCancelGain: 0,
  },
  logic7_surround: {
    label: 'Logic 7 Surround',
    shortLabel: 'Logic 7',
    badge: 'LOGIC 7',
    family: 'harman',
    tagline: 'Harman-inspired 5.1 matrix: locked dash vocals + wraparound cabin ambience',
    haasLeftMs: 14,
    haasRightMs: 21,
    sideWetScale: 0.34,
    centerBoostMaxDb: 3.0,
    sideWidthTarget: 125,
    sideBandpassHz: 1600,
    sideBandpassQ: 0.55,
    heightBoostMaxDb: 1.4,
    useAllPassPhase: false,
    crosstalkCancelGain: 0,
  },
  concert_hall: {
    label: 'Concert Hall',
    shortLabel: 'Concert',
    badge: 'HALL 7.1',
    family: 'harman',
    tagline: 'Deep spacious early reflections & wide acoustic venue bloom',
    haasLeftMs: 22,
    haasRightMs: 31,
    sideWetScale: 0.44,
    centerBoostMaxDb: 1.8,
    sideWidthTarget: 138,
    sideBandpassHz: 1500,
    sideBandpassQ: 0.5,
    heightBoostMaxDb: 2.2,
    useAllPassPhase: true,
    crosstalkCancelGain: 0,
  },
  studio_focused: {
    label: 'Studio Focused',
    shortLabel: 'Studio',
    badge: 'STUDIO',
    family: 'harman',
    tagline: 'Tight nearfield center lock with crisp micro-air separation',
    haasLeftMs: 7,
    haasRightMs: 11,
    sideWetScale: 0.18,
    centerBoostMaxDb: 3.6,
    sideWidthTarget: 112,
    sideBandpassHz: 1900,
    sideBandpassQ: 0.7,
    heightBoostMaxDb: 0.9,
    useAllPassPhase: false,
    crosstalkCancelGain: 0,
  },
  dolby_pro_logic_ii: {
    label: 'Dolby Pro Logic II',
    shortLabel: 'Dolby PLII',
    badge: 'DOLBY PLII',
    family: 'dolby_virtual',
    tagline: '5.1 Music Matrix: 90° phase-shifted rear surround (7 kHz band-limited) + dash center',
    haasLeftMs: 16,
    haasRightMs: 20,
    sideWetScale: 0.4,
    centerBoostMaxDb: 3.4,
    sideWidthTarget: 128,
    sideBandpassHz: 1250,
    sideBandpassQ: 0.48,
    heightBoostMaxDb: 1.6,
    useAllPassPhase: true,
    crosstalkCancelGain: 0,
  },
  dolby_atmos_virtual: {
    label: 'Dolby Virtual Atmos',
    shortLabel: 'Atmos 3D',
    badge: 'ATMOS 3D',
    family: 'dolby_virtual',
    tagline: '3D cabin height dome: pinna overhead elevation air + multi-tap 360° spatial field',
    haasLeftMs: 11,
    haasRightMs: 19,
    sideWetScale: 0.42,
    centerBoostMaxDb: 3.2,
    sideWidthTarget: 136,
    sideBandpassHz: 2100,
    sideBandpassQ: 0.45,
    heightBoostMaxDb: 4.2,
    useAllPassPhase: true,
    crosstalkCancelGain: 0.08,
  },
  virtual_surround_360: {
    label: '360° Virtualizer',
    shortLabel: 'Virtual 360',
    badge: 'VIRTUAL 360',
    family: 'dolby_virtual',
    tagline: 'Cross-talk cancelled binaural / wide cabin 360° virtual surround + Android HW Virtualizer',
    haasLeftMs: 18,
    haasRightMs: 27,
    sideWetScale: 0.48,
    centerBoostMaxDb: 2.2,
    sideWidthTarget: 146,
    sideBandpassHz: 1750,
    sideBandpassQ: 0.42,
    heightBoostMaxDb: 3.2,
    useAllPassPhase: true,
    crosstalkCancelGain: 0.16,
  },
};

export const AUDIO_OUTPUT_ROUTES: ReadonlyArray<{ id: AudioOutputRouteId; label: string }> = [
  { id: 'headunit_speakers', label: 'Cabin Speakers' },
  { id: 'bluetooth_a2dp', label: 'Bluetooth Audio' },
  { id: 'usb_dac_aux', label: 'USB DAC / AUX' },
];

export const DEFAULT_DEVICE_PRESET_MAP: Record<AudioOutputRouteId, string> = {
  headunit_speakers: 'w203_bose',
  bluetooth_a2dp: 'highway_cruiser',
  usb_dac_aux: 'flat',
};

export const SUB_LPF_OPTIONS_HZ: readonly SubLpfHz[] = [0, 80, 100, 120, 160];
export const LIMITER_CEILING_OPTIONS_DB: readonly LimiterCeilingDb[] = [0, -0.5, -1.5, -3.0];

export interface DspSettings {
  dspEnabled: boolean;
  eqViewMode: '5band' | '16band';
  selectedPresetId: string;
  bands16: DspBandGains16;
  preampDb: number; // -12 to +6 dB
  bassBoostDb: number; // 0 to +12 dB
  bassBoostFreqHz: number; // 40 to 100 Hz
  hpfCutoffHz: HpfCutoffHz; // 0 (off), 60, 80, 100, 120
  crossoverSlopeDb: CrossoverSlopeDb; // 12 dB/oct Butterworth or 24 dB/oct Linkwitz-Riley
  loudnessContour: boolean; // Fletcher-Munson dynamic low-volume boost
  listeningStage: ListeningStagePreset;
  leftDelayMs: number; // 0.0 to 5.0 ms
  rightDelayMs: number; // 0.0 to 5.0 ms
  balance: number; // -100 (full L) to +100 (full R)
  fader: number; // -100 (Rear) to +100 (Front)
  stereoWidthPercent: number; // 0 (mono) to 150 (wide)
  compressorProfile: CompressorProfileId;
  subPhaseInverted: boolean;
  subLpfHz: SubLpfHz;
  subTrimDb: number; // -6 to +6 dB
  subDelayMs: number; // 0.0 to 15.0 ms (pull trunk bass forward onto dash)
  monoFoldDown: boolean;
  peakLimiterCeilingDb: LimiterCeilingDb;
  autoGenrePresetEnabled: boolean;
  autoSwitchDevicePreset: boolean;
  devicePresetMap: Record<AudioOutputRouteId, string>;
  surroundMode: SurroundModeId;
  surroundIntensity: number; // 0 to 100%
  centerCoherence: number; // 0 to 100% (Mid L+R vocal focus)
  virtualHeight: number; // 0 to 100% (Atmos / 360 overhead elevation air)
  roadNoiseEqEnabled: boolean; // Speed-sensitive cabin spectral tilt
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
  crossoverSlopeDb: 12,
  loudnessContour: true,
  listeningStage: 'all',
  leftDelayMs: 0,
  rightDelayMs: 0,
  balance: 0,
  fader: 0,
  stereoWidthPercent: 100,
  compressorProfile: 'balanced',
  subPhaseInverted: false,
  subLpfHz: 0,
  subTrimDb: 0,
  subDelayMs: 0,
  monoFoldDown: false,
  peakLimiterCeilingDb: -0.5,
  autoGenrePresetEnabled: false,
  autoSwitchDevicePreset: false,
  devicePresetMap: { ...DEFAULT_DEVICE_PRESET_MAP },
  surroundMode: 'off',
  surroundIntensity: 65,
  centerCoherence: 55,
  virtualHeight: 45,
  roadNoiseEqEnabled: false,
};

export function clampNumber(val: number, min: number, max: number, step = 0.5): number {
  if (!Number.isFinite(val)) return min;
  const clamped = Math.max(min, Math.min(max, val));
  const rounded = Math.round(clamped / step) * step;
  return rounded === 0 ? 0 : Math.round(rounded * 1000) / 1000;
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
 * Speed-Sensitive Road-Noise Acoustic Contour ("Cabin EQ Compensator"):
 * Counteracts low-frequency tire/drivetrain masking (< 120 Hz) and wind/cabin vocal masking (~2.8 kHz)
 * smoothly above 50 km/h, reaching full highway contour (+4.0 dB bass, +1.5 dB vocal) by 130 km/h.
 */
export function calculateRoadNoiseContourDb(
  speedKmH: number,
  enabled: boolean
): { subBassDb: number; vocalPresenceDb: number } {
  if (!enabled || !Number.isFinite(speedKmH) || speedKmH <= 50) {
    return { subBassDb: 0, vocalPresenceDb: 0 };
  }
  const normalized = Math.max(0, Math.min(1, (speedKmH - 50) / 80));
  const curve = Math.pow(normalized, 1.1);
  const subBassDb = Math.round(4.0 * curve * 10) / 10;
  const vocalPresenceDb = Math.round(1.5 * curve * 10) / 10;
  return {
    subBassDb: subBassDb === 0 ? 0 : subBassDb,
    vocalPresenceDb: vocalPresenceDb === 0 ? 0 : vocalPresenceDb,
  };
}

/**
 * Harman automotive target contour (gentle bass warmth, smooth neutral mids, refined top rolloff)
 * used by the 5-second Cabin RTA Auto-Calibration sweep.
 */
export const HARMAN_CABIN_TARGET_OFFSET_16: readonly number[] = [
  2.5, 2.5, 2.0, 1.0, 0.5, 0.0, 0.0, 0.0, -0.2, -0.5, -0.8, -1.0, -1.2, -1.5, -1.8, -2.0,
];

/**
 * Given 16 measured cabin RTA band levels (in dB), calculates safe clamped (-6..+6 dB, 0.5 dB steps)
 * inverse EQ corrections toward the Harman automotive cabin target curve.
 */
export function computeCabinRtaCorrections(measuredBandDb: readonly number[]): DspBandGains16 {
  if (!Array.isArray(measuredBandDb) || measuredBandDb.length < 16) {
    return [...ZERO_BANDS_16] as DspBandGains16;
  }
  // Use midrange reference average (250 Hz..5 kHz, bands 3..9) so bass dips/peaks don't skew overall level
  let refSum = 0;
  let refCount = 0;
  for (let i = 3; i <= 9; i++) {
    const v = Number(measuredBandDb[i]);
    if (Number.isFinite(v)) {
      refSum += v;
      refCount++;
    }
  }
  const refAvg = refCount > 0 ? refSum / refCount : 0;

  const out = new Array(16).fill(0) as DspBandGains16;
  for (let i = 0; i < 16; i++) {
    const measured = Number(measuredBandDb[i]);
    if (!Number.isFinite(measured)) {
      out[i] = 0;
      continue;
    }
    const relativeMeasured = measured - refAvg;
    const target = HARMAN_CABIN_TARGET_OFFSET_16[i] ?? 0;
    // Apply 65% damping factor to avoid over-correcting narrow cabin nulls
    const rawCorrection = (target - relativeMeasured) * 0.65;
    out[i] = clampNumber(rawCorrection, -6, 6, 0.5);
  }
  return out;
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

export function calculateCabinStageGains(
  balance: number,
  fader: number
): { leftGain: number; rightGain: number; faderHighShelfDb: number } {
  const { leftGain, rightGain } = calculateBalanceGains(balance);
  const clampedFader = Math.max(-100, Math.min(100, fader));
  // Shifting rearward smoothly attenuates front-stage amplitude slightly & warms crisp upper highs
  const rearFactor = clampedFader < 0 ? Math.abs(clampedFader) / 100 : 0;
  const faderGain = Math.pow(10, (-4 * rearFactor) / 20);
  return {
    leftGain: Math.round(leftGain * faderGain * 1000) / 1000,
    rightGain: Math.round(rightGain * faderGain * 1000) / 1000,
    faderHighShelfDb: rearFactor === 0 ? 0 : Math.round(-2.5 * rearFactor * 10) / 10,
  };
}

export function matchPresetForTrack(
  genre?: string | null,
  folderPath?: string | null,
  title?: string | null
): string | null {
  const haystack = [genre, folderPath, title].filter(Boolean).join(' ').toLowerCase().trim();
  if (!haystack) return null;

  if (['podcast', 'spoken', 'audiobook', 'talk', 'news', 'lecture', 'radio', 'speech'].some((k) => haystack.includes(k))) {
    return 'podcast_clarity';
  }
  if (['rock', 'metal', 'punk', 'grunge', 'hardcore', 'alternative', 'indie', 'guitar'].some((k) => haystack.includes(k))) {
    return 'rock_metal';
  }
  if (['electronic', 'techno', 'house', 'trance', 'edm', 'club', 'dnb', 'drum & bass', 'dubstep', 'synth', 'dance', 'dj'].some((k) => haystack.includes(k))) {
    return 'club_electronic';
  }
  if (['hip hop', 'hip-hop', 'rap', 'trap', 'drill', 'bass'].some((k) => haystack.includes(k))) {
    return 'cabin_bass';
  }
  if (['jazz', 'acoustic', 'blues', 'soul', 'classical', 'folk', 'bossa', 'ambient'].some((k) => haystack.includes(k))) {
    return 'vocal_jazz';
  }
  return null;
}

export function applyPresetToDsp(current: DspSettings, preset: DspPreset16): DspSettings {
  return {
    ...current,
    dspEnabled: true,
    selectedPresetId: preset.id,
    bands16: [...preset.bands16] as DspBandGains16,
    bassBoostDb: preset.bassBoostDb ?? current.bassBoostDb,
    bassBoostFreqHz: preset.bassBoostFreqHz ?? current.bassBoostFreqHz,
    preampDb: preset.preampDb ?? current.preampDb,
    hpfCutoffHz: preset.hpfCutoffHz ?? current.hpfCutoffHz,
    loudnessContour: preset.loudnessContour ?? current.loudnessContour,
    compressorProfile: preset.compressorProfile ?? current.compressorProfile,
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
 * incorporating HPF rolloff (12 or 24 dB/oct) + Bass Boost + Sub LPF Trim + 16 EQ bands +
 * Loudness Contour + Road-Noise Acoustic Contour + Logic 7 Center Presence.
 */
export function buildDspResponseCurvePoints(
  settings: Pick<
    DspSettings,
    'dspEnabled' | 'bands16' | 'bassBoostDb' | 'bassBoostFreqHz' | 'hpfCutoffHz' | 'loudnessContour'
  > &
    Partial<
      Pick<
        DspSettings,
        'subLpfHz' | 'subTrimDb' | 'fader' | 'crossoverSlopeDb' | 'roadNoiseEqEnabled' | 'surroundMode' | 'centerCoherence' | 'virtualHeight'
      >
    >,
  volumePercent: number,
  width = 640,
  height = 140,
  speedKmH = 0
): Array<{ x: number; y: number; freq: number; db: number }> {
  const minLog = Math.log10(25);
  const maxLog = Math.log10(18000);
  const loudness = calculateLoudnessContourGains(
    settings.dspEnabled && settings.loudnessContour,
    volumePercent
  );
  const roadNoise = calculateRoadNoiseContourDb(
    speedKmH,
    Boolean(settings.dspEnabled && settings.roadNoiseEqEnabled)
  );
  const subTrim = settings.subTrimDb ?? 0;
  const subCutoff = settings.subLpfHz && settings.subLpfHz > 0 ? settings.subLpfHz : 100;
  const fader = settings.fader ?? 0;
  const slopeOrder = settings.crossoverSlopeDb === 24 ? 2 : 1;
  const surroundSpec = settings.surroundMode ? SURROUND_MODES[settings.surroundMode] : SURROUND_MODES.off;
  const centerBoostDb =
    surroundSpec && settings.surroundMode && settings.surroundMode !== 'off'
      ? surroundSpec.centerBoostMaxDb * ((settings.centerCoherence ?? 55) / 100) * 0.6
      : 0;
  const heightBoostDb =
    surroundSpec && settings.surroundMode && settings.surroundMode !== 'off'
      ? surroundSpec.heightBoostMaxDb * ((settings.virtualHeight ?? 45) / 100) * 0.8
      : 0;

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

      // Dedicated Sub LPF trim below subCutoff
      if (subTrim !== 0) {
        const subPower = settings.crossoverSlopeDb === 24 ? 3.8 : 2.4;
        const subRatio = 1 / (1 + Math.pow(freq / subCutoff, subPower));
        db += subTrim * subRatio;
      }

      // Dynamic Fletcher-Munson loudness shelves
      if (loudness.lowShelfDb > 0) {
        db += loudness.lowShelfDb / (1 + Math.pow(freq / 95, 1.8));
      }
      if (loudness.highShelfDb > 0) {
        db += loudness.highShelfDb / (1 + Math.pow(9000 / freq, 1.8));
      }

      // Speed-Sensitive Road-Noise Acoustic Contour
      if (roadNoise.subBassDb > 0) {
        db += roadNoise.subBassDb / (1 + Math.pow(freq / 115, 2.0));
      }
      if (roadNoise.vocalPresenceDb > 0) {
        const vDist = (Math.log2(freq) - Math.log2(2800)) / 0.65;
        db += roadNoise.vocalPresenceDb * Math.exp(-0.5 * vDist * vDist);
      }

      // Logic 7 / Dolby / Virtual Surround Center Vocal Coherence bell (~2.2 kHz)
      if (centerBoostDb > 0.05) {
        const cDist = (Math.log2(freq) - Math.log2(2200)) / 0.65;
        db += centerBoostDb * Math.exp(-0.5 * cDist * cDist);
      }

      // Dolby Virtual Atmos / 360° Virtualizer Pinna Overhead Elevation bell (~7.2 kHz)
      if (heightBoostDb > 0.05) {
        const hDist = (Math.log2(freq) - Math.log2(7200)) / 0.72;
        db += heightBoostDb * Math.exp(-0.5 * hDist * hDist);
      }

      // Rear-stage subtle upper-high warmth when faded rearward
      if (fader < 0 && freq >= 3500) {
        db -= (Math.abs(fader) / 100) * 2.5 * Math.min(1, (freq - 3500) / 4500);
      }

      // 2nd-order (12 dB/oct) or 4th-order Linkwitz-Riley (24 dB/oct) High-Pass Filter rolloff
      if (settings.hpfCutoffHz > 0 && freq < settings.hpfCutoffHz * 1.6) {
        const ratio = freq / settings.hpfCutoffHz;
        const mag = (ratio * ratio) / Math.sqrt(Math.pow(1 - ratio * ratio, 2) + 2 * ratio * ratio);
        const hpfDb = 20 * Math.log10(Math.max(0.01, mag)) * slopeOrder;
        db += Math.max(-24, hpfDb);
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
  if (!raw || typeof raw !== 'object') {
    return { ...DEFAULT_DSP_SETTINGS, devicePresetMap: { ...DEFAULT_DEVICE_PRESET_MAP } };
  }
  const obj = raw as Record<string, unknown>;
  const validHpf: HpfCutoffHz[] = [0, 60, 80, 100, 120];
  const validSubLpf: SubLpfHz[] = [0, 80, 100, 120, 160];
  const validCeilings: LimiterCeilingDb[] = [0, -0.5, -1.5, -3.0];
  const validStages: ListeningStagePreset[] = ['all', 'driver', 'driver_rhd', 'center', 'custom'];
  const validComp: CompressorProfileId[] = ['off', 'balanced', 'night_highway', 'club_punch'];
  const validSurround: SurroundModeId[] = [...SURROUND_MODE_ORDER];

  const rawMap = obj.devicePresetMap && typeof obj.devicePresetMap === 'object'
    ? (obj.devicePresetMap as Record<string, unknown>)
    : null;
  const devicePresetMap: Record<AudioOutputRouteId, string> = { ...DEFAULT_DEVICE_PRESET_MAP };
  if (rawMap) {
    for (const route of AUDIO_OUTPUT_ROUTES) {
      const mapped = rawMap[route.id];
      if (typeof mapped === 'string' && mapped.trim()) {
        devicePresetMap[route.id] = mapped.trim();
      }
    }
  }

  const rawCeiling = Number(obj.peakLimiterCeilingDb ?? DEFAULT_DSP_SETTINGS.peakLimiterCeilingDb);
  const nearestCeiling = validCeilings.reduce((best, cand) =>
    Math.abs(cand - rawCeiling) < Math.abs(best - rawCeiling) ? cand : best
  , DEFAULT_DSP_SETTINGS.peakLimiterCeilingDb);

  const rawSlope = Number(obj.crossoverSlopeDb ?? DEFAULT_DSP_SETTINGS.crossoverSlopeDb);
  const crossoverSlopeDb: CrossoverSlopeDb = rawSlope === 24 ? 24 : 12;

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
    crossoverSlopeDb,
    loudnessContour:
      typeof obj.loudnessContour === 'boolean' ? obj.loudnessContour : DEFAULT_DSP_SETTINGS.loudnessContour,
    listeningStage: validStages.includes(obj.listeningStage as ListeningStagePreset)
      ? (obj.listeningStage as ListeningStagePreset)
      : DEFAULT_DSP_SETTINGS.listeningStage,
    leftDelayMs: clampNumber(Number(obj.leftDelayMs ?? 0), 0, 5, 0.1),
    rightDelayMs: clampNumber(Number(obj.rightDelayMs ?? 0), 0, 5, 0.1),
    balance: clampNumber(Number(obj.balance ?? 0), -100, 100, 5),
    fader: clampNumber(Number(obj.fader ?? 0), -100, 100, 5),
    stereoWidthPercent: clampNumber(Number(obj.stereoWidthPercent ?? 100), 0, 150, 5),
    compressorProfile: validComp.includes(obj.compressorProfile as CompressorProfileId)
      ? (obj.compressorProfile as CompressorProfileId)
      : DEFAULT_DSP_SETTINGS.compressorProfile,
    subPhaseInverted: typeof obj.subPhaseInverted === 'boolean' ? obj.subPhaseInverted : false,
    subLpfHz: validSubLpf.includes(obj.subLpfHz as SubLpfHz) ? (obj.subLpfHz as SubLpfHz) : 0,
    subTrimDb: clampNumber(Number(obj.subTrimDb ?? 0), -6, 6, 0.5),
    subDelayMs: clampNumber(Number(obj.subDelayMs ?? 0), 0, 15, 0.5),
    monoFoldDown: typeof obj.monoFoldDown === 'boolean' ? obj.monoFoldDown : false,
    peakLimiterCeilingDb: Number.isFinite(rawCeiling) ? nearestCeiling : DEFAULT_DSP_SETTINGS.peakLimiterCeilingDb,
    autoGenrePresetEnabled: typeof obj.autoGenrePresetEnabled === 'boolean' ? obj.autoGenrePresetEnabled : false,
    autoSwitchDevicePreset: typeof obj.autoSwitchDevicePreset === 'boolean' ? obj.autoSwitchDevicePreset : false,
    devicePresetMap,
    surroundMode: validSurround.includes(obj.surroundMode as SurroundModeId)
      ? (obj.surroundMode as SurroundModeId)
      : 'off',
    surroundIntensity: clampNumber(Number(obj.surroundIntensity ?? DEFAULT_DSP_SETTINGS.surroundIntensity), 0, 100, 5),
    centerCoherence: clampNumber(Number(obj.centerCoherence ?? DEFAULT_DSP_SETTINGS.centerCoherence), 0, 100, 5),
    virtualHeight: clampNumber(Number(obj.virtualHeight ?? DEFAULT_DSP_SETTINGS.virtualHeight), 0, 100, 5),
    roadNoiseEqEnabled: typeof obj.roadNoiseEqEnabled === 'boolean' ? obj.roadNoiseEqEnabled : false,
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
