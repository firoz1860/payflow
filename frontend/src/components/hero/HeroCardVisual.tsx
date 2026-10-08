import { motion, useReducedMotion } from 'framer-motion';

/**
 * HeroCardVisual — a self-contained, premium animated PayFlow hero visual.
 *
 * This is a pure CSS + framer-motion composition that mirrors the Spline 3D
 * scene (a floating brand-blue payment card, orbiting transaction-flow rings,
 * and drifting payment nodes). It is the production fallback that ships by
 * default: it looks complete and communicates PayFlow even when the live
 * Spline scene / WebGL is unavailable, and it is lightweight (transform &
 * opacity animations only, GPU-friendly). Motion is disabled automatically
 * when the viewer prefers reduced motion.
 *
 * Decorative only — the hero's meaning lives in the headline/CTAs, so the
 * container is marked aria-hidden by the parent.
 */
export function HeroCardVisual() {
  const reduce = useReducedMotion();

  const float = reduce ? {} : { y: [0, -16, 0], rotateZ: [0, 1.2, 0] };
  const floatTransition = { duration: 7, repeat: Infinity, ease: 'easeInOut' as const };

  return (
    <div className="relative mx-auto flex h-full w-full max-w-[560px] items-center justify-center">
      {/* Ambient glow */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute left-1/2 top-1/2 h-[360px] w-[360px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-brand-500/30 blur-[90px]" />
        <div className="absolute left-[30%] top-[62%] h-40 w-40 -translate-x-1/2 -translate-y-1/2 rounded-full bg-indigo-500/25 blur-[70px]" />
        <div className="absolute left-[72%] top-[30%] h-36 w-36 -translate-x-1/2 -translate-y-1/2 rounded-full bg-cyan-400/20 blur-[70px]" />
      </div>

      {/* Orbit / transaction-flow rings */}
      <OrbitRing size={440} tilt={64} spin={28} reduce={!!reduce} className="border-brand-400/40" />
      <OrbitRing size={340} tilt={70} spin={-36} reduce={!!reduce} className="border-sky-300/30" />

      {/* Drifting payment nodes */}
      <PaymentNode className="left-[8%] top-[34%]" delay={0} reduce={!!reduce} variant="gold" size={44} />
      <PaymentNode className="right-[6%] top-[20%]" delay={0.8} reduce={!!reduce} variant="gold" size={38} />
      <PaymentNode className="left-[14%] bottom-[16%]" delay={1.4} reduce={!!reduce} variant="blue" size={40} />
      <PaymentNode className="right-[12%] bottom-[12%]" delay={0.4} reduce={!!reduce} variant="blue" size={30} />

      {/* Glow dots */}
      <GlowDot className="left-[22%] top-[18%]" delay={0.2} reduce={!!reduce} />
      <GlowDot className="right-[24%] top-[52%]" delay={0.9} reduce={!!reduce} />
      <GlowDot className="left-[40%] bottom-[8%]" delay={1.6} reduce={!!reduce} />

      {/* The card */}
      <div className="relative" style={{ perspective: 1400 }}>
        <motion.div
          initial={reduce ? false : { opacity: 0, y: 24, rotateX: 20 }}
          animate={reduce ? { opacity: 1 } : { opacity: 1, y: 0, rotateX: 0 }}
          transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }}
          className="relative"
          style={{ transformStyle: 'preserve-3d' }}
        >
          <motion.div
            animate={float}
            transition={floatTransition}
            className="relative aspect-[1.586/1] w-[290px] overflow-hidden rounded-[22px] p-5 shadow-2xl shadow-brand-900/50 sm:w-[340px] md:w-[380px]"
            style={{
              background: 'linear-gradient(135deg, #3b82f6 0%, #2563eb 42%, #1e3a8a 100%)',
              transform: reduce ? undefined : 'rotateX(10deg) rotateY(-16deg) rotateZ(3deg)',
              transformStyle: 'preserve-3d',
            }}
          >
            {/* Sheen */}
            <div className="pointer-events-none absolute -inset-1 bg-gradient-to-tr from-white/0 via-white/15 to-white/0" />
            {/* Top row: wordmark + contactless */}
            <div className="relative flex items-start justify-between">
              <span className="text-sm font-semibold tracking-tight text-white/95">PayFlow</span>
              <ContactlessGlyph />
            </div>
            {/* Chip */}
            <div className="relative mt-4 h-9 w-12 rounded-md bg-gradient-to-br from-[#f0d98a] via-[#d9b45b] to-[#b8923f] shadow-inner">
              <div className="absolute inset-x-1 top-1/2 h-px -translate-y-[3px] bg-[#9a7c36]/70" />
              <div className="absolute inset-x-1 top-1/2 h-px translate-y-[3px] bg-[#9a7c36]/70" />
              <div className="absolute inset-y-1 left-1/2 w-px -translate-x-1/2 bg-[#9a7c36]/70" />
            </div>
            {/* Number row */}
            <div className="relative mt-5 flex items-center gap-3">
              {[0, 1, 2, 3].map((i) => (
                <span key={i} className="h-2.5 w-11 rounded-full bg-white/80" />
              ))}
            </div>
            {/* Name / valid row */}
            <div className="relative mt-4 flex items-end justify-between">
              <div className="space-y-1.5">
                <span className="block h-1.5 w-16 rounded-full bg-white/45" />
                <span className="block h-2 w-24 rounded-full bg-white/70" />
              </div>
              <div className="space-y-1.5 text-right">
                <span className="block h-1.5 w-10 rounded-full bg-white/35" />
                <span className="block h-2 w-14 rounded-full bg-white/60" />
              </div>
            </div>
          </motion.div>
        </motion.div>
      </div>
    </div>
  );
}

function OrbitRing({
  size,
  tilt,
  spin,
  reduce,
  className,
}: {
  size: number;
  tilt: number;
  spin: number;
  reduce: boolean;
  className?: string;
}) {
  return (
    <motion.div
      className={`pointer-events-none absolute rounded-full border ${className ?? ''}`}
      style={{ width: size, height: size, transform: `rotateX(${tilt}deg)` }}
      animate={reduce ? undefined : { rotateZ: spin > 0 ? 360 : -360 }}
      transition={{ duration: Math.abs(spin), repeat: Infinity, ease: 'linear' }}
    />
  );
}

function PaymentNode({
  className,
  delay,
  reduce,
  variant,
  size,
}: {
  className?: string;
  delay: number;
  reduce: boolean;
  variant: 'gold' | 'blue';
  size: number;
}) {
  const bg =
    variant === 'gold'
      ? 'linear-gradient(135deg,#f0d98a,#d9b45b 60%,#b8923f)'
      : 'linear-gradient(135deg,#7cb0ff,#3b82f6 60%,#1d4ed8)';
  return (
    <motion.div
      className={`pointer-events-none absolute rounded-full shadow-lg ${className ?? ''}`}
      style={{ width: size, height: size, background: bg }}
      animate={reduce ? undefined : { y: [0, -14, 0] }}
      transition={{ duration: 4 + delay, repeat: Infinity, ease: 'easeInOut', delay }}
    >
      <div className="absolute inset-0 rounded-full ring-1 ring-inset ring-white/40" />
      <div className="absolute left-1/4 top-1/5 h-1/3 w-1/3 rounded-full bg-white/50 blur-[2px]" />
    </motion.div>
  );
}

function GlowDot({ className, delay, reduce }: { className?: string; delay: number; reduce: boolean }) {
  return (
    <motion.span
      className={`pointer-events-none absolute h-2.5 w-2.5 rounded-full bg-sky-300 shadow-[0_0_12px_4px_rgba(125,211,252,0.6)] ${className ?? ''}`}
      animate={reduce ? undefined : { opacity: [0.4, 1, 0.4], scale: [0.9, 1.15, 0.9] }}
      transition={{ duration: 3 + delay, repeat: Infinity, ease: 'easeInOut', delay }}
    />
  );
}

function ContactlessGlyph() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true" className="text-white/80">
      <path d="M9 8a6 6 0 0 1 0 8" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M12.5 6a9 9 0 0 1 0 12" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M16 4.5a12 12 0 0 1 0 15" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" opacity="0.7" />
    </svg>
  );
}
