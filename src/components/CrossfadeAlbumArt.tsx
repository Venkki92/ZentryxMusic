import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  resolveEffectiveArtworkSrc,
  shouldCrossfadeArtwork,
} from '../utils/artworkTransition';

export interface CrossfadeAlbumArtProps {
  src?: string | null;
  alt: string;
  trackKey?: string;
  fallback: React.ReactNode;
  className?: string;
  imgClassName?: string;
  durationMs?: number;
  /** Enables a gentle micro-scale settle (1.03 -> 1.0) during crossfade transitions. */
  subtleScale?: boolean;
}

interface ArtLayer {
  id: number;
  src: string | null;
  alt: string;
}

type TransitionPhase = 'idle' | 'preparing' | 'crossfading';

const CROSSFADE_EASING = 'cubic-bezier(0.22, 1, 0.36, 1)';
const IMAGE_READY_TIMEOUT_MS = 220;

/**
 * Renders album artwork with a smooth simultaneous cross-dissolve whenever the
 * displayed artwork changes (image -> image, image -> fallback, or fallback -> image).
 * Waits for the incoming image to decode before beginning the fade so there are
 * no blank flashes, harsh pop-ins, or abrupt outgoing snap-outs.
 */
export const CrossfadeAlbumArt: React.FC<CrossfadeAlbumArtProps> = ({
  src,
  alt,
  fallback,
  className = 'w-full h-full',
  imgClassName = 'w-full h-full object-cover',
  durationMs = 350,
  subtleScale = true,
}) => {
  const [erroredSrcs, setErroredSrcs] = useState<Record<string, true>>({});
  const effectiveSrc = resolveEffectiveArtworkSrc(src, erroredSrcs);

  const seqRef = useRef(1);
  const [activeLayer, setActiveLayer] = useState<ArtLayer>(() => ({
    id: 0,
    src: effectiveSrc,
    alt,
  }));
  const [previousLayer, setPreviousLayer] = useState<ArtLayer | null>(null);
  const [phase, setPhase] = useState<TransitionPhase>('idle');

  const activeLayerRef = useRef<ArtLayer>(activeLayer);
  const previousLayerRef = useRef<ArtLayer | null>(previousLayer);
  const phaseRef = useRef<TransitionPhase>(phase);
  const altRef = useRef<string>(alt);
  const pendingLayerIdRef = useRef<number | null>(null);
  const timersRef = useRef<{
    raf1: number;
    raf2: number;
    loadFallbackTimer: number | null;
    cleanupTimer: number | null;
  }>({
    raf1: 0,
    raf2: 0,
    loadFallbackTimer: null,
    cleanupTimer: null,
  });

  altRef.current = alt;
  activeLayerRef.current = activeLayer;
  previousLayerRef.current = previousLayer;
  phaseRef.current = phase;

  const clearScheduledTransition = useCallback(() => {
    const timers = timersRef.current;
    if (timers.raf1) {
      cancelAnimationFrame(timers.raf1);
      timers.raf1 = 0;
    }
    if (timers.raf2) {
      cancelAnimationFrame(timers.raf2);
      timers.raf2 = 0;
    }
    if (timers.loadFallbackTimer !== null) {
      window.clearTimeout(timers.loadFallbackTimer);
      timers.loadFallbackTimer = null;
    }
    if (timers.cleanupTimer !== null) {
      window.clearTimeout(timers.cleanupTimer);
      timers.cleanupTimer = null;
    }
  }, []);

  const beginCrossfadeForLayer = useCallback(
    (layerId: number, outgoingLayerId: number) => {
      if (pendingLayerIdRef.current !== layerId) {
        return;
      }
      pendingLayerIdRef.current = null;

      const timers = timersRef.current;
      if (timers.loadFallbackTimer !== null) {
        window.clearTimeout(timers.loadFallbackTimer);
        timers.loadFallbackTimer = null;
      }

      timers.raf1 = requestAnimationFrame(() => {
        timers.raf2 = requestAnimationFrame(() => {
          phaseRef.current = 'crossfading';
          setPhase('crossfading');

          timers.cleanupTimer = window.setTimeout(() => {
            setPreviousLayer((cur) => (cur?.id === outgoingLayerId ? null : cur));
            phaseRef.current = 'idle';
            setPhase('idle');
            timers.cleanupTimer = null;
          }, durationMs + 40);
        });
      });
    },
    [durationMs]
  );

  // Keep alt text synchronized without re-triggering a crossfade
  useEffect(() => {
    setActiveLayer((cur) => (cur.alt === alt ? cur : { ...cur, alt }));
  }, [alt]);

  // Trigger a smooth crossfade only when the resolved artwork visual actually changes
  useEffect(() => {
    const current = activeLayerRef.current;
    if (!shouldCrossfadeArtwork(current.src, effectiveSrc)) {
      return;
    }

    clearScheduledTransition();

    // During rapid track skipping, if the previous incoming layer never became visible yet,
    // retain the last actually-visible outgoing layer so it does not snap out harshly.
    const outgoingSnapshot =
      phaseRef.current === 'preparing' && previousLayerRef.current
        ? previousLayerRef.current
        : current;

    const nextLayer: ArtLayer = {
      id: ++seqRef.current,
      src: effectiveSrc,
      alt: altRef.current,
    };

    previousLayerRef.current = outgoingSnapshot;
    activeLayerRef.current = nextLayer;
    phaseRef.current = 'preparing';
    pendingLayerIdRef.current = nextLayer.id;

    setPreviousLayer(outgoingSnapshot);
    setActiveLayer(nextLayer);
    setPhase('preparing');

    if (effectiveSrc === null) {
      // Transitioning to fallback needs no image decode wait
      beginCrossfadeForLayer(nextLayer.id, outgoingSnapshot.id);
    } else {
      // Safety net in case a slow/stalled image delays onLoad
      timersRef.current.loadFallbackTimer = window.setTimeout(() => {
        beginCrossfadeForLayer(nextLayer.id, outgoingSnapshot.id);
      }, IMAGE_READY_TIMEOUT_MS);
    }
  }, [effectiveSrc, clearScheduledTransition, beginCrossfadeForLayer]);

  useEffect(() => clearScheduledTransition, [clearScheduledTransition]);

  const handleIncomingImgRef = useCallback(
    (node: HTMLImageElement | null, layerId: number) => {
      if (!node || pendingLayerIdRef.current !== layerId) return;
      if (node.complete && node.naturalWidth > 0 && previousLayerRef.current) {
        beginCrossfadeForLayer(layerId, previousLayerRef.current.id);
      }
    },
    [beginCrossfadeForLayer]
  );

  const renderLayerContent = (layer: ArtLayer, isIncoming: boolean) => {
    const validSrc = resolveEffectiveArtworkSrc(layer.src, erroredSrcs);
    if (validSrc) {
      return (
        <img
          ref={isIncoming ? (node) => handleIncomingImgRef(node, layer.id) : undefined}
          src={validSrc}
          alt={layer.alt}
          decoding="async"
          onLoad={
            isIncoming
              ? () => {
                  if (previousLayerRef.current) {
                    beginCrossfadeForLayer(layer.id, previousLayerRef.current.id);
                  }
                }
              : undefined
          }
          onError={() => {
            setErroredSrcs((prev) => (prev[validSrc] ? prev : { ...prev, [validSrc]: true }));
            if (isIncoming && previousLayerRef.current) {
              beginCrossfadeForLayer(layer.id, previousLayerRef.current.id);
            }
          }}
          className={imgClassName}
        />
      );
    }
    return <div className="w-full h-full flex items-center justify-center">{fallback}</div>;
  };

  const isCrossfading = phase === 'crossfading';
  const isPreparing = phase === 'preparing';

  const transitionStyle: React.CSSProperties | undefined = isCrossfading
    ? {
        transitionDuration: `${durationMs}ms`,
        transitionTimingFunction: CROSSFADE_EASING,
      }
    : undefined;

  const outgoingClassName = isCrossfading
    ? `opacity-0 ${subtleScale ? 'scale-[0.985]' : 'scale-100'} transition-[opacity,transform] motion-reduce:transition-none will-change-[opacity,transform]`
    : 'opacity-100 scale-100 transition-none';

  const incomingClassName = isPreparing
    ? `opacity-0 ${subtleScale ? 'scale-[1.03]' : 'scale-100'} transition-none will-change-[opacity,transform]`
    : isCrossfading
      ? 'opacity-100 scale-100 transition-[opacity,transform] motion-reduce:transition-none will-change-[opacity,transform]'
      : 'opacity-100 scale-100';

  return (
    <div className={`relative overflow-hidden ${className}`}>
      {previousLayer && (
        <div
          key={`prev-${previousLayer.id}`}
          aria-hidden="true"
          className={`absolute inset-0 w-full h-full pointer-events-none ${outgoingClassName}`}
          style={transitionStyle}
        >
          {renderLayerContent(previousLayer, false)}
        </div>
      )}

      <div
        key={`active-${activeLayer.id}`}
        className={`absolute inset-0 w-full h-full ${incomingClassName}`}
        style={transitionStyle}
      >
        {renderLayerContent(activeLayer, true)}
      </div>
    </div>
  );
};
