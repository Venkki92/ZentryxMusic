import test from 'node:test';
import assert from 'node:assert/strict';
import {
  estimateBitrateKbps,
  getID3v2TagTotalSize,
  parseAudioMetadata,
  parseFlacMetadata,
  parseID3v1,
  parseID3v2,
  parseMp4Metadata,
  parseOggMetadata,
} from './id3Parser';
import {
  mergeTrackMetadata,
  needsMetadataFetch,
  parseTotalSizeFromContentRange,
} from './metadataCollector';

function writeUtf8(str: string): number[] {
  return Array.from(new TextEncoder().encode(str));
}

function uint32BE(n: number): number[] {
  return [(n >>> 24) & 0xff, (n >>> 16) & 0xff, (n >>> 8) & 0xff, n & 0xff];
}

function uint32LE(n: number): number[] {
  return [n & 0xff, (n >>> 8) & 0xff, (n >>> 16) & 0xff, (n >>> 24) & 0xff];
}

test('id3Parser: UTF-16 BOM, TPE2 fallback, TRCK, and getID3v2TagTotalSize', () => {
  // UTF-16LE BOM text frame helper
  const makeUtf16Frame = (id: string, text: string) => {
    const utf16Bytes: number[] = [0xff, 0xfe];
    for (let i = 0; i < text.length; i++) {
      const code = text.charCodeAt(i);
      utf16Bytes.push(code & 0xff, (code >> 8) & 0xff);
    }
    const payload = [0x01, ...utf16Bytes]; // encoding 1
    return [...writeUtf8(id), ...uint32BE(payload.length), 0, 0, ...payload];
  };

  const makeUtf8Frame = (id: string, text: string) => {
    const payload = [0x03, ...writeUtf8(text)]; // encoding 3
    return [...writeUtf8(id), ...uint32BE(payload.length), 0, 0, ...payload];
  };

  const frames = [
    ...makeUtf16Frame('TIT2', 'Балкани Drive'),
    ...makeUtf8Frame('TPE2', 'Album Artist'),
    ...makeUtf8Frame('TALB', 'S203 Roadtrip'),
    ...makeUtf8Frame('TYER', '2026'),
    ...makeUtf8Frame('TRCK', '4/12'),
    ...makeUtf8Frame('TCON', '(17)Rock'),
  ];

  // Syncsafe encode frames.length
  const sz = frames.length;
  const header = [
    0x49, 0x44, 0x33, 0x03, 0x00, 0x00,
    (sz >> 21) & 0x7f,
    (sz >> 14) & 0x7f,
    (sz >> 7) & 0x7f,
    sz & 0x7f,
  ];

  const buffer = new Uint8Array([...header, ...frames]);
  assert.equal(getID3v2TagTotalSize(buffer), 10 + sz);

  const parsed = parseID3v2(buffer);
  assert.ok(parsed);
  assert.equal(parsed?.title, 'Балкани Drive');
  assert.equal(parsed?.artist, 'Album Artist');
  assert.equal(parsed?.album, 'S203 Roadtrip');
  assert.equal(parsed?.year, '2026');
  assert.equal(parsed?.trackNumber, 4);
  assert.equal(parsed?.genre, 'Rock');
  assert.equal(parsed?.codec, 'MP3');
});

test('id3Parser: parseID3v1 reads 128-byte tail tag safely', () => {
  const tag = new Uint8Array(128).fill(0);
  const enc = new TextEncoder();
  tag.set(enc.encode('TAG'), 0);
  tag.set(enc.encode('Midnight Autobahn'), 3);
  tag.set(enc.encode('Zentryx'), 33);
  tag.set(enc.encode('Night Mix'), 63);
  tag.set(enc.encode('2025'), 93);

  const parsed = parseID3v1(tag);
  assert.ok(parsed);
  assert.equal(parsed?.title, 'Midnight Autobahn');
  assert.equal(parsed?.artist, 'Zentryx');
  assert.equal(parsed?.album, 'Night Mix');
  assert.equal(parsed?.year, '2025');
});

test('id3Parser: parseFlacMetadata extracts STREAMINFO sampleRate/duration and Vorbis comments', () => {
  // Build STREAMINFO block (34 bytes): 44100 Hz (0x0AC44), 2 ch ((1)<<1), 441000 samples (10s)
  const streamInfo = new Uint8Array(34).fill(0);
  // Byte 10..12: 44100 << 4 = 0x0AC440 -> 0x0A, 0xC4, 0x42 (ch=2 => (2-1)<<1 = 2)
  streamInfo[10] = 0x0a;
  streamInfo[11] = 0xc4;
  streamInfo[12] = 0x42;
  // Total samples = 441000
  streamInfo.set(uint32BE(441000), 14);

  const makeComment = (k: string, v: string) => {
    const str = writeUtf8(`${k}=${v}`);
    return [...uint32LE(str.length), ...str];
  };
  const vendor = writeUtf8('libFLAC');
  const commentsPayload = new Uint8Array([
    ...uint32LE(vendor.length),
    ...vendor,
    ...uint32LE(3),
    ...makeComment('TITLE', 'Lossless Drive'),
    ...makeComment('ARTIST', 'Stuttgart Audio'),
    ...makeComment('ALBUM', 'Hi-Res FLAC'),
  ]);

  const block0Header = [0x00, 0x00, 0x00, streamInfo.length]; // type 0, not last
  const cl = commentsPayload.length;
  const block4Header = [0x84, (cl >> 16) & 0xff, (cl >> 8) & 0xff, cl & 0xff]; // type 4, isLast=1

  const flacBytes = new Uint8Array([
    ...writeUtf8('fLaC'),
    ...block0Header,
    ...streamInfo,
    ...block4Header,
    ...commentsPayload,
  ]);

  const parsed = parseAudioMetadata(flacBytes);
  assert.ok(parsed);
  assert.equal(parsed?.codec, 'FLAC');
  assert.equal(parsed?.sampleRateHz, 44100);
  assert.equal(parsed?.channels, 2);
  assert.equal(parsed?.durationSeconds, 10);
  assert.equal(parsed?.title, 'Lossless Drive');
  assert.equal(parsed?.artist, 'Stuttgart Audio');
  assert.equal(parsed?.album, 'Hi-Res FLAC');
  assert.ok(parseFlacMetadata(flacBytes));
});

test('id3Parser: parseOggMetadata extracts OpusTags Vorbis comments', () => {
  const vendor = writeUtf8('libopus');
  const comment = writeUtf8('TITLE=Opus Stream');
  const opusPayload = new Uint8Array([
    ...writeUtf8('OggS'),
    ...new Array(24).fill(0),
    ...writeUtf8('OpusTags'),
    ...uint32LE(vendor.length),
    ...vendor,
    ...uint32LE(1),
    ...uint32LE(comment.length),
    ...comment,
  ]);

  const parsed = parseOggMetadata(opusPayload);
  assert.ok(parsed);
  assert.equal(parsed?.codec, 'OPUS');
  assert.equal(parsed?.title, 'Opus Stream');
});

test('id3Parser: parseMp4Metadata extracts ilst atoms inside moov/udta/meta', () => {
  const makeAtom = (nameBytes: number[], content: number[]) => [
    ...uint32BE(8 + content.length),
    ...nameBytes,
    ...content,
  ];
  const makeDataAtom = (val: string) =>
    makeAtom(writeUtf8('data'), [0, 0, 0, 1, 0, 0, 0, 0, ...writeUtf8(val)]);

  const ilst = makeAtom(writeUtf8('ilst'), [
    ...makeAtom([0xa9, 0x6e, 0x61, 0x6d], makeDataAtom('AAC Track')),
    ...makeAtom([0xa9, 0x41, 0x52, 0x54], makeDataAtom('M4A Artist')),
  ]);
  const meta = makeAtom(writeUtf8('meta'), [0, 0, 0, 0, ...ilst]);
  const udta = makeAtom(writeUtf8('udta'), meta);
  const moov = makeAtom(writeUtf8('moov'), udta);
  const ftyp = makeAtom(writeUtf8('ftyp'), writeUtf8('M4A \x00\x00\x00\x00M4A '));

  const parsed = parseMp4Metadata(new Uint8Array([...ftyp, ...moov]));
  assert.ok(parsed);
  assert.equal(parsed?.codec, 'M4A');
  assert.equal(parsed?.title, 'AAC Track');
  assert.equal(parsed?.artist, 'M4A Artist');
});

test('metadataCollector: estimateBitrateKbps, Content-Range, needsMetadataFetch & mergeTrackMetadata', () => {
  assert.equal(estimateBitrateKbps(4_000_000, 100), 320);
  assert.equal(parseTotalSizeFromContentRange('bytes 0-65535/34200069'), 34200069);
  assert.equal(parseTotalSizeFromContentRange('bytes 0-100/*'), null);

  const baseTrack = {
    id: 't1',
    title: '01_track',
    artist: 'Unknown Artist',
    album: 'Music',
    duration: '...',
    durationSeconds: 0,
    sizeBytes: 4_000_000,
    sizeFormatted: '3.8 MB',
    href: '/Music/01_track.mp3',
    streamUrl: '/api/audio-stream?path=01_track.mp3',
    fileName: '01_track.mp3',
    folderPath: 'Music',
  };

  assert.equal(needsMetadataFetch(baseTrack, null), true);
  assert.equal(
    needsMetadataFetch(baseTrack, {
      id: 't1',
      sizeBytes: 4_000_000,
      hasCheckedTag: true,
      updatedAt: Date.now(),
    }),
    false
  );

  const merged = mergeTrackMetadata(
    baseTrack,
    { title: 'Real Track Name', artist: 'Real Artist', codec: 'MP3', sampleRateHz: 44100 },
    100
  );
  assert.equal(merged.title, 'Real Track Name');
  assert.equal(merged.artist, 'Real Artist');
  assert.equal(merged.duration, '1:40');
  assert.equal(merged.bitrateKbps, 320);
  assert.equal(merged.streamUrl, baseTrack.streamUrl);
  assert.equal(merged.metadataHydrated, true);
});
