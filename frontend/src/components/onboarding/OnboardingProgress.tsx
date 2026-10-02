import { motion, useReducedMotion } from 'framer-motion';

export function OnboardingProgress({ step, total = 5 }: { step: number; total?: number }) {
  const reduceMotion = useReducedMotion();
  const percent = Math.max(0, Math.min(100, (step / total) * 100));

  return (
    <div aria-label={`Step ${step} of ${total}`} className="space-y-2">
      <div className="flex items-center justify-between text-xs text-slate-500">
        <span>Step {step} of {total}</span>
        <span>{Math.round(percent)}%</span>
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-slate-200">
        <motion.div
          className="h-full rounded-full bg-gradient-to-r from-blue-600 to-indigo-600"
          initial={false}
          animate={{ width: `${percent}%` }}
          transition={{ duration: reduceMotion ? 0 : 0.25 }}
        />
      </div>
    </div>
  );
}
