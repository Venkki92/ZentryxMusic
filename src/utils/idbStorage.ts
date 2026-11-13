import { Playlist } from './fuzzySearch';
import { Track } from '../App';

const DB_NAME = 'zentryx_mbux_cockpit';
const DB_VERSION = 2;

export interface OfflineTrackData {
  id: string;
  track: Track;
  blob: Blob;
  mimeType: string;
  coverBlob?: Blob;
  downloadedAt: number;
}

export interface ProgressBookmark {
  id: string;
  position: number;
  duration: number;
  timestamp: number;
}

export interface CachedTrackMetadata {
  id: string;
  title?: string;
  artist?: string;
  album?: string;
  year?: number;
  genre?: string;
  codec?: string;
  bitrateKbps?: number;
  sampleRateHz?: number;
  durationSeconds?: number;
  replayGainDb?: number;
  coverBlob?: Blob;
  sizeBytes?: number;
  hasCheckedTag: boolean;
  updatedAt: number;
}

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !window.indexedDB) {
      reject(new Error('IndexedDB not supported in this environment'));
      return;
    }
    const request = window.indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = (event: any) => {
      const db = event.target.result as IDBDatabase;
      if (!db.objectStoreNames.contains('offline_tracks')) {
        db.createObjectStore('offline_tracks', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('track_progress')) {
        db.createObjectStore('track_progress', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('playlists')) {
        db.createObjectStore('playlists', { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains('track_metadata')) {
        db.createObjectStore('track_metadata', { keyPath: 'id' });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

// Offline Track Operations
export async function saveOfflineTrack(
  track: Track,
  audioBlob: Blob,
  mimeType: string,
  coverBlob?: Blob
): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('offline_tracks', 'readwrite');
    const store = tx.objectStore('offline_tracks');
    const record: OfflineTrackData = {
      id: track.id,
      track,
      blob: audioBlob,
      mimeType,
      coverBlob,
      downloadedAt: Date.now(),
    };
    const req = store.put(record);
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}

export async function getOfflineTrack(trackId: string): Promise<OfflineTrackData | null> {
  try {
    const db = await openDB();
    return new Promise((resolve) => {
      const tx = db.transaction('offline_tracks', 'readonly');
      const store = tx.objectStore('offline_tracks');
      const req = store.get(trackId);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => resolve(null);
    });
  } catch {
    return null;
  }
}

export async function getAllOfflineTracks(): Promise<OfflineTrackData[]> {
  try {
    const db = await openDB();
    return new Promise((resolve) => {
      const tx = db.transaction('offline_tracks', 'readonly');
      const store = tx.objectStore('offline_tracks');
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => resolve([]);
    });
  } catch {
    return [];
  }
}

export async function deleteOfflineTrack(trackId: string): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('offline_tracks', 'readwrite');
    const store = tx.objectStore('offline_tracks');
    const req = store.delete(trackId);
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}

export async function clearAllOfflineTracks(): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('offline_tracks', 'readwrite');
    const store = tx.objectStore('offline_tracks');
    const req = store.clear();
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}

// Track Progress Bookmark Operations
export async function saveTrackProgress(trackId: string, position: number, duration: number): Promise<void> {
  try {
    const db = await openDB();
    const tx = db.transaction('track_progress', 'readwrite');
    const store = tx.objectStore('track_progress');
    const record: ProgressBookmark = {
      id: trackId,
      position: Math.floor(position),
      duration: Math.floor(duration),
      timestamp: Date.now(),
    };
    store.put(record);
  } catch {}
}

export async function getTrackProgress(trackId: string): Promise<number | null> {
  try {
    const db = await openDB();
    return new Promise((resolve) => {
      const tx = db.transaction('track_progress', 'readonly');
      const store = tx.objectStore('track_progress');
      const req = store.get(trackId);
      req.onsuccess = () => {
        if (req.result && typeof req.result.position === 'number') {
          resolve(req.result.position);
        } else {
          resolve(null);
        }
      };
      req.onerror = () => resolve(null);
    });
  } catch {
    return null;
  }
}

// Persistent Track Metadata & Artwork Cache Operations
export async function getCachedTrackMetadata(trackId: string): Promise<CachedTrackMetadata | null> {
  try {
    const db = await openDB();
    return new Promise((resolve) => {
      const tx = db.transaction('track_metadata', 'readonly');
      const store = tx.objectStore('track_metadata');
      const req = store.get(trackId);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => resolve(null);
    });
  } catch {
    return null;
  }
}

export async function getAllCachedTrackMetadata(): Promise<CachedTrackMetadata[]> {
  try {
    const db = await openDB();
    return new Promise((resolve) => {
      const tx = db.transaction('track_metadata', 'readonly');
      const store = tx.objectStore('track_metadata');
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => resolve([]);
    });
  } catch {
    return [];
  }
}

export async function saveCachedTrackMetadata(record: CachedTrackMetadata): Promise<void> {
  try {
    const db = await openDB();
    return new Promise((resolve) => {
      const tx = db.transaction('track_metadata', 'readwrite');
      const store = tx.objectStore('track_metadata');
      const req = store.put(record);
      req.onsuccess = () => resolve();
      req.onerror = () => resolve();
    });
  } catch {}
}

export async function clearAllCachedTrackMetadata(): Promise<void> {
  try {
    const db = await openDB();
    return new Promise((resolve) => {
      const tx = db.transaction('track_metadata', 'readwrite');
      const store = tx.objectStore('track_metadata');
      const req = store.clear();
      req.onsuccess = () => resolve();
      req.onerror = () => resolve();
    });
  } catch {}
}

// Playlist Persistence Operations
export async function getAllPlaylists(): Promise<Playlist[]> {
  try {
    const db = await openDB();
    return new Promise((resolve) => {
      const tx = db.transaction('playlists', 'readonly');
      const store = tx.objectStore('playlists');
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => resolve([]);
    });
  } catch {
    return [];
  }
}

export async function savePlaylistRecord(playlist: Playlist): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('playlists', 'readwrite');
    const store = tx.objectStore('playlists');
    const req = store.put(playlist);
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}

export async function deletePlaylistRecord(playlistId: string): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('playlists', 'readwrite');
    const store = tx.objectStore('playlists');
    const req = store.delete(playlistId);
    req.onsuccess = () => resolve();
    req.onerror = () => reject(req.error);
  });
}
