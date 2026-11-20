import test from 'node:test';
import assert from 'node:assert/strict';
import {
  serializeResumeSession,
  parseResumeSession,
  calculateSoftStartGain,
  isDriverSafeLockActive,
  calculateDimmerOverlayOpacity,
  DEFAULT_SWC_BINDINGS,
  resolveSteeringAction,
  replayGainDbToMultiplier,
  calculateCrossfadeGains,
  selectOfflineCacheEvictions,
  recordTrackPlayStat,
  buildSmartMixTracks,
  resolveTrackGenre,
  sortAndShuffleTracksByGenre,
  summarizeMixGenres,
} from './carConvenience';
import { parseReplayGainValue, parseID3v2, parseFlacMetadata } from './id3Parser';

test('carConvenience: serializeResumeSession & parseResumeSession round-trip safely', () => {
  const raw = serializeResumeSession({
    trackId: 't-42',
    positionSeconds: 127.8,
    queueIds: ['t-41', 't-42', 't-43'],
    shuffle: true,
    repeatMode: 'ALL',
    wasPlaying: true,
    savedAt: 1700000000000,
  });

  const parsed = parseResumeSession(raw);
  assert.ok(parsed);
  assert.equal(parsed.trackId, 't-42');
  assert.equal(parsed.positionSeconds, 127);
  assert.deepEqual(parsed.queueIds, ['t-41', 't-42', 't-43']);
  assert.equal(parsed.shuffle, true);
  assert.equal(parsed.repeatMode, 'ALL');
  assert.equal(parsed.wasPlaying, true);
  assert.equal(parseResumeSession('{invalid json'), null);
});

test('carConvenience: calculateSoftStartGain ramps smoothly from 0.12 to 1.0', () => {
  assert.equal(calculateSoftStartGain(0, 1500), 0.12);
  const mid = calculateSoftStartGain(750, 1500);
  assert.ok(mid > 0.5 && mid < 0.9);
  assert.equal(calculateSoftStartGain(2000, 1500), 1);
});

test('carConvenience: isDriverSafeLockActive enforces speed threshold only when enabled', () => {
  assert.equal(isDriverSafeLockActive(false, 80, 10), false);
  assert.equal(isDriverSafeLockActive(true, 5, 10), false);
  assert.equal(isDriverSafeLockActive(true, 10, 10), true);
  assert.equal(isDriverSafeLockActive(true, 65, 10), true);
});

test('carConvenience: calculateDimmerOverlayOpacity clamps brightness bounds', () => {
  assert.equal(calculateDimmerOverlayOpacity(100), 0);
  assert.equal(calculateDimmerOverlayOpacity(60), 0.4);
  assert.equal(calculateDimmerOverlayOpacity(10), 0.65);
});

test('carConvenience: resolveSteeringAction matches custom learned CANBUS keys', () => {
  const custom = { ...DEFAULT_SWC_BINDINGS, next: 'F8', visualizer: 'KeyB' };
  assert.equal(resolveSteeringAction('F8', 'F8', custom), 'next');
  assert.equal(resolveSteeringAction('b', 'KeyB', custom), 'visualizer');
  assert.equal(resolveSteeringAction('z', 'KeyZ', custom), null);
});

test('carConvenience: replayGainDbToMultiplier & calculateCrossfadeGains', () => {
  assert.equal(replayGainDbToMultiplier(-6.02, false), 1.0);
  const attenuated = replayGainDbToMultiplier(-6.02, true);
  assert.ok(attenuated > 0.49 && attenuated < 0.51);
  assert.equal(replayGainDbToMultiplier(-50, true), 0.18);

  const start = calculateCrossfadeGains(0);
  assert.ok(start.outgoingGain > 0.99 && start.incomingGain < 0.01);
  const mid = calculateCrossfadeGains(0.5);
  assert.ok(Math.abs(mid.outgoingGain - mid.incomingGain) < 0.001);
  assert.ok(mid.outgoingGain > 0.7);
});

test('carConvenience: selectOfflineCacheEvictions preserves favorites and upcoming queue', () => {
  const evictions = selectOfflineCacheEvictions(
    [
      { id: 'fav', sizeBytes: 400, downloadedAt: 10 },
      { id: 'up-next', sizeBytes: 400, downloadedAt: 20 },
      { id: 'old-1', sizeBytes: 300, downloadedAt: 30 },
      { id: 'old-2', sizeBytes: 300, downloadedAt: 40 },
    ],
    new Set(['fav']),
    new Set(['up-next']),
    1000
  );

  assert.deepEqual(evictions, ['old-1', 'old-2']);
});

test('carConvenience: recordTrackPlayStat and buildSmartMixTracks build accurate mixes', () => {
  let stats: Record<string, { playCount: number; lastPlayedAt: number }> = {};
  stats = recordTrackPlayStat(stats, 'b', 100);
  stats = recordTrackPlayStat(stats, 'a', 200);
  stats = recordTrackPlayStat(stats, 'a', 300);

  const library = [
    { id: 'a', artist: 'Daft Punk', title: 'Voyager' },
    { id: 'b', artist: 'Daft Punk', title: 'Veridis Quo' },
    { id: 'c', artist: 'Kiasmos', title: 'Blurred' },
  ];

  const recent = buildSmartMixTracks(library, 'recent', stats, new Set(), 5);
  assert.deepEqual(recent.map((t) => t.id), ['a', 'b']);

  const most = buildSmartMixTracks(library, 'most_played', stats, new Set(), 5);
  assert.deepEqual(most.map((t) => t.id), ['a', 'b']);

  const cruising = buildSmartMixTracks(library, 'cruising', stats, new Set(['c']), 5);
  assert.equal(cruising.length, 3);
  // First two tracks should avoid clustering same artist consecutively when Kiasmos ('c') is present
  assert.notEqual(cruising[0].artist, cruising[1].artist);
});

test('id3Parser: parses REPLAYGAIN_TRACK_GAIN from ID3v2 TXXX and FLAC Vorbis comments', () => {
  assert.equal(parseReplayGainValue('-6.42 dB'), -6.42);
  assert.equal(parseReplayGainValue('+2.1 dB'), 2.1);

  // Build minimal ID3v2.3 with TXXX frame containing "REPLAYGAIN_TRACK_GAIN\0-5.25 dB"
  const enc = new TextEncoder();
  const payload = enc.encode('\x00REPLAYGAIN_TRACK_GAIN\x00-5.25 dB');
  const frame = new Uint8Array(10 + payload.length);
  frame.set(enc.encode('TXXX'), 0);
  frame[7] = payload.length;
  frame.set(payload, 10);

  const id3 = new Uint8Array(10 + frame.length);
  id3.set(enc.encode('ID3'), 0);
  id3[3] = 3;
  id3[9] = frame.length;
  id3.set(frame, 10);

  const parsedId3 = parseID3v2(id3);
  assert.equal(parsedId3?.replayGainDb, -5.25);

  // Build minimal FLAC VORBIS_COMMENT block with REPLAYGAIN_TRACK_GAIN=-3.8 dB
  const commentStr = 'REPLAYGAIN_TRACK_GAIN=-3.8 dB';
  const commentBytes = enc.encode(commentStr);
  const vorbisBlock = new Uint8Array(8 + 4 + commentBytes.length);
  vorbisBlock[0] = 0; // vendor len 0
  vorbisBlock[4] = 1; // 1 comment
  vorbisBlock[8] = commentBytes.length;
  vorbisBlock.set(commentBytes, 12);

  const flac = new Uint8Array(4 + 4 + vorbisBlock.length);
  flac.set(enc.encode('fLaC'), 0);
  flac[4] = 0x80 | 4; // last block, type 4 (VORBIS_COMMENT)
  flac[7] = vorbisBlock.length;
  flac.set(vorbisBlock, 8);

  const parsedFlac = parseFlacMetadata(flac);
  assert.equal(parsedFlac?.replayGainDb, -3.8);
});

import {
  DEFAULT_DRIVER_PROFILES,
  sanitizeDriverProfiles,
  buildRoadtripOfflinePlan,
  DEFAULT_SWC_LONG_PRESS_BINDINGS,
  sanitizeSteeringLongPressBindings,
} from './carConvenience';

test('carConvenience: Driver Profiles sanitize cleanly and expose 3 default slots', () => {
  assert.equal(DEFAULT_DRIVER_PROFILES.length, 3);
  const sanitized = sanitizeDriverProfiles([
    {
      id: 'custom_1',
      name: '  Autobahn  ',
      accentKey: 'amber',
      nightBrightness: 20,
      surroundMode: 'concert_hall',
      subTrimDb: 99,
    },
  ]);
  assert.equal(sanitized.length, 1);
  assert.equal(sanitized[0].name, 'Autobahn');
  assert.equal(sanitized[0].accentKey, 'AMBER');
  assert.equal(sanitized[0].nightBrightness, 35);
  assert.equal(sanitized[0].surroundMode, 'concert_hall');
  assert.equal(sanitized[0].subTrimDb, 6);
});

test('carConvenience: Offline Roadtrip Budget Planner sizes tracks to trip hours and counts missing offline songs', () => {
  const tracks = Array.from({ length: 30 }, (_, idx) => ({
    id: `t-${idx}`,
    duration: 240, // 4 min each -> 15 tracks = 60 min (1 hour)
    size: 8 * 1024 * 1024, // 8 MB each
    artist: `Artist ${idx % 5}`,
  }));
  const favs = new Set(['t-0', 't-1', 't-2']);
  const offline = new Set(['t-0', 't-1']);

  const plan1h = buildRoadtripOfflinePlan(tracks, 1, 'favorites_unheard', favs, {}, offline);
  assert.equal(plan1h.tracks.length, 15);
  assert.equal(plan1h.totalMinutes, 60);
  assert.equal(plan1h.alreadyOfflineCount, 2);
  assert.equal(plan1h.toDownloadCount, 13);
  assert.equal(plan1h.totalBytes, 15 * 8 * 1024 * 1024);

  const favOnly = buildRoadtripOfflinePlan(tracks, 2, 'favorites', favs, {}, offline);
  assert.equal(favOnly.tracks.length, 3);
  assert.equal(favOnly.toDownloadCount, 1);
});

test('carConvenience: SWC Long-Press bindings resolve cruising_mix and cycle_surround actions', () => {
  const sanitized = sanitizeSteeringLongPressBindings({
    cruising_mix: 'KeyR',
    cycle_surround: 'F9',
  });
  assert.equal(resolveSteeringAction('r', 'KeyR', sanitized), 'cruising_mix');
  assert.equal(resolveSteeringAction('F9', 'F9', sanitized), 'cycle_surround');
  assert.equal(DEFAULT_SWC_LONG_PRESS_BINDINGS.duck, 'Space');
});

function createSeededRng(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (1664525 * state + 1013904223) >>> 0;
    return state / 0x100000000;
  };
}

test('carConvenience: resolveTrackGenre prioritizes tags, falls back to meaningful folder, else Unclassified', () => {
  assert.equal(resolveTrackGenre({ genre: 'synthwave', folderPath: 'Music/Rock' }), 'Synthwave');
  assert.equal(resolveTrackGenre({ genre: 'EDM', folderPath: 'Music/Local' }), 'EDM');
  assert.equal(resolveTrackGenre({ genre: 'Unknown', folderPath: 'Music/WoW Radio/DKTN' }), 'DKTN');
  assert.equal(resolveTrackGenre({ genre: '', folderPath: 'Music/deep_house' }), 'Deep House');
  assert.equal(resolveTrackGenre({ genre: 'Other', folderPath: 'Music/Local/Downloads' }), 'Unclassified');
  assert.equal(resolveTrackGenre({}), 'Unclassified');
});

test('carConvenience: sortAndShuffleTracksByGenre keeps genres sorted A-Z with Unclassified last and shuffles within each genre', () => {
  const library = [
    { id: 'r1', artist: 'Muse', genre: 'Rock' },
    { id: 'r2', artist: 'Muse', genre: 'Rock' },
    { id: 'r3', artist: 'Arctic Monkeys', genre: 'Rock' },
    { id: 'a1', artist: 'Tycho', genre: 'Ambient' },
    { id: 'a2', artist: 'Brian Eno', genre: 'Ambient' },
    { id: 'a3', artist: 'Hammock', genre: 'Ambient' },
    { id: 'u1', artist: 'Mystery', folderPath: 'Music/Local' },
    { id: 'e1', artist: 'Daft Punk', genre: 'Electronic' },
    { id: 'e2', artist: 'Justice', genre: 'Electronic' },
  ];

  const passA = sortAndShuffleTracksByGenre(library, createSeededRng(11));
  const passB = sortAndShuffleTracksByGenre(library, createSeededRng(99));

  assert.equal(passA.length, library.length);
  assert.deepEqual(
    passA.map((t) => resolveTrackGenre(t)),
    ['Ambient', 'Ambient', 'Ambient', 'Electronic', 'Electronic', 'Rock', 'Rock', 'Rock', 'Unclassified']
  );
  assert.deepEqual(
    passB.map((t) => resolveTrackGenre(t)),
    ['Ambient', 'Ambient', 'Ambient', 'Electronic', 'Electronic', 'Rock', 'Rock', 'Rock', 'Unclassified']
  );

  // Within the Rock block (indices 5..7), Arctic Monkeys should split the two Muse tracks when possible
  const rockBlock = passA.slice(5, 8);
  assert.equal(rockBlock.length, 3);
  assert.ok(
    rockBlock[0].artist !== rockBlock[1].artist || rockBlock[1].artist !== rockBlock[2].artist,
    'Rock block should decluster artists'
  );

  // Different seeds should produce a different order overall
  assert.notDeepEqual(
    passA.map((t) => t.id),
    passB.map((t) => t.id)
  );
});

test('carConvenience: buildSmartMixTracks supports full-library genre-sorted Cruising reshuffle, Deep Cuts, and Favorites Radio', () => {
  const library = [
    { id: 't1', artist: 'Artist A', genre: 'House' },
    { id: 't2', artist: 'Artist B', genre: 'Ambient' },
    { id: 't3', artist: 'Artist C', genre: 'Techno' },
    { id: 't4', artist: 'Artist D', genre: 'Ambient' },
    { id: 't5', artist: 'Artist E', genre: 'House' },
    { id: 't6', artist: 'Artist F', folderPath: 'Music' },
  ];
  const stats = {
    t1: { playCount: 12, lastPlayedAt: 500 },
    t2: { playCount: 8, lastPlayedAt: 400 },
  };
  const favs = new Set(['t1', 't3', 't4']);

  const summary = summarizeMixGenres(library);
  assert.equal(summary.genreCount, 4);
  assert.deepEqual(summary.genres, [
    { genre: 'Ambient', count: 2 },
    { genre: 'House', count: 2 },
    { genre: 'Techno', count: 1 },
    { genre: 'Unclassified', count: 1 },
  ]);

  const cruisingReshuffled = buildSmartMixTracks(library, 'cruising', stats, favs, library.length, {
    reshuffle: true,
    sortByGenre: true,
    rng: createSeededRng(42),
  });
  assert.equal(cruisingReshuffled.length, library.length);
  assert.deepEqual(
    cruisingReshuffled.map((t) => resolveTrackGenre(t)),
    ['Ambient', 'Ambient', 'House', 'House', 'Techno', 'Unclassified']
  );

  const favRadio = buildSmartMixTracks(library, 'favorites_radio', stats, favs, 10, {
    reshuffle: true,
    sortByGenre: true,
    rng: createSeededRng(7),
  });
  assert.deepEqual(
    favRadio.map((t) => t.id),
    ['t4', 't1', 't3'] // Ambient -> House -> Techno
  );

  const deepCuts = buildSmartMixTracks(library, 'deep_cuts', stats, favs, 3, {
    reshuffle: false,
    sortByGenre: false,
  });
  assert.equal(deepCuts.length, 3);
  // All picked tracks should be from the 0-play unheard pool (t3, t4, t5, t6)
  for (const track of deepCuts) {
    assert.equal(stats[track.id as keyof typeof stats]?.playCount || 0, 0);
  }
});
