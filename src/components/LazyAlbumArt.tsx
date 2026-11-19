import React, { useEffect, useRef, useState } from 'react';
import { Disc, FileAudio } from 'lucide-react';

export interface LazyAlbumArtProps {
  src?: string;
  alt?: string;
  accentHex?: string;
  isCurrent?: boolean;
  variant?: 'row' | 'album';
  root?: Element | null;
  rootMargin?: string;
  className?: string;
  onVisible?: () => void;
}

/**
 * IntersectionObserver-driven lazy album artwork renderer.
 * Defers <img> mounting/decoding until the artwork enters (or nears) the viewport,
 * and drops the DOM <img> when scrolled out of view to keep headunit RAM minimal.
 */
export const LazyAlbumArt: React.FC<LazyAlbumArtProps> = ({
  src,
  alt = '',
  accentHex = '#a3e635',
  isCurrent = false,
  variant = 'row',
  root = null,
  rootMargin = '100px 0px',
  className = '',
  onVisible,
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [isIntersecting, setIsIntersecting] = useState(false);
  const [isLoaded, setIsLoaded] = useState(false);
  const [hasError, setHasError] = useState(false);
  const notifiedVisibleRef = useRef(false);

  useEffect(() => {
    setIsLoaded(false);
    setHasError(false);
    notifiedVisibleRef.current = false;
  }, [src]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    if (typeof IntersectionObserver === 'undefined') {
      setIsIntersecting(true);
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          const visible = entry.isIntersecting || entry.intersectionRatio > 0;
          setIsIntersecting(visible);
          if (visible && onVisible && !notifiedVisibleRef.current) {
            notifiedVisibleRef.current = true;
            onVisible();
          }
        }
      },
      {
        root,
        rootMargin,
        threshold: 0.01,
      }
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, [root, rootMargin, onVisible]);

  const showImage = Boolean(isIntersecting && src && !hasError);

  if (variant === 'album') {
    return (
      <div
        ref={containerRef}
        className={`aspect-square rounded-xl bg-gradient-to-b from-slate-800 to-black flex items-center justify-center overflow-hidden relative ${className}`}
      >
        <Disc
          className={`w-12 h-12 transition-opacity duration-200 ${
            showImage && isLoaded ? 'opacity-0' : 'opacity-60 group-hover:scale-105 transition-transform'
          }`}
          style={{ color: accentHex }}
        />
        {showImage && (
          <img
            src={src}
            alt={alt}
            loading="lazy"
            decoding="async"
            onLoad={() => setIsLoaded(true)}
            onError={() => setHasError(true)}
            className={`absolute inset-0 w-full h-full object-cover transition-opacity duration-200 ${
              isLoaded ? 'opacity-100' : 'opacity-0'
            }`}
          />
        )}
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 border border-slate-800 bg-black/60 shadow-inner overflow-hidden relative ${className}`}
      style={isCurrent ? { borderColor: accentHex } : {}}
    >
      <FileAudio
        className={`w-4 h-4 transition-opacity duration-150 ${
          showImage && isLoaded ? 'opacity-0' : 'opacity-100'
        }`}
        style={{ color: isCurrent ? accentHex : '#94a3b8' }}
      />
      {showImage && (
        <img
          src={src}
          alt={alt}
          loading="lazy"
          decoding="async"
          onLoad={() => setIsLoaded(true)}
          onError={() => setHasError(true)}
          className={`absolute inset-0 w-full h-full object-cover transition-opacity duration-200 ${
            isLoaded ? 'opacity-100' : 'opacity-0'
          }`}
        />
      )}
    </div>
  );
};
