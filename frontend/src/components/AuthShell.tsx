import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { CheckCircle2 } from 'lucide-react';
import { PayFlowMark } from './landing/PayFlowMark';
import { HeroVisual } from './hero/HeroVisual';

const TRUST_POINTS = [
  'Idempotent, retry-safe payment APIs',
  'Double-entry ledger, balanced to the rupee',
  'Signature-verified provider webhooks',
];

/**
 * AuthShell — the shared backdrop for the sign-in / register pages.
 *
 * On large screens it presents a two-column split: a cinematic marketing panel
 * (brand, headline, the animated hero visual, and trust points) on the left and
 * the auth form (passed as `children`) on the right. Below `lg` the marketing
 * panel is hidden and the form is centered on the brand gradient — preserving
 * the original single-card auth experience. Form logic lives entirely in the
 * pages that render this shell; AuthShell only provides layout and branding.
 */
export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="relative min-h-screen overflow-hidden bg-gradient-to-br from-slate-900 via-slate-800 to-brand-900">
      {/* Ambient animated glow (shared with the original auth design) */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 1 }}
        className="pointer-events-none absolute inset-0 overflow-hidden"
      >
        <motion.div
          animate={{ x: [0, 30, 0], y: [0, -20, 0] }}
          transition={{ duration: 20, repeat: Infinity, ease: 'easeInOut' }}
          className="absolute -right-40 -top-40 h-96 w-96 rounded-full bg-brand-500/20 blur-3xl"
        />
        <motion.div
          animate={{ x: [0, -30, 0], y: [0, 20, 0] }}
          transition={{ duration: 25, repeat: Infinity, ease: 'easeInOut' }}
          className="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-brand-700/20 blur-3xl"
        />
      </motion.div>

      <div className="relative grid min-h-screen lg:grid-cols-2">
        {/* Marketing panel (large screens only) */}
        <aside className="relative hidden flex-col justify-between p-10 lg:flex xl:p-14">
          <Link to="/" className="flex items-center gap-2.5" aria-label="PayFlow home">
            <PayFlowMark className="h-9 w-9" />
            <span className="text-lg font-bold tracking-tight text-white">PayFlow</span>
          </Link>

          <div className="py-8">
            <span className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3.5 py-1.5 text-xs font-semibold uppercase tracking-wider text-brand-200">
              <span className="h-1.5 w-1.5 rounded-full bg-brand-400" />
              Smart payments, simplified
            </span>
            <h2 className="mt-5 max-w-md text-4xl font-bold leading-tight tracking-tight text-white">
              Move money.{' '}
              <span className="bg-gradient-to-r from-brand-300 to-indigo-300 bg-clip-text text-transparent">
                Reconcile everything.
              </span>
            </h2>

            <div className="relative mt-4 h-64 xl:h-72">
              <HeroVisual />
            </div>

            <ul className="mt-4 space-y-2">
              {TRUST_POINTS.map((point) => (
                <li key={point} className="flex items-center gap-2 text-sm text-slate-300">
                  <CheckCircle2 className="h-4 w-4 flex-shrink-0 text-brand-400" />
                  {point}
                </li>
              ))}
            </ul>
          </div>

          <p className="text-xs text-slate-400">
            © {new Date().getFullYear()} PayFlow. Payments infrastructure.
          </p>
        </aside>

        {/* Form column */}
        <main className="flex items-center justify-center p-4 sm:p-6">{children}</main>
      </div>
    </div>
  );
}
