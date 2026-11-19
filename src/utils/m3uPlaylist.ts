import { Playlist } from './fuzzySearch';

export interface M3uTrackRef {
  id: string;
  title: string;
  artist: string;
  durationSeconds?: number;
  folderPath?: string;
  fileName: string;
}

export function buildTrackRelativePath(track: Pick<M3uTrackRef, 'folderPath' | 'fileName'>): string {
  const cleanFolder = (track.folderPath || '').replace(/^\/+/, '').replace(/\/+$/, '');
  const cleanFile = (track.fileName || '').replace(/^\/+/, '');
  return cleanFolder ? `${cleanFolder}/${cleanFile}` : cleanFile;
}

export function serializeM3u8(playlistName: string, tracks: M3uTrackRef[]): string {
  const lines: string[] = [
    '#EXTM3U',
    `#PLAYLIST:${playlistName.replace(/[\r\n]+/g, ' ').trim()}`,
  ];
  for (const t of tracks) {
    const sec = Math.max(0, Math.floor(t.durationSeconds || 0));
    lines.push(`#EXTINF:${sec},${t.artist || 'Unknown Artist'} - ${t.title || t.fileName}`);
    lines.push(buildTrackRelativePath(t));
  }
  return `${lines.join('\n')}\n`;
}

export function parseM3u8Paths(content: string): string[] {
  if (!content || !content.trim()) return [];
  const paths: string[] = [];
  for (const rawLine of content.split(/\r?\n/)) {
    const trimmed = rawLine.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    paths.push(trimmed.replace(/^\/+/, ''));
  }
  return paths;
}

export function resolvePlaylistNameFromM3u8(content: string, fallbackName: string): string {
  for (const rawLine of content.split(/\r?\n/)) {
    const trimmed = rawLine.trim();
    if (trimmed.toUpperCase().startsWith('#PLAYLIST:')) {
      const candidate = trimmed.slice('#PLAYLIST:'.length).trim();
      if (candidate) return candidate;
    }
  }
  return fallbackName;
}

export function serializePlaylistFileName(name: string): string {
  const safe = (name || 'playlist')
    .trim()
    .replace(/[\\/:*?"<>|]/g, '_')
    .replace(/\s+/g, ' ');
  return `${safe || 'playlist'}.m3u8`;
}

export function resolveMatchedTracksForPaths<T extends M3uTrackRef>(
  paths: string[],
  libraryTracks: T[]
): T[] {
  if (paths.length === 0 || libraryTracks.length === 0) return [];
  const byRel = new Map<string, T>();
  const byName = new Map<string, T>();
  for (const t of libraryTracks) {
    const rel = buildTrackRelativePath(t).toLowerCase();
    if (!byRel.has(rel)) byRel.set(rel, t);
    const fname = (t.fileName || '').toLowerCase();
    if (fname && !byName.has(fname)) byName.set(fname, t);
  }
  const matched: T[] = [];
  const seenIds = new Set<string>();
  for (const raw of paths) {
    const clean = raw.replace(/^\/+/, '').toLowerCase();
    const baseName = clean.split('/').pop() || clean;
    const found = byRel.get(clean) || byName.get(baseName);
    if (found && !seenIds.has(found.id)) {
      seenIds.add(found.id);
      matched.push(found);
    }
  }
  return matched;
}

export function resolveTracksForPlaylist<T extends M3uTrackRef>(
  playlist: Playlist,
  libraryTracks: T[]
): T[] {
  const map = new Map(libraryTracks.map((t) => [t.id, t]));
  return playlist.trackIds.map((id) => map.get(id)).filter(Boolean) as T[];
}
