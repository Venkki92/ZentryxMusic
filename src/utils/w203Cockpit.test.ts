import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  CockpitTrackLike,
  buildAlbumSwapGroups,
  buildFolderSwapGroups,
  extractFolderKey,
  findAdjacentSwapGroup,
  isW203NightWindow,
  resolveW203DimmerState,
  selectTracksForWifiAutoCache,
} from './w203Cockpit';

function makeTrack(overrides: Partial<CockpitTrackLike> & { id: string; title: string }): CockpitTrackLike {
  return {
    artist: 'Test Artist',
    album: 'Test Album',
    folderPath: 'Music/Synthwave',
    fileName: `${overrides.title}.flac`,
    sizeBytes: 10 * 1024 * 1024,
    sourceType: 'WEBDAV',
    ...overrides,
  };
}

describe('w203Cockpit utilities', () => {
  it('extracts normalized folder keys and groups folders deterministically', () => {
    const tracks = [
      makeTrack({ id: '1', title: 'Track B', folderPath: '/Music/Synthwave/', fileName: '02.flac' }),
      makeTrack({ id: '2', title: 'Track A', folderPath: 'Music\\Synthwave', fileName: '01.flac' }),
      makeTrack({ id: '3', title: 'Jazz 1', folderPath: 'Music/Jazz', fileName: '01.flac' }),
    ];
    assert.equal(extractFolderKey(tracks[0]), 'Music/Synthwave');
    assert.equal(extractFolderKey(tracks[1]), 'Music/Synthwave');

    const groups = buildFolderSwapGroups(tracks, new Set(['1', '3']), ['music/synthwave']);
    assert.equal(groups.length, 2);
    assert.equal(groups[0].id, 'Music/Jazz');
    assert.equal(groups[0].cachedCount, 1);
    assert.equal(groups[0].isPinnedForWifiCache, false);

    assert.equal(groups[1].id, 'Music/Synthwave');
    assert.equal(groups[1].tracks[0].id, '2');
    assert.equal(groups[1].cachedCount, 1);
    assert.equal(groups[1].isPinnedForWifiCache, true);
  });

  it('cycles forward and backward across folders and albums seamlessly', () => {
    const tracks = [
      makeTrack({ id: 'a1', title: 'One', album: 'Alpha', artist: 'Band A', folderPath: 'Music/A' }),
      makeTrack({ id: 'b1', title: 'Two', album: 'Beta', artist: 'Band B', folderPath: 'Music/B' }),
      makeTrack({ id: 'c1', title: 'Three', album: 'Gamma', artist: 'Band C', folderPath: 'Music/C' }),
    ];
    const folders = buildFolderSwapGroups(tracks, new Set());
    const wrappedNext = findAdjacentSwapGroup(folders, tracks[2], 'folders', 1);
    assert.ok(wrappedNext);
    assert.equal(wrappedNext.group.id, 'Music/A');
    assert.equal(wrappedNext.index, 0);

    const wrappedPrev = findAdjacentSwapGroup(folders, tracks[0], 'folders', -1);
    assert.ok(wrappedPrev);
    assert.equal(wrappedPrev.group.id, 'Music/C');
    assert.equal(wrappedPrev.index, 2);

    const albums = buildAlbumSwapGroups(tracks, new Set());
    const nextAlbum = findAdjacentSwapGroup(albums, tracks[0], 'albums', 1);
    assert.ok(nextAlbum);
    assert.equal(nextAlbum.group.name, 'Beta');
  });

  it('evaluates W203 Day/Night Amber dimmer schedule and overrides', () => {
    assert.equal(isW203NightWindow(21, 7, 19), true);
    assert.equal(isW203NightWindow(5, 7, 19), true);
    assert.equal(isW203NightWindow(13, 7, 19), false);

    const customNight = resolveW203DimmerState({
      themeMode: 'auto',
      hourOfDay: 20,
      dayStartHour: 6,
      nightStartHour: 18,
      isSolarDaylight: true,
      autoAmberAtNight: true,
    });
    assert.equal(customNight.useDayMode, false);
    assert.equal(customNight.isAmberNightActive, true);

    const forcedDay = resolveW203DimmerState({
      themeMode: 'day',
      hourOfDay: 23,
      autoAmberAtNight: true,
      manualAmberFilter: false,
    });
    assert.equal(forcedDay.useDayMode, true);
    assert.equal(forcedDay.isAmberNightActive, false);
  });

  it('selects uncached pinned WebDAV tracks while respecting storage cap', () => {
    const mb = 1024 * 1024;
    const tracks = [
      makeTrack({ id: '1', title: 'A', folderPath: 'Music/Roadtrip', fileName: '01.flac', sizeBytes: 20 * mb }),
      makeTrack({ id: '2', title: 'B', folderPath: 'Music/Roadtrip', fileName: '02.flac', sizeBytes: 25 * mb }),
      makeTrack({ id: '3', title: 'C', folderPath: 'Music/Roadtrip', fileName: '03.flac', sizeBytes: 30 * mb }),
      makeTrack({ id: '4', title: 'D', folderPath: 'Music/Roadtrip', fileName: '04.flac', sizeBytes: 10 * mb }),
      makeTrack({ id: '5', title: 'E', folderPath: 'Music/Roadtrip', fileName: '05.flac', sizeBytes: 10 * mb, sourceType: 'LOCAL' }),
      makeTrack({ id: '6', title: 'F', folderPath: 'Music/Other', fileName: '01.flac', sizeBytes: 10 * mb }),
    ];

    const selected = selectTracksForWifiAutoCache({
      tracks,
      offlineTrackIds: new Set(['4']),
      pinnedFolders: ['music/roadtrip'],
      maxCacheMb: 100,
      currentCachedBytes: 50 * mb,
    });
    assert.deepEqual(
      selected.map((t) => t.id),
      ['1', '2']
    );
  });
});
