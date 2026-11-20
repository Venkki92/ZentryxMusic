/**
 * Pure artwork crossfade transition helpers.
 * Determines whether a track/artwork change represents a true visual change
 * so consecutive tracks with identical artwork or identical fallback placeholders
 * do not flicker unnecessarily.
 */

export function resolveEffectiveArtworkSrc(
  src: string | null | undefined,
  erroredSrcs?: Readonly<Record<string, true>>
): string | null {
  if (!src) return null;
  const trimmed = src.trim();
  if (!trimmed) return null;
  if (erroredSrcs && erroredSrcs[trimmed]) return null;
  return trimmed;
}

export function shouldCrossfadeArtwork(
  currentSrc: string | null,
  nextSrc: string | null
): boolean {
  // Identical resolved artwork (including null -> null fallback-to-fallback) should not flash
  return currentSrc !== nextSrc;
}
