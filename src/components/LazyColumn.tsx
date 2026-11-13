import React, {
  useState,
  useEffect,
  useLayoutEffect,
  useRef,
  useMemo,
  useCallback,
} from 'react';
import {
  calculateVirtualWindow,
  buildAlphabetIndex,
  isFastScrolling,
  getSlotPoolSize,
  getRecycledSlotIndex,
  shouldAnimateSlotAppearance,
  getSlotFadeInConfig,
} from '../utils/virtualList';

export interface LazyColumnProps<T> {
  items: T[];
  itemHeight?: number; // Fixed height in pixels for each row (default 68px)
  overscan?: number; // Extra rows rendered above/below viewport (default 4)
  renderItem: (item: T, index: number, isFastScrolling: boolean) => React.ReactNode;
  keyExtractor: (item: T, index: number) => string;
  className?: string;
  viewportHeight?: number; // Optional explicit height; otherwise measured dynamically
  emptyPlaceholder?: React.ReactNode;
  enableAlphabetScrubber?: boolean;
  enableFadeIn?: boolean; // Gentle compositor fade-in when recycled rows bind new items
  fadeDurationMs?: number;
  accentHex?: string;
  scrollRequest?: { index: number; nonce: number } | null;
}

interface RecycledRowProps {
  itemKey: string;
  isFast: boolean;
  enableFadeIn: boolean;
  fadeDurationMs: number;
  children: React.ReactNode;
}

const RecycledRow: React.FC<RecycledRowProps> = ({
  itemKey,
  isFast,
  enableFadeIn,
  fadeDurationMs,
  children,
}) => {
  const innerRef = useRef<HTMLDivElement | null>(null);
  const prevKeyRef = useRef<string | null>(null);
  const animRef = useRef<Animation | null>(null);

  useLayoutEffect(() => {
    const prevKey = prevKeyRef.current;
    prevKeyRef.current = itemKey;

    if (!enableFadeIn) return;
    if (!shouldAnimateSlotAppearance(prevKey, itemKey, true)) return;

    const el = innerRef.current;
    if (!el || typeof el.animate !== 'function') return;

    const prefersReduced =
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) return;

    animRef.current?.cancel();
    const { keyframes, options } = getSlotFadeInConfig(isFast, fadeDurationMs);
    animRef.current = el.animate(keyframes, options);
  }, [itemKey, isFast, enableFadeIn, fadeDurationMs]);

  useEffect(() => {
    return () => {
      animRef.current?.cancel();
    };
  }, []);

  return (
    <div
      ref={innerRef}
      data-item-key={itemKey}
      className="h-full w-full"
      style={{
        willChange: enableFadeIn ? 'opacity, transform' : undefined,
      }}
    >
      {children}
    </div>
  );
};

export function LazyColumn<T extends { title?: string; name?: string }>({
  items,
  itemHeight = 68,
  overscan = 4,
  renderItem,
  keyExtractor,
  className = '',
  viewportHeight: explicitViewportHeight,
  emptyPlaceholder,
  enableAlphabetScrubber = true,
  enableFadeIn = true,
  fadeDurationMs = 200,
  accentHex = '#A3E635',
  scrollRequest = null,
}: LazyColumnProps<T>) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [scrollTop, setScrollTop] = useState(0);
  const [measuredHeight, setMeasuredHeight] = useState(explicitViewportHeight || 480);
  const [isFast, setIsFast] = useState(false);
  const [activeScrubLetter, setActiveScrubLetter] = useState<string | null>(null);

  // Velocity tracking & cleanup refs
  const lastScrollTopRef = useRef(0);
  const lastScrollTimeRef = useRef(Date.now());
  const fastScrollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const scrubTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const rafIdRef = useRef<number | null>(null);

  // Cleanup timers and animation frames on unmount
  useEffect(() => {
    return () => {
      if (rafIdRef.current !== null) cancelAnimationFrame(rafIdRef.current);
      if (fastScrollTimeoutRef.current) clearTimeout(fastScrollTimeoutRef.current);
      if (scrubTimeoutRef.current) clearTimeout(scrubTimeoutRef.current);
    };
  }, []);

  // Measure container height dynamically
  useEffect(() => {
    if (explicitViewportHeight) {
      setMeasuredHeight(explicitViewportHeight);
      return;
    }

    const container = containerRef.current;
    if (!container) return;

    const updateHeight = () => {
      const h = container.clientHeight;
      if (h > 0) {
        setMeasuredHeight(h);
      }
    };

    updateHeight();

    if (typeof ResizeObserver !== 'undefined') {
      const ro = new ResizeObserver(() => {
        updateHeight();
      });
      ro.observe(container);
      return () => ro.disconnect();
    }
  }, [explicitViewportHeight]);

  // Clamp scrollTop when item list shrinks (e.g., filtering)
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const maxScroll = Math.max(0, items.length * itemHeight - measuredHeight);
    if (container.scrollTop > maxScroll) {
      container.scrollTop = maxScroll;
      setScrollTop(maxScroll);
    }
  }, [items.length, itemHeight, measuredHeight]);

  // Handle scroll events with RAF pacing and fast-scroll velocity detection
  const handleScroll = useCallback(() => {
    const container = containerRef.current;
    if (!container) return;

    if (rafIdRef.current !== null) return;

    rafIdRef.current = requestAnimationFrame(() => {
      rafIdRef.current = null;
      const currentScrollTop = container.scrollTop;
      const now = Date.now();
      const deltaT = now - lastScrollTimeRef.current;

      const fast = isFastScrolling(
        lastScrollTopRef.current,
        currentScrollTop,
        deltaT,
        1.2 // 1.2 px/ms = 1200 px/sec
      );

      lastScrollTopRef.current = currentScrollTop;
      lastScrollTimeRef.current = now;

      setScrollTop(currentScrollTop);

      if (fast) {
        setIsFast(true);
        if (fastScrollTimeoutRef.current) {
          clearTimeout(fastScrollTimeoutRef.current);
        }
        fastScrollTimeoutRef.current = setTimeout(() => {
          setIsFast(false);
        }, 160);
      }
    });
  }, []);

  // Compute virtual window
  const virtualWindow = useMemo(() => {
    return calculateVirtualWindow(
      items.length,
      itemHeight,
      measuredHeight,
      scrollTop,
      overscan
    );
  }, [items.length, itemHeight, measuredHeight, scrollTop, overscan]);

  // Fixed DOM slot pool size for ViewHolder-style recycling
  const poolSize = useMemo(
    () => getSlotPoolSize(measuredHeight, itemHeight, overscan),
    [measuredHeight, itemHeight, overscan]
  );

  // Pre-calculate alphabet index for quick automotive scrubbing
  const alphabetIndex = useMemo(() => {
    if (!enableAlphabetScrubber || items.length < 15) return [];
    return buildAlphabetIndex(items);
  }, [items, enableAlphabetScrubber]);

  // Scroll to track index
  const scrollToIndex = useCallback(
    (index: number, letter?: string) => {
      const container = containerRef.current;
      if (!container) return;
      const targetTop = index * itemHeight;
      container.scrollTop = targetTop;
      setScrollTop(targetTop);

      if (letter) {
        setActiveScrubLetter(letter);
        if (scrubTimeoutRef.current) clearTimeout(scrubTimeoutRef.current);
        scrubTimeoutRef.current = setTimeout(() => setActiveScrubLetter(null), 900);
      }
    },
    [itemHeight]
  );

  // External scroll-to-index request (e.g. "Jump to Playing Track")
  useEffect(() => {
    if (!scrollRequest || scrollRequest.index < 0 || scrollRequest.index >= items.length) return;
    scrollToIndex(scrollRequest.index);
  }, [scrollRequest, items.length, scrollToIndex]);

  if (items.length === 0) {
    return <>{emptyPlaceholder || null}</>;
  }

  // Build visible entries mapped to deterministic recycled DOM slots
  const visibleEntries: Array<{ item: T; index: number; slotId: number; itemKey: string }> = [];
  for (let i = virtualWindow.startIndex; i <= virtualWindow.endIndex; i++) {
    if (i >= 0 && i < items.length) {
      visibleEntries.push({
        item: items[i],
        index: i,
        slotId: getRecycledSlotIndex(i, poolSize),
        itemKey: keyExtractor(items[i], i),
      });
    }
  }
  // Keep React child order sorted by slotId so DOM nodes stay stationary in the tree
  visibleEntries.sort((a, b) => a.slotId - b.slotId);

  const hasScrubber = alphabetIndex.length > 0;

  return (
    <div
      className="relative w-full overflow-hidden rounded-2xl border border-slate-800/80 bg-[#0b1120] shadow-xl"
      data-testid="lazy-column"
      data-mounted-count={virtualWindow.visibleCount}
      data-total-count={items.length}
      data-fade-enabled={enableFadeIn ? 'true' : 'false'}
    >
      {/* Scrollable Virtual Viewport with strict layout containment & GPU layer promotion */}
      <div
        ref={containerRef}
        onScroll={handleScroll}
        className={`w-full overflow-y-auto relative scrollbar-thin scrollbar-thumb-slate-700 scrollbar-track-transparent ${className}`}
        style={{
          maxHeight: explicitViewportHeight ? `${explicitViewportHeight}px` : '65vh',
          height: explicitViewportHeight ? `${explicitViewportHeight}px` : '65vh',
          contain: 'strict',
          willChange: 'scroll-position',
          transform: 'translateZ(0)',
          WebkitOverflowScrolling: 'touch',
        }}
      >
        {/* Full virtual scroll height placeholder */}
        <div
          style={{
            height: `${virtualWindow.totalHeight}px`,
            position: 'relative',
            width: '100%',
          }}
        >
          {visibleEntries.map(({ item, index, slotId, itemKey }) => (
            <div
              key={`recycled-slot-${slotId}`}
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: hasScrubber ? '2rem' : 0,
                height: `${itemHeight}px`,
                transform: `translate3d(0, ${index * itemHeight}px, 0)`,
                willChange: 'transform',
                contain: 'layout style',
                overflow: 'hidden',
              }}
              className="border-b border-slate-800/60"
            >
              <RecycledRow
                itemKey={itemKey}
                isFast={isFast}
                enableFadeIn={enableFadeIn}
                fadeDurationMs={fadeDurationMs}
              >
                {renderItem(item, index, isFast)}
              </RecycledRow>
            </div>
          ))}
        </div>
      </div>

      {/* Automotive Alphabet Quick-Scrubber (Right Edge) */}
      {hasScrubber && (
        <div
          aria-label="Automotive Quick-Letter Jump"
          className="absolute right-1.5 top-2 bottom-2 w-6 flex flex-col items-center justify-between py-1.5 z-20 pointer-events-auto bg-black/50 rounded-full border border-slate-800/70 text-[10px] font-mono text-slate-400 select-none"
        >
          {alphabetIndex.map(({ letter, index }) => (
            <button
              key={letter}
              type="button"
              aria-label={`Jump to ${letter}`}
              onClick={(e) => {
                e.stopPropagation();
                scrollToIndex(index, letter);
              }}
              className="w-5 h-5 flex items-center justify-center rounded hover:bg-slate-700 hover:text-white transition-colors active:scale-125"
            >
              {letter}
            </button>
          ))}
        </div>
      )}

      {/* Floating Center Scrub Letter HUD */}
      {activeScrubLetter && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none z-30">
          <div
            className="w-16 h-16 rounded-2xl bg-black/90 border-2 flex items-center justify-center text-3xl font-bold font-mono shadow-2xl"
            style={{ borderColor: accentHex, color: accentHex }}
          >
            {activeScrubLetter}
          </div>
        </div>
      )}
    </div>
  );
}
