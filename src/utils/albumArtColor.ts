export interface RgbColor {
  r: number;
  g: number;
  b: number;
}

export interface AmbientArtPalette {
  /** WCAG-safe vibrant accent hex for progress bars, icons, and highlights on dark surfaces */
  hex: string;
  /** Subtle tinted border color for player card & artwork rim */
  border: string;
  /** Soft radial ambient wash behind artwork */
  glowStrong: string;
  /** Secondary diffused ambient edge light */
  glowSoft: string;
  /** High-contrast foreground text/icon color for buttons filled with `hex` */
  textOnAccent: '#020617' | '#ffffff';
  /** Contrast ratio of `hex` against the #0b1120 player card surface */
  contrastOnDark: number;
}

const DARK_SURFACE_RGB: RgbColor = { r: 11, g: 17, b: 32 }; // #0b1120
const DARK_TEXT_RGB: RgbColor = { r: 2, g: 6, b: 23 }; // #020617
const WHITE_RGB: RgbColor = { r: 255, g: 255, b: 255 };

function clamp01(v: number): number {
  return Math.max(0, Math.min(1, v));
}

export function rgbToHex({ r, g, b }: RgbColor): string {
  const toHex = (n: number) =>
    Math.max(0, Math.min(255, Math.round(n)))
      .toString(16)
      .padStart(2, '0');
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

export function rgbToHsl({ r, g, b }: RgbColor): { h: number; s: number; l: number } {
  const rn = r / 255;
  const gn = g / 255;
  const bn = b / 255;
  const max = Math.max(rn, gn, bn);
  const min = Math.min(rn, gn, bn);
  const l = (max + min) / 2;
  const d = max - min;

  if (d === 0) return { h: 0, s: 0, l };

  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  let h = 0;
  if (max === rn) {
    h = ((gn - bn) / d + (gn < bn ? 6 : 0)) / 6;
  } else if (max === gn) {
    h = ((bn - rn) / d + 2) / 6;
  } else {
    h = ((rn - gn) / d + 4) / 6;
  }
  return { h, s, l };
}

export function hslToRgb(h: number, s: number, l: number): RgbColor {
  const hn = ((h % 1) + 1) % 1;
  const sn = clamp01(s);
  const ln = clamp01(l);

  if (sn === 0) {
    const v = Math.round(ln * 255);
    return { r: v, g: v, b: v };
  }

  const hue2rgb = (p: number, q: number, t: number) => {
    let tn = t;
    if (tn < 0) tn += 1;
    if (tn > 1) tn -= 1;
    if (tn < 1 / 6) return p + (q - p) * 6 * tn;
    if (tn < 1 / 2) return q;
    if (tn < 2 / 3) return p + (q - p) * (2 / 3 - tn) * 6;
    return p;
  };

  const q = ln < 0.5 ? ln * (1 + sn) : ln + sn - ln * sn;
  const p = 2 * ln - q;

  return {
    r: Math.round(hue2rgb(p, q, hn + 1 / 3) * 255),
    g: Math.round(hue2rgb(p, q, hn) * 255),
    b: Math.round(hue2rgb(p, q, hn - 1 / 3) * 255),
  };
}

export function relativeLuminance({ r, g, b }: RgbColor): number {
  const linear = [r, g, b].map((c) => {
    const v = c / 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
}

export function contrastRatio(a: RgbColor, b: RgbColor): number {
  const la = relativeLuminance(a);
  const lb = relativeLuminance(b);
  const lighter = Math.max(la, lb);
  const darker = Math.min(la, lb);
  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Extracts a visually characteristic dominant RGB color from an RGBA pixel buffer.
 * Favors saturated mid-tones over near-black/near-white borders and flat grays.
 */
export function extractDominantColorFromRgba(rgba: Uint8ClampedArray): RgbColor | null {
  if (!rgba || rgba.length < 4) return null;

  // 24 hue buckets (15 deg each) to prevent opposing colors from averaging into gray mud
  const buckets = Array.from({ length: 24 }, () => ({
    weight: 0,
    rSum: 0,
    gSum: 0,
    bSum: 0,
  }));

  let fallbackWeight = 0;
  let fallbackR = 0;
  let fallbackG = 0;
  let fallbackB = 0;

  for (let i = 0; i < rgba.length; i += 4) {
    const a = rgba[i + 3];
    if (a < 160) continue;

    const r = rgba[i];
    const g = rgba[i + 1];
    const b = rgba[i + 2];
    const { h, s, l } = rgbToHsl({ r, g, b });

    // Skip extreme shadows & blown highlights
    if (l < 0.07 || l > 0.93) continue;

    fallbackWeight += 1;
    fallbackR += r;
    fallbackG += g;
    fallbackB += b;

    if (s < 0.14) continue;

    const midtoneBias = 1 - Math.abs(l - 0.52) * 1.45;
    const weight = Math.max(0.05, s * s * Math.max(0.2, midtoneBias));
    const bucketIdx = Math.min(23, Math.floor(h * 24));
    const bucket = buckets[bucketIdx];
    bucket.weight += weight;
    bucket.rSum += r * weight;
    bucket.gSum += g * weight;
    bucket.bSum += b * weight;
  }

  let best = buckets[0];
  for (let i = 1; i < buckets.length; i++) {
    if (buckets[i].weight > best.weight) {
      best = buckets[i];
    }
  }

  if (best.weight > 0) {
    return {
      r: Math.round(best.rSum / best.weight),
      g: Math.round(best.gSum / best.weight),
      b: Math.round(best.bSum / best.weight),
    };
  }

  if (fallbackWeight > 0) {
    return {
      r: Math.round(fallbackR / fallbackWeight),
      g: Math.round(fallbackG / fallbackWeight),
      b: Math.round(fallbackB / fallbackWeight),
    };
  }

  return null;
}

/**
 * Clamps any extracted album-art RGB color into an automotive dark-cockpit palette
 * guaranteeing >= 4.8:1 WCAG AA contrast against the #0b1120 player surface and
 * selecting high-contrast foreground text/icons for filled buttons.
 */
export function clampAmbientAccentForDarkSurface(rgb: RgbColor): AmbientArtPalette {
  const { h, s, l } = rgbToHsl(rgb);

  // Keep saturation refined (automotive MBUX feel, never muddy and never harsh neon)
  const clampedSat = s < 0.12 ? Math.max(0.18, s) : Math.max(0.46, Math.min(0.82, s));
  let clampedLight = Math.max(0.54, Math.min(0.72, l < 0.45 ? 0.58 : l));

  let candidate = hslToRgb(h, clampedSat, clampedLight);
  let ratio = contrastRatio(candidate, DARK_SURFACE_RGB);

  // Iteratively lift lightness if a deep blue/indigo/red still falls short of WCAG AA (4.8:1 target)
  let guard = 0;
  while (ratio < 4.8 && clampedLight < 0.84 && guard < 20) {
    clampedLight = Math.min(0.84, clampedLight + 0.025);
    candidate = hslToRgb(h, clampedSat, clampedLight);
    ratio = contrastRatio(candidate, DARK_SURFACE_RGB);
    guard++;
  }

  const contrastWithDarkText = contrastRatio(candidate, DARK_TEXT_RGB);
  const contrastWithWhiteText = contrastRatio(candidate, WHITE_RGB);
  const textOnAccent: '#020617' | '#ffffff' =
    contrastWithDarkText >= contrastWithWhiteText ? '#020617' : '#ffffff';

  const { r, g, b } = candidate;

  return {
    hex: rgbToHex(candidate),
    border: `rgba(${r}, ${g}, ${b}, 0.36)`,
    glowStrong: `rgba(${r}, ${g}, ${b}, 0.24)`,
    glowSoft: `rgba(${r}, ${g}, ${b}, 0.09)`,
    textOnAccent,
    contrastOnDark: Math.round(ratio * 100) / 100,
  };
}

const paletteCache = new Map<string, AmbientArtPalette | null>();
const MAX_CACHE_ENTRIES = 120;

function setCachedPalette(key: string, value: AmbientArtPalette | null) {
  if (paletteCache.size >= MAX_CACHE_ENTRIES) {
    const oldest = paletteCache.keys().next().value;
    if (oldest !== undefined) paletteCache.delete(oldest);
  }
  paletteCache.set(key, value);
}

/**
 * Extracts a WCAG-clamped ambient palette from a cover art image URL using a 32x32
 * offscreen canvas. Gracefully returns null if image load fails or canvas is tainted.
 */
export async function extractAmbientPaletteFromImageUrl(
  imageUrl: string | null | undefined
): Promise<AmbientArtPalette | null> {
  if (!imageUrl || typeof document === 'undefined' || typeof Image === 'undefined') {
    return null;
  }

  if (paletteCache.has(imageUrl)) {
    return paletteCache.get(imageUrl) ?? null;
  }

  return new Promise<AmbientArtPalette | null>((resolve) => {
    const img = new Image();
    const isLocalObjectOrData = imageUrl.startsWith('blob:') || imageUrl.startsWith('data:');
    if (!isLocalObjectOrData) {
      img.crossOrigin = 'anonymous';
    }
    img.decoding = 'async';

    let settled = false;
    const finish = (palette: AmbientArtPalette | null) => {
      if (settled) return;
      settled = true;
      setCachedPalette(imageUrl, palette);
      resolve(palette);
    };

    const timeoutId = window.setTimeout(() => finish(null), 3500);

    img.onload = () => {
      window.clearTimeout(timeoutId);
      try {
        const size = 32;
        const canvas = document.createElement('canvas');
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext('2d', { willReadFrequently: true });
        if (!ctx) {
          finish(null);
          return;
        }
        ctx.drawImage(img, 0, 0, size, size);
        const { data } = ctx.getImageData(0, 0, size, size);
        const dominant = extractDominantColorFromRgba(data);
        finish(dominant ? clampAmbientAccentForDarkSurface(dominant) : null);
      } catch {
        // SecurityError on cross-origin tainted canvas or decode error
        finish(null);
      }
    };

    img.onerror = () => {
      window.clearTimeout(timeoutId);
      finish(null);
    };

    img.src = imageUrl;
  });
}
