import { useState } from 'react';
import { CheckCircle2, Server, Trash2, User } from 'lucide-react';
import { ButtonSpinner } from '../Spinner';
import { toast } from '../Toast';
import { extractError } from '../../api';
import { formatDateTime } from '../../lib/utils';
import {
  deleteCredential,
  type AiCredential,
} from '../../services/copilotService';

interface CredentialStatusProps {
  credential: AiCredential;
  onRemoved: () => void;
}

export function CredentialStatus({ credential, onRemoved }: CredentialStatusProps) {
  const [removing, setRemoving] = useState(false);
  const isServer = credential.source === 'SERVER';

  const handleRemove = async () => {
    setRemoving(true);
    try {
      await deleteCredential(credential.provider);
      toast('success', `${credential.provider} disconnected.`);
      onRemoved();
    } catch (err) {
      toast('error', extractError(err));
    } finally {
      setRemoving(false);
    }
  };

  return (
    <div className="rounded-xl border border-emerald-200/70 bg-emerald-50/60 p-3">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-1.5">
            <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600" />
            <span className="text-sm font-semibold text-emerald-900">{credential.provider}</span>
            <span className="badge bg-emerald-100 text-emerald-700">
              {isServer ? (
                <>
                  <Server className="h-3 w-3" /> Server
                </>
              ) : (
                <>
                  <User className="h-3 w-3" /> Your key
                </>
              )}
            </span>
          </div>
          {credential.masked && (
            <p className="mt-1 truncate font-mono text-xs text-emerald-800/80">{credential.masked}</p>
          )}
          {credential.expiresAt && (
            <p className="mt-0.5 text-[11px] text-emerald-700/70">
              Expires {formatDateTime(credential.expiresAt)}
            </p>
          )}
        </div>
        {!isServer && (
          <button
            type="button"
            onClick={handleRemove}
            disabled={removing}
            className="btn-ghost shrink-0 px-2.5 py-1.5 text-xs text-rose-600 hover:bg-rose-50 hover:text-rose-700"
            aria-label={`Remove ${credential.provider} credential`}
          >
            {removing ? <ButtonSpinner /> : <Trash2 className="h-3.5 w-3.5" />}
            Remove
          </button>
        )}
      </div>
    </div>
  );
}
