import { DspSettings, clampNumber } from './dspConsole';

export type AdaptiveCabinSource = 'hybrid' | 'microphone' | 'speed';
export type AdaptiveCabinSensitivity = 'subtle' | 'balanced' | 'aggressive';
export type AdaptiveCabinFocus = 'driver_lhd' | 'driver_rhd' | 'front_center';
export type AdaptiveCabinProfileId = 'quiet' | 'city' | 'highway' | 'high_noise';

export interface AdaptiveCabinConfig {
  enabled: boolean;
  source: AdaptiveCabinSource;
  sensitivity: AdaptiveCabinSensitivity;
  cabinFocus: AdaptiveCabinFocus;
  // Speed thresholds in km/h
  speedCityKmH: number;
  speedHighwayKmH: number;
  speedHighKmH: number;
  // Cabin ambient noise thresholds in estimated dB SPL
  noiseModerateDb: number;
  noiseLoudDb: number;
  noiseExtremeDb: number;
}

export interface AdaptiveCabinProfileSpec {
  id: AdaptiveCabinProfileId;
  rank: number;
  label: string;
  shortLabel: string;
  description: string;
  baseGainDb: number;       // Added to DSP preamp gain
  baseBalanceShift: number; // Directional shift toward driver side (negated for LHD, positive for RHD, 0 for center)
  baseFaderShift: number;   // Positive shifts soundstage forward toward front doors/dash
  baseBassTiltDb: number;   // Sub/mid-bass tire roar compensation
  baseVocalTiltDb: number;  // 2.5-3.5 kHz clarity compensation
}

export const ADAPTIVE_CABIN_PROFILES: Record<AdaptiveCabinProfileId, AdaptiveCabinProfileSpec> = {
  quiet: {
    id: 'quiet',
    rank: 0,
    label: 'Quiet / Parked',
    shortLabel: 'Quiet',
    description: 'Reference base DSP tuning with zero adaptive offset',
    baseGainDb: 0,
    baseBalanceShift: 0,
    baseFaderShift: 0,
    baseBassTiltDb: 0,
    baseVocalTiltDb: 0,
  },
  city: {
    id: 'city',
    rank: 1,
    label: 'City Cruise',
    shortLabel: 'City',
    description: 'Gentle gain lift and light front-stage focus over urban road rumble',
    baseGainDb: 1.5,
    baseBalanceShift: 6,
    baseFaderShift: 10,
    baseBassTiltDb: 1.0,
    baseVocalTiltDb: 0.5,
  },
  highway: {
    id: 'highway',
    rank: 2,
    label: 'Highway Cruise',
    shortLabel: 'Highway',
    description: 'Compensates tire roar & wind noise with stronger gain, front fader & driver balance',
    baseGainDb: 3.0,
    baseBalanceShift: 12,
    baseFaderShift: 18,
    baseBassTiltDb: 2.0,
    baseVocalTiltDb: 1.0,
  },
  high_noise: {
    id: 'high_noise',
    rank: 3,
    label: 'High Noise / Autobahn',
    shortLabel: 'High Noise',
    description: 'Maximum road-noise gain lift and locked front-driver stage clarity',
    baseGainDb: 4.5,
    baseBalanceShift: 18,
    baseFaderShift: 25,
    baseBassTiltDb: 3.0,
    baseVocalTiltDb: 1.5,
  },
};

export const ADAPTIVE_PROFILE_ORDER: readonly AdaptiveCabinProfileId[] = [
  'quiet',
  'city',
  'highway',
  'high_noise',
];

export const DEFAULT_ADAPTIVE_CABIN_CONFIG: AdaptiveCabinConfig = {
  enabled: false,
  source: 'hybrid',
  sensitivity: 'balanced',
  cabinFocus: 'driver_lhd',
  speedCityKmH: 45,
  speedHighwayKmH: 85,
  speedHighKmH: 120,
  noiseModerateDb: 58,
  noiseLoudDb: 68,
  noiseExtremeDb: 76,
};

export interface AdaptiveDspDelta {
  profileId: AdaptiveCabinProfileId;
  gainDb: number;
  balanceDelta: number;
  faderDelta: number;
  bassTiltDb: number;
  vocalTiltDb: number;
}

export interface AdaptiveEvaluationState {
  activeProfile: AdaptiveCabinProfileId;
  pendingProfile: AdaptiveCabinProfileId | null;
  pendingSinceMs: number;
  triggerReason: 'disabled' | 'idle' | 'microphone' | 'speed' | 'hybrid';
}

export const INITIAL_ADAPTIVE_STATE: AdaptiveEvaluationState = {
  activeProfile: 'quiet',
  pendingProfile: null,
  pendingSinceMs: 0,
  triggerReason: 'idle',
};

const SENSITIVITY_SCALE: Record<AdaptiveCabinSensitivity, number> = {
  subtle: 0.65,
  balanced: 1.0,
  aggressive: 1.35,
};

const SPEED_HYSTERESIS_KMH = 7;
const NOISE_HYSTERESIS_DB = 3.5;
const ESCALATE_DWELL_MS = 1400;
const DEESCALATE_DWELL_MS = 3200;

export function sanitizeAdaptiveCabinConfig(raw: unknown): AdaptiveCabinConfig {
  if (!raw || typeof raw !== 'object') {
    return { ...DEFAULT_ADAPTIVE_CABIN_CONFIG };
  }
  const obj = raw as Record<string, unknown>;
  const validSources: AdaptiveCabinSource[] = ['hybrid', 'microphone', 'speed'];
  const validSens: AdaptiveCabinSensitivity[] = ['subtle', 'balanced', 'aggressive'];
  const validFocus: AdaptiveCabinFocus[] = ['driver_lhd', 'driver_rhd', 'front_center'];

  const speedCityKmH = clampNumber(Number(obj.speedCityKmH ?? DEFAULT_ADAPTIVE_CABIN_CONFIG.speedCityKmH), 20, 80, 5);
  const speedHighwayKmH = clampNumber(
    Math.max(speedCityKmH + 15, Number(obj.speedHighwayKmH ?? DEFAULT_ADAPTIVE_CABIN_CONFIG.speedHighwayKmH)),
    50,
    130,
    5
  );
  const speedHighKmH = clampNumber(
    Math.max(speedHighwayKmH + 15, Number(obj.speedHighKmH ?? DEFAULT_ADAPTIVE_CABIN_CONFIG.speedHighKmH)),
    80,
    180,
    5
  );

  const noiseModerateDb = clampNumber(Number(obj.noiseModerateDb ?? DEFAULT_ADAPTIVE_CABIN_CONFIG.noiseModerateDb), 42, 72, 1);
  const noiseLoudDb = clampNumber(
    Math.max(noiseModerateDb + 5, Number(obj.noiseLoudDb ?? DEFAULT_ADAPTIVE_CABIN_CONFIG.noiseLoudDb)),
    52,
    84,
    1
  );
  const noiseExtremeDb = clampNumber(
    Math.max(noiseLoudDb + 5, Number(obj.noiseExtremeDb ?? DEFAULT_ADAPTIVE_CABIN_CONFIG.noiseExtremeDb)),
    62,
    92,
    1
  );

  return {
    enabled: typeof obj.enabled === 'boolean' ? obj.enabled : DEFAULT_ADAPTIVE_CABIN_CONFIG.enabled,
    source: validSources.includes(obj.source as AdaptiveCabinSource)
      ? (obj.source as AdaptiveCabinSource)
      : DEFAULT_ADAPTIVE_CABIN_CONFIG.source,
    sensitivity: validSens.includes(obj.sensitivity as AdaptiveCabinSensitivity)
      ? (obj.sensitivity as AdaptiveCabinSensitivity)
      : DEFAULT_ADAPTIVE_CABIN_CONFIG.sensitivity,
    cabinFocus: validFocus.includes(obj.cabinFocus as AdaptiveCabinFocus)
      ? (obj.cabinFocus as AdaptiveCabinFocus)
      : DEFAULT_ADAPTIVE_CABIN_CONFIG.cabinFocus,
    speedCityKmH,
    speedHighwayKmH,
    speedHighKmH,
    noiseModerateDb,
    noiseLoudDb,
    noiseExtremeDb,
  };
}

function classifyBySpeed(
  speedKmH: number,
  currentRank: number,
  config: AdaptiveCabinConfig
): AdaptiveCabinProfileId {
  if (!Number.isFinite(speedKmH) || speedKmH <= 0) return 'quiet';
  const highTh = currentRank >= 3 ? config.speedHighKmH - SPEED_HYSTERESIS_KMH : config.speedHighKmH;
  const hwTh = currentRank >= 2 ? config.speedHighwayKmH - SPEED_HYSTERESIS_KMH : config.speedHighwayKmH;
  const cityTh = currentRank >= 1 ? config.speedCityKmH - SPEED_HYSTERESIS_KMH : config.speedCityKmH;

  if (speedKmH >= highTh) return 'high_noise';
  if (speedKmH >= hwTh) return 'highway';
  if (speedKmH >= cityTh) return 'city';
  return 'quiet';
}

function classifyByNoise(
  cabinNoiseDb: number | null,
  currentRank: number,
  config: AdaptiveCabinConfig
): AdaptiveCabinProfileId {
  if (cabinNoiseDb === null || !Number.isFinite(cabinNoiseDb)) return 'quiet';
  const extTh = currentRank >= 3 ? config.noiseExtremeDb - NOISE_HYSTERESIS_DB : config.noiseExtremeDb;
  const loudTh = currentRank >= 2 ? config.noiseLoudDb - NOISE_HYSTERESIS_DB : config.noiseLoudDb;
  const modTh = currentRank >= 1 ? config.noiseModerateDb - NOISE_HYSTERESIS_DB : config.noiseModerateDb;

  if (cabinNoiseDb >= extTh) return 'high_noise';
  if (cabinNoiseDb >= loudTh) return 'highway';
  if (cabinNoiseDb >= modTh) return 'city';
  return 'quiet';
}

/**
 * Hysteresis + dwell-time state machine that evaluates live cabin dB and GPS km/h
 * and returns the next active acoustic profile without rapid flickering.
 */
export function evaluateAdaptiveCabinState(
  prevState: AdaptiveEvaluationState,
  config: AdaptiveCabinConfig,
  inputs: {
    cabinNoiseDb: number | null;
    speedKmH: number;
    nowMs: number;
  }
): AdaptiveEvaluationState {
  if (!config.enabled) {
    return {
      activeProfile: 'quiet',
      pendingProfile: null,
      pendingSinceMs: 0,
      triggerReason: 'disabled',
    };
  }

  const currentRank = ADAPTIVE_CABIN_PROFILES[prevState.activeProfile].rank;
  const useMic = config.source === 'microphone' || config.source === 'hybrid';
  const useSpeed = config.source === 'speed' || config.source === 'hybrid';

  const micProfile = useMic ? classifyByNoise(inputs.cabinNoiseDb, currentRank, config) : 'quiet';
  const speedProfile = useSpeed ? classifyBySpeed(inputs.speedKmH, currentRank, config) : 'quiet';

  const micRank = ADAPTIVE_CABIN_PROFILES[micProfile].rank;
  const speedRank = ADAPTIVE_CABIN_PROFILES[speedProfile].rank;

  let targetProfile: AdaptiveCabinProfileId = 'quiet';
  let reason: AdaptiveEvaluationState['triggerReason'] = 'idle';

  if (micRank === 0 && speedRank === 0) {
    targetProfile = 'quiet';
    reason = 'idle';
  } else if (micRank > speedRank) {
    targetProfile = micProfile;
    reason = 'microphone';
  } else if (speedRank > micRank) {
    targetProfile = speedProfile;
    reason = 'speed';
  } else {
    targetProfile = micProfile;
    reason = config.source === 'hybrid' ? 'hybrid' : config.source;
  }

  if (targetProfile === prevState.activeProfile) {
    return {
      activeProfile: prevState.activeProfile,
      pendingProfile: null,
      pendingSinceMs: 0,
      triggerReason: reason,
    };
  }

  const targetRank = ADAPTIVE_CABIN_PROFILES[targetProfile].rank;
  const requiredDwellMs = targetRank > currentRank ? ESCALATE_DWELL_MS : DEESCALATE_DWELL_MS;

  if (prevState.pendingProfile !== targetProfile) {
    return {
      activeProfile: prevState.activeProfile,
      pendingProfile: targetProfile,
      pendingSinceMs: inputs.nowMs,
      triggerReason: prevState.triggerReason,
    };
  }

  const elapsed = inputs.nowMs - prevState.pendingSinceMs;
  if (elapsed >= requiredDwellMs) {
    return {
      activeProfile: targetProfile,
      pendingProfile: null,
      pendingSinceMs: 0,
      triggerReason: reason,
    };
  }

  return prevState;
}

/**
 * Computes exact DSP gain, balance, fader, and spectral tilt deltas for the active profile.
 */
export function calculateAdaptiveDspDelta(
  profileId: AdaptiveCabinProfileId,
  config: AdaptiveCabinConfig
): AdaptiveDspDelta {
  if (!config.enabled || profileId === 'quiet') {
    return {
      profileId: 'quiet',
      gainDb: 0,
      balanceDelta: 0,
      faderDelta: 0,
      bassTiltDb: 0,
      vocalTiltDb: 0,
    };
  }

  const spec = ADAPTIVE_CABIN_PROFILES[profileId];
  const scale = SENSITIVITY_SCALE[config.sensitivity] ?? 1.0;

  const gainDb = clampNumber(spec.baseGainDb * scale, 0, 6, 0.5);
  const rawBalanceMag = Math.round((spec.baseBalanceShift * scale) / 5) * 5;
  const balanceDelta =
    config.cabinFocus === 'driver_lhd'
      ? -rawBalanceMag
      : config.cabinFocus === 'driver_rhd'
        ? rawBalanceMag
        : 0;
  const faderDelta = Math.round((spec.baseFaderShift * scale) / 5) * 5;
  const bassTiltDb = clampNumber(spec.baseBassTiltDb * scale, 0, 4.5, 0.5);
  const vocalTiltDb = clampNumber(spec.baseVocalTiltDb * scale, 0, 2.5, 0.5);

  return {
    profileId,
    gainDb,
    balanceDelta,
    faderDelta,
    bassTiltDb,
    vocalTiltDb,
  };
}

/**
 * Non-destructively overlays adaptive cabin gain, balance/fader, and bass/vocal tilt onto
 * the user's base DspSettings without mutating or persisting over their manual tune.
 */
export function applyAdaptiveCabinOverlay(
  base: DspSettings,
  delta: AdaptiveDspDelta
): DspSettings {
  if (
    delta.profileId === 'quiet' ||
    (delta.gainDb === 0 && delta.balanceDelta === 0 && delta.faderDelta === 0)
  ) {
    return base;
  }

  const nextBands = [...base.bands16] as DspSettings['bands16'];
  if (delta.bassTiltDb > 0) {
    nextBands[0] = clampNumber(nextBands[0] + delta.bassTiltDb * 0.85, -12, 12, 0.5);
    nextBands[1] = clampNumber(nextBands[1] + delta.bassTiltDb, -12, 12, 0.5);
    nextBands[2] = clampNumber(nextBands[2] + delta.bassTiltDb * 0.55, -12, 12, 0.5);
  }
  if (delta.vocalTiltDb > 0) {
    nextBands[6] = clampNumber(nextBands[6] + delta.vocalTiltDb * 0.6, -12, 12, 0.5);
    nextBands[7] = clampNumber(nextBands[7] + delta.vocalTiltDb, -12, 12, 0.5);
    nextBands[8] = clampNumber(nextBands[8] + delta.vocalTiltDb * 0.7, -12, 12, 0.5);
  }

  return {
    ...base,
    preampDb: clampNumber(base.preampDb + delta.gainDb, -12, 6, 0.5),
    balance: clampNumber(base.balance + delta.balanceDelta, -100, 100, 5),
    fader: clampNumber(base.fader + delta.faderDelta, -100, 100, 5),
    bands16: nextBands,
  };
}

/**
 * Converts normalized time-domain PCM float samples [-1..1] to an estimated cabin dB SPL
 * readout (32..98 dB) calibrated for automotive interior noise floors.
 */
export function estimateCabinNoiseDbFromPcm(samples: ArrayLike<number>): number {
  const n = samples.length;
  if (n === 0) return 35;
  let sumSq = 0;
  for (let i = 0; i < n; i++) {
    const v = samples[i];
    sumSq += v * v;
  }
  const rms = Math.sqrt(sumSq / n);
  const dbfs = 20 * Math.log10(Math.max(rms, 1e-5)); // -100 .. 0 dBFS
  // Typical automotive headunit / laptop mic calibration: -60 dBFS ~= 42 dB SPL, -14 dBFS ~= 88 dB SPL
  const estimatedSpl = dbfs + 102;
  return Math.round(Math.max(32, Math.min(98, estimatedSpl)) * 10) / 10;
}

export interface CabinMicMonitorHandle {
  stop: () => void;
}

export type CabinMicStatus = 'off' | 'starting' | 'active' | 'denied' | 'unavailable';

/**
 * Starts a low-overhead Web Audio microphone cabin noise meter.
 * Immediately stops all MediaStream tracks when `stop()` is called.
 */
export function startCabinMicNoiseMonitor(params: {
  onNoiseDb: (smoothedDb: number) => void;
  onStatusChange: (status: CabinMicStatus) => void;
  sampleIntervalMs?: number;
}): CabinMicMonitorHandle {
  const { onNoiseDb, onStatusChange, sampleIntervalMs = 250 } = params;
  let stopped = false;
  let stream: MediaStream | null = null;
  let ctx: AudioContext | null = null;
  let source: MediaStreamAudioSourceNode | null = null;
  let analyser: AnalyserNode | null = null;
  let timerId: number | null = null;
  let emaDb: number | null = null;

  const cleanup = () => {
    if (timerId !== null) {
      window.clearInterval(timerId);
      timerId = null;
    }
    try {
      source?.disconnect();
    } catch {}
    try {
      analyser?.disconnect();
    } catch {}
    if (stream) {
      stream.getTracks().forEach((track) => {
        try {
          track.stop();
        } catch {}
      });
      stream = null;
    }
    if (ctx && ctx.state !== 'closed') {
      ctx.close().catch(() => {});
    }
    ctx = null;
    source = null;
    analyser = null;
  };

  if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) {
    onStatusChange('unavailable');
    return { stop: cleanup };
  }

  onStatusChange('starting');

  navigator.mediaDevices
    .getUserMedia({
      audio: {
        echoCancellation: false,
        noiseSuppression: false,
        autoGainControl: false,
      },
    })
    .then((mediaStream) => {
      if (stopped) {
        mediaStream.getTracks().forEach((t) => t.stop());
        return;
      }
      stream = mediaStream;
      const AudioCtxCtor = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!AudioCtxCtor) {
        cleanup();
        onStatusChange('unavailable');
        return;
      }
      ctx = new AudioCtxCtor();
      analyser = ctx.createAnalyser();
      analyser.fftSize = 1024;
      source = ctx.createMediaStreamSource(mediaStream);
      source.connect(analyser);

      const timeBuf = new Float32Array(analyser.fftSize);
      onStatusChange('active');

      timerId = window.setInterval(() => {
        if (stopped || !analyser) return;
        analyser.getFloatTimeDomainData(timeBuf);
        const instantDb = estimateCabinNoiseDbFromPcm(timeBuf);
        emaDb = emaDb === null ? instantDb : emaDb * 0.72 + instantDb * 0.28;
        onNoiseDb(Math.round(emaDb * 10) / 10);
      }, sampleIntervalMs);
    })
    .catch((err: unknown) => {
      if (stopped) return;
      cleanup();
      const errName = (err as { name?: string })?.name || '';
      if (errName === 'NotAllowedError' || errName === 'PermissionDeniedError') {
        onStatusChange('denied');
      } else {
        onStatusChange('unavailable');
      }
    });

  return {
    stop: () => {
      stopped = true;
      cleanup();
      onStatusChange('off');
    },
  };
}
