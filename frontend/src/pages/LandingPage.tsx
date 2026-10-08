import { useRef } from 'react';
import { Link } from 'react-router-dom';
import { motion, useReducedMotion, useScroll, useTransform } from 'framer-motion';
import { ArrowRight, CheckCircle2 } from 'lucide-react';
import { cn } from '../lib/utils';
import { LandingNavbar } from '../components/landing/LandingNavbar';
import { LandingFooter } from '../components/landing/LandingFooter';
import { HeroVisual } from '../components/hero/HeroVisual';
import {
  ArchitectureMap,
  CoreCapabilities,
  FaqSection,
  FinalCTA,
  HowItWorks,
  Reliability,
  SecuritySection,
  WhoFor,
  WhyPayFlow,
} from '../components/landing/StorySections';

const TRUST_POINTS = [
  'Retry-safe payment APIs',
  'Verified provider updates',
  'Balanced ledger entries',
  'Merchant dashboard',
];

const fadeUp = {
  hidden: { opacity: 0, y: 20 },
  show: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.6, delay: i * 0.08, ease: [0.16, 1, 0.3, 1] as const },
  }),
};

export function LandingPage() {
  const reduce = useReducedMotion();
  const stageRef = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({ target: stageRef, offset: ['start start', 'end start'] });
  // Restrained depth: hero content eases back slightly and dims as the panel covers it.
  const heroScale = useTransform(scrollYProgress, [0, 0.45], [1, 0.97]);
  const heroDim = useTransform(scrollYProgress, [0, 0.5], [0, 0.32]);
  const pinned = !reduce; // curtain only when motion is allowed (and only lg+ via classes)

  return (
    <div className="relative min-h-screen bg-slate-50 text-slate-900">
      {/* Ambient brand glow (clipped in its own container so it never causes overflow) */}
      <div className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[720px] overflow-hidden">
        <div className="absolute -left-24 -top-24 h-96 w-96 rounded-full bg-brand-200/40 blur-3xl" />
        <div className="absolute right-[-6rem] top-10 h-[28rem] w-[28rem] rounded-full bg-indigo-200/35 blur-3xl" />
      </div>

      <LandingNavbar />

      <main>
        {/* CURTAIN STAGE: the hero stays pinned (lg+) while Core Capabilities rises over it */}
        <div ref={stageRef} className="relative">
          <section
            className={cn(
              'relative z-0',
              pinned && 'lg:sticky lg:top-16 lg:flex lg:h-[calc(100vh-4rem)] lg:items-center'
            )}
          >
            <motion.div
              style={pinned ? { scale: heroScale } : undefined}
              className="mx-auto w-full max-w-7xl px-4 pb-16 pt-10 sm:px-6 lg:px-8 lg:pb-0 lg:pt-0"
            >
              <div className="grid items-center gap-10 lg:grid-cols-[1.05fr_0.95fr] lg:gap-8">
                {/* Left: copy */}
                <div className="text-center lg:text-left">
                  <motion.span
                    variants={fadeUp}
                    custom={0}
                    initial="hidden"
                    animate="show"
                    className="inline-flex items-center gap-2 rounded-full border border-brand-200 bg-brand-50 px-3.5 py-1.5 text-xs font-semibold uppercase tracking-wider text-brand-700"
                  >
                    <span className="h-1.5 w-1.5 rounded-full bg-brand-500" />
                    Payment processing with a clear audit trail
                  </motion.span>

                  <motion.h1
                    variants={fadeUp}
                    custom={1}
                    initial="hidden"
                    animate="show"
                    className="mt-5 text-balance text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl lg:text-6xl"
                  >
                    Accept payments.{' '}
                    <span className="bg-gradient-to-r from-brand-600 to-indigo-500 bg-clip-text text-transparent">
                      Keep your records in balance.
                    </span>
                  </motion.h1>

                  <motion.p
                    variants={fadeUp}
                    custom={2}
                    initial="hidden"
                    animate="show"
                    className="mx-auto mt-6 max-w-xl text-lg leading-relaxed text-slate-600 lg:mx-0"
                  >
                    PayFlow brings payment collection, transaction tracking, and ledger records into one merchant
                    workspace. Create payments through an API, offer hosted or UPI QR checkout, and follow each
                    transaction from initiation to its recorded outcome.
                  </motion.p>

                  <motion.p
                    variants={fadeUp}
                    custom={3}
                    initial="hidden"
                    animate="show"
                    className="mx-auto mt-3 max-w-xl text-sm leading-relaxed text-slate-500 lg:mx-0"
                  >
                    Built to prevent duplicate payment creation during retries, verify provider updates, and keep
                    financial entries traceable.
                  </motion.p>

                  <motion.div
                    variants={fadeUp}
                    custom={4}
                    initial="hidden"
                    animate="show"
                    className="mt-8 flex flex-col items-center gap-3 sm:flex-row lg:justify-start"
                  >
                    <Link to="/register" className="btn-primary w-full px-6 py-3 text-base sm:w-auto">
                      Create merchant account <ArrowRight className="h-4 w-4" />
                    </Link>
                    <a href="#how" className="btn-secondary w-full px-6 py-3 text-base sm:w-auto">
                      See how payments work
                    </a>
                  </motion.div>

                  <motion.ul
                    variants={fadeUp}
                    custom={5}
                    initial="hidden"
                    animate="show"
                    className="mt-8 flex flex-wrap items-center justify-center gap-x-5 gap-y-2 lg:justify-start"
                  >
                    {TRUST_POINTS.map((point) => (
                      <li key={point} className="flex items-center gap-1.5 text-sm text-slate-500">
                        <CheckCircle2 className="h-4 w-4 text-brand-500" />
                        {point}
                      </li>
                    ))}
                  </motion.ul>
                </div>

                {/* Right: animated visual (Spline, with graceful fallback) */}
                <motion.div
                  initial={{ opacity: 0, scale: 0.96 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
                  className="relative"
                >
                  <div className="relative mx-auto aspect-[4/3] w-full max-w-[560px] overflow-hidden rounded-3xl border border-white/10 bg-gradient-to-br from-slate-900 via-slate-800 to-brand-900 shadow-2xl shadow-brand-900/30 sm:aspect-[5/4] lg:aspect-[4/3]">
                    <HeroVisual />
                  </div>
                </motion.div>
              </div>
            </motion.div>

            {/* Depth dimming on the exposed hero as the panel approaches (desktop only) */}
            {pinned && (
              <motion.div
                aria-hidden="true"
                style={{ opacity: heroDim }}
                className="pointer-events-none absolute inset-0 hidden bg-slate-950 lg:block"
              />
            )}
          </section>

          {/* Core Capabilities — opaque panel that slides up over the hero */}
          <div className="relative z-10 rounded-t-[1.75rem] border-t border-slate-200/70 bg-slate-50 shadow-[0_-24px_48px_-24px_rgba(15,23,42,0.18)] sm:rounded-t-[2rem] lg:rounded-t-[2.5rem]">
            <CoreCapabilities />
          </div>
        </div>

        {/* STORY (normal flow) */}
        <WhyPayFlow />
        <HowItWorks />
        <Reliability />
        <ArchitectureMap />
        <SecuritySection />
        <WhoFor />
        <FaqSection />
        <FinalCTA />
      </main>

      <LandingFooter />
    </div>
  );
}
