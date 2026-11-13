export interface TrackItem {
  id: string;
  title: string;
  artist: string;
  album: string;
  fileName: string;
  folderPath?: string;
}

export interface Playlist {
  id: string;
  name: string;
  description?: string;
  createdAt: number;
  trackIds: string[];
}

export function normalizeStr(str: string): string {
  return (str || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function fuzzyMatch(pattern: string, text: string): boolean {
  const normPattern = normalizeStr(pattern);
  const normText = normalizeStr(text);

  if (!normPattern) return true;
  if (!normText) return false;

  // Direct substring check
  if (normText.includes(normPattern)) return true;

  // Word-by-word token matching
  const tokens = normPattern.split(' ');
  const allTokensMatch = tokens.every((token) => normText.includes(token));
  if (allTokensMatch) return true;

  // Subsequence matching
  let patternIdx = 0;
  for (let i = 0; i < normText.length && patternIdx < normPattern.length; i++) {
    if (normText[i] === normPattern[patternIdx]) {
      patternIdx++;
    }
  }
  return patternIdx === normPattern.length;
}

export function searchTracks<T extends TrackItem>(tracks: T[], query: string): T[] {
  const clean = query.trim();
  if (!clean) return tracks;

  return tracks.filter((t) => {
    return (
      fuzzyMatch(clean, t.title) ||
      fuzzyMatch(clean, t.artist) ||
      fuzzyMatch(clean, t.album) ||
      fuzzyMatch(clean, t.fileName) ||
      (t.folderPath && fuzzyMatch(clean, t.folderPath))
    );
  });
}

export function createPlaylist(name: string, description?: string): Playlist {
  return {
    id: 'pl_' + Date.now().toString(36) + '_' + Math.random().toString(36).substring(2, 6),
    name: name.trim() || 'Untitled Playlist',
    description: description?.trim() || '',
    createdAt: Date.now(),
    trackIds: [],
  };
}

export function addTrackToPlaylist(playlist: Playlist, trackId: string): Playlist {
  if (playlist.trackIds.includes(trackId)) {
    return playlist;
  }
  return {
    ...playlist,
    trackIds: [...playlist.trackIds, trackId],
  };
}

export function removeTrackFromPlaylist(playlist: Playlist, trackId: string): Playlist {
  return {
    ...playlist,
    trackIds: playlist.trackIds.filter((id) => id !== trackId),
  };
}

export function reorderPlaylist(playlist: Playlist, fromIndex: number, toIndex: number): Playlist {
  if (
    fromIndex < 0 ||
    fromIndex >= playlist.trackIds.length ||
    toIndex < 0 ||
    toIndex >= playlist.trackIds.length ||
    fromIndex === toIndex
  ) {
    return playlist;
  }
  const updated = [...playlist.trackIds];
  const [removed] = updated.splice(fromIndex, 1);
  updated.splice(toIndex, 0, removed);
  return {
    ...playlist,
    trackIds: updated,
  };
}
