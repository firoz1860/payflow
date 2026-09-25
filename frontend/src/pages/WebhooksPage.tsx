import { useEffect, useState } from 'react';
import { AlertTriangle, CheckCircle2, RefreshCw, Webhook } from 'lucide-react';
import api, { extractError } from '../api';
import { PageHeader } from '../components/PageHeader';

type Availability = 'CHECKING' | 'AVAILABLE' | 'UNAVAILABLE';

export function WebhooksPage() {
  const [availability, setAvailability] = useState<Availability>('CHECKING');
  const [message, setMessage] = useState('Checking merchant webhook service…');
  const [checking, setChecking] = useState(false);

  const check = async () => {
    setChecking(true);
    setAvailability('CHECKING');
    try {
      await api.get('/webhook-endpoints');
      setAvailability('AVAILABLE');
      setMessage('Merchant webhook endpoint API is reachable.');
    } catch (error: any) {
      const status = error?.response?.status;
      if (status === 404 || status === 502 || status === 503 || status === 504) {
        setAvailability('UNAVAILABLE');
        setMessage('The API Gateway route exists, but the merchant webhook service is not part of the current core deployment.');
      } else if (status === 401 || status === 403) {
        setAvailability('AVAILABLE');
        setMessage('Webhook service responded but this account is not authorized for the requested operation.');
      } else {
        setAvailability('UNAVAILABLE');
        setMessage(extractError(error));
      }
    } finally {
      setChecking(false);
    }
  };

  useEffect(() => {
    check();
  }, []);

  return (
    <>
      <PageHeader
        title="Webhooks"
        description="Provider webhook verification is implemented in the core backend. Merchant webhook endpoint management is reserved behind the gateway route shown here."
        actions={
          <button type="button" onClick={check} disabled={checking} className="btn-secondary">
            <RefreshCw className={`h-4 w-4 ${checking ? 'animate-spin' : ''}`} /> Check Service
          </button>
        }
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="glass-panel-elevated rounded-2xl p-6 lg:col-span-2">
          <div className="flex items-center gap-3">
            <div className={`flex h-11 w-11 items-center justify-center rounded-xl ${
              availability === 'AVAILABLE'
                ? 'bg-emerald-100 text-emerald-700'
                : availability === 'UNAVAILABLE'
                  ? 'bg-amber-100 text-amber-700'
                  : 'bg-blue-100 text-blue-700'
            }`}>
              {availability === 'AVAILABLE' ? <CheckCircle2 className="h-5 w-5" /> : <Webhook className="h-5 w-5" />}
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Merchant Webhook Service</p>
              <h2 className="mt-1 text-lg font-bold text-slate-900">{availability}</h2>
            </div>
          </div>
          <p className="mt-4 text-sm leading-6 text-slate-600">{message}</p>

          {availability !== 'AVAILABLE' && (
            <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50/80 p-4">
              <div className="flex items-start gap-2">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
                <p className="text-xs leading-5 text-amber-800">
                  This frontend does not fake endpoint lists, delivery attempts, retry counts, or signatures. Once webhook-service is implemented behind
                  <code className="mx-1 rounded bg-white/70 px-1 font-mono">/api/v1/webhook-endpoints/**</code>,
                  this page can consume those real records.
                </p>
              </div>
            </div>
          )}
        </div>

        <div className="card p-6">
          <h3 className="text-sm font-bold text-slate-900">Provider webhook guarantees</h3>
          <ul className="mt-4 space-y-3 text-xs leading-5 text-slate-600">
            <li>• Verify signatures over the raw request body.</li>
            <li>• Reject stale/replayed events according to provider verification rules.</li>
            <li>• Deduplicate provider events before applying state changes.</li>
            <li>• Never expose webhook secrets in this frontend.</li>
          </ul>
        </div>
      </div>
    </>
  );
}
