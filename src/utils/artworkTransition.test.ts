import assert from 'node:assert/strict';
import test from 'node:test';
import {
  resolveEffectiveArtworkSrc,
  shouldCrossfadeArtwork,
} from './artworkTransition.ts';

test('resolveEffectiveArtworkSrc normalizes empty, whitespace, and errored URLs to null', () => {
  assert.equal(resolveEffectiveArtworkSrc(undefined), null);
  assert.equal(resolveEffectiveArtworkSrc(null), null);
  assert.equal(resolveEffectiveArtworkSrc('   '), null);
  assert.equal(resolveEffectiveArtworkSrc('blob:https://example/123'), 'blob:https://example/123');
  assert.equal(
    resolveEffectiveArtworkSrc('blob:https://example/bad', { 'blob:https://example/bad': true }),
    null
  );
});

test('shouldCrossfadeArtwork triggers only when displayed artwork actually changes', () => {
  assert.equal(shouldCrossfadeArtwork('blob:art-a', 'blob:art-b'), true);
  assert.equal(shouldCrossfadeArtwork('blob:art-a', null), true);
  assert.equal(shouldCrossfadeArtwork(null, 'blob:art-b'), true);

  // Same URL or fallback -> fallback should not trigger a redundant flash
  assert.equal(shouldCrossfadeArtwork('blob:art-a', 'blob:art-a'), false);
  assert.equal(shouldCrossfadeArtwork(null, null), false);
});
