import test from 'node:test';
import assert from 'node:assert/strict';
import {
  calculateVirtualWindow,
  buildAlphabetIndex,
  isFastScrolling,
  getSlotPoolSize,
  getRecycledSlotIndex,
  shouldAnimateSlotAppearance,
  getSlotFadeInConfig,
} from './virtualList';

test('calculateVirtualWindow: handles empty collections safely', () => {
  const res = calculateVirtualWindow(0, 68, 500, 0, 4);
  assert.equal(res.startIndex, 0);
  assert.equal(res.endIndex, -1);
  assert.equal(res.totalHeight, 0);
  assert.equal(res.visibleCount, 0);
});

test('calculateVirtualWindow: computes correct start, end, and offsetY at top of list', () => {
  // 100 items, each 70px high, viewport 350px (5 items visible)
  const res = calculateVirtualWindow(100, 70, 350, 0, 2);

  // At scrollTop 0: rawStart = 0, rawEnd = 5.
  // With overscan 2: startIndex = 0, endIndex = 7.
  assert.equal(res.startIndex, 0);
  assert.equal(res.endIndex, 7);
  assert.equal(res.offsetY, 0);
  assert.equal(res.totalHeight, 7000);
  assert.equal(res.visibleCount, 8);
});

test('calculateVirtualWindow: accurately offsets and windows when scrolled midway', () => {
  // Scroll down to 1400px (item index 20)
  const res = calculateVirtualWindow(100, 70, 350, 1400, 2);

  // rawStart = 20, rawEnd = 25.
  // with overscan 2: startIndex = 18, endIndex = 27.
  assert.equal(res.startIndex, 18);
  assert.equal(res.endIndex, 27);
  assert.equal(res.offsetY, 18 * 70); // 1260px
  assert.equal(res.visibleCount, 10);
});

test('calculateVirtualWindow: clamps to bottom bounds when scrolled to end', () => {
  // Scrolled near bottom
  const res = calculateVirtualWindow(50, 70, 400, 3200, 3);
  assert.equal(res.endIndex, 49); // Clamped to totalItems - 1
  assert.ok(res.startIndex <= res.endIndex);
  assert.equal(res.totalHeight, 3500);
});

test('buildAlphabetIndex: groups tracks by first character and provides target indices', () => {
  const tracks = [
    { title: '1984' },
    { title: 'Airplanes' },
    { title: 'Blue Plz S1' },
    { title: 'Blue Plz S2' },
    { title: 'Danger Zone' },
  ];

  const index = buildAlphabetIndex(tracks);
  assert.deepEqual(index, [
    { letter: '#', index: 0 },
    { letter: 'A', index: 1 },
    { letter: 'B', index: 2 },
    { letter: 'D', index: 4 },
  ]);
});

test('isFastScrolling: detects rapid flick gestures above velocity threshold', () => {
  // 300px in 100ms = 3.0 px/ms (> 1.0 threshold)
  assert.equal(isFastScrolling(0, 300, 100, 1.0), true);

  // 40px in 100ms = 0.4 px/ms (< 1.0 threshold)
  assert.equal(isFastScrolling(0, 40, 100, 1.0), false);

  // Zero delta time should not crash
  assert.equal(isFastScrolling(0, 50, 0, 1.0), false);
});

test('getSlotPoolSize & getRecycledSlotIndex: guarantees unique slots across any visible window', () => {
  const poolSize = getSlotPoolSize(580, 68, 4);
  const win = calculateVirtualWindow(200, 68, 580, 1950, 4);

  const usedSlots = new Set<number>();
  for (let idx = win.startIndex; idx <= win.endIndex; idx++) {
    usedSlots.add(getRecycledSlotIndex(idx, poolSize));
  }

  assert.equal(usedSlots.size, win.visibleCount);
  assert.ok(poolSize >= win.visibleCount);
});

test('shouldAnimateSlotAppearance: triggers only when slot binds a new item key', () => {
  assert.equal(shouldAnimateSlotAppearance('track-1', 'track-1'), false);
  assert.equal(shouldAnimateSlotAppearance('track-1', 'track-15'), true);
  assert.equal(shouldAnimateSlotAppearance(null, 'track-0', true), true);
  assert.equal(shouldAnimateSlotAppearance(null, 'track-0', false), false);
});

test('getSlotFadeInConfig: returns smooth GPU keyframes for normal and fast scroll', () => {
  const normal = getSlotFadeInConfig(false, 200);
  assert.equal(normal.keyframes[0].opacity, 0);
  assert.equal(normal.keyframes[1].opacity, 1);
  assert.equal(normal.options.duration, 200);
  assert.equal(normal.options.fill, 'backwards');

  const fast = getSlotFadeInConfig(true, 200);
  assert.ok(fast.keyframes[0].opacity > 0);
  assert.ok(fast.options.duration < normal.options.duration);
});
