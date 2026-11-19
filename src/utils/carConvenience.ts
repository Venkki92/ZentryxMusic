export interface IgnitionResumeSession {
  trackId: string;
  positionSeconds: number;
  queueIds: string[];
  shuffle: boolean;
  repeatMode: 'OFF' | 'ALL' | 'ONE';
  wasPlaying: boolean;
  savedAt: number;
}

export function serializeResumeSession(session: IgnitionResumeSession): string {
  return JSON.stringify({
    trackId: session.trackId,
    positionSeconds: Math.max(0, Math.floor(session.positionSeconds || 0)),
    queueIds: Array.isArray(session.queueIds) ? session.queueIds.slice(0, 500) : [],
    shuffle: Boolean(session.shuffle),
    repeatMode: session.repeatMode === 'ALL' || session.repeatMode === 'ONE' ? session.repeatMode : 'OFF',
    wasPlaying: Boolean(session.wasPlaying),
    savedAt: session.savedAt || Date.now(),
  });
}

export function parseResumeSession(raw: string | null | undefined): IgnitionResumeSession | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed.trackId !== 'string' || !parsed.trackId) return null;
    return {
      trackId: parsed.trackId,
      positionSeconds:
        typeof parsed.positionSeconds === 'number' && Number.isFinite(parsed.positionSeconds)
          ? Math.max(0, parsed.positionSeconds)
          : 0,
      queueIds: Array.isArray(parsed.queueIds)
        ? parsed.queueIds.filter((x: unknown): x is string => typeof x === 'string')
        : [],
      shuffle: Boolean(parsed.shuffle),
      repeatMode:
        parsed.repeatMode === 'ALL' || parsed.repeatMode === 'ONE' ? parsed.repeatMode : 'OFF',
      wasPlaying: Boolean(parsed.wasPlaying),
      savedAt: typeof parsed.savedAt === 'number' ? parsed.savedAt : 0,
    };
  } catch {
    return null;
  }
}

/**
 * Smooth ease-in volume ramp during ignition boot resume so cabin speakers never blast suddenly.
 */
export function calculateSoftStartGain(elapsedMs: number, rampDurationMs = 1500): number {
  if (rampDurationMs <= 0) return 1;
  const t = Math.max(0, Math.min(1, elapsedMs / rampDurationMs));
  // Gentle quadratic ease-in from 0.12 -> 1.0
  return 0.12 + 0.88 * t * (2 - t);
}

/**
 * Speed-aware Driver Safe Lock activation check.
 */
export function isDriverSafeLockActive(
  enabled: boolean,
  speedKmh: number,
  thresholdKmh = 10
): boolean {
  if (!enabled) return false;
  return Number.isFinite(speedKmh) && speedKmh >= thresholdKmh;
}

/**
 * Computes dark dimming overlay alpha from a user brightness level (35..100%).
 */
export function calculateDimmerOverlayOpacity(brightnessPercent: number): number {
  const clamped = Math.max(35, Math.min(100, brightnessPercent));
  return Math.round(((100 - clamped) / 100) * 1000) / 1000;
}

export type SteeringAction =
  | 'play_pause'
  | 'next'
  | 'prev'
  | 'mute'
  | 'duck'
  | 'visualizer'
  | 'favorite'
  | 'cruising_mix'
  | 'drive_mode'
  | 'cycle_surround';

export type SteeringKeyBindings = Record<SteeringAction, string>;
export type SteeringLongPressBindings = Record<SteeringAction, string>;

export const STEERING_ACTION_LABELS: Record<SteeringAction, string> = {
  play_pause: 'Play / Pause',
  next: 'Next Track',
  prev: 'Previous Track',
  mute: 'Mute / Unmute',
  duck: 'Talk Duck (-20 dB)',
  visualizer: 'Toggle Visualizer',
  favorite: 'Favorite Track',
  cruising_mix: 'Start Cruising Mix',
  drive_mode: 'Toggle Drive Mode',
  cycle_surround: 'Cycle Logic 7 Mode',
};

export const DEFAULT_SWC_BINDINGS: SteeringKeyBindings = {
  play_pause: 'Space',
  next: 'MediaTrackNext',
  prev: 'MediaTrackPrevious',
  mute: 'KeyM',
  duck: 'KeyD',
  visualizer: 'KeyV',
  favorite: 'KeyL',
  cruising_mix: 'KeyC',
  drive_mode: 'KeyG',
  cycle_surround: 'KeyS',
};

export const DEFAULT_SWC_LONG_PRESS_BINDINGS: SteeringLongPressBindings = {
  play_pause: 'MediaPlayPause',
  next: '',
  prev: '',
  mute: '',
  duck: 'Space',
  visualizer: '',
  favorite: 'MediaTrackNext',
  cruising_mix: 'MediaTrackPrevious',
  drive_mode: 'KeyM',
  cycle_surround: 'KeyV',
};

export function sanitizeSteeringBindings(raw: unknown): SteeringKeyBindings {
  const base = { ...DEFAULT_SWC_BINDINGS };
  if (!raw || typeof raw !== 'object') return base;
  const obj = raw as Record<string, unknown>;
  (Object.keys(base) as SteeringAction[]).forEach((action) => {
    const val = obj[action];
    if (typeof val === 'string' && val.trim().length > 0 && val.trim().length <= 40) {
      base[action] = val.trim();
    }
  });
  return base;
}

export function sanitizeSteeringLongPressBindings(raw: unknown): SteeringLongPressBindings {
  const base = { ...DEFAULT_SWC_LONG_PRESS_BINDINGS };
  if (!raw || typeof raw !== 'object') return base;
  const obj = raw as Record<string, unknown>;
  (Object.keys(base) as SteeringAction[]).forEach((action) => {
    const val = obj[action];
    if (typeof val === 'string' && val.trim().length <= 40) {
      base[action] = val.trim();
    }
  });
  return base;
}

export function resolveSteeringAction(
  eventKey: string,
  eventCode: string,
  bindings: SteeringKeyBindings | SteeringLongPressBindings
): SteeringAction | null {
  const normalizedKey = (eventKey || '').trim();
  const normalizedCode = (eventCode || '').trim();
  const actions = Object.keys(bindings) as SteeringAction[];

  for (let i = 0; i < actions.length; i++) {
    const action = actions[i];
    const bound = bindings[action];
    if (!bound) continue;
    if (
      bound === normalizedCode ||
      bound === normalizedKey ||
      bound.toLowerCase() === normalizedKey.toLowerCase()
    ) {
      return action;
    }
  }
  return null;
}

export function formatSteeringKeyLabel(binding: string): string {
  if (!binding) return 'Unbound';
  if (binding === ' ') return 'Space';
  return binding.replace(/^Key/, '').replace(/^Digit/, '');
}

/**
 * Multi-Profile Driver Slots: atomically switches cockpit accent, night dimmer,
 * cabin stage alignment, surround mode, and EQ preset in 1 tap.
 */
export interface DriverProfile {
  id: string;
  name: string;
  subtitle: string;
  accentKey: 'LIME' | 'AMBER' | 'CYAN' | 'SILVER' | 'RED';
  cockpitThemeMode: 'auto' | 'night' | 'day';
  nightBrightness: number; // 35..100
  warmAmberFilter: boolean;
  listeningStage: 'all' | 'driver' | 'driver_rhd' | 'center' | 'custom';
  leftDelayMs: number;
  rightDelayMs: number;
  balance: number;
  fader: number;
  surroundMode: 'off' | 'logic7_surround' | 'concert_hall' | 'studio_focused';
  surroundIntensity: number;
  selectedPresetId: string;
  subTrimDb: number;
}

export const DEFAULT_DRIVER_PROFILES: DriverProfile[] = [
  {
    id: 'venki',
    name: 'Venki',
    subtitle: 'LHD Driver Stage · Logic 7 · W203 Tune',
    accentKey: 'LIME',
    cockpitThemeMode: 'night',
    nightBrightness: 100,
    warmAmberFilter: false,
    listeningStage: 'driver',
    leftDelayMs: 1.4,
    rightDelayMs: 0,
    balance: -5,
    fader: 15,
    surroundMode: 'logic7_surround',
    surroundIntensity: 70,
    selectedPresetId: 'w203_bose',
    subTrimDb: 2,
  },
  {
    id: 'night_cruise',
    name: 'Night Cruise',
    subtitle: 'W203 Amber Comfort · Concert Hall · Highway',
    accentKey: 'AMBER',
    cockpitThemeMode: 'night',
    nightBrightness: 72,
    warmAmberFilter: true,
    listeningStage: 'driver',
    leftDelayMs: 1.4,
    rightDelayMs: 0,
    balance: 0,
    fader: 10,
    surroundMode: 'concert_hall',
    surroundIntensity: 60,
    selectedPresetId: 'highway_cruiser',
    subTrimDb: 1.5,
  },
  {
    id: 'passenger_off',
    name: 'Full Cabin',
    subtitle: 'All Seats Symmetric · Studio · Balanced',
    accentKey: 'CYAN',
    cockpitThemeMode: 'auto',
    nightBrightness: 100,
    warmAmberFilter: false,
    listeningStage: 'all',
    leftDelayMs: 0,
    rightDelayMs: 0,
    balance: 0,
    fader: 0,
    surroundMode: 'studio_focused',
    surroundIntensity: 45,
    selectedPresetId: 'flat',
    subTrimDb: 0,
  },
];

export function sanitizeDriverProfiles(raw: unknown): DriverProfile[] {
  if (!Array.isArray(raw) || raw.length === 0) {
    return DEFAULT_DRIVER_PROFILES.map((p) => ({ ...p }));
  }
  const validAccents = new Set(['LIME', 'AMBER', 'CYAN', 'SILVER', 'RED']);
  const validThemes = new Set(['auto', 'night', 'day']);
  const validStages = new Set(['all', 'driver', 'driver_rhd', 'center', 'custom']);
  const validSurround = new Set(['off', 'logic7_surround', 'concert_hall', 'studio_focused']);

  const out: DriverProfile[] = [];
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue;
    const obj = item as Record<string, unknown>;
    if (typeof obj.id !== 'string' || !obj.id.trim() || typeof obj.name !== 'string' || !obj.name.trim()) {
      continue;
    }
    const accent = typeof obj.accentKey === 'string' && validAccents.has(obj.accentKey.toUpperCase())
      ? (obj.accentKey.toUpperCase() as DriverProfile['accentKey'])
      : 'LIME';
    out.push({
      id: obj.id.trim().slice(0, 32),
      name: obj.name.trim().slice(0, 24),
      subtitle: typeof obj.subtitle === 'string' ? obj.subtitle.trim().slice(0, 64) : 'Custom Cockpit Profile',
      accentKey: accent,
      cockpitThemeMode: validThemes.has(String(obj.cockpitThemeMode))
        ? (obj.cockpitThemeMode as DriverProfile['cockpitThemeMode'])
        : 'night',
      nightBrightness: Math.max(35, Math.min(100, Number(obj.nightBrightness ?? 100) || 100)),
      warmAmberFilter: Boolean(obj.warmAmberFilter),
      listeningStage: validStages.has(String(obj.listeningStage))
        ? (obj.listeningStage as DriverProfile['listeningStage'])
        : 'all',
      leftDelayMs: Math.max(0, Math.min(5, Number(obj.leftDelayMs ?? 0) || 0)),
      rightDelayMs: Math.max(0, Math.min(5, Number(obj.rightDelayMs ?? 0) || 0)),
      balance: Math.max(-100, Math.min(100, Number(obj.balance ?? 0) || 0)),
      fader: Math.max(-100, Math.min(100, Number(obj.fader ?? 0) || 0)),
      surroundMode: validSurround.has(String(obj.surroundMode))
        ? (obj.surroundMode as DriverProfile['surroundMode'])
        : 'off',
      surroundIntensity: Math.max(0, Math.min(100, Number(obj.surroundIntensity ?? 65) || 65)),
      selectedPresetId: typeof obj.selectedPresetId === 'string' && obj.selectedPresetId.trim()
        ? obj.selectedPresetId.trim()
        : 'w203_bose',
      subTrimDb: Math.max(-6, Math.min(6, Number(obj.subTrimDb ?? 0) || 0)),
    });
  }
  return out.length > 0 ? out.slice(0, 6) : DEFAULT_DRIVER_PROFILES.map((p) => ({ ...p }));
}

export type RoadtripPool = 'favorites_unheard' | 'favorites' | 'most_played' | 'all';

export interface RoadtripPlanResult<T> {
  tracks: T[];
  totalMinutes: number;
  totalBytes: number;
  alreadyOfflineCount: number;
  toDownloadCount: number;
}

/**
 * Offline Roadtrip Budget Planner: picks tracks up to `hours` duration from the chosen pool,
 * estimating exact offline cache storage and missing track count for 1-tap roadtrip pinning.
 */
export function buildRoadtripOfflinePlan<
  T extends {
    id: string;
    durationSeconds?: number;
    duration?: number | string;
    sizeBytes?: number;
    size?: number;
    artist?: string;
  }
>(
  tracks: T[],
  hours: number,
  pool: RoadtripPool,
  favoriteIds: Set<string>,
  playStats: Record<string, TrackPlayStat>,
  offlineIds: Set<string>
): RoadtripPlanResult<T> {
  const targetSeconds = Math.max(15 * 60, Math.round(Math.max(0.5, Math.min(12, hours)) * 3600));
  if (tracks.length === 0) {
    return { tracks: [], totalMinutes: 0, totalBytes: 0, alreadyOfflineCount: 0, toDownloadCount: 0 };
  }

  let candidates: T[] = [];
  if (pool === 'favorites') {
    candidates = tracks.filter((t) => favoriteIds.has(t.id));
  } else if (pool === 'most_played') {
    candidates = [...tracks]
      .filter((t) => (playStats[t.id]?.playCount || 0) > 0)
      .sort((a, b) => (playStats[b.id]?.playCount || 0) - (playStats[a.id]?.playCount || 0));
    if (candidates.length === 0) candidates = [...tracks];
  } else if (pool === 'favorites_unheard') {
    const favs = tracks.filter((t) => favoriteIds.has(t.id));
    const unheard = tracks.filter((t) => !favoriteIds.has(t.id) && (playStats[t.id]?.playCount || 0) === 0);
    const rest = tracks.filter((t) => !favoriteIds.has(t.id) && (playStats[t.id]?.playCount || 0) > 0);
    candidates = [...favs, ...unheard, ...rest];
  } else {
    candidates = [...tracks];
  }

  const picked: T[] = [];
  let accumSeconds = 0;
  let totalBytes = 0;
  let alreadyOfflineCount = 0;

  for (let i = 0; i < candidates.length; i++) {
    const track = candidates[i];
    const rawDuration =
      typeof track.durationSeconds === 'number'
        ? track.durationSeconds
        : typeof track.duration === 'number'
          ? track.duration
          : 0;
    const durSec = rawDuration > 10 ? rawDuration : 225; // Default 3m45s estimate
    const rawSize =
      typeof track.sizeBytes === 'number'
        ? track.sizeBytes
        : typeof track.size === 'number'
          ? track.size
          : 0;
    const bytes = rawSize > 0 ? rawSize : Math.round((durSec * 320 * 1000) / 8);
    picked.push(track);
    accumSeconds += durSec;
    totalBytes += bytes;
    if (offlineIds.has(track.id)) alreadyOfflineCount++;
    if (accumSeconds >= targetSeconds) break;
  }

  return {
    tracks: picked,
    totalMinutes: Math.round(accumSeconds / 60),
    totalBytes,
    alreadyOfflineCount,
    toDownloadCount: picked.length - alreadyOfflineCount,
  };
}

/**
 * Converts a track's ReplayGain tag (dB) into a linear gain multiplier safely clamped [0.18, 2.0].
 */
export function replayGainDbToMultiplier(
  gainDb: number | undefined,
  enabled: boolean,
  preampDb = 0
): number {
  if (!enabled || gainDb === undefined || !Number.isFinite(gainDb)) return 1.0;
  const totalDb = gainDb + preampDb;
  const linear = Math.pow(10, totalDb / 20);
  return Math.max(0.18, Math.min(2.0, Math.round(linear * 1000) / 1000));
}

/**
 * Equal-power cosine/sine crossfade curve so perceived volume remains steady across track transitions.
 */
export function calculateCrossfadeGains(progressRatio: number): {
  outgoingGain: number;
  incomingGain: number;
} {
  const t = Math.max(0, Math.min(1, progressRatio));
  return {
    outgoingGain: Math.cos(t * Math.PI * 0.5),
    incomingGain: Math.sin(t * Math.PI * 0.5),
  };
}

export interface OfflineCacheCandidate {
  id: string;
  sizeBytes: number;
  downloadedAt: number;
}

/**
 * Selects oldest unpinned, non-favorite, non-queued track IDs to evict when offline cache exceeds capBytes.
 */
export function selectOfflineCacheEvictions(
  records: OfflineCacheCandidate[],
  favoriteIds: Set<string>,
  protectedQueueIds: Set<string>,
  capBytes: number
): string[] {
  if (capBytes <= 0 || records.length === 0) return [];
  let totalBytes = 0;
  for (let i = 0; i < records.length; i++) {
    totalBytes += Math.max(0, records[i].sizeBytes || 0);
  }
  if (totalBytes <= capBytes) return [];

  // Sort oldest first; never evict favorites or immediate protected queue items
  const evictable = records
    .filter((r) => !favoriteIds.has(r.id) && !protectedQueueIds.has(r.id))
    .sort((a, b) => (a.downloadedAt || 0) - (b.downloadedAt || 0));

  const toEvict: string[] = [];
  for (let i = 0; i < evictable.length && totalBytes > capBytes; i++) {
    toEvict.push(evictable[i].id);
    totalBytes -= Math.max(0, evictable[i].sizeBytes || 0);
  }
  return toEvict;
}

export interface TrackPlayStat {
  playCount: number;
  lastPlayedAt: number;
}

export function recordTrackPlayStat(
  stats: Record<string, TrackPlayStat>,
  trackId: string,
  now = Date.now()
): Record<string, TrackPlayStat> {
  const prev = stats[trackId];
  return {
    ...stats,
    [trackId]: {
      playCount: (prev?.playCount || 0) + 1,
      lastPlayedAt: now,
    },
  };
}

export type SmartMixType = 'recent' | 'most_played' | 'cruising';

/**
 * Builds deterministic car-friendly Smart Mixes from local play statistics and favorites.
 */
export function buildSmartMixTracks<
  T extends { id: string; artist?: string; title?: string }
>(
  tracks: T[],
  mixType: SmartMixType,
  stats: Record<string, TrackPlayStat>,
  favoriteIds: Set<string>,
  limit = 40
): T[] {
  if (tracks.length === 0 || limit <= 0) return [];

  if (mixType === 'recent') {
    return tracks
      .filter((t) => (stats[t.id]?.lastPlayedAt || 0) > 0)
      .sort((a, b) => (stats[b.id]?.lastPlayedAt || 0) - (stats[a.id]?.lastPlayedAt || 0))
      .slice(0, limit);
  }

  if (mixType === 'most_played') {
    return tracks
      .filter((t) => (stats[t.id]?.playCount || 0) > 0)
      .sort((a, b) => {
        const diff = (stats[b.id]?.playCount || 0) - (stats[a.id]?.playCount || 0);
        if (diff !== 0) return diff;
        return (stats[b.id]?.lastPlayedAt || 0) - (stats[a.id]?.lastPlayedAt || 0);
      })
      .slice(0, limit);
  }

  // 'cruising': weighted score combining favorites + play history + spread across artists
  const scored = [...tracks].sort((a, b) => {
    const scoreA =
      (favoriteIds.has(a.id) ? 50 : 0) +
      Math.min(30, (stats[a.id]?.playCount || 0) * 5) +
      ((a.id.charCodeAt(0) || 0) % 11);
    const scoreB =
      (favoriteIds.has(b.id) ? 50 : 0) +
      Math.min(30, (stats[b.id]?.playCount || 0) * 5) +
      ((b.id.charCodeAt(0) || 0) % 11);
    return scoreB - scoreA;
  });

  // De-cluster consecutive tracks by identical artist when alternatives exist
  const pool = [...scored];
  const result: T[] = [];
  let lastArtist = '';

  while (pool.length > 0 && result.length < limit) {
    let pickIdx = pool.findIndex(
      (item) => (item.artist || '').toLowerCase() !== lastArtist.toLowerCase()
    );
    if (pickIdx === -1) pickIdx = 0;
    const [picked] = pool.splice(pickIdx, 1);
    result.push(picked);
    lastArtist = picked.artist || '';
  }

  return result;
}
