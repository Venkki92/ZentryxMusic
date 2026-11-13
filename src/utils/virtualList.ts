// High-Performance Virtualization & Memory Recycling (LazyColumn) for MediaTek 825X Headunit
// Eliminates DOM thrashing and layout recalculations during high-speed scrolling.

export interface VirtualWindow {
  startIndex: number;
  endIndex: number;
  offsetY: number;
  totalHeight: number;
  visibleCount: number;
}

/**
 * Pure calculation of visible item indices and translateY offset for fixed-height virtual list.
 */
export function calculateVirtualWindow(
  totalItems: number,
  itemHeight: number,
  viewportHeight: number,
  scrollTop: number,
  overscan = 4
): VirtualWindow {
  if (totalItems <= 0 || itemHeight <= 0 || viewportHeight <= 0) {
    return {
      startIndex: 0,
      endIndex: -1,
      offsetY: 0,
      totalHeight: 0,
      visibleCount: 0,
    };
  }

  const safeScrollTop = Math.max(0, scrollTop);
  const totalHeight = totalItems * itemHeight;

  // Raw index of the topmost visible item
  const rawStart = Math.floor(safeScrollTop / itemHeight);
  // Raw index of bottom-most visible item
  const rawEnd = Math.floor((safeScrollTop + viewportHeight) / itemHeight);

  // Apply overscan buffer to avoid blank frames during fast flicks
  const startIndex = Math.max(0, rawStart - overscan);
  const endIndex = Math.min(totalItems - 1, rawEnd + overscan);

  const offsetY = startIndex * itemHeight;
  const visibleCount = Math.max(0, endIndex - startIndex + 1);

  return {
    startIndex,
    endIndex,
    offsetY,
    totalHeight,
    visibleCount,
  };
}

export interface AlphabetIndexItem {
  letter: string;
  index: number;
}

/**
 * Builds an index of first letters and their first track index for automotive quick-scrubbing.
 */
export function buildAlphabetIndex<T extends { title?: string; name?: string }>(
  items: T[]
): AlphabetIndexItem[] {
  const map = new Map<string, number>();

  for (let i = 0; i < items.length; i++) {
    const raw = (items[i].title || items[i].name || '').trim();
    if (!raw) continue;
    const firstChar = raw[0].toUpperCase();
    const letter = /[A-Z]/.test(firstChar) ? firstChar : '#';

    if (!map.has(letter)) {
      map.set(letter, i);
    }
  }

  return Array.from(map.entries())
    .map(([letter, index]) => ({ letter, index }))
    .sort((a, b) => {
      if (a.letter === '#') return -1;
      if (b.letter === '#') return 1;
      return a.letter.localeCompare(b.letter);
    });
}

/**
 * Detects whether scrolling velocity exceeds threshold (in px/ms).
 */
export function isFastScrolling(
  prevScrollTop: number,
  currentScrollTop: number,
  deltaTimeMs: number,
  velocityThreshold = 1.0 // 1.0 px/ms = 1000 px/s
): boolean {
  if (deltaTimeMs <= 0) return false;
  const speed = Math.abs(currentScrollTop - prevScrollTop) / deltaTimeMs;
  return speed >= velocityThreshold;
}

/**
 * Computes the fixed DOM pool size needed to cover the viewport + overscan without collisions.
 */
export function getSlotPoolSize(
  viewportHeight: number,
  itemHeight: number,
  overscan = 4
): number {
  if (viewportHeight <= 0 || itemHeight <= 0) return 1;
  return Math.ceil(viewportHeight / itemHeight) + overscan * 2 + 2;
}

/**
 * Maps a logical list index to a recycled DOM slot index [0..poolSize-1].
 * Any contiguous window of length <= poolSize maps bijectively, avoiding React unmount/remount churn.
 */
export function getRecycledSlotIndex(itemIndex: number, poolSize: number): number {
  if (poolSize <= 1) return 0;
  return ((itemIndex % poolSize) + poolSize) % poolSize;
}

export interface FadeInAnimationConfig {
  keyframes: Array<{ opacity: number; transform: string }>;
  options: {
    duration: number;
    easing: string;
    fill: 'backwards' | 'both' | 'none';
  };
}

/**
 * Determines whether a recycled DOM slot should run the fade-in transition.
 * Avoids re-animating rows that stay stationary across scroll frames or receive non-scroll prop updates.
 */
export function shouldAnimateSlotAppearance(
  prevItemKey: string | null,
  nextItemKey: string,
  animateOnMount = true
): boolean {
  if (prevItemKey === nextItemKey) return false;
  if (prevItemKey === null) return animateOnMount;
  return true;
}

/**
 * Builds GPU-composited keyframe parameters for entering LazyColumn rows.
 * Fast flick scrolls use a shorter, opacity-focused fade to keep text legible immediately.
 */
export function getSlotFadeInConfig(
  isFastScroll: boolean,
  fadeDurationMs = 200
): FadeInAnimationConfig {
  const safeDuration = Math.max(80, Math.min(500, fadeDurationMs));

  if (isFastScroll) {
    return {
      keyframes: [
        { opacity: 0.25, transform: 'translate3d(0, 0, 0)' },
        { opacity: 1, transform: 'translate3d(0, 0, 0)' },
      ],
      options: {
        duration: Math.max(90, Math.round(safeDuration * 0.65)),
        easing: 'ease-out',
        fill: 'backwards',
      },
    };
  }

  return {
    keyframes: [
      { opacity: 0, transform: 'translate3d(0, 6px, 0)' },
      { opacity: 1, transform: 'translate3d(0, 0, 0)' },
    ],
    options: {
      duration: safeDuration,
      easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
      fill: 'backwards',
    },
  };
}
