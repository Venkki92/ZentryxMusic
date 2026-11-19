import React, { useEffect, useRef, useState } from 'react';

export interface CrossfadeAlbumArtProps {
  src?: string | null;
  alt: string;
  trackKey?: string;
  fallback: React.ReactNode;
  className?: string;
  imgClassName?: string;
  durationMs?: number;
}

interface ArtLayer {
  id: number;
  src: string | null;
  alt: string;
}

/**
 * Renders album artwork with a subtle 300ms crossfade (fade-in over the outgoing art)
 * whenever the track's album art changes, preventing harsh image swaps or blank flashes.
 */
export const CrossfadeAlbumArt: React.FC<CrossfadeAlbumArtProps> = ({
  src,
  alt,
  trackKey,
  fallback,
  className = 'w-full h-full',
  imgClassName = 'w-full h-full object-cover',
  durationMs = 300,
}) => {
  const normalizedSrc = src || null;
  const seqRef = useRef(1);
  const [activeLayer, setActiveLayer] = useState<ArtLayer>(() => ({
    id: 0,
    src: normalizedSrc,
    alt,
  }));
  const [previousLayer, setPreviousLayer] = useState<ArtLayer | null>(null);
  const [incomingVisible, setIncomingVisible] = useState(true);
  const [erroredSrcs, setErroredSrcs] = useState<Record<string, true>>({});
  const lastSignatureRef = useRef<string>(`${normalizedSrc ?? '__none__'}`);

  useEffect(() => {
    const nextSignature = `${normalizedSrc ?? `__none__:${trackKey ?? ''}`}`;
    if (nextSignature === lastSignatureRef.current) {
      return;
    }

    // If both tracks resolve to the exact same valid artwork URL, skip redundant flash
    if (normalizedSrc && normalizedSrc === activeLayer.src) {
      lastSignatureRef.current = nextSignature;
      return;
    }

    lastSignatureRef.current = nextSignature;
    const prevSnapshot = activeLayer;
    const nextLayer: ArtLayer = {
      id: ++seqRef.current,
      src: normalizedSrc,
      alt,
    };

    setPreviousLayer(prevSnapshot);
    setActiveLayer(nextLayer);
    setIncomingVisible(false);

    let raf1 = 0;
    let raf2 = 0;
    raf1 = requestAnimationFrame(() => {
      raf2 = requestAnimationFrame(() => {
        setIncomingVisible(true);
      });
    });

    const cleanupTimer = window.setTimeout(() => {
      setPreviousLayer((cur) => (cur?.id === prevSnapshot.id ? null : cur));
    }, durationMs + 50);

    return () => {
      cancelAnimationFrame(raf1);
      cancelAnimationFrame(raf2);
      window.clearTimeout(cleanupTimer);
    };
  }, [normalizedSrc, trackKey, alt, durationMs]);

  const renderLayerContent = (layer: ArtLayer) => {
    const validSrc = layer.src && !erroredSrcs[layer.src] ? layer.src : null;
    if (validSrc) {
      return (
        <img
          src={validSrc}
          alt={layer.alt}
          decoding="async"
          onError={() => {
            setErroredSrcs((prev) => (prev[validSrc] ? prev : { ...prev, [validSrc]: true }));
          }}
          className={imgClassName}
        />
      );
    }
    return <div className="w-full h-full flex items-center justify-center">{fallback}</div>;
  };

  return (
    <div className={`relative overflow-hidden ${className}`}>
      {previousLayer && (
        <div
          key={`prev-${previousLayer.id}`}
          aria-hidden="true"
          className="absolute inset-0 w-full h-full pointer-events-none"
        >
          {renderLayerContent(previousLayer)}
        </div>
      )}

      <div
        key={`active-${activeLayer.id}`}
        className={`absolute inset-0 w-full h-full transition-opacity ease-out ${
          incomingVisible ? 'opacity-100' : 'opacity-0'
        }`}
        style={{ transitionDuration: `${durationMs}ms` }}
      >
        {renderLayerContent(activeLayer)}
      </div>
    </div>
  );
};
