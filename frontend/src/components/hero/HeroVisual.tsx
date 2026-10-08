import { Component, lazy, Suspense, useState, type ReactNode } from 'react';
import { useReducedMotion } from 'framer-motion';
import clsx from 'clsx';
import { HeroCardVisual } from './HeroCardVisual';

// Lazy-loaded so the Spline runtime (~heavy, WebGL) is only fetched as a
// separate chunk when a published scene URL is actually configured.
const Spline = lazy(() => import('@splinetool/react-spline'));

/** Catches any Spline load/render failure and signals the parent to fall back. */
class SplineErrorBoundary extends Component<{ onError: () => void; children: ReactNode }, { crashed: boolean }> {
  state = { crashed: false };
  static getDerivedStateFromError() {
    return { crashed: true };
  }
  componentDidCatch() {
    this.props.onError();
  }
  render() {
    if (this.state.crashed) return null;
    return this.props.children;
  }
}

/**
 * HeroVisual — the PayFlow hero's animated visual.
 *
 * Strategy (production-ready, honest about capabilities):
 *  - The CSS/framer-motion {@link HeroCardVisual} is ALWAYS rendered as the
 *    base layer, so the hero is complete and on-brand with zero dependencies.
 *  - When `VITE_SPLINE_SCENE_URL` points at a published Spline scene, the live
 *    interactive 3D scene is lazy-loaded and cross-fades in on top once it has
 *    finished loading. Any failure (network, WebGL, runtime) silently keeps the
 *    fallback visible.
 *  - When the viewer prefers reduced motion, the Spline scene is skipped
 *    entirely and the (motion-free) fallback is used.
 *
 * Decorative: marked aria-hidden; the hero's meaning is in the headline + CTAs.
 */
export function HeroVisual({ className }: { className?: string }) {
  const reduce = useReducedMotion();
  const sceneUrl = import.meta.env.VITE_SPLINE_SCENE_URL as string | undefined;
  const [loaded, setLoaded] = useState(false);
  const [failed, setFailed] = useState(false);

  const useSpline = Boolean(sceneUrl) && !reduce && !failed;

  return (
    <div className={clsx('relative h-full w-full', className)} aria-hidden="true">
      {/* Base layer: always-complete fallback (fades out once Spline is ready) */}
      <div
        className={clsx(
          'absolute inset-0 transition-opacity duration-700',
          loaded ? 'opacity-0' : 'opacity-100'
        )}
      >
        <HeroCardVisual />
      </div>

      {useSpline && (
        <SplineErrorBoundary
          onError={() => {
            setFailed(true);
            setLoaded(false);
          }}
        >
          <Suspense fallback={null}>
            <div
              className={clsx(
                'absolute inset-0 transition-opacity duration-700',
                loaded ? 'opacity-100' : 'opacity-0'
              )}
            >
              <Spline scene={sceneUrl as string} onLoad={() => setLoaded(true)} />
            </div>
          </Suspense>
        </SplineErrorBoundary>
      )}
    </div>
  );
}
