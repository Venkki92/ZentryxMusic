import test from 'node:test';
import assert from 'node:assert/strict';
import {
  addToQueue,
  playNextInQueue,
  reorderQueue,
  removeFromQueue,
  resolveNextIndex,
  resolvePreviousIndex,
} from './audioQueue';
import { parseSyncSafeInt, parseID3v2 } from './id3Parser';
import {
  fuzzyMatch,
  searchTracks,
  createPlaylist,
  addTrackToPlaylist,
  removeTrackFromPlaylist,
  reorderPlaylist,
} from './fuzzySearch';

test('audioQueue: addToQueue appends tracks without duplicate head', () => {
  const dummyTrack = {
    id: 't1',
    title: 'Blue Plz S1E01',
    artist: 'TotalBiscuit',
    album: 'WoW Radio',
    duration: '1:02:14',
    durationSeconds: 3734,
    sizeBytes: 40000000,
    sizeFormatted: '38 MB',
    href: '/Music/t1.mp3',
    streamUrl: '/api/stream/1',
    fileName: 'Blue Plz S1E01.mp3',
    folderPath: 'WoW Radio',
  };
  const q1 = addToQueue([], dummyTrack);
  assert.equal(q1.length, 1);
  assert.equal(q1[0].id, 't1');

  // Consecutive add ignored
  const q2 = addToQueue(q1, dummyTrack);
  assert.equal(q2.length, 1);

  // Different track appends
  const dummyTrack2 = { ...dummyTrack, id: 't2', title: 'Blue Plz S1E02' };
  const q3 = addToQueue(q2, dummyTrack2);
  assert.equal(q3.length, 2);
  assert.equal(q3[1].id, 't2');
});

test('audioQueue: playNextInQueue inserts right after current track', () => {
  const t1 = { id: 't1' } as any;
  const t2 = { id: 't2' } as any;
  const t3 = { id: 't3' } as any;
  const q = [t1, t2];
  const updated = playNextInQueue(q, 0, t3);
  assert.equal(updated.length, 3);
  assert.equal(updated[0].id, 't1');
  assert.equal(updated[1].id, 't3');
  assert.equal(updated[2].id, 't2');
});

test('audioQueue: reorderQueue and removeFromQueue work accurately', () => {
  const t1 = { id: 't1' } as any;
  const t2 = { id: 't2' } as any;
  const t3 = { id: 't3' } as any;
  const q = [t1, t2, t3];
  const reordered = reorderQueue(q, 2, 0);
  assert.deepEqual(reordered.map((t) => t.id), ['t3', 't1', 't2']);

  const removed = removeFromQueue(reordered, 1);
  assert.deepEqual(removed.map((t) => t.id), ['t3', 't2']);
});

test('audioQueue: resolveNextIndex and resolvePreviousIndex handle loop and restart', () => {
  assert.equal(resolveNextIndex(3, 0, false, 'OFF'), 1);
  assert.equal(resolveNextIndex(3, 2, false, 'ALL'), 0);
  assert.equal(resolveNextIndex(3, 2, false, 'OFF'), -1);
  assert.equal(resolveNextIndex(3, 1, false, 'ONE'), 1);

  // Position > 3s restarts current track
  assert.equal(resolvePreviousIndex(3, 2, 10), 2);
  // Position <= 3s goes to previous track
  assert.equal(resolvePreviousIndex(3, 2, 1), 1);
  // Position <= 3s at start wraps to end
  assert.equal(resolvePreviousIndex(3, 0, 1), 2);
});

test('id3Parser: parseSyncSafeInt decodes 7-bit integers correctly', () => {
  // Syncsafe 0x00 0x00 0x02 0x01 = 2 * 128 + 1 = 257
  const bytes = new Uint8Array([0x00, 0x00, 0x02, 0x01]);
  assert.equal(parseSyncSafeInt(bytes, 0), 257);
});

test('id3Parser: parseID3v2 extracts TIT2, TPE1, and TALB frames', () => {
  // Construct a minimal valid ID3v2.3 tag
  // Header: 'ID3', v2.3, flags 0, size syncsafe 40 bytes
  const header = [0x49, 0x44, 0x33, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30];
  // TIT2 frame: 'TIT2', size 4 bytes: 0x00, 0x00, 0x00, 0x05, flags 0, 0, encoding 0, text 'Song'
  const tit2 = [
    0x54, 0x49, 0x54, 0x32, // TIT2
    0x00, 0x00, 0x00, 0x05, // size 5
    0x00, 0x00,             // flags
    0x00,                   // encoding ISO-8859-1
    0x53, 0x6f, 0x6e, 0x67, // 'Song'
  ];
  const tagBytes = new Uint8Array([...header, ...tit2]);
  const parsed = parseID3v2(tagBytes);
  assert.ok(parsed);
  assert.equal(parsed?.title, 'Song');
});

test('fuzzySearch: fuzzyMatch finds direct and phonetic substring matches', () => {
  assert.equal(fuzzyMatch('blue plz', 'Blue Plz S5E28'), true);
  assert.equal(fuzzyMatch('totalbiscuit', 'TotalBiscuit WoW Radio'), true);
  assert.equal(fuzzyMatch('metallica', 'Iron Maiden'), false);
  assert.equal(fuzzyMatch('s5e28', 'Blue Plz Season 5 Episode 28 (S5E28).mp3'), true);
});

test('fuzzySearch: searchTracks filters tracks accurately', () => {
  const tracks = [
    { id: '1', title: 'Blue Plz S1E01', artist: 'TB', album: 'WoW Radio', fileName: 'bp1.mp3' },
    { id: '2', title: 'Epic Rock', artist: 'Queen', album: 'Greatest', fileName: 'queen.mp3' },
  ];
  const res = searchTracks(tracks, 'blue');
  assert.equal(res.length, 1);
  assert.equal(res[0].id, '1');
});

test('fuzzySearch: playlist management creates, adds, removes, and reorders', () => {
  const pl = createPlaylist('Highway Drives', 'Night cruising tracks');
  assert.equal(pl.name, 'Highway Drives');
  assert.equal(pl.trackIds.length, 0);

  const plWithTrack = addTrackToPlaylist(pl, 'track_101');
  assert.equal(plWithTrack.trackIds.length, 1);
  assert.equal(plWithTrack.trackIds[0], 'track_101');

  // Prevent duplicate additions
  const plDup = addTrackToPlaylist(plWithTrack, 'track_101');
  assert.equal(plDup.trackIds.length, 1);

  const plTwo = addTrackToPlaylist(plDup, 'track_102');
  assert.equal(plTwo.trackIds.length, 2);

  const reordered = reorderPlaylist(plTwo, 1, 0);
  assert.deepEqual(reordered.trackIds, ['track_102', 'track_101']);

  const removed = removeTrackFromPlaylist(reordered, 'track_101');
  assert.deepEqual(removed.trackIds, ['track_102']);
});
