import test from 'node:test';
import assert from 'node:assert/strict';
import {
  getOptimizedCanvasFps,
  getWindowedTracks,
  buildTrackIndexMap,
  DEFAULT_MEDIATEK_CONFIG,
} from './mediatekOptimization';

test('DEFAULT_MEDIATEK_CONFIG: defaults to high performance automotive profile', () => {
  assert.equal(DEFAULT_MEDIATEK_CONFIG.ecoMode, true);
  assert.equal(DEFAULT_MEDIATEK_CONFIG.disableBlurs, true);
  assert.equal(DEFAULT_MEDIATEK_CONFIG.domWindowing, true);
});

test('getOptimizedCanvasFps: returns 30 FPS when ecoMode is on, 50 FPS when off', () => {
  assert.equal(getOptimizedCanvasFps(true), 30);
  assert.equal(getOptimizedCanvasFps(false), 50);
});

test('getWindowedTracks: handles track windowing and reports hasMore accurately', () => {
  const sampleTracks = Array.from({ length: 171 }, (_, i) => ({ id: `track-${i}` }));

  // Windowed to 50
  const windowed = getWindowedTracks(sampleTracks, 50, true);
  assert.equal(windowed.displayed.length, 50);
  assert.equal(windowed.hasMore, true);
  assert.equal(windowed.total, 171);

  // When windowing is disabled, returns all
  const disabled = getWindowedTracks(sampleTracks, 50, false);
  assert.equal(disabled.displayed.length, 171);
  assert.equal(disabled.hasMore, false);

  // When count is less than limit, hasMore is false
  const small = getWindowedTracks(sampleTracks.slice(0, 20), 50, true);
  assert.equal(small.displayed.length, 20);
  assert.equal(small.hasMore, false);
});

test('buildTrackIndexMap: provides O(1) index lookup for arbitrary track IDs', () => {
  const sample = [{ id: 'a' }, { id: 'b' }, { id: 'c' }];
  const map = buildTrackIndexMap(sample);

  assert.equal(map.get('a'), 0);
  assert.equal(map.get('b'), 1);
  assert.equal(map.get('c'), 2);
  assert.equal(map.get('unknown'), undefined);
});
