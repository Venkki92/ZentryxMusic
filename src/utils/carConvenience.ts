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
  | 'favorite';

export type SteeringKeyBindings = Record<SteeringAction, string>;

export const STEERING_ACTION_LABELS: Record<SteeringAction, string> = {
  play_pause: 'Play / Pause',
  next: 'Next Track',
  prev: 'Previous Track',
  mute: 'Mute / Unmute',
  duck: 'Voice / Nav Duck',
  visualizer: 'Toggle Visualizer',
  favorite: 'Favorite Current Track',
};

export const DEFAULT_SWC_BINDINGS: SteeringKeyBindings = {
  play_pause: 'Space',
  next: 'MediaTrackNext',
  prev: 'MediaTrackPrevious',
  mute: 'KeyM',
  duck: 'KeyD',
  visualizer: 'KeyV',
  favorite: 'KeyL',
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

export function resolveSteeringAction(
  eventKey: string,
  eventCode: string,
  bindings: SteeringKeyBindings
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
