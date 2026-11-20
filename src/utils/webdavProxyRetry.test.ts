import test from 'node:test';
import assert from 'node:assert/strict';
import {
  WEBDAV_CHUNK_MAX_RETRIES,
  computeRetryBackoffMs,
  isChunkProxyRequest,
  isRetryableProxyError,
  isRetryableUpstreamStatus,
  shouldRetryChunkRequest,
} from '../../serverProxy.ts';

test('isChunkProxyRequest identifies audio and byte-range GET/HEAD requests only', () => {
  assert.equal(isChunkProxyRequest('GET', '/remote.php/webdav/Music/song.mp3'), true);
  assert.equal(isChunkProxyRequest('GET', '/remote.php/webdav/WoW%20Radio/set.flac?auth=abc'), true);
  assert.equal(isChunkProxyRequest('HEAD', '/remote.php/webdav/Music/track.m4a'), true);
  assert.equal(isChunkProxyRequest('GET', '/remote.php/webdav/cover.jpg', 'bytes=0-65535'), true);

  // Non-idempotent WebDAV verbs and non-chunk requests must not be auto-retried
  assert.equal(isChunkProxyRequest('PUT', '/remote.php/webdav/Music/song.mp3', 'bytes=0-1023'), false);
  assert.equal(isChunkProxyRequest('DELETE', '/remote.php/webdav/Music/song.mp3'), false);
  assert.equal(isChunkProxyRequest('PROPFIND', '/remote.php/webdav/Music/'), false);
  assert.equal(isChunkProxyRequest('GET', '/remote.php/webdav/Music/'), false);
});

test('isRetryableProxyError & isRetryableUpstreamStatus classify transient car network failures accurately', () => {
  assert.equal(isRetryableProxyError(Object.assign(new Error('read ECONNRESET'), { code: 'ECONNRESET' })), true);
  assert.equal(isRetryableProxyError(Object.assign(new Error('connect ETIMEDOUT'), { code: 'ETIMEDOUT' })), true);
  assert.equal(isRetryableProxyError(new Error('socket hang up')), true);
  assert.equal(isRetryableProxyError(new Error('Invalid URL syntax')), false);

  assert.equal(isRetryableUpstreamStatus(408), true);
  assert.equal(isRetryableUpstreamStatus(429), true);
  assert.equal(isRetryableUpstreamStatus(502), true);
  assert.equal(isRetryableUpstreamStatus(503), true);
  assert.equal(isRetryableUpstreamStatus(504), true);
  assert.equal(isRetryableUpstreamStatus(401), false);
  assert.equal(isRetryableUpstreamStatus(404), false);
  assert.equal(isRetryableUpstreamStatus(206), false);
});

test('computeRetryBackoffMs grows exponentially with bounded jitter', () => {
  // Deterministic midpoint randomFn = 0.5 -> zero jitter
  assert.equal(computeRetryBackoffMs(1, 350, 2500, 0.2, () => 0.5), 350);
  assert.equal(computeRetryBackoffMs(2, 350, 2500, 0.2, () => 0.5), 700);
  assert.equal(computeRetryBackoffMs(3, 350, 2500, 0.2, () => 0.5), 1400);
  assert.equal(computeRetryBackoffMs(5, 350, 2500, 0.2, () => 0.5), 2500);

  // Jitter stays within ±20%
  const low = computeRetryBackoffMs(2, 350, 2500, 0.2, () => 0);
  const high = computeRetryBackoffMs(2, 350, 2500, 0.2, () => 1);
  assert.equal(low, 560);
  assert.equal(high, 840);
});

test('shouldRetryChunkRequest enforces retry budget, client-alive, and headers-not-sent invariants', () => {
  const netErr = Object.assign(new Error('socket hang up'), { code: 'ECONNRESET' });

  // Eligible chunk retry on attempt 0
  assert.equal(
    shouldRetryChunkRequest({
      method: 'GET',
      url: '/remote.php/webdav/Music/mix.mp3',
      rangeHeader: 'bytes=0-8388607',
      attempt: 0,
      headersSent: false,
      responseEnded: false,
      clientDisconnected: false,
      error: netErr,
    }),
    true
  );

  // Eligible chunk retry on transient HTTP 503
  assert.equal(
    shouldRetryChunkRequest({
      method: 'GET',
      url: '/remote.php/webdav/Music/mix.mp3',
      rangeHeader: 'bytes=8388608-16777215',
      attempt: 1,
      headersSent: false,
      responseEnded: false,
      clientDisconnected: false,
      statusCode: 503,
    }),
    true
  );

  // Exhausted retry budget
  assert.equal(
    shouldRetryChunkRequest({
      method: 'GET',
      url: '/remote.php/webdav/Music/mix.mp3',
      rangeHeader: 'bytes=0-8388607',
      attempt: WEBDAV_CHUNK_MAX_RETRIES,
      headersSent: false,
      responseEnded: false,
      clientDisconnected: false,
      error: netErr,
    }),
    false
  );

  // Never retry if headers already sent or car browser aborted request
  assert.equal(
    shouldRetryChunkRequest({
      method: 'GET',
      url: '/remote.php/webdav/Music/mix.mp3',
      attempt: 0,
      headersSent: true,
      responseEnded: false,
      clientDisconnected: false,
      error: netErr,
    }),
    false
  );
  assert.equal(
    shouldRetryChunkRequest({
      method: 'GET',
      url: '/remote.php/webdav/Music/mix.mp3',
      attempt: 0,
      headersSent: false,
      responseEnded: false,
      clientDisconnected: true,
      error: netErr,
    }),
    false
  );
});
