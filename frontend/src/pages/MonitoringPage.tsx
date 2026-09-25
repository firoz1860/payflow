import { useEffect, useState } from 'react';
import { Activity, Database, Gauge, Radio, Server, ShieldCheck } from 'lucide-react';
import { API_BASE_URL } from '../api';
import { PageHeader } from '../components/PageHeader';
import { motion } from 'framer-motion';

type GatewayHealth = { status?: string; groups?: string[] };

export function MonitoringPage() {
  const [health, setHealth] = useState<GatewayHealth | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const gatewayRoot = API_BASE_URL.replace(/\/api\/v1\/?$/, '');
    fetch(`${gatewayRoot}/actuator/health`)
      .then(async (r) => {
        if (!r.ok) throw new Error('Health endpoint unavailable');
        return r.json();
      })
      .then(setHealth)
      .catch(() => setHealth({ status: 'UNKNOWN' }))
      .finally(() => setLoading(false));
  }, []);

  const gatewayStatus = loading ? 'CHECKING' : health?.status || 'UNKNOWN';

  const services = [
    { name: 'API Gateway', status: gatewayStatus, detail: 'Direct health probe', icon: <ShieldCheck className="w-5 h-5" /> },
    { name: 'Auth Service', status: 'PRIVATE', detail: 'Internal Docker health check', icon: <Server className="w-5 h-5" /> },
    { name: 'Merchant Service', status: 'PRIVATE', detail: 'Internal Docker health check', icon: <Server className="w-5 h-5" /> },
    { name: 'Payment Service', status: 'PRIVATE', detail: 'Internal Docker health check', icon: <Server className="w-5 h-5" /> },
    { name: 'Provider Service', status: 'PRIVATE', detail: 'Internal Docker health check', icon: <Radio className="w-5 h-5" /> },
    { name: 'Ledger Service', status: 'PRIVATE', detail: 'Internal Docker health check', icon: <Server className="w-5 h-5" /> },
    { name: 'PostgreSQL', status: 'PRIVATE', detail: 'Not exposed to browser', icon: <Database className="w-5 h-5" /> },
    { name: 'Redis', status: 'PRIVATE', detail: 'Not exposed to browser', icon: <Gauge className="w-5 h-5" /> },
    { name: 'Kafka', status: 'PRIVATE', detail: 'Not exposed to browser', icon: <Activity className="w-5 h-5" /> },
  ];

  return (
    <>
      <PageHeader title="System Monitoring" description="Safe browser-visible health information without exposing private infrastructure endpoints" />

      <div className="card glass-card p-5 mb-6">
        <p className="text-sm text-slate-600">
          This dashboard intentionally probes only the public API Gateway health endpoint. Internal service, database, Redis and Kafka endpoints stay private.
          Deeper metrics remain available through Prometheus and Grafana on the operations network.
        </p>
      </div>

      <div className="grid sm:grid-cols-2 xl:grid-cols-3 gap-4">
        {services.map((service, i) => (
          <motion.div
            key={service.name}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.04 }}
            className="card glass-card p-5"
          >
            <div className="flex items-center justify-between gap-4">
              <div className="w-10 h-10 rounded-xl bg-brand-100 text-brand-700 flex items-center justify-center">{service.icon}</div>
              <span className={`badge ${badge(service.status)}`}>{service.status}</span>
            </div>
            <h2 className="font-semibold text-slate-950 mt-4">{service.name}</h2>
            <p className="text-sm text-slate-500 mt-1">{service.detail}</p>
          </motion.div>
        ))}
      </div>

      <div className="grid md:grid-cols-2 gap-6 mt-6">
        <div className="card glass-card p-6">
          <h2 className="font-semibold text-slate-950">Prometheus</h2>
          <p className="text-sm text-slate-500 mt-2">Collects service metrics such as request latency, JVM usage, circuit breaker state and outbox backlog.</p>
          <div className="mt-4 p-4 rounded-xl bg-white/55 border border-white/70 font-mono text-xs text-slate-600">/actuator/prometheus</div>
        </div>
        <div className="card glass-card p-6">
          <h2 className="font-semibold text-slate-950">Grafana</h2>
          <p className="text-sm text-slate-500 mt-2">Visualizes Prometheus metrics for operations. Keep the Grafana surface private or authenticated in production.</p>
          <div className="mt-4 p-4 rounded-xl bg-white/55 border border-white/70 text-sm text-slate-600">Docker Compose default: port 3000</div>
        </div>
      </div>
    </>
  );
}

function badge(status: string) {
  if (status === 'UP') return 'bg-emerald-100 text-emerald-700';
  if (status === 'CHECKING') return 'bg-amber-100 text-amber-700';
  if (status === 'PRIVATE') return 'bg-blue-100 text-blue-700';
  return 'bg-slate-100 text-slate-600';
}
