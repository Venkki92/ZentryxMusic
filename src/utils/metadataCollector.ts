import {
  ParsedAudioMetadata,
  estimateBitrateKbps,
  getID3v2TagTotalSize,
  parseAudioMetadata,
  parseID3v1,
} from './id3Parser';
import type { CachedTrackMetadata } from './idbStorage';
import type { Track } from '../App';

const INITIAL_HEADER_BYTES = 65536; // 64 KiB initial peek (fast over hotspot)
const MAX_TAG_FETCH_BYTES = 1024 * 1024; // 1 MiB ceiling so huge art never stalls playback

export interface EnrichedTrack extends Track {
  genre?: string;
  codec?: string;
  bitrateKbps?: number;
  sampleRateHz?: number;
  replayGainDb?: number;
  metadataHydrated?: boolean;
}

/**
 * Extracts total entity length from an HTTP Content-Range header (e.g. "bytes 0-65535/34200069").
 */
export function parseTotalSizeFromContentRange(header: string | null | undefined): number | null {
  if (!header) return null;
  const match = header.match(/\/(\d+)$/);
  if (!match) return null;
  const total = parseInt(match[1], 10);
  return isNaN(total) || total <= 0 ? null : total;
}

/**
 * Determines whether a track still needs a Range metadata inspection.
 * Returns false if cached in IndexedDB for the exact same file size (negative + positive caching).
 */
export function needsMetadataFetch(
  track: Pick<EnrichedTrack, 'sizeBytes' | 'metadataHydrated'>,
  cachedMeta: CachedTrackMetadata | null | undefined
): boolean {
  if (cachedMeta?.hasCheckedTag) {
    if (!track.sizeBytes || !cachedMeta.sizeBytes || cachedMeta.sizeBytes === track.sizeBytes) {
      return false;
    }
  }
  return !track.metadataHydrated;
}

function formatDurationClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const hrs = Math.floor(safe / 3600);
  const mins = Math.floor((safe % 3600) / 60);
  const secs = safe % 60;
  if (hrs > 0) {
    return `${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

/**
 * Pure immutable merge of parsed audio tags or technical metadata onto a Track object.
 * Preserves streamUrl, id, and href so HTML5 audio playback is never interrupted.
 */
export function mergeTrackMetadata(
  track: EnrichedTrack,
  parsed: Partial<ParsedAudioMetadata> | CachedTrackMetadata | null | undefined,
  durationOverrideSec?: number
): EnrichedTrack {
  const nextDurationSec =
    durationOverrideSec && durationOverrideSec > 0
      ? Math.floor(durationOverrideSec)
      : parsed?.durationSeconds && parsed.durationSeconds > 0
      ? Math.floor(parsed.durationSeconds)
      : track.durationSeconds;

  const yearNum =
    typeof parsed?.year === 'number'
      ? parsed.year
      : typeof parsed?.year === 'string' && /^\d{4}/.test(parsed.year)
      ? parseInt(parsed.year.slice(0, 4), 10)
      : track.year;

  const resolvedBitrate =
    (parsed && 'bitrateKbps' in parsed ? parsed.bitrateKbps : undefined) ??
    track.bitrateKbps ??
    estimateBitrateKbps(track.sizeBytes, nextDurationSec);

  return {
    ...track,
    title: parsed?.title?.trim() || track.title,
    artist: parsed?.artist?.trim() || track.artist,
    album: parsed?.album?.trim() || track.album,
    year: yearNum,
    genre: parsed?.genre?.trim() || track.genre,
    codec: parsed?.codec || track.codec,
    sampleRateHz: parsed?.sampleRateHz || track.sampleRateHz,
    bitrateKbps: resolvedBitrate,
    replayGainDb: parsed?.replayGainDb ?? track.replayGainDb,
    durationSeconds: nextDurationSec,
    duration: nextDurationSec > 0 ? formatDurationClock(nextDurationSec) : track.duration,
    metadataHydrated: true,
  };
}

// In-flight request deduplication keyed by trackId
const inFlightRequests = new Map<string, Promise<ParsedAudioMetadata | null>>();

export interface RangeMetadataRequest {
  trackId: string;
  url: string;
  authHeader: string;
  fileName?: string;
  sizeBytes?: number;
  signal?: AbortSignal;
  initialBytes?: number;
  maxTagBytes?: number;
}

let metadataRateLimitedUntil = 0;

/**
 * Two-stage HTTP Range header metadata extractor:
 * 1) Peeks initial 64 KiB header chunk.
 * 2) If ID3v2 declares a larger tag (e.g., high-res cover art), expands up to maxTagBytes (1 MiB cap).
 * 3) If no header tags were found on an MP3, peeks only the final 128 bytes for ID3v1.
 */
export async function extractTrackMetadataFromUrl({
  trackId,
  url,
  authHeader,
  fileName,
  sizeBytes,
  signal,
  initialBytes = INITIAL_HEADER_BYTES,
  maxTagBytes = MAX_TAG_FETCH_BYTES,
}: RangeMetadataRequest): Promise<ParsedAudioMetadata | null> {
  if (Date.now() < metadataRateLimitedUntil) {
    return null;
  }

  const existing = inFlightRequests.get(trackId);
  if (existing) {
    return existing;
  }

  const run = async (): Promise<ParsedAudioMetadata | null> => {
    const cleanUrl = url.split('?')[0];
    const headers: Record<string, string> = {
      Range: `bytes=0-${initialBytes - 1}`,
    };
    if (authHeader) {
      headers.Authorization = authHeader;
    }

    const res = await fetch(cleanUrl, {
      headers,
      signal,
      cache: 'no-store',
    });

    if (res.status === 429) {
      metadataRateLimitedUntil = Date.now() + 60_000;
      return null;
    }
    if (!res.ok && res.status !== 206) {
      return null;
    }

    const totalSize = parseTotalSizeFromContentRange(res.headers.get('Content-Range')) ?? sizeBytes;
    const firstChunk = new Uint8Array(await res.arrayBuffer());
    if (firstChunk.length === 0) return null;

    // Check if an ID3v2 tag extends beyond our initial 64 KiB peek (for cover art or large comments)
    const declaredTagBytes = getID3v2TagTotalSize(firstChunk);
    let workingBuffer = firstChunk;

    if (declaredTagBytes > firstChunk.length && declaredTagBytes <= maxTagBytes && Date.now() >= metadataRateLimitedUntil) {
      const targetEnd = Math.min(declaredTagBytes, maxTagBytes) - 1;
      const expandedHeaders: Record<string, string> = {
        Range: `bytes=0-${targetEnd}`,
      };
      if (authHeader) {
        expandedHeaders.Authorization = authHeader;
      }

      const expandedRes = await fetch(cleanUrl, {
        headers: expandedHeaders,
        signal,
        cache: 'no-store',
      });
      if (expandedRes.status === 429) {
        metadataRateLimitedUntil = Date.now() + 60_000;
      } else if (expandedRes.ok || expandedRes.status === 206) {
        const expandedBytes = new Uint8Array(await expandedRes.arrayBuffer());
        if (expandedBytes.length >= firstChunk.length) {
          workingBuffer = expandedBytes;
        }
      }
    }

    let parsed = parseAudioMetadata(workingBuffer, fileName);

    // Fallback to ID3v1 128-byte tail peek ONLY when MP3 has no text tags in header
    const missingTextTags = !parsed?.title && !parsed?.artist && !parsed?.album;
    if (
      missingTextTags &&
      Date.now() >= metadataRateLimitedUntil &&
      totalSize &&
      totalSize > 256 &&
      (!fileName || /\.mp3$/i.test(fileName))
    ) {
      const tailStart = totalSize - 128;
      const tailHeaders: Record<string, string> = {
        Range: `bytes=${tailStart}-${totalSize - 1}`,
      };
      if (authHeader) {
        tailHeaders.Authorization = authHeader;
      }

      try {
        const tailRes = await fetch(cleanUrl, {
          headers: tailHeaders,
          signal,
          cache: 'no-store',
        });
        if (tailRes.status === 429) {
          metadataRateLimitedUntil = Date.now() + 60_000;
        } else if (tailRes.ok || tailRes.status === 206) {
          const tailBuf = new Uint8Array(await tailRes.arrayBuffer());
          const v1 = parseID3v1(tailBuf);
          if (v1) {
            parsed = {
              ...(parsed || {}),
              title: parsed?.title || v1.title,
              artist: parsed?.artist || v1.artist,
              album: parsed?.album || v1.album,
              year: parsed?.year || v1.year,
              codec: parsed?.codec || 'MP3',
              hasEmbeddedTag: true,
            };
          }
        }
      } catch {
        // Ignore tail fetch abort/failure
      }
    }

    return parsed;
  };

  const promise = run().finally(() => {
    inFlightRequests.delete(trackId);
  });

  inFlightRequests.set(trackId, promise);
  return promise;
}
