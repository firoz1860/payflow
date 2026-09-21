import { statusConfig } from '../lib/status';
import { cn } from '../lib/utils';

interface StatusBadgeProps {
  status: string;
  config?: Record<string, { color: string; bg: string; label: string }>;
}

export function StatusBadge({ status, config = statusConfig }: StatusBadgeProps) {
  const cfg = config[status] || { color: 'text-slate-600', bg: 'bg-slate-100', label: status };
  return (
    <span className={cn('badge', cfg.bg, cfg.color)}>
      <span className={cn('w-1.5 h-1.5 rounded-full', cfg.color.replace('text-', 'bg-'))} />
      {cfg.label}
    </span>
  );
}
