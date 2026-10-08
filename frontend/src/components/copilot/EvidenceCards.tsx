import {
  AlertTriangle,
  BadgeCheck,
  CircleHelp,
  CreditCard,
  Landmark,
  Lightbulb,
  Plug,
  ShieldQuestion,
} from 'lucide-react';
import { cn } from '../../lib/utils';
import type { Confidence, Evidence } from '../../services/copilotService';

// Evidence falls into one of three kinds, each visually distinct so a merchant
// can instantly tell a verified fact from a model inference or a gap in data.
export type EvidenceKind = 'FACT' | 'INFERENCE' | 'MISSING';

export function evidenceKind(evidence: Evidence): EvidenceKind {
  if (!evidence.resourceId) return 'MISSING';
  return evidence.verified ? 'FACT' : 'INFERENCE';
}

const KIND_STYLES: Record<
  EvidenceKind,
  { label: string; badge: string; ring: string; icon: typeof BadgeCheck }
> = {
  FACT: {
    label: 'Fact',
    badge: 'bg-emerald-100 text-emerald-700',
    ring: 'border-emerald-200/70 bg-emerald-50/50',
    icon: BadgeCheck,
  },
  INFERENCE: {
    label: 'Inference',
    badge: 'bg-blue-100 text-blue-700',
    ring: 'border-blue-200/70 bg-blue-50/50',
    icon: Lightbulb,
  },
  MISSING: {
    label: 'Missing',
    badge: 'bg-amber-100 text-amber-700',
    ring: 'border-amber-200/70 bg-amber-50/50',
    icon: ShieldQuestion,
  },
};

const CONFIDENCE_STYLES: Record<Confidence, { label: string; badge: string }> = {
  HIGH: { label: 'High confidence', badge: 'bg-emerald-100 text-emerald-700' },
  MEDIUM: { label: 'Medium confidence', badge: 'bg-blue-100 text-blue-700' },
  LOW: { label: 'Low confidence', badge: 'bg-amber-100 text-amber-700' },
  UNKNOWN: { label: 'Unknown confidence', badge: 'bg-slate-100 text-slate-600' },
};

function resourceIcon(resourceType: string) {
  const t = resourceType?.toUpperCase?.() ?? '';
  if (t.includes('PAYMENT')) return CreditCard;
  if (t.includes('LEDGER') || t.includes('POSTING')) return Landmark;
  if (t.includes('PROVIDER')) return Plug;
  return CircleHelp;
}

export function ConfidenceBadge({ confidence }: { confidence: Confidence }) {
  const cfg = CONFIDENCE_STYLES[confidence] ?? CONFIDENCE_STYLES.UNKNOWN;
  return <span className={cn('badge', cfg.badge)}>{cfg.label}</span>;
}

interface EvidenceCardsProps {
  evidence: Evidence[];
  confidence?: Confidence;
  warnings?: string[];
}

export function EvidenceCards({ evidence, confidence, warnings = [] }: EvidenceCardsProps) {
  const hasContent = evidence.length > 0 || warnings.length > 0 || Boolean(confidence);
  if (!hasContent) {
    return (
      <p className="text-sm text-slate-500">
        Evidence from the backend appears here after Copilot answers.
      </p>
    );
  }

  return (
    <div className="space-y-3">
      {confidence && (
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Answer confidence
          </span>
          <ConfidenceBadge confidence={confidence} />
        </div>
      )}

      {evidence.map((item, index) => {
        const kind = evidenceKind(item);
        const style = KIND_STYLES[kind];
        const KindIcon = style.icon;
        const ResourceIcon = resourceIcon(item.resourceType);
        return (
          <div
            key={`${item.source}-${item.resourceId}-${index}`}
            className={cn('rounded-xl border p-3', style.ring)}
            data-evidence-kind={kind}
          >
            <div className="flex items-start justify-between gap-2">
              <div className="flex min-w-0 items-center gap-2">
                <ResourceIcon className="h-4 w-4 shrink-0 text-slate-500" />
                <span className="truncate text-sm font-semibold text-slate-800">
                  {item.source || item.resourceType || 'Evidence'}
                </span>
              </div>
              <span className={cn('badge shrink-0', style.badge)}>
                <KindIcon className="h-3 w-3" />
                {style.label}
              </span>
            </div>
            <dl className="mt-2 space-y-0.5 text-xs text-slate-500">
              {item.resourceType && (
                <div className="flex justify-between gap-2">
                  <dt>Resource</dt>
                  <dd className="font-mono text-slate-700">{item.resourceType}</dd>
                </div>
              )}
              <div className="flex justify-between gap-2">
                <dt>Reference</dt>
                <dd className="truncate font-mono text-slate-700">
                  {item.resourceId || '— not found —'}
                </dd>
              </div>
            </dl>
          </div>
        );
      })}

      {warnings.map((warning, index) => (
        <div
          key={`warning-${index}`}
          className="flex items-start gap-2 rounded-xl border border-amber-200/70 bg-amber-50/60 p-3"
          role="note"
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
          <p className="text-xs leading-relaxed text-amber-800">{warning}</p>
        </div>
      ))}
    </div>
  );
}
