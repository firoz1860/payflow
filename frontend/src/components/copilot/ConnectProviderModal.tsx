import { useState } from 'react';
import { Eye, EyeOff, KeyRound, ShieldCheck } from 'lucide-react';
import { Modal } from '../Modal';
import { ButtonSpinner } from '../Spinner';
import { toast } from '../Toast';
import { extractError } from '../../api';
import {
  connectCredential,
  type AiProvider,
  type ConnectCredentialResponse,
} from '../../services/copilotService';

interface ProviderOption {
  value: AiProvider;
  label: string;
  hint: string;
  needsBaseUrl?: boolean;
}

const PROVIDERS: ProviderOption[] = [
  { value: 'ANTHROPIC', label: 'Anthropic', hint: 'Claude models' },
  { value: 'OPENAI', label: 'OpenAI', hint: 'GPT models' },
  { value: 'GEMINI', label: 'Google Gemini', hint: 'Gemini models' },
  { value: 'XAI', label: 'xAI', hint: 'Grok models' },
  {
    value: 'CUSTOM_OPENAI_COMPATIBLE',
    label: 'Custom (OpenAI-compatible)',
    hint: 'Self-hosted / proxy endpoint',
    needsBaseUrl: true,
  },
];

interface ConnectProviderModalProps {
  open: boolean;
  onClose: () => void;
  onConnected: (result: ConnectCredentialResponse) => void;
}

export function ConnectProviderModal({ open, onClose, onConnected }: ConnectProviderModalProps) {
  const [provider, setProvider] = useState<AiProvider>('ANTHROPIC');
  // The raw key lives ONLY in this local state for the moment of submission and
  // is cleared immediately afterwards. It is never written to localStorage /
  // sessionStorage, the Zustand store, the URL, or any log.
  const [apiKey, setApiKey] = useState('');
  const [baseUrl, setBaseUrl] = useState('');
  const [showKey, setShowKey] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const selected = PROVIDERS.find((p) => p.value === provider) ?? PROVIDERS[0];
  const needsBaseUrl = Boolean(selected.needsBaseUrl);

  const reset = () => {
    setApiKey('');
    setBaseUrl('');
    setShowKey(false);
    setSubmitting(false);
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const key = apiKey.trim();
    if (!key) {
      toast('warning', 'Enter an API key to connect.');
      return;
    }
    if (needsBaseUrl && !baseUrl.trim()) {
      toast('warning', 'A base URL is required for a custom provider.');
      return;
    }
    setSubmitting(true);
    try {
      const result = await connectCredential({
        provider,
        apiKey: key,
        baseUrl: needsBaseUrl ? baseUrl.trim() : undefined,
      });
      // Clear the secret from memory the instant it is no longer needed.
      setApiKey('');
      setBaseUrl('');
      setShowKey(false);
      toast('success', `${selected.label} connected (${result.masked}).`);
      onConnected(result);
      onClose();
    } catch (err) {
      toast('error', extractError(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open={open} onClose={handleClose} title="Connect an AI provider" size="md">
      <form onSubmit={handleSubmit} className="space-y-5">
        <div className="flex items-start gap-3 rounded-xl border border-blue-200/70 bg-blue-50/70 p-3">
          <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-blue-600" />
          <p className="text-xs leading-relaxed text-blue-800">
            Your key is sent once to the PayFlow backend to provision Copilot and is{' '}
            <span className="font-semibold">never stored in this browser</span>. It is not saved
            to local storage, the app state, or the URL.
          </p>
        </div>

        <div>
          <label htmlFor="copilot-provider" className="label">
            Provider
          </label>
          <select
            id="copilot-provider"
            className="input"
            value={provider}
            onChange={(e) => setProvider(e.target.value as AiProvider)}
          >
            {PROVIDERS.map((p) => (
              <option key={p.value} value={p.value}>
                {p.label} — {p.hint}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="copilot-api-key" className="label">
            API key
          </label>
          <div className="relative">
            <KeyRound className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              id="copilot-api-key"
              type={showKey ? 'text' : 'password'}
              className="input pl-9 pr-10 font-mono"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              placeholder="sk-…"
              autoComplete="off"
              spellCheck={false}
              autoCorrect="off"
              autoCapitalize="off"
            />
            <button
              type="button"
              onClick={() => setShowKey((v) => !v)}
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600 focus:outline-none focus:ring-2 focus:ring-blue-500"
              aria-label={showKey ? 'Hide API key' : 'Show API key'}
              aria-pressed={showKey}
            >
              {showKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
        </div>

        {needsBaseUrl && (
          <div>
            <label htmlFor="copilot-base-url" className="label">
              Base URL
            </label>
            <input
              id="copilot-base-url"
              type="url"
              className="input font-mono"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              placeholder="https://your-endpoint/v1"
              autoComplete="off"
              spellCheck={false}
            />
          </div>
        )}

        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={handleClose} className="btn-secondary">
            Cancel
          </button>
          <button type="submit" className="btn-primary" disabled={submitting}>
            {submitting ? <ButtonSpinner /> : null}
            Connect &amp; Continue
          </button>
        </div>
      </form>
    </Modal>
  );
}
