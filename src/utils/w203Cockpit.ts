export type SwapGroupMode = 'folders' | 'albums';

export interface CockpitTrackLike {
  id: string;
  title: string;
  artist: string;
  album: string;
  folderPath?: string;
  href?: string;
  fileName?: string;
  sizeBytes?: number;
  sourceType?: 'WEBDAV' | 'LOCAL';
  streamUrl?: string;
}

export interface SwapMediaGroup<T extends CockpitTrackLike = CockpitTrackLike> {
  id: string;
  name: string;
  subtitle: string;
  tracks: T[];
  cachedCount: number;
  isPinnedForWifiCache: boolean;
  artworkTrackId: string | null;
}

export interface W203DimmerResolution {
  useDayMode: boolean;
  isAmberNightActive: boolean;
}

export interface StereoSpectrumSnapshot {
  leftBands: number[];
  rightBands: number[];
  midRms: number;
  sideRms: number;
  stereoSeparationPercent: number;
  isActive: boolean;
  sampleRateHz: number;
}

export const STEREO_SPECTRUM_FREQS_HZ: readonly number[] = [
  60, 100, 160, 250, 400, 630, 1000, 1600,
  2200, 3150, 4500, 6300, 7200, 9500, 12500, 16000,
];

export const STEREO_SPECTRUM_LABELS: readonly string[] = [
  '60', '100', '160', '250', '400', '630', '1k', '1.6k',
  '2.2k', '3.1k', '4.5k', '6.3k', '7.2k', '9.5k', '12k', '16k',
];

export const W203_AMBER_HEX = '#f59e0b';

export function normalizeFolderKey(raw: string): string {
  return raw
    .replace(/\\/g, '/')
    .trim()
    .replace(/^\/+/, '')
    .replace(/\/+$/, '');
}

export function extractFolderKey(track: CockpitTrackLike): string {
  const folder = normalizeFolderKey(track.folderPath || '');
  if (folder) return folder;

  const hrefClean = normalizeFolderKey(decodeURIComponentSafe(track.href || ''));
  const filesIdx = hrefClean.indexOf('/files/');
  const rel = filesIdx >= 0 ? hrefClean.slice(filesIdx + '/files/'.length) : hrefClean;
  // Strip Nextcloud username segment if present (e.g. remote.php/dav/files/<user>/<folder>/<file>)
  const strippedUser = filesIdx >= 0 && rel.includes('/') ? rel.slice(rel.indexOf('/') + 1) : rel;
  const slashIdx = strippedUser.lastIndexOf('/');
  if (slashIdx > 0) {
    return normalizeFolderKey(strippedUser.slice(0, slashIdx));
  }
  const album = (track.album || '').trim();
  return album || 'Root Music';
}

function decodeURIComponentSafe(val: string): string {
  try {
    return decodeURIComponent(val);
  } catch {
    return val;
  }
}

export function formatFolderDisplayParts(folderKey: string): { name: string; parent: string } {
  const clean = normalizeFolderKey(folderKey);
  if (!clean) return { name: 'Root Music', parent: '/' };
  const slashIdx = clean.lastIndexOf('/');
  if (slashIdx > 0) {
    return {
      name: clean.slice(slashIdx + 1),
      parent: clean.slice(0, slashIdx),
    };
  }
  return { name: clean, parent: 'Nextcloud Folder' };
}

export function buildFolderSwapGroups<T extends CockpitTrackLike>(
  tracks: T[],
  offlineTrackIds: Set<string>,
  pinnedFolders: Iterable<string> = [],
  offlineOnly = false
): SwapMediaGroup<T>[] {
  const filtered = offlineOnly ? tracks.filter((t) => offlineTrackIds.has(t.id)) : tracks;
  if (filtered.length === 0) return [];

  const normalizedPins = new Set<string>();
  for (const pin of pinnedFolders) {
    normalizedPins.add(normalizeFolderKey(pin).toLowerCase());
  }

  const buckets = new Map<string, T[]>();
  for (const track of filtered) {
    const key = extractFolderKey(track);
    const list = buckets.get(key);
    if (list) list.push(track);
    else buckets.set(key, [track]);
  }

  return Array.from(buckets.entries())
    .sort((a, b) => a[0].toLowerCase().localeCompare(b[0].toLowerCase()))
    .map(([folderKey, groupTracks]) => {
      const ordered = [...groupTracks].sort((a, b) =>
        (a.fileName || a.title).toLowerCase().localeCompare((b.fileName || b.title).toLowerCase())
      );
      const { name, parent } = formatFolderDisplayParts(folderKey);
      const cachedCount = ordered.reduce((acc, t) => acc + (offlineTrackIds.has(t.id) ? 1 : 0), 0);
      return {
        id: folderKey,
        name,
        subtitle: parent,
        tracks: ordered,
        cachedCount,
        isPinnedForWifiCache: normalizedPins.has(normalizeFolderKey(folderKey).toLowerCase()),
        artworkTrackId: ordered[0]?.id ?? null,
      };
    });
}

export function buildAlbumSwapGroups<T extends CockpitTrackLike>(
  tracks: T[],
  offlineTrackIds: Set<string>,
  pinnedFolders: Iterable<string> = [],
  offlineOnly = false
): SwapMediaGroup<T>[] {
  const filtered = offlineOnly ? tracks.filter((t) => offlineTrackIds.has(t.id)) : tracks;
  if (filtered.length === 0) return [];

  const normalizedPins = new Set<string>();
  for (const pin of pinnedFolders) {
    normalizedPins.add(normalizeFolderKey(pin).toLowerCase());
  }

  const buckets = new Map<string, T[]>();
  for (const track of filtered) {
    const album = (track.album || '').trim() || 'Unknown Album';
    const artist = (track.artist || '').trim() || 'Unknown Artist';
    const key = `${album}||${artist}`;
    const list = buckets.get(key);
    if (list) list.push(track);
    else buckets.set(key, [track]);
  }

  return Array.from(buckets.entries())
    .sort((a, b) => a[0].toLowerCase().localeCompare(b[0].toLowerCase()))
    .map(([key, groupTracks]) => {
      const [albumName = 'Unknown Album', artistName = 'Unknown Artist'] = key.split('||');
      const ordered = [...groupTracks].sort((a, b) =>
        (a.fileName || a.title).toLowerCase().localeCompare((b.fileName || b.title).toLowerCase())
      );
      const folderKey = extractFolderKey(ordered[0]);
      const cachedCount = ordered.reduce((acc, t) => acc + (offlineTrackIds.has(t.id) ? 1 : 0), 0);
      return {
        id: folderKey,
        name: albumName,
        subtitle: artistName,
        tracks: ordered,
        cachedCount,
        isPinnedForWifiCache: normalizedPins.has(normalizeFolderKey(folderKey).toLowerCase()),
        artworkTrackId: ordered[0]?.id ?? null,
      };
    });
}

export function findCurrentSwapGroupIndex<T extends CockpitTrackLike>(
  groups: SwapMediaGroup<T>[],
  currentTrack: T | undefined | null,
  mode: SwapGroupMode
): number {
  if (groups.length === 0 || !currentTrack) return -1;
  const directIdx = groups.findIndex((g) => g.tracks.some((t) => t.id === currentTrack.id));
  if (directIdx >= 0) return directIdx;

  if (mode === 'folders') {
    const key = extractFolderKey(currentTrack).toLowerCase();
    return groups.findIndex((g) => g.id.toLowerCase() === key);
  }
  const album = (currentTrack.album || '').trim().toLowerCase();
  const artist = (currentTrack.artist || '').trim().toLowerCase();
  return groups.findIndex(
    (g) => g.name.toLowerCase() === album && g.subtitle.toLowerCase() === artist
  );
}

export function findAdjacentSwapGroup<T extends CockpitTrackLike>(
  groups: SwapMediaGroup<T>[],
  currentTrack: T | undefined | null,
  mode: SwapGroupMode,
  delta: number
): { group: SwapMediaGroup<T>; index: number } | null {
  if (groups.length === 0) return null;
  const curIdx = findCurrentSwapGroupIndex(groups, currentTrack, mode);
  const nextIdx =
    curIdx < 0
      ? delta >= 0
        ? 0
        : groups.length - 1
      : (((curIdx + delta) % groups.length) + groups.length) % groups.length;
  return { group: groups[nextIdx], index: nextIdx };
}

export function isW203NightWindow(
  hourOfDay: number,
  dayStartHour = 7,
  nightStartHour = 19
): boolean {
  const h = Math.max(0, Math.min(23, Math.floor(hourOfDay)));
  const day = Math.max(0, Math.min(23, Math.floor(dayStartHour)));
  const night = Math.max(0, Math.min(23, Math.floor(nightStartHour)));
  if (day === night) return false;
  return day < night ? h < day || h >= night : h >= night && h < day;
}

export function resolveW203DimmerState(params: {
  themeMode: 'auto' | 'night' | 'day';
  hourOfDay: number;
  dayStartHour?: number;
  nightStartHour?: number;
  isSolarDaylight?: boolean | null;
  autoAmberAtNight?: boolean;
  manualAmberFilter?: boolean;
}): W203DimmerResolution {
  const dayStart = params.dayStartHour ?? 7;
  const nightStart = params.nightStartHour ?? 19;
  const autoAmberAtNight = params.autoAmberAtNight ?? true;
  const manualAmberFilter = params.manualAmberFilter ?? false;

  let useDayMode: boolean;
  if (params.themeMode === 'day') {
    useDayMode = true;
  } else if (params.themeMode === 'night') {
    useDayMode = false;
  } else {
    const scheduleIsNight = isW203NightWindow(params.hourOfDay, dayStart, nightStart);
    if (params.isSolarDaylight !== undefined && params.isSolarDaylight !== null && dayStart === 7 && nightStart === 19) {
      useDayMode = params.isSolarDaylight;
    } else {
      useDayMode = !scheduleIsNight;
    }
  }

  return {
    useDayMode,
    isAmberNightActive: manualAmberFilter || (!useDayMode && autoAmberAtNight),
  };
}

export function selectTracksForWifiAutoCache<T extends CockpitTrackLike>(params: {
  tracks: T[];
  offlineTrackIds: Set<string>;
  pinnedFolders: Iterable<string>;
  maxCacheMb: number;
  currentCachedBytes: number;
}): T[] {
  const normalizedPins = new Set<string>();
  for (const pin of params.pinnedFolders) {
    const norm = normalizeFolderKey(pin).toLowerCase();
    if (norm) normalizedPins.add(norm);
  }
  if (normalizedPins.size === 0 || params.tracks.length === 0) return [];

  const capBytes = params.maxCacheMb <= 0 ? Number.POSITIVE_INFINITY : params.maxCacheMb * 1024 * 1024;
  const candidates = params.tracks
    .filter((track) => {
      if (params.offlineTrackIds.has(track.id)) return false;
      if (track.sourceType === 'LOCAL') return false;
      const folderKey = normalizeFolderKey(extractFolderKey(track)).toLowerCase();
      return normalizedPins.has(folderKey);
    })
    .sort((a, b) => {
      const fCmp = extractFolderKey(a).toLowerCase().localeCompare(extractFolderKey(b).toLowerCase());
      if (fCmp !== 0) return fCmp;
      return (a.fileName || a.title).toLowerCase().localeCompare((b.fileName || b.title).toLowerCase());
    });

  if (!Number.isFinite(capBytes)) return candidates;

  const selected: T[] = [];
  let projectedBytes = Math.max(0, params.currentCachedBytes);
  for (const track of candidates) {
    const estBytes = track.sizeBytes && track.sizeBytes > 0 ? track.sizeBytes : 8 * 1024 * 1024;
    if (projectedBytes + estBytes > capBytes) break;
    selected.push(track);
    projectedBytes += estBytes;
  }
  return selected;
}

export function isUnmeteredWifiLikely(): boolean {
  if (typeof navigator === 'undefined') return true;
  if (navigator.onLine === false) return false;
  const conn = (navigator as any).connection;
  if (!conn) return true;
  if (conn.saveData === true) return false;
  if (typeof conn.type === 'string' && conn.type === 'cellular') return false;
  return true;
}
