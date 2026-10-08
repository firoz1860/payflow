import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowRight, CheckCircle2 } from 'lucide-react';
import { LandingNavbar } from '../components/landing/LandingNavbar';
import { LandingFooter } from '../components/landing/LandingFooter';
import { HeroVisual } from '../components/hero/HeroVisual';
import {
  Audiences,
  ArchitectureMap,
  CoreCapabilities,
  Correctness,
  FinalCTA,
  PaymentFlowSection,
  SecuritySection,
} from '../components/landing/StorySections';

const TRUST_POINTS = ['Idempotent APIs', 'Double-entry ledger', 'Verified webhooks', 'UPI QR checkout'];

const fadeUp = {
  hidden: { opacity: 0, y: 20 },
  show: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.6, delay: i * 0.08, ease: [0.16, 1, 0.3, 1] as const },
  }),
};

export function LandingPage() {
  return (
    <div className="relative min-h-screen overflow-x-hidden bg-slate-50 text-slate-900">
      {/* Ambient brand glow */}
      <div className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[720px] overflow-hidden">
        <div className="absolute -left-24 -top-24 h-96 w-96 rounded-full bg-brand-200/40 blur-3xl" />
        <div className="absolute right-[-6rem] top-10 h-[28rem] w-[28rem] rounded-full bg-indigo-200/35 blur-3xl" />
      </div>

      <LandingNavbar />

      <main>
        {/* HERO */}
        <section className="mx-auto max-w-7xl px-4 pb-16 pt-10 sm:px-6 lg:px-8 lg:pb-24 lg:pt-16">
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
                Payments infrastructure, engineered for correctness
              </motion.span>

              <motion.h1
                variants={fadeUp}
                custom={1}
                initial="hidden"
                animate="show"
                className="mt-5 text-balance text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl lg:text-6xl"
              >
                Move money.{' '}
                <span className="bg-gradient-to-r from-brand-600 to-indigo-500 bg-clip-text text-transparent">
                  Reconcile everything.
                </span>{' '}
                Grow faster.
              </motion.h1>

              <motion.p
                variants={fadeUp}
                custom={2}
                initial="hidden"
                animate="show"
                className="mx-auto mt-6 max-w-xl text-lg leading-relaxed text-slate-600 lg:mx-0"
              >
                PayFlow is a microservices payment platform for merchants — idempotent payment APIs,
                single-use UPI QR checkout, and a double-entry ledger that stays balanced. Every state
                change is driven only by a signature-verified provider webhook.
              </motion.p>

              <motion.div
                variants={fadeUp}
                custom={3}
                initial="hidden"
                animate="show"
                className="mt-8 flex flex-col items-center gap-3 sm:flex-row lg:justify-start"
              >
                <Link to="/register" className="btn-primary w-full px-6 py-3 text-base sm:w-auto">
                  Get started <ArrowRight className="h-4 w-4" />
                </Link>
                <a href="#platform" className="btn-secondary w-full px-6 py-3 text-base sm:w-auto">
                  Explore PayFlow
                </a>
              </motion.div>

              <motion.ul
                variants={fadeUp}
                custom={4}
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

            {/* Right: animated visual in a cinematic panel (Spline, with graceful fallback) */}
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
        </section>

        {/* STORY */}
        <CoreCapabilities />
        <PaymentFlowSection />
        <Correctness />
        <ArchitectureMap />
        <SecuritySection />
        <Audiences />
        <FinalCTA />
      </main>

      <LandingFooter />
    </div>
  );
}
