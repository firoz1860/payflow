import type { AuthPhase } from '../services/authReadiness';

export function AuthStartupStatus({ phase, submitting, cancel }: {
  phase?: AuthPhase; submitting: string; cancel: () => void;
}) {
  if (!phase) return null;
  return (
    <div className="mt-3 rounded-lg bg-slate-50 p-3 text-sm text-slate-600">
      <p role="status" aria-live="polite">
        {phase === 'starting' ? 'Connecting to PayFlow. This may take a few minutes.' : submitting}
      </p>
      {phase === 'starting' && <button type="button" onClick={cancel}
        className="mt-2 font-medium text-brand-600 hover:text-brand-700">Cancel</button>}
    </div>
  );
}
