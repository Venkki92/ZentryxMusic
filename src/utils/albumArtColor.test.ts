import test from 'node:test';
import assert from 'node:assert/strict';
import {
  clampAmbientAccentForDarkSurface,
  contrastRatio,
  extractDominantColorFromRgba,
  rgbToHex,
} from './albumArtColor.ts';

function makeSolidBuffer(r: number, g: number, b: number, count = 64): Uint8ClampedArray {
  const buf = new Uint8ClampedArray(count * 4);
  for (let i = 0; i < count; i++) {
    buf[i * 4] = r;
    buf[i * 4 + 1] = g;
    buf[i * 4 + 2] = b;
    buf[i * 4 + 3] = 255;
  }
  return buf;
}

test('extractDominantColorFromRgba prefers vibrant pixels over dark border framing', () => {
  const buf = new Uint8ClampedArray(64 * 4);
  // 48 dark pixels + 16 rich crimson pixels
  for (let i = 0; i < 48; i++) {
    buf[i * 4] = 8;
    buf[i * 4 + 1] = 10;
    buf[i * 4 + 2] = 14;
    buf[i * 4 + 3] = 255;
  }
  for (let i = 48; i < 64; i++) {
    buf[i * 4] = 220;
    buf[i * 4 + 1] = 48;
    buf[i * 4 + 2] = 64;
    buf[i * 4 + 3] = 255;
  }

  const dominant = extractDominantColorFromRgba(buf);
  assert.ok(dominant);
  assert.ok(dominant.r > 180, `expected high red channel, got ${dominant.r}`);
  assert.ok(dominant.g < 80, `expected low green channel, got ${dominant.g}`);
});

test('clampAmbientAccentForDarkSurface enforces >= 4.8:1 WCAG contrast even for deep navy covers', () => {
  const deepNavy = { r: 16, g: 34, b: 140 };
  const palette = clampAmbientAccentForDarkSurface(deepNavy);

  assert.ok(palette.contrastOnDark >= 4.8, `expected >= 4.8 contrast, got ${palette.contrastOnDark}`);
  assert.match(palette.hex, /^#[0-9a-f]{6}$/);
  assert.ok(palette.border.startsWith('rgba('));
  assert.ok(palette.glowStrong.startsWith('rgba('));
  assert.ok(palette.textOnAccent === '#020617' || palette.textOnAccent === '#ffffff');
});

test('clampAmbientAccentForDarkSurface picks dark text for bright amber/lime hues', () => {
  const amber = { r: 245, g: 175, b: 35 };
  const palette = clampAmbientAccentForDarkSurface(amber);
  assert.equal(palette.textOnAccent, '#020617');
  assert.ok(palette.contrastOnDark >= 4.8);
});

test('extractDominantColorFromRgba handles monochrome covers gracefully without crashing', () => {
  const grayBuf = makeSolidBuffer(128, 128, 128, 32);
  const dominant = extractDominantColorFromRgba(grayBuf);
  assert.ok(dominant);
  assert.equal(rgbToHex(dominant), '#808080');

  const palette = clampAmbientAccentForDarkSurface(dominant);
  assert.ok(palette.contrastOnDark >= 4.8);
});

test('extractDominantColorFromRgba returns null for fully transparent images', () => {
  const empty = new Uint8ClampedArray(16 * 4);
  assert.equal(extractDominantColorFromRgba(empty), null);
});

test('contrastRatio returns 21 for pure black vs pure white', () => {
  const ratio = contrastRatio({ r: 0, g: 0, b: 0 }, { r: 255, g: 255, b: 255 });
  assert.equal(Math.round(ratio), 21);
});
