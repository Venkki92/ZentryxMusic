export interface ParsedCoverImage {
  mimeType: string;
  data: Uint8Array;
}

export interface ParsedID3 {
  title?: string;
  artist?: string;
  album?: string;
  year?: string;
  trackNumber?: number;
  genre?: string;
  codec?: string;
  bitrateKbps?: number;
  sampleRateHz?: number;
  channels?: number;
  durationSeconds?: number;
  replayGainDb?: number;
  tagTotalBytes?: number;
  hasEmbeddedTag?: boolean;
  coverImage?: ParsedCoverImage;
}

/**
 * Parses ReplayGain dB string (e.g. "-6.42 dB", "+1.8 dB") and clamps to safe bounds [-24, 18].
 */
export function parseReplayGainValue(raw: string | undefined): number | undefined {
  if (!raw) return undefined;
  const match = raw.trim().match(/^([+-]?\d+(?:\.\d+)?)\s*(?:db)?$/i);
  if (!match) return undefined;
  const val = parseFloat(match[1]);
  if (!Number.isFinite(val)) return undefined;
  return Math.max(-24, Math.min(18, Math.round(val * 100) / 100));
}

export type ParsedAudioMetadata = ParsedID3;

export function parseSyncSafeInt(bytes: Uint8Array, offset: number): number {
  return (
    ((bytes[offset] & 0x7f) << 21) |
    ((bytes[offset + 1] & 0x7f) << 14) |
    ((bytes[offset + 2] & 0x7f) << 7) |
    (bytes[offset + 3] & 0x7f)
  );
}

export function parseStandardInt(bytes: Uint8Array, offset: number): number {
  return (
    ((bytes[offset] << 24) |
      (bytes[offset + 1] << 16) |
      (bytes[offset + 2] << 8) |
      bytes[offset + 3]) >>>
    0
  );
}

function parseUint32LE(bytes: Uint8Array, offset: number): number {
  return (
    (bytes[offset] |
      (bytes[offset + 1] << 8) |
      (bytes[offset + 2] << 16) |
      (bytes[offset + 3] << 24)) >>>
    0
  );
}

function parseUint24BE(bytes: Uint8Array, offset: number): number {
  return ((bytes[offset] << 16) | (bytes[offset + 1] << 8) | bytes[offset + 2]) >>> 0;
}

function readAscii(bytes: Uint8Array, start: number, end: number): string {
  let out = '';
  const safeEnd = Math.min(end, bytes.length);
  for (let i = start; i < safeEnd; i++) {
    out += String.fromCharCode(bytes[i]);
  }
  return out;
}

function decodeString(data: Uint8Array, encoding: number): string {
  if (data.length === 0) return '';
  try {
    if (encoding === 0) {
      return new TextDecoder('latin1').decode(data).replace(/\0+$/, '');
    }
    if (encoding === 1) {
      // UTF-16 with BOM
      if (data.length >= 2 && data[0] === 0xfe && data[1] === 0xff) {
        return new TextDecoder('utf-16be').decode(data.subarray(2)).replace(/\0+$/, '');
      }
      if (data.length >= 2 && data[0] === 0xff && data[1] === 0xfe) {
        return new TextDecoder('utf-16le').decode(data.subarray(2)).replace(/\0+$/, '');
      }
      return new TextDecoder('utf-16le').decode(data).replace(/\0+$/, '');
    }
    if (encoding === 2) {
      return new TextDecoder('utf-16be').decode(data).replace(/\0+$/, '');
    }
    return new TextDecoder('utf-8').decode(data).replace(/\0+$/, '');
  } catch {
    return '';
  }
}

/**
 * Reads the total byte length of an ID3v2 header + tag payload, or 0 if not ID3v2.
 */
export function getID3v2TagTotalSize(buffer: ArrayBuffer | Uint8Array): number {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  if (bytes.length < 10) return 0;
  if (bytes[0] !== 0x49 || bytes[1] !== 0x44 || bytes[2] !== 0x33) return 0;
  const majorVersion = bytes[3];
  if (majorVersion !== 2 && majorVersion !== 3 && majorVersion !== 4) return 0;
  return 10 + parseSyncSafeInt(bytes, 6);
}

/**
 * Estimates kbps bitrate cleanly from file byte size and duration.
 */
export function estimateBitrateKbps(sizeBytes?: number, durationSeconds?: number): number | undefined {
  if (!sizeBytes || !durationSeconds || sizeBytes <= 0 || durationSeconds <= 0) {
    return undefined;
  }
  const kbps = Math.round((sizeBytes * 8) / durationSeconds / 1000);
  return kbps > 0 && kbps < 15000 ? kbps : undefined;
}

export function parseID3v2(buffer: ArrayBuffer | Uint8Array): ParsedID3 | null {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  if (bytes.length < 10) return null;

  // Header check: "ID3"
  if (bytes[0] !== 0x49 || bytes[1] !== 0x44 || bytes[2] !== 0x33) {
    return null;
  }

  const majorVersion = bytes[3];
  if (majorVersion !== 3 && majorVersion !== 4) {
    return null;
  }

  const headerFlags = bytes[5];
  const tagSize = parseSyncSafeInt(bytes, 6);
  const tagTotalBytes = 10 + tagSize;
  const endOffset = Math.min(tagTotalBytes, bytes.length);

  let offset = 10;
  // Skip ID3v2 extended header if present (bit 6 = 0x40)
  if ((headerFlags & 0x40) !== 0 && offset + 4 <= endOffset) {
    const extSize =
      majorVersion === 4 ? parseSyncSafeInt(bytes, offset) : 4 + parseStandardInt(bytes, offset);
    if (extSize > 0 && offset + extSize <= endOffset) {
      offset += extSize;
    }
  }

  const result: ParsedID3 = {
    codec: 'MP3',
    tagTotalBytes,
    hasEmbeddedTag: true,
  };
  let albumArtistFallback = '';

  while (offset + 10 <= endOffset) {
    if (bytes[offset] === 0) break;

    const frameId = readAscii(bytes, offset, offset + 4);
    const frameSize =
      majorVersion === 4
        ? parseSyncSafeInt(bytes, offset + 4)
        : parseStandardInt(bytes, offset + 4);

    if (frameSize <= 0 || offset + 10 + frameSize > endOffset) {
      break;
    }

    const frameData = bytes.subarray(offset + 10, offset + 10 + frameSize);

    if (frameId === 'TIT2') {
      const val = decodeString(frameData.subarray(1), frameData[0] || 0).trim();
      if (val) result.title = val;
    } else if (frameId === 'TPE1') {
      const val = decodeString(frameData.subarray(1), frameData[0] || 0).trim();
      if (val) result.artist = val;
    } else if (frameId === 'TPE2') {
      albumArtistFallback = decodeString(frameData.subarray(1), frameData[0] || 0).trim();
    } else if (frameId === 'TALB') {
      const val = decodeString(frameData.subarray(1), frameData[0] || 0).trim();
      if (val) result.album = val;
    } else if (frameId === 'TYER' || frameId === 'TDRC') {
      const val = decodeString(frameData.subarray(1), frameData[0] || 0).trim().slice(0, 4);
      if (val) result.year = val;
    } else if (frameId === 'TRCK') {
      const raw = decodeString(frameData.subarray(1), frameData[0] || 0).trim();
      const num = parseInt(raw.split('/')[0], 10);
      if (!isNaN(num) && num > 0) result.trackNumber = num;
    } else if (frameId === 'TCON') {
      const val = decodeString(frameData.subarray(1), frameData[0] || 0).trim();
      if (val) result.genre = val.replace(/^\(\d+\)/, '').trim() || val;
    } else if (frameId === 'TXXX' && result.replayGainDb === undefined) {
      try {
        const encoding = frameData[0] || 0;
        let descEnd = 1;
        if (encoding === 1 || encoding === 2) {
          while (
            descEnd + 1 < frameData.length &&
            !(frameData[descEnd] === 0 && frameData[descEnd + 1] === 0)
          ) {
            descEnd += 2;
          }
          const desc = decodeString(frameData.subarray(1, descEnd), encoding).trim().toUpperCase();
          const val = decodeString(frameData.subarray(descEnd + 2), encoding).trim();
          if (desc === 'REPLAYGAIN_TRACK_GAIN') {
            result.replayGainDb = parseReplayGainValue(val);
          }
        } else {
          while (descEnd < frameData.length && frameData[descEnd] !== 0) {
            descEnd++;
          }
          const desc = decodeString(frameData.subarray(1, descEnd), encoding).trim().toUpperCase();
          const val = decodeString(frameData.subarray(descEnd + 1), encoding).trim();
          if (desc === 'REPLAYGAIN_TRACK_GAIN') {
            result.replayGainDb = parseReplayGainValue(val);
          }
        }
      } catch {}
    } else if (frameId === 'APIC' && !result.coverImage) {
      try {
        const encoding = frameData[0] || 0;
        let mimeEnd = 1;
        while (mimeEnd < frameData.length && frameData[mimeEnd] !== 0) {
          mimeEnd++;
        }
        let mimeType = new TextDecoder('latin1')
          .decode(frameData.subarray(1, mimeEnd))
          .toLowerCase()
          .trim();
        if (!mimeType.includes('/')) {
          mimeType = mimeType === 'png' ? 'image/png' : 'image/jpeg';
        }

        let descOffset = mimeEnd + 2; // skip null terminator + picture type byte

        if (encoding === 1 || encoding === 2) {
          while (
            descOffset + 1 < frameData.length &&
            !(frameData[descOffset] === 0 && frameData[descOffset + 1] === 0)
          ) {
            descOffset += 2;
          }
          descOffset += 2;
        } else {
          while (descOffset < frameData.length && frameData[descOffset] !== 0) {
            descOffset++;
          }
          descOffset += 1;
        }

        if (descOffset < frameData.length) {
          const imgData = frameData.subarray(descOffset);
          if (imgData.length > 32) {
            result.coverImage = {
              mimeType: mimeType || 'image/jpeg',
              data: new Uint8Array(imgData),
            };
          }
        }
      } catch {
        // Ignore malformed APIC frame
      }
    }

    offset += 10 + frameSize;
  }

  if (!result.artist && albumArtistFallback) {
    result.artist = albumArtistFallback;
  }

  return result;
}

/**
 * Parses a legacy 128-byte ID3v1 tag from a tail buffer or exact 128-byte slice.
 */
export function parseID3v1(buffer: ArrayBuffer | Uint8Array): ParsedID3 | null {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  if (bytes.length < 128) return null;

  const start = bytes.length - 128;
  if (bytes[start] !== 0x54 || bytes[start + 1] !== 0x41 || bytes[start + 2] !== 0x47) {
    return null;
  }

  const decoder = new TextDecoder('latin1');
  const cleanField = (s: number, e: number) =>
    decoder.decode(bytes.subarray(start + s, start + e)).replace(/\0+$/, '').trim();

  const title = cleanField(3, 33);
  const artist = cleanField(33, 63);
  const album = cleanField(63, 93);
  const year = cleanField(93, 97);

  if (!title && !artist && !album) return null;

  return {
    title: title || undefined,
    artist: artist || undefined,
    album: album || undefined,
    year: /^\d{4}$/.test(year) ? year : undefined,
    codec: 'MP3',
    hasEmbeddedTag: true,
  };
}

function applyVorbisCommentPairs(
  bytes: Uint8Array,
  startOffset: number,
  endOffset: number,
  result: ParsedID3
): void {
  let offset = startOffset;
  if (offset + 4 > endOffset) return;

  const vendorLen = parseUint32LE(bytes, offset);
  offset += 4 + vendorLen;
  if (offset + 4 > endOffset) return;

  const commentCount = parseUint32LE(bytes, offset);
  offset += 4;

  const utf8 = new TextDecoder('utf-8');
  let albumArtist = '';

  for (let i = 0; i < commentCount && offset + 4 <= endOffset; i++) {
    const len = parseUint32LE(bytes, offset);
    offset += 4;
    if (offset + len > endOffset) break;

    const entry = utf8.decode(bytes.subarray(offset, offset + len));
    offset += len;

    const eqIdx = entry.indexOf('=');
    if (eqIdx <= 0) continue;

    const key = entry.slice(0, eqIdx).toUpperCase();
    const val = entry.slice(eqIdx + 1).trim();
    if (!val) continue;

    if (key === 'TITLE' && !result.title) result.title = val;
    else if (key === 'ARTIST' && !result.artist) result.artist = val;
    else if (key === 'ALBUMARTIST' && !albumArtist) albumArtist = val;
    else if (key === 'ALBUM' && !result.album) result.album = val;
    else if ((key === 'DATE' || key === 'YEAR') && !result.year) result.year = val.slice(0, 4);
    else if (key === 'GENRE' && !result.genre) result.genre = val;
    else if (key === 'REPLAYGAIN_TRACK_GAIN' && result.replayGainDb === undefined) {
      result.replayGainDb = parseReplayGainValue(val);
    }
    else if (key === 'TRACKNUMBER' && !result.trackNumber) {
      const n = parseInt(val.split('/')[0], 10);
      if (!isNaN(n) && n > 0) result.trackNumber = n;
    }
  }

  if (!result.artist && albumArtist) {
    result.artist = albumArtist;
  }
}

/**
 * Parses FLAC STREAMINFO, VORBIS_COMMENT, and PICTURE metadata blocks from the file header chunk.
 */
export function parseFlacMetadata(buffer: ArrayBuffer | Uint8Array): ParsedID3 | null {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  if (bytes.length < 8) return null;
  if (readAscii(bytes, 0, 4) !== 'fLaC') return null;

  const result: ParsedID3 = {
    codec: 'FLAC',
    hasEmbeddedTag: true,
  };

  let offset = 4;
  while (offset + 4 <= bytes.length) {
    const header = bytes[offset];
    const isLast = (header & 0x80) !== 0;
    const blockType = header & 0x7f;
    const blockLength = parseUint24BE(bytes, offset + 1);
    const blockStart = offset + 4;
    const blockEnd = blockStart + blockLength;

    if (blockEnd > bytes.length) {
      // Partial chunk—record tagTotalBytes so range fetcher can inspect if needed
      result.tagTotalBytes = Math.max(result.tagTotalBytes || 0, blockEnd);
      break;
    }

    if (blockType === 0 && blockLength >= 18) {
      // STREAMINFO
      const b10 = bytes[blockStart + 10];
      const b11 = bytes[blockStart + 11];
      const b12 = bytes[blockStart + 12];
      const b13 = bytes[blockStart + 13];
      const sampleRate = ((b10 << 12) | (b11 << 4) | (b12 >> 4)) >>> 0;
      const channels = ((b12 >> 1) & 0x07) + 1;
      const highSampleBits = b13 & 0x0f;
      const lowSampleBits = parseStandardInt(bytes, blockStart + 14);
      const totalSamples = highSampleBits * 4294967296 + lowSampleBits;

      if (sampleRate > 0) {
        result.sampleRateHz = sampleRate;
        result.channels = channels;
        if (totalSamples > 0) {
          result.durationSeconds = Math.round(totalSamples / sampleRate);
        }
      }
    } else if (blockType === 4) {
      applyVorbisCommentPairs(bytes, blockStart, blockEnd, result);
    } else if (blockType === 6 && !result.coverImage && blockLength > 32) {
      // PICTURE block
      try {
        let p = blockStart + 4; // skip picture type
        const mimeLen = parseStandardInt(bytes, p);
        p += 4;
        const mimeType = readAscii(bytes, p, p + mimeLen).toLowerCase() || 'image/jpeg';
        p += mimeLen;
        const descLen = parseStandardInt(bytes, p);
        p += 4 + descLen + 16; // skip description + width/height/depth/colors
        if (p + 4 <= blockEnd) {
          const imgLen = parseStandardInt(bytes, p);
          p += 4;
          if (imgLen > 32 && p + imgLen <= blockEnd) {
            result.coverImage = {
              mimeType,
              data: new Uint8Array(bytes.subarray(p, p + imgLen)),
            };
          }
        }
      } catch {}
    }

    offset = blockEnd;
    result.tagTotalBytes = offset;
    if (isLast) break;
  }

  return result;
}

/**
 * Parses Vorbis/Opus comment metadata from the initial OggS container pages.
 */
export function parseOggMetadata(buffer: ArrayBuffer | Uint8Array): ParsedID3 | null {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  if (bytes.length < 32 || readAscii(bytes, 0, 4) !== 'OggS') return null;

  const result: ParsedID3 = {
    codec: 'OGG',
    hasEmbeddedTag: true,
  };

  // Search for "\x03vorbis" or "OpusTags" in the first ~64 KB
  const limit = Math.min(bytes.length - 12, 65536);
  for (let i = 0; i < limit; i++) {
    if (
      bytes[i] === 0x03 &&
      bytes[i + 1] === 0x76 &&
      bytes[i + 2] === 0x6f &&
      bytes[i + 3] === 0x72 &&
      bytes[i + 4] === 0x62 &&
      bytes[i + 5] === 0x69 &&
      bytes[i + 6] === 0x73
    ) {
      result.codec = 'VORBIS';
      applyVorbisCommentPairs(bytes, i + 7, bytes.length, result);
      return result;
    }

    if (
      bytes[i] === 0x4f &&
      bytes[i + 1] === 0x70 &&
      bytes[i + 2] === 0x75 &&
      bytes[i + 3] === 0x73 &&
      bytes[i + 4] === 0x54 &&
      bytes[i + 5] === 0x61 &&
      bytes[i + 6] === 0x67 &&
      bytes[i + 7] === 0x73
    ) {
      result.codec = 'OPUS';
      applyVorbisCommentPairs(bytes, i + 8, bytes.length, result);
      return result;
    }
  }

  return result;
}

/**
 * Parses MP4 / M4A iTunes metadata atoms (moov -> udta -> meta -> ilst) when present in header chunk.
 */
export function parseMp4Metadata(buffer: ArrayBuffer | Uint8Array): ParsedID3 | null {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  if (bytes.length < 16 || readAscii(bytes, 4, 8) !== 'ftyp') return null;

  const result: ParsedID3 = {
    codec: 'M4A',
    hasEmbeddedTag: false,
  };
  const utf8 = new TextDecoder('utf-8');

  const findAtom = (start: number, end: number, target: string): [number, number] | null => {
    let off = start;
    while (off + 8 <= end) {
      const size = parseStandardInt(bytes, off);
      if (size < 8 || off + size > end) break;
      const name = readAscii(bytes, off + 4, off + 8);
      if (name === target) return [off + 8, off + size];
      off += size;
    }
    return null;
  };

  const moov = findAtom(0, bytes.length, 'moov');
  if (!moov) return result;

  const mvhd = findAtom(moov[0], moov[1], 'mvhd');
  if (mvhd && mvhd[0] + 20 <= mvhd[1]) {
    const version = bytes[mvhd[0]];
    if (version === 0) {
      const timescale = parseStandardInt(bytes, mvhd[0] + 12);
      const durUnits = parseStandardInt(bytes, mvhd[0] + 16);
      if (timescale > 0 && durUnits > 0) {
        result.durationSeconds = Math.round(durUnits / timescale);
      }
    }
  }

  const udta = findAtom(moov[0], moov[1], 'udta');
  if (!udta) return result;
  const meta = findAtom(udta[0], udta[1], 'meta');
  if (!meta || meta[0] + 4 > meta[1]) return result;
  const ilst = findAtom(meta[0] + 4, meta[1], 'ilst');
  if (!ilst) return result;

  result.hasEmbeddedTag = true;
  let off = ilst[0];
  let albumArtist = '';

  while (off + 8 <= ilst[1]) {
    const size = parseStandardInt(bytes, off);
    if (size < 8 || off + size > ilst[1]) break;
    const atomType = readAscii(bytes, off + 4, off + 8);
    const dataAtom = findAtom(off + 8, off + size, 'data');

    if (dataAtom && dataAtom[0] + 8 <= dataAtom[1]) {
      const dataType = parseStandardInt(bytes, dataAtom[0]);
      const payload = bytes.subarray(dataAtom[0] + 8, dataAtom[1]);

      if (atomType === '\xa9nam') result.title = utf8.decode(payload).trim();
      else if (atomType === '\xa9ART') result.artist = utf8.decode(payload).trim();
      else if (atomType === 'aART') albumArtist = utf8.decode(payload).trim();
      else if (atomType === '\xa9alb') result.album = utf8.decode(payload).trim();
      else if (atomType === '\xa9day') result.year = utf8.decode(payload).trim().slice(0, 4);
      else if (atomType === '\xa9gen') result.genre = utf8.decode(payload).trim();
      else if (atomType === 'covr' && !result.coverImage && payload.length > 32) {
        result.coverImage = {
          mimeType: dataType === 14 ? 'image/png' : 'image/jpeg',
          data: new Uint8Array(payload),
        };
      }
    }
    off += size;
  }

  if (!result.artist && albumArtist) {
    result.artist = albumArtist;
  }

  return result;
}

/**
 * Unified format-aware metadata parser supporting MP3 (ID3v2 / ID3v1), FLAC, OGG/Opus, and MP4/M4A.
 */
export function parseAudioMetadata(
  buffer: ArrayBuffer | Uint8Array,
  fileNameOrMime?: string
): ParsedID3 | null {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  if (bytes.length < 4) return null;

  if (bytes[0] === 0x49 && bytes[1] === 0x44 && bytes[2] === 0x33) {
    return parseID3v2(bytes);
  }
  if (readAscii(bytes, 0, 4) === 'fLaC') {
    return parseFlacMetadata(bytes);
  }
  if (readAscii(bytes, 0, 4) === 'OggS') {
    return parseOggMetadata(bytes);
  }
  if (bytes.length >= 8 && readAscii(bytes, 4, 8) === 'ftyp') {
    return parseMp4Metadata(bytes);
  }

  // Fallback check for ID3v1 tail if buffer ends with TAG...
  const v1 = parseID3v1(bytes);
  if (v1) return v1;

  if (fileNameOrMime) {
    const lower = fileNameOrMime.toLowerCase();
    if (lower.endsWith('.wav') || lower.includes('wav')) return { codec: 'WAV', hasEmbeddedTag: false };
    if (lower.endsWith('.mp3') || lower.includes('mpeg')) return { codec: 'MP3', hasEmbeddedTag: false };
    if (lower.endsWith('.flac') || lower.includes('flac')) return { codec: 'FLAC', hasEmbeddedTag: false };
    if (lower.endsWith('.m4a') || lower.endsWith('.aac') || lower.includes('mp4')) return { codec: 'M4A', hasEmbeddedTag: false };
  }

  return null;
}
