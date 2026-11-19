import test from 'node:test';
import assert from 'node:assert/strict';
import {
  DEFAULT_MUSIC_FOLDERS,
  DEFAULT_MUSIC_FOLDER_STRING,
  addFolderToList,
  mergeDiscoveredTracks,
  normalizeFolderPath,
  parseScanFolders,
  removeFolderFromList,
  resolveInitialBrowserPath,
} from './folderScanner.ts';

test('default music folders include WoW Radio and Music in order', () => {
  assert.deepEqual([...DEFAULT_MUSIC_FOLDERS], ['WoW Radio', 'Music']);
  assert.equal(DEFAULT_MUSIC_FOLDER_STRING, 'WoW Radio, Music');
  assert.deepEqual(parseScanFolders(''), ['WoW Radio', 'Music']);
  assert.deepEqual(parseScanFolders(undefined), ['WoW Radio', 'Music']);
  assert.deepEqual(parseScanFolders(DEFAULT_MUSIC_FOLDER_STRING), ['WoW Radio', 'Music']);
});

test('parseScanFolders parses comma/semicolon/newline lists, strips slashes, and merges extras', () => {
  const parsed = parseScanFolders('/WoW Radio/, /Music ; Podcasts\nDJ Sets', ['music', 'Audiobooks/2025']);
  assert.deepEqual(parsed, ['WoW Radio', 'Music', 'Podcasts', 'DJ Sets', 'Audiobooks/2025']);
});

test('parseScanFolders preserves explicit root / selection', () => {
  assert.deepEqual(parseScanFolders('/'), ['']);
});

test('normalizeFolderPath removes leading/trailing and duplicate slashes', () => {
  assert.equal(normalizeFolderPath('  ///WoW Radio//Sub/  '), 'WoW Radio/Sub');
  assert.equal(normalizeFolderPath('/'), '');
});

test('addFolderToList and removeFolderFromList deduplicate case-insensitively', () => {
  const added1 = addFolderToList(['WoW Radio', 'Music'], '/Podcasts/Tech/');
  assert.deepEqual(added1, ['WoW Radio', 'Music', 'Podcasts/Tech']);

  const addedDup = addFolderToList(added1, 'podcasts/tech');
  assert.deepEqual(addedDup, ['WoW Radio', 'Music', 'Podcasts/Tech']);

  const removed = removeFolderFromList(addedDup, '/PODCASTS/TECH');
  assert.deepEqual(removed, ['WoW Radio', 'Music']);
});

test('mergeDiscoveredTracks in additive mode preserves existing cloud and local tracks', () => {
  const existing = [
    { id: 'nc_1', title: 'Track 1', sourceType: 'WEBDAV' },
    { id: 'loc_1', title: 'USB Track', sourceType: 'LOCAL' },
  ];
  const discovered = [
    { id: 'nc_1', title: 'Track 1 Updated', sourceType: 'WEBDAV' },
    { id: 'nc_2', title: 'WoW Radio Intro', sourceType: 'WEBDAV' },
    { id: 'nc_3', title: 'WoW Radio Mix', sourceType: 'WEBDAV' },
  ];

  const result = mergeDiscoveredTracks(existing, discovered, 'additive');
  assert.equal(result.tracks.length, 4);
  assert.equal(result.newlyAddedCount, 2);
  assert.equal(result.updatedCount, 1);
  assert.equal(result.tracks.find((t) => t.id === 'nc_1')?.title, 'Track 1 Updated');
  assert.ok(result.tracks.some((t) => t.id === 'loc_1'));
  assert.ok(result.tracks.some((t) => t.id === 'nc_2'));
  assert.ok(result.tracks.some((t) => t.id === 'nc_3'));
});

test('mergeDiscoveredTracks in replace-cloud mode keeps LOCAL tracks while replacing WEBDAV tracks', () => {
  const existing = [
    { id: 'nc_old', title: 'Old Cloud', sourceType: 'WEBDAV' },
    { id: 'loc_1', title: 'USB Track', sourceType: 'LOCAL' },
  ];
  const discovered = [
    { id: 'nc_new', title: 'New Cloud', sourceType: 'WEBDAV' },
  ];

  const result = mergeDiscoveredTracks(existing, discovered, 'replace-cloud');
  assert.equal(result.tracks.length, 2);
  assert.ok(result.tracks.some((t) => t.id === 'loc_1'));
  assert.ok(result.tracks.some((t) => t.id === 'nc_new'));
  assert.ok(!result.tracks.some((t) => t.id === 'nc_old'));
});

test('resolveInitialBrowserPath opens root when configured string contains multiple folders', () => {
  assert.equal(resolveInitialBrowserPath(undefined, 'WoW Radio, Music'), '');
  assert.equal(resolveInitialBrowserPath('WoW Radio, Music', 'WoW Radio, Music'), '');
  assert.equal(resolveInitialBrowserPath('/WoW Radio/', 'WoW Radio, Music'), 'WoW Radio');
});
