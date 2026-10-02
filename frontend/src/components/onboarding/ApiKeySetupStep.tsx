import { useMemo, useState } from 'react';
import { AlertTriangle, Check, CheckCircle2, Copy, KeyRound, Loader2 } from 'lucide-react';
import { createApiKey } from '../../services/merchantService';
import { extractError } from '../../api';
import { toast } from '../Toast';
import type { ApiKey, CreateApiKeyResponse } from '../../types';

export function ApiKeySetupStep({
  keys,
  canManage,
  tutorialMode,
  onKeysChanged,
  onSecretPendingChange,
}: {
  keys: ApiKey[];
  canManage: boolean;
  tutorialMode: boolean;
  onKeysChanged: () => Promise<void>;
  onSecretPendingChange: (pending: boolean) => void;
}) {
  const configured = useMemo(
    () => keys.find((key) =>
      key.environment === 'TEST' &&
      key.keyType === 'SECRET' &&
      key.status === 'ACTIVE'
    ),
    [keys]
  );
  const [generated, setGenerated] = useState<CreateApiKeyResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [copied, setCopied] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);

  const generate = async () => {
    setCreating(true);
    try {
      const result = await createApiKey({
        environment: 'TEST',
        keyType: 'SECRET',
        label: 'Getting Started',
      });
      setGenerated(result);
      setAcknowledged(false);
      setCopied(false);
      onSecretPendingChange(true);
      toast('success', 'TEST API key created');
      await onKeysChanged();
    } catch (error) {
      toast('error', extractError(error));
    } finally {
      setCreating(false);
    }
  };

  const copy = async () => {
    if (!generated?.secret) return;
    await navigator.clipboard.writeText(generated.secret);
    setCopied(true);
    toast('success', 'API key copied');
    window.setTimeout(() => setCopied(false), 2000);
  };

  const acknowledge = () => {
    setAcknowledged(true);
    onSecretPendingChange(false);
  };

  if (generated) {
    return (
      <div className="space-y-5">
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50/70 p-4">
          <div className="flex items-center gap-2 text-emerald-800">
            <CheckCircle2 className="h-5 w-5" />
            <h3 className="font-semibold">API Key Created</h3>
          </div>
          <p className="mt-1 text-sm text-emerald-700">
            Your TEST secret key is ready for server-side integration.
          </p>
        </div>

        <div className="rounded-2xl border border-amber-200 bg-amber-50/80 p-4">
          <div className="flex gap-3">
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
            <div>
              <p className="font-semibold text-amber-950">Save this key now.</p>
              <p className="mt-1 text-sm text-amber-800">PayFlow will never show this secret again.</p>
            </div>
          </div>
        </div>

        <div>
          <label className="label">One-time API secret</label>
          <div className="flex flex-col gap-2 sm:flex-row">
            <input
              readOnly
              value={generated.secret}
              className="input min-w-0 flex-1 bg-slate-50 font-mono text-xs"
              aria-label="New API key secret"
            />
            <button type="button" onClick={copy} className="btn-secondary shrink-0">
              {copied ? <Check className="h-4 w-4 text-emerald-600" /> : <Copy className="h-4 w-4" />}
              {copied ? 'Copied' : 'Copy API Key'}
            </button>
          </div>
          <p className="sr-only" aria-live="polite">{copied ? 'API key copied' : ''}</p>
        </div>

        <div className="grid grid-cols-1 gap-3 rounded-xl bg-slate-50/80 p-4 text-sm sm:grid-cols-2">
          <div>
            <p className="text-xs text-slate-500">Key ID</p>
            <p className="mt-1 break-all font-mono text-xs font-medium text-slate-900">{generated.keyId}</p>
          </div>
          <div>
            <p className="text-xs text-slate-500">Environment</p>
            <p className="mt-1 font-medium text-slate-900">{generated.environment}</p>
          </div>
        </div>

        <button
          type="button"
          onClick={acknowledge}
          disabled={acknowledged}
          className={acknowledged ? 'btn-secondary' : 'btn-primary'}
        >
          <CheckCircle2 className="h-4 w-4" />
          {acknowledged ? 'Key saved acknowledgement recorded' : "I've saved this key"}
        </button>
      </div>
    );
  }

  if (configured) {
    return (
      <div className="space-y-5">
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50/70 p-5">
          <div className="flex items-center gap-2 text-emerald-800">
            <CheckCircle2 className="h-5 w-5" />
            <h3 className="font-semibold">TEST API key already configured</h3>
          </div>
          <p className="mt-1 text-sm text-emerald-700">
            Existing secrets cannot be recovered. PayFlow only stores a secure hash.
          </p>
        </div>
        <dl className="grid grid-cols-1 gap-3 rounded-2xl border border-slate-200 bg-white/60 p-4 text-sm sm:grid-cols-2">
          <KeyMeta label="Label" value={configured.label || 'Unlabelled'} />
          <KeyMeta label="Key ID" value={configured.keyId} mono />
          <KeyMeta label="Masked key" value={configured.maskedKey} mono />
          <KeyMeta label="Status" value={configured.status} />
        </dl>
        <p className="text-xs text-slate-500">
          Lost the secret? It cannot be recovered. Create a new key and revoke the old one.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="rounded-2xl border border-slate-200 bg-white/65 p-5">
        <div className="flex items-start gap-3">
          <div className="rounded-xl bg-blue-100 p-2 text-blue-700"><KeyRound className="h-5 w-5" /></div>
          <div>
            <h3 className="font-semibold text-slate-950">Create your first TEST API key</h3>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              TEST keys are for safe integration and sandbox payments. The raw secret is shown exactly once.
            </p>
          </div>
        </div>
      </div>

      {tutorialMode ? (
        <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-600">
          Tutorial replay is read-only. Open API Keys if you want to create a new credential.
        </p>
      ) : !canManage ? (
        <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-600">
          Your role does not have <code className="font-mono text-xs">api_keys:manage</code>, so key creation is unavailable.
        </p>
      ) : (
        <button type="button" onClick={generate} disabled={creating} className="btn-primary">
          {creating ? <Loader2 className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}
          {creating ? 'Generating…' : 'Generate my TEST API key'}
        </button>
      )}
    </div>
  );
}

function KeyMeta({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className={`mt-1 break-all font-medium text-slate-900 ${mono ? 'font-mono text-xs' : ''}`}>{value}</dd>
    </div>
  );
}
