export const DEFAULT_MUSIC_FOLDERS = ['WoW Radio', 'Music'] as const;
export const DEFAULT_MUSIC_FOLDER_STRING = DEFAULT_MUSIC_FOLDERS.join(', ');

/**
 * Normalizes a Nextcloud WebDAV folder path by trimming whitespace and leading/trailing slashes.
 * An explicit "/" or empty string normalizes to "" (Nextcloud root).
 */
export function normalizeFolderPath(raw: string | null | undefined): string {
  if (!raw) return '';
  return raw
    .trim()
    .replace(/\\/g, '/')
    .replace(/\/+/g, '/')
    .replace(/^\/+/, '')
    .replace(/\/+$/, '')
    .trim();
}

/**
 * Parses a comma/semicolon/newline-separated list of scan folders plus optional extra folders.
 * When `raw` is empty/blank and no extra folders are supplied, defaults to `['WoW Radio', 'Music']`.
 * Preserves an explicit root selection `"/"` as `[""]`.
 */
export function parseScanFolders(
  raw: string | null | undefined,
  extraFolders: readonly string[] = []
): string[] {
  const result: string[] = [];
  const seen = new Set<string>();

  const pushUnique = (path: string) => {
    const key = path.toLowerCase();
    if (!seen.has(key)) {
      seen.add(key);
      result.push(path);
    }
  };

  const trimmedRaw = (raw ?? '').trim();
  if (trimmedRaw === '/') {
    pushUnique('');
  } else if (trimmedRaw.length > 0) {
    const tokens = trimmedRaw.split(/[,;\n]+/);
    for (const token of tokens) {
      const t = token.trim();
      if (!t) continue;
      if (t === '/') {
        pushUnique('');
      } else {
        const clean = normalizeFolderPath(t);
        if (clean) pushUnique(clean);
      }
    }
  }

  if (result.length === 0) {
    for (const def of DEFAULT_MUSIC_FOLDERS) {
      pushUnique(def);
    }
  }

  for (const extra of extraFolders) {
    const t = extra.trim();
    if (!t) continue;
    if (t === '/') {
      pushUnique('');
    } else {
      const clean = normalizeFolderPath(t);
      if (clean) pushUnique(clean);
    }
  }

  return result;
}

/**
 * Adds a folder to an existing list of folders (case-insensitive deduplication).
 */
export function addFolderToList(existing: readonly string[], folderToAdd: string): string[] {
  const trimmed = folderToAdd.trim();
  const normalized = trimmed === '/' ? '' : normalizeFolderPath(trimmed);
  if (trimmed.length === 0) return [...existing];

  const key = normalized.toLowerCase();
  const alreadyPresent = existing.some((f) => normalizeFolderPath(f).toLowerCase() === key);
  if (alreadyPresent) return [...existing];

  return [...existing, normalized === '' ? '/' : normalized];
}

/**
 * Removes a folder from an existing list (case-insensitive).
 */
export function removeFolderFromList(existing: readonly string[], folderToRemove: string): string[] {
  const targetKey = (folderToRemove.trim() === '/' ? '' : normalizeFolderPath(folderToRemove)).toLowerCase();
  return existing.filter((f) => {
    const k = (f.trim() === '/' ? '' : normalizeFolderPath(f)).toLowerCase();
    return k !== targetKey;
  });
}

export interface TrackLike {
  id: string;
  sourceType?: 'WEBDAV' | 'LOCAL' | string;
}

export interface MergeTracksResult<T extends TrackLike> {
  tracks: T[];
  newlyAddedCount: number;
  updatedCount: number;
}

/**
 * Merges newly discovered tracks into an existing library list.
 * - `additive`: preserves every already-discovered track (WebDAV + LOCAL) and appends/updates new ones.
 * - `replace-cloud`: preserves LOCAL tracks and replaces cloud/WEBDAV tracks with `discovered`.
 */
export function mergeDiscoveredTracks<T extends TrackLike>(
  existing: readonly T[],
  discovered: readonly T[],
  mode: 'additive' | 'replace-cloud'
): MergeTracksResult<T> {
  const map = new Map<string, T>();
  const existingIds = new Set<string>();

  for (const track of existing) {
    existingIds.add(track.id);
    if (mode === 'additive' || track.sourceType === 'LOCAL') {
      map.set(track.id, track);
    }
  }

  let newlyAddedCount = 0;
  let updatedCount = 0;

  for (const track of discovered) {
    if (map.has(track.id)) {
      updatedCount++;
    } else if (!existingIds.has(track.id) || mode === 'replace-cloud') {
      newlyAddedCount++;
    }
    map.set(track.id, track);
  }

  return {
    tracks: Array.from(map.values()),
    newlyAddedCount,
    updatedCount,
  };
}

/**
 * Resolves the initial folder browser path cleanly when the configured music folder
 * holds multiple comma-separated default folders (e.g. "WoW Radio, Music").
 */
export function resolveInitialBrowserPath(input: string | undefined, configuredFolders: string): string {
  const candidate = input !== undefined ? input : configuredFolders;
  if (!candidate || /[,;\n]/.test(candidate)) {
    return '';
  }
  return normalizeFolderPath(candidate);
}
