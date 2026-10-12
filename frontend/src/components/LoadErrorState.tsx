import { AlertTriangle } from 'lucide-react';
import { EmptyState } from './Spinner';

export function LoadErrorState({ title, description, onRetry }: {
  title: string;
  description: string;
  onRetry: () => void;
}) {
  return (
    <div role="alert">
      <EmptyState
        icon={<AlertTriangle className="w-6 h-6 text-amber-600" />}
        title={title}
        description={description}
        action={<button type="button" onClick={onRetry} className="btn-secondary">Retry</button>}
      />
    </div>
  );
}
