import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { ArrowRight, ChevronDown, Minus, Plus, ShieldCheck } from 'lucide-react';
import { cn } from '../../lib/utils';
import {
  AUDIENCES,
  CAPABILITIES,
  FAQ,
  HOW_STEPS,
  RELIABILITY,
  RELIABILITY_STATS,
  SECURITY,
  SERVICE_GROUPS,
  SERVICES,
  TECHNICAL_FLOW,
  WHY_PROBLEMS,
  type Service,
} from './landingContent';

const EASE = [0.16, 1, 0.3, 1] as const;

const container = { hidden: {}, show: { transition: { staggerChildren: 0.07, delayChildren: 0.04 } } };
const item = { hidden: { opacity: 0, y: 20 }, show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: EASE } } };

/** A scroll-revealed section. Reveal reverses naturally on scroll-up; static under reduced motion. */
function StorySection({ id, children, className }: { id?: string; children: ReactNode; className?: string }) {
  const reduce = useReducedMotion();
  if (reduce) {
    return (
      <section id={id} className={cn('scroll-mt-20', className)}>
        {children}
      </section>
    );
  }
  return (
    <motion.section
      id={id}
      className={cn('scroll-mt-20', className)}
      initial={{ opacity: 0, y: 28 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: false, amount: 0.15 }}
      transition={{ duration: 0.6, ease: EASE }}
    >
      {children}
    </motion.section>
  );
}

function SectionHeading({
  eyebrow,
  title,
  intro,
  dark,
}: {
  eyebrow: string;
  title: string;
  intro?: string;
  dark?: boolean;
}) {
  return (
    <div className="mx-auto max-w-2xl text-center">
      <span className={cn('text-xs font-semibold uppercase tracking-[0.18em]', dark ? 'text-brand-300' : 'text-brand-600')}>
        {eyebrow}
      </span>
      <h2 className={cn('mt-3 text-3xl font-bold tracking-tight sm:text-4xl', dark ? 'text-white' : 'text-slate-900')}>
        {title}
      </h2>
      {intro && <p className={cn('mt-4 text-lg leading-relaxed', dark ? 'text-slate-300' : 'text-slate-600')}>{intro}</p>}
    </div>
  );
}

/** A rounded, elevated surface that overlaps the previous section to create depth. */
function Surface({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        'relative -mt-6 overflow-hidden rounded-[2.5rem] border border-white/70 bg-white/70 shadow-[0_24px_64px_-32px_rgba(15,23,42,0.25)] backdrop-blur-xl sm:-mt-8 lg:-mt-10',
        className
      )}
    >
      {children}
    </div>
  );
}

/* ------------------------------------------------------------- capabilities */

function ExpandableCard({
  title,
  icon: Icon,
  summary,
  detail,
  expanded,
  dimmed,
  onToggle,
}: {
  title: string;
  icon: typeof ShieldCheck;
  summary: string;
  detail: string;
  expanded: boolean;
  dimmed: boolean;
  onToggle: () => void;
}) {
  const reduce = useReducedMotion();
  return (
    <motion.div
      variants={item}
      layout={reduce ? false : true}
      transition={{ layout: { duration: 0.35, ease: EASE } }}
      role="button"
      tabIndex={0}
      aria-expanded={expanded}
      onClick={onToggle}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onToggle();
        }
      }}
      className={cn(
        'card group relative cursor-pointer p-6 text-left outline-none transition-[transform,box-shadow,opacity] duration-300',
        'focus-visible:ring-2 focus-visible:ring-blue-500/50',
        expanded ? 'z-10 shadow-xl ring-1 ring-blue-200' : 'hover:-translate-y-1 hover:shadow-lg',
        dimmed && 'opacity-55'
      )}
    >
      <motion.div layout={reduce ? false : 'position'} className="flex items-start gap-3">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
          <Icon className="h-5 w-5" />
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="text-base font-semibold text-slate-900">{title}</h3>
          <p className="mt-1 text-sm leading-relaxed text-slate-600">{summary}</p>
        </div>
        <span
          className={cn(
            'flex h-7 w-7 shrink-0 items-center justify-center rounded-full border transition-colors',
            expanded ? 'border-blue-200 bg-blue-50 text-blue-600' : 'border-slate-200 text-slate-400 group-hover:text-slate-600'
          )}
        >
          {expanded ? <Minus className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
        </span>
      </motion.div>

      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            key="detail"
            initial={reduce ? { opacity: 0 } : { opacity: 0, height: 0 }}
            animate={reduce ? { opacity: 1 } : { opacity: 1, height: 'auto' }}
            exit={reduce ? { opacity: 0 } : { opacity: 0, height: 0 }}
            transition={{ duration: 0.3, ease: EASE }}
            className="overflow-hidden"
          >
            <p className="mt-4 border-t border-slate-100 pt-4 text-sm leading-relaxed text-slate-600">{detail}</p>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}

/**
 * CoreCapabilities — the panel that rises over the hero in the curtain
 * transition. It is a plain opaque section (no scroll-fade) so the overlap is
 * its reveal; LandingPage supplies the rounded top + stacking.
 */
export function CoreCapabilities() {
  const [expanded, setExpanded] = useState<string | null>(null);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setExpanded(null);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  return (
    <section id="platform" className="mx-auto max-w-7xl scroll-mt-20 px-4 py-20 sm:px-6 lg:px-8">
      <SectionHeading
        eyebrow="What you can do"
        title="Collect payments. Track outcomes. Understand the records."
        intro="A clear view of payment activity for merchants, and the APIs and controls developers need to integrate it. Select a capability to read how it works."
      />
      <motion.div
        variants={container}
        initial="hidden"
        whileInView="show"
        viewport={{ once: true, amount: 0.2 }}
        className="mt-12 grid items-start gap-5 sm:grid-cols-2 lg:grid-cols-3"
      >
        {CAPABILITIES.map((cap) => (
          <ExpandableCard
            key={cap.id}
            title={cap.title}
            icon={cap.icon}
            summary={cap.summary}
            detail={cap.detail}
            expanded={expanded === cap.id}
            dimmed={expanded !== null && expanded !== cap.id}
            onToggle={() => setExpanded((cur) => (cur === cap.id ? null : cap.id))}
          />
        ))}
      </motion.div>
    </section>
  );
}

/* ---------------------------------------------------------------- why payflow */

export function WhyPayFlow() {
  return (
    <StorySection id="why" className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <SectionHeading
        eyebrow="Why PayFlow"
        title="A payment involves more than a success screen."
        intro="A customer can finish checkout while the confirmation arrives late. A request can time out and be retried. A provider can deliver the same webhook twice. Handled carelessly, these create duplicates, confusing statuses, or inconsistent records."
      />
      <motion.div
        variants={container}
        initial="hidden"
        whileInView="show"
        viewport={{ once: true, amount: 0.2 }}
        className="mt-12 grid gap-5 md:grid-cols-3"
      >
        {WHY_PROBLEMS.map((p) => (
          <motion.div key={p.problem} variants={item} className="card p-6">
            <p className="text-xs font-semibold uppercase tracking-wider text-rose-500">Problem</p>
            <p className="mt-2 text-sm font-medium text-slate-900">{p.problem}</p>
            <p className="mt-4 text-xs font-semibold uppercase tracking-wider text-brand-600">Approach</p>
            <p className="mt-2 text-sm leading-relaxed text-slate-600">{p.approach}</p>
          </motion.div>
        ))}
      </motion.div>
      <p className="mx-auto mt-10 max-w-2xl text-center text-base text-slate-500">
        PayFlow connects payment requests, verified provider events, and ledger entries so merchants can see what
        happened — and developers can investigate why.
      </p>
    </StorySection>
  );
}

/* ---------------------------------------------------------------- how it works */

export function HowItWorks() {
  const [tech, setTech] = useState(false);
  return (
    <StorySection id="how" className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <Surface className="px-4 py-16 sm:px-10">
        <SectionHeading
          eyebrow="How it works"
          title="From a payment request to a traceable record."
          intro="PayFlow coordinates the steps between your application, the payment provider, and the ledger. Each step has a clear responsibility."
        />
        <motion.ol
          variants={container}
          initial="hidden"
          whileInView="show"
          viewport={{ once: true, amount: 0.15 }}
          className="mx-auto mt-12 grid max-w-3xl gap-4"
        >
          {HOW_STEPS.map((step, i) => (
            <motion.li key={step.title} variants={item} className="flex gap-4">
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand-600 text-sm font-semibold text-white">
                {i + 1}
              </span>
              <div className="min-w-0">
                <p className="text-sm font-semibold text-slate-900">{step.title}</p>
                <p className="mt-1 text-sm leading-relaxed text-slate-600">{step.body}</p>
              </div>
            </motion.li>
          ))}
        </motion.ol>

        <div className="mx-auto mt-8 max-w-3xl">
          <button
            type="button"
            aria-expanded={tech}
            onClick={() => setTech((t) => !t)}
            className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm font-semibold text-brand-700 outline-none transition hover:text-brand-800 focus-visible:ring-2 focus-visible:ring-blue-500/50"
          >
            <ChevronDown className={cn('h-4 w-4 transition-transform', tech && 'rotate-180')} />
            Technical details
          </button>
          <AnimatePresence initial={false}>
            {tech && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                transition={{ duration: 0.3, ease: EASE }}
                className="overflow-hidden"
              >
                <ul className="mt-3 space-y-2 rounded-2xl border border-slate-200/70 bg-white/70 p-5">
                  {TECHNICAL_FLOW.map((line, i) => (
                    <li key={i} className="flex gap-3 text-sm leading-relaxed text-slate-600">
                      <span className="font-mono text-xs text-brand-500">{String(i + 1).padStart(2, '0')}</span>
                      {line}
                    </li>
                  ))}
                </ul>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </Surface>
    </StorySection>
  );
}

/* ----------------------------------------------------------- reliability */

export function Reliability() {
  const [expanded, setExpanded] = useState<string | null>('retry');
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setExpanded(null);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  return (
    <StorySection className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <Surface className="bg-gradient-to-br from-slate-900 via-slate-900 to-brand-950 px-4 py-16 text-white sm:px-10">
        <SectionHeading
          dark
          eyebrow="Designed for payment failure cases"
          title="Handle retries, delays, and repeated events deliberately."
          intro="Payment integrations have to stay understandable when networks fail or messages arrive more than once. These are the controls PayFlow actually implements."
        />
        <motion.div
          variants={container}
          initial="hidden"
          whileInView="show"
          viewport={{ once: true, amount: 0.15 }}
          className="mx-auto mt-12 max-w-3xl space-y-3"
        >
          {RELIABILITY.map((d) => {
            const open = expanded === d.id;
            return (
              <motion.div
                key={d.id}
                variants={item}
                layout
                transition={{ layout: { duration: 0.35, ease: EASE } }}
                className={cn(
                  'overflow-hidden rounded-2xl border transition-colors',
                  open ? 'border-brand-400/40 bg-white/10' : 'border-white/10 bg-white/[0.04]'
                )}
              >
                <button
                  type="button"
                  aria-expanded={open}
                  onClick={() => setExpanded((cur) => (cur === d.id ? null : d.id))}
                  className="flex w-full items-center gap-4 px-5 py-4 text-left outline-none focus-visible:ring-2 focus-visible:ring-brand-400/60"
                >
                  <span className="flex-1">
                    <span className="block font-semibold text-white">{d.title}</span>
                    <span className="mt-0.5 block text-sm text-slate-300">{d.summary}</span>
                  </span>
                  <span className="text-slate-400">{open ? <Minus className="h-4 w-4" /> : <Plus className="h-4 w-4" />}</span>
                </button>
                <AnimatePresence initial={false}>
                  {open && (
                    <motion.div
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: 'auto' }}
                      exit={{ opacity: 0, height: 0 }}
                      transition={{ duration: 0.3, ease: EASE }}
                      className="overflow-hidden"
                    >
                      <p className="px-5 pb-5 text-sm leading-relaxed text-slate-300">{d.detail}</p>
                    </motion.div>
                  )}
                </AnimatePresence>
              </motion.div>
            );
          })}
        </motion.div>
      </Surface>
    </StorySection>
  );
}

/* ------------------------------------------------------------ architecture */

const GROUP_ACCENT: Record<string, string> = {
  Edge: 'text-sky-600 bg-sky-50 border-sky-200',
  Identity: 'text-violet-600 bg-violet-50 border-violet-200',
  Money: 'text-brand-600 bg-brand-50 border-brand-200',
  Intelligence: 'text-amber-600 bg-amber-50 border-amber-200',
};

export function ArchitectureMap() {
  const [active, setActive] = useState<Service>(SERVICES[3]); // payment-service

  return (
    <StorySection id="architecture" className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <SectionHeading
        eyebrow="Under the hood"
        title="Separate responsibilities. Connected payment records."
        intro="Identity, merchant management, payment processing, provider communication, ledger recording and investigation are separate services. Each owns its own database — no service queries another’s tables. Select a service to see what it owns."
      />
      <div className="mt-12 grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <div className="space-y-5">
          {SERVICE_GROUPS.map((g) => {
            const svcs = SERVICES.filter((s) => s.group === g.group);
            if (!svcs.length) return null;
            return (
              <div key={g.group} className="card p-5">
                <div className="mb-3 flex items-baseline justify-between">
                  <span className={cn('rounded-full border px-2.5 py-0.5 text-xs font-semibold', GROUP_ACCENT[g.group])}>
                    {g.group}
                  </span>
                  <span className="text-xs text-slate-400">{g.blurb}</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {svcs.map((s) => {
                    const on = active.name === s.name;
                    return (
                      <button
                        key={s.name}
                        type="button"
                        onClick={() => setActive(s)}
                        aria-pressed={on}
                        className={cn(
                          'rounded-xl border px-3 py-2 font-mono text-xs transition-all duration-200 outline-none focus-visible:ring-2 focus-visible:ring-blue-500/50',
                          on
                            ? 'border-brand-300 bg-brand-600 text-white shadow-sm shadow-blue-500/20'
                            : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'
                        )}
                      >
                        {s.name}
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>

        <div className="card relative overflow-hidden p-6">
          <AnimatePresence mode="wait">
            <motion.div
              key={active.name}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
              transition={{ duration: 0.25, ease: EASE }}
            >
              <div className="flex items-center justify-between">
                <span className={cn('rounded-full border px-2.5 py-0.5 text-xs font-semibold', GROUP_ACCENT[active.group])}>
                  {active.group}
                </span>
                <span className="font-mono text-xs text-slate-400">:{active.port}</span>
              </div>
              <h3 className="mt-4 font-mono text-lg font-semibold text-slate-900">{active.name}</h3>
              <p className="mt-2 text-sm leading-relaxed text-slate-600">{active.responsibility}</p>
            </motion.div>
          </AnimatePresence>
          <p className="mt-6 border-t border-slate-100 pt-4 text-xs leading-relaxed text-slate-400">
            Services talk over REST for synchronous answers and through Kafka (with a transactional outbox) for events.
            Selecting a service shows its responsibility, not a live health check.
          </p>
        </div>
      </div>

      <motion.div
        variants={container}
        initial="hidden"
        whileInView="show"
        viewport={{ once: true, amount: 0.3 }}
        className="mt-8 grid grid-cols-2 gap-4 sm:grid-cols-4"
      >
        {RELIABILITY_STATS.map((s) => (
          <motion.div key={s.label} variants={item} className="card p-5 text-center">
            <p className="text-xl font-bold tracking-tight text-slate-900">{s.value}</p>
            <p className="mt-1 text-xs leading-snug text-slate-500">{s.label}</p>
          </motion.div>
        ))}
      </motion.div>
    </StorySection>
  );
}

/* ---------------------------------------------------------------- security */

export function SecuritySection() {
  return (
    <StorySection id="security" className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <SectionHeading
        eyebrow="Security controls"
        title="Protect access. Verify events. Scope merchant data."
        intro="Concrete controls implemented in PayFlow, with the risk each one addresses. No compliance or certification claims — just what the code does."
      />
      <motion.div
        variants={container}
        initial="hidden"
        whileInView="show"
        viewport={{ once: true, amount: 0.15 }}
        className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
      >
        {SECURITY.map((row) => (
          <motion.div key={row.threat} variants={item} className="card p-5">
            <div className="flex items-center gap-2 text-sm font-semibold text-slate-900">
              <ShieldCheck className="h-4 w-4 text-brand-600" />
              {row.threat}
            </div>
            <p className="mt-2 text-sm leading-relaxed text-slate-600">{row.defence}</p>
          </motion.div>
        ))}
      </motion.div>
    </StorySection>
  );
}

/* --------------------------------------------------------------- who it's for */

export function WhoFor() {
  return (
    <StorySection id="developers" className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <SectionHeading eyebrow="Who it is for" title="A merchant workspace backed by developer-facing APIs." />
      <motion.div
        variants={container}
        initial="hidden"
        whileInView="show"
        viewport={{ once: true, amount: 0.2 }}
        className="mt-12 grid gap-6 md:grid-cols-3"
      >
        {AUDIENCES.map((col) => (
          <motion.div key={col.title} variants={item} className="card p-7">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
              <col.icon className="h-5 w-5" />
            </span>
            <h3 className="mt-4 text-lg font-bold tracking-tight text-slate-900">{col.title}</h3>
            <ul className="mt-4 space-y-2.5">
              {col.points.map((p) => (
                <li key={p} className="flex items-start gap-2.5 text-sm text-slate-600">
                  <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-brand-500" />
                  {p}
                </li>
              ))}
            </ul>
          </motion.div>
        ))}
      </motion.div>
      <p className="mx-auto mt-8 max-w-2xl text-center text-sm text-slate-400">
        PayFlow is an engineering project that runs a real payment lifecycle against a sandbox gateway (and Razorpay when
        configured). It is not a commercial, production-certified payment service.
      </p>
    </StorySection>
  );
}

/* ---------------------------------------------------------------- faq */

export function FaqSection() {
  const [open, setOpen] = useState<number | null>(0);
  return (
    <StorySection id="faq" className="mx-auto max-w-3xl px-4 py-20 sm:px-6 lg:px-8">
      <SectionHeading eyebrow="FAQ" title="Questions, answered precisely." />
      <div className="mt-12 space-y-3">
        {FAQ.map((f, i) => {
          const isOpen = open === i;
          return (
            <div key={f.q} className="card overflow-hidden p-0">
              <button
                type="button"
                aria-expanded={isOpen}
                onClick={() => setOpen((cur) => (cur === i ? null : i))}
                className="flex w-full items-center gap-4 px-5 py-4 text-left outline-none focus-visible:ring-2 focus-visible:ring-blue-500/50"
              >
                <span className="flex-1 text-sm font-semibold text-slate-900">{f.q}</span>
                <span className="text-slate-400">{isOpen ? <Minus className="h-4 w-4" /> : <Plus className="h-4 w-4" />}</span>
              </button>
              <AnimatePresence initial={false}>
                {isOpen && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: 'auto' }}
                    exit={{ opacity: 0, height: 0 }}
                    transition={{ duration: 0.3, ease: EASE }}
                    className="overflow-hidden"
                  >
                    <p className="px-5 pb-5 text-sm leading-relaxed text-slate-600">{f.a}</p>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          );
        })}
      </div>
    </StorySection>
  );
}

/* ---------------------------------------------------------------- final CTA */

export function FinalCTA() {
  return (
    <StorySection className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
      <div className="relative overflow-hidden rounded-[2.5rem] bg-gradient-to-br from-brand-600 via-brand-700 to-indigo-800 px-6 py-16 text-center shadow-2xl shadow-brand-900/30 sm:px-12">
        <div className="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-white/10 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-24 -left-16 h-64 w-64 rounded-full bg-indigo-400/20 blur-3xl" />
        <h2 className="relative mx-auto max-w-2xl text-3xl font-bold tracking-tight text-white sm:text-4xl">
          Explore the payment lifecycle from one workspace
        </h2>
        <p className="relative mx-auto mt-4 max-w-xl text-lg text-blue-100">
          Create a merchant account, use the available test environment, and follow a payment from its initial request
          to its recorded outcome.
        </p>
        <div className="relative mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <Link
            to="/register"
            className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-white px-6 py-3 text-base font-semibold text-brand-700 shadow-sm transition hover:bg-blue-50 active:scale-[0.98] sm:w-auto"
          >
            Get started <ArrowRight className="h-4 w-4" />
          </Link>
          <Link
            to="/login"
            className="inline-flex w-full items-center justify-center gap-2 rounded-xl border border-white/30 px-6 py-3 text-base font-semibold text-white transition hover:bg-white/10 active:scale-[0.98] sm:w-auto"
          >
            Sign in
          </Link>
        </div>
        <p className="relative mt-5 text-xs text-blue-200/80">Test transactions use the sandbox and do not move real money.</p>
      </div>
    </StorySection>
  );
}
