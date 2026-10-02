import { CheckCircle2, Circle, Rocket, X } from 'lucide-react';
import { motion } from 'framer-motion';

export interface SetupMilestones {
  business: boolean;
  apiKey: boolean;
  payment: boolean;
  finished: boolean;
}

export function OnboardingResumeCard({
  milestones,
  onContinue,
  onDismissView,
}: {
  milestones: SetupMilestones;
  onContinue: () => void;
  onDismissView: () => void;
}) {
  const entries = [
    ['Business profile', milestones.business],
    ['TEST API key', milestones.apiKey],
    ['First payment', milestones.payment],
    ['Finish setup', milestones.finished],
  ] as const;
  const ready = entries.filter(([, complete]) => complete).length;

  return (
    <motion.section
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      className="relative mb-6 overflow-hidden rounded-2xl border border-blue-200/70 bg-white/75 p-5 shadow-sm backdrop-blur-xl"
    >
      <button
        type="button"
        onClick={onDismissView}
        aria-label="Hide setup card for this view"
        className="absolute right-3 top-3 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
      >
        <X className="h-4 w-4" />
      </button>
      <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
        <div className="min-w-0">
          <div className="mb-2 flex items-center gap-2">
            <div className="rounded-xl bg-blue-100 p-2 text-blue-700"><Rocket className="h-4 w-4" /></div>
            <div>
              <h2 className="font-semibold text-slate-950">Complete your PayFlow setup</h2>
              <p className="text-sm text-slate-500">{ready} of 4 ready</p>
            </div>
          </div>
          <div className="mt-3 h-1.5 max-w-xl overflow-hidden rounded-full bg-slate-200">
            <div className="h-full rounded-full bg-blue-600 transition-all" style={{ width: `${(ready / 4) * 100}%` }} />
          </div>
          <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2">
            {entries.map(([label, complete]) => (
              <span key={label} className="flex items-center gap-1.5 text-xs text-slate-600">
                {complete ? <CheckCircle2 className="h-4 w-4 text-emerald-600" /> : <Circle className="h-4 w-4 text-slate-300" />}
                {label}
              </span>
            ))}
          </div>
        </div>
        <button type="button" onClick={onContinue} className="btn-primary shrink-0">
          Continue setup
        </button>
      </div>
    </motion.section>
  );
}
