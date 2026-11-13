// Hardware Optimizations for MediaTek 825X_Pro-Octa core 9 Automotive Headunit
// (ARM Cortex-A55 Octa-Core + ARM Mali-G52 GPU / PowerVR GE8300, Android 9-11 WebView)

export interface MediaTekConfig {
  ecoMode: boolean; // 30 FPS canvas cap, no shadowBlur to eliminate dashboard thermal throttling
  disableBlurs: boolean; // Removes CSS backdrop-blur filter to avoid Mali-G52 GPU readbacks
  domWindowing: boolean; // Limits rendered DOM rows to 50 at a time for smooth 60fps scrolling
}

export const DEFAULT_MEDIATEK_CONFIG: MediaTekConfig = {
  ecoMode: true,
  disableBlurs: true,
  domWindowing: true,
};

/**
 * Returns optimized target FPS for canvas rendering.
 * 30 FPS saves ~50% GPU/CPU power and prevents thermal throttling in dash cavity.
 */
export function getOptimizedCanvasFps(ecoMode: boolean): number {
  return ecoMode ? 30 : 50;
}

/**
 * Slices large music collections into manageable DOM windows for low-overhead rendering.
 */
export function getWindowedTracks<T>(
  tracks: T[],
  limit: number,
  enabled: boolean
): { displayed: T[]; hasMore: boolean; total: number } {
  if (!enabled || tracks.length <= limit) {
    return {
      displayed: tracks,
      hasMore: false,
      total: tracks.length,
    };
  }

  return {
    displayed: tracks.slice(0, limit),
    hasMore: true,
    total: tracks.length,
  };
}

/**
 * Builds an O(1) track index lookup map to eliminate O(N^2) searches inside render loops.
 */
export function buildTrackIndexMap(tracks: Array<{ id: string }>): Map<string, number> {
  const map = new Map<string, number>();
  for (let i = 0; i < tracks.length; i++) {
    map.set(tracks[i].id, i);
  }
  return map;
}
