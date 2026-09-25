import { useEffect, useState } from 'react';
import { Activity, AlertTriangle, CheckCircle2, ExternalLink, RefreshCw, Webhook } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { listWebhookEndpoints, type WebhookEndpointSummary } from '../services/webhookService';
import { motion } from 'framer-motion';

export function WebhooksPage() {
  const [items, setItems] = useState<WebhookEndpointSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [available, setAvailable] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const data = await listWebhookEndpoints();
      setItems(data);
      setAvailable(true);
    } catch {
      setItems([]);
      setAvailable(false);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  return (
    <>
      <PageHeader
        title="Webhooks"
        description="Merchant webhook delivery and debugging surface"
        actions={<button onClick={load} className="btn-secondary"><RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} /> Refresh</button>}
      />

      {!available && (
        <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} className="card glass-card p-5 mb-6 border-amber-200/80">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-amber-600 mt-0.5" />
            <div>
              <h2 className="font-semibold text-slate-950">Webhook delivery service is not currently reachable</h2>
              <p className="text-sm text-slate-600 mt-1">
                The gateway route exists, but the standalone merchant webhook delivery service is still a planned service in this repository.
                This page is ready to consume <code className="font-mono text-xs">/api/v1/webhook-endpoints</code> when that service is deployed.
              </p>
            </div>
          </div>
        </motion.div>
      )}

      <div className="grid md:grid-cols-3 gap-4 mb-6">
        <Stat icon={<Webhook className="w-5 h-5" />} label="Endpoints" value={items.length.toString()} />
        <Stat icon={<CheckCircle2 className="w-5 h-5" />} label="Service" value={available ? 'Reachable' : 'Unavailable'} good={available} />
        <Stat icon={<Activity className="w-5 h-5" />} label="Delivery Mode" value="Signed + retried" />
      </div>

      <div className="card glass-card overflow-hidden">
        <div className="px-6 py-5 border-b border-white/70">
          <h2 className="font-semibold text-slate-950">Webhook endpoints</h2>
          <p className="text-sm text-slate-500 mt-1">Only backend-returned endpoint data is shown here. No webhook secret is ever rendered.</p>
        </div>

        {items.length === 0 ? (
          <div className="p-10 text-center">
            <Webhook className="w-10 h-10 text-slate-300 mx-auto" />
            <h3 className="font-semibold text-slate-900 mt-4">No webhook endpoints available</h3>
            <p className="text-sm text-slate-500 mt-1 max-w-xl mx-auto">
              Once the webhook service is deployed, configured merchant endpoints and delivery health will appear here automatically.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-white/45">
                <tr className="text-xs uppercase tracking-wider text-slate-500">
                  <th className="px-6 py-3 text-left">Endpoint</th>
                  <th className="px-6 py-3 text-left">Status</th>
                  <th className="px-6 py-3 text-left">Events</th>
                  <th className="px-6 py-3 text-left">Success Rate</th>
                  <th className="px-6 py-3 text-left">Last Delivery</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/60">
                {items.map((item, i) => (
                  <tr key={item.id || item.url || i} className="table-row-hover">
                    <td className="px-6 py-4 text-sm text-slate-800">
                      <span className="inline-flex items-center gap-2"><ExternalLink className="w-4 h-4 text-slate-400" />{item.url || '—'}</span>
                    </td>
                    <td className="px-6 py-4"><span className="badge bg-emerald-100 text-emerald-700">{item.status || 'ACTIVE'}</span></td>
                    <td className="px-6 py-4 text-sm text-slate-600">{item.events?.join(', ') || '—'}</td>
                    <td className="px-6 py-4 text-sm text-slate-600">{item.successRate == null ? '—' : `${item.successRate}%`}</td>
                    <td className="px-6 py-4 text-sm text-slate-500">{item.lastDeliveryAt || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}

function Stat({ icon, label, value, good }: { icon: React.ReactNode; label: string; value: string; good?: boolean }) {
  return (
    <div className="card glass-card p-5">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-brand-100 text-brand-700 flex items-center justify-center">{icon}</div>
        <div>
          <p className="text-xs text-slate-500 uppercase tracking-wider">{label}</p>
          <p className={`font-semibold mt-1 ${good ? 'text-emerald-700' : 'text-slate-950'}`}>{value}</p>
        </div>
      </div>
    </div>
  );
}
