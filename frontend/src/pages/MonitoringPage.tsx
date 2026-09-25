import { useEffect, useMemo, useState } from 'react';
import { Activity, AlertTriangle, CheckCircle2, Database, Gauge, RefreshCw, Server, WifiOff } from 'lucide-react';
import { API_BASE_URL } from '../api';
import { PageHeader } from '../components/PageHeader';

type HealthState = 'UP' | 'DOWN' | 'UNKNOWN';

function gatewayRoot() {
  if (API_BASE_URL === '/api/v1') return '';
  return API_BASE_URL.replace(/\/api\/v1\/?$/, '');
}

export function MonitoringPage() {
  const [status, setStatus] = useState<HealthState>('UNKNOWN');
  const [lastChecked, setLastChecked] = useState<string>('Never');
  const [checking, setChecking] = useState(false);

  const healthUrl = useMemo(() => `${gatewayRoot()}/actuator/health`, []);

  const checkHealth = async () => {
    setChecking(true);
    try {
      const response = await fetch(healthUrl, { headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const body = await response.json();
      setStatus(body?.status === 'UP' ? 'UP' : 'DOWN');
    } catch {
      setStatus('DOWN');
    } finally {
      setLastChecked(new Date().toLocaleTimeString());
      setChecking(false);
    }
  };

  useEffect(() => {
    checkHealth();
    const timer = window.setInterval(checkHealth, 30000);
    return () => window.clearInterval(timer);
  }, []);

  return (
    <>
      <PageHeader
        title="System Monitoring"
        description="Frontend-safe observability. This page checks the public API Gateway health endpoint and documents the internal monitoring stack."
        actions={
          <button type="button" onClick={checkHealth} disabled={checking} className="btn-secondary">
            <RefreshCw className={`h-4 w-4 ${checking ? 'animate-spin' : ''}`} /> Refresh
          </button>
        }
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <HealthCard
          title="API Gateway"
          state={status}
          detail={status === 'UP' ? 'Public health endpoint is responding.' : status === 'DOWN' ? 'Gateway health endpoint is unreachable.' : 'Checking gateway health…'}
          icon={<Server className="h-5 w-5" />}
        />
        <InfoCard title="Metrics" value="Prometheus" detail="/actuator/prometheus on backend services" icon={<Gauge className="h-5 w-5" />} />
        <InfoCard title="Dashboards" value="Grafana" detail="Optional local/ops dashboard from docker-compose" icon={<Activity className="h-5 w-5" />} />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="card p-6">
          <h2 className="text-sm font-bold text-slate-900">Core Runtime</h2>
          <p className="mt-1 text-xs text-slate-500">
            These services are part of the current PayFlow core deployment. Internal health endpoints are intentionally not exposed through the dashboard.
          </p>
          <div className="mt-5 space-y-2">
            {[
              ['API Gateway', '8000'],
              ['Auth Service', '8081'],
              ['Merchant Service', '8082'],
              ['Payment Service', '8085'],
              ['Provider Service', '8086'],
              ['Ledger Service', '8088'],
              ['PostgreSQL', '5433'],
              ['Redis', '6381'],
              ['Kafka', '9092'],
            ].map(([name, port]) => (
              <div key={name} className="flex items-center justify-between rounded-xl border border-slate-200/70 bg-white/70 px-3 py-2.5">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="h-4 w-4 text-slate-400" />
                  <span className="text-sm font-medium text-slate-800">{name}</span>
                </div>
                <span className="font-mono text-xs text-slate-400">:{port}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="card p-6">
          <h2 className="text-sm font-bold text-slate-900">Operational Signals</h2>
          <p className="mt-1 text-xs text-slate-500">
            Prometheus is configured to scrape service metrics. The browser does not fabricate internal latency, Kafka lag, or database numbers.
          </p>
          <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Signal title="Gateway health" value={status} ok={status === 'UP'} />
            <Signal title="Last checked" value={lastChecked} />
            <Signal title="Service metrics" value="Backend only" />
            <Signal title="Kafka lag" value="Prometheus / Grafana" />
            <Signal title="Outbox backlog" value="Prometheus / Grafana" />
            <Signal title="DB pool usage" value="Prometheus / Grafana" />
          </div>

          <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50/80 p-4">
            <div className="flex items-start gap-2">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
              <p className="text-xs text-amber-800">
                PostgreSQL, Redis, Kafka and internal service actuator endpoints should remain private. Do not make them public just to populate this dashboard.
              </p>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

function HealthCard({ title, state, detail, icon }: { title: string; state: HealthState; detail: string; icon: React.ReactNode }) {
  const up = state === 'UP';
  const down = state === 'DOWN';
  return (
    <div className="card p-5">
      <div className="flex items-center justify-between">
        <span className={up ? 'text-emerald-600' : down ? 'text-rose-600' : 'text-slate-400'}>{icon}</span>
        <span className={`badge ${up ? 'bg-emerald-100 text-emerald-700' : down ? 'bg-rose-100 text-rose-700' : 'bg-slate-100 text-slate-600'}`}>
          {state}
        </span>
      </div>
      <h3 className="mt-4 text-sm font-bold text-slate-900">{title}</h3>
      <p className="mt-1 text-xs text-slate-500">{detail}</p>
    </div>
  );
}

function InfoCard({ title, value, detail, icon }: { title: string; value: string; detail: string; icon: React.ReactNode }) {
  return (
    <div className="card p-5">
      <div className="text-blue-600">{icon}</div>
      <p className="mt-4 text-xs font-semibold uppercase tracking-wider text-slate-400">{title}</p>
      <h3 className="mt-1 text-lg font-bold text-slate-900">{value}</h3>
      <p className="mt-1 text-xs text-slate-500">{detail}</p>
    </div>
  );
}

function Signal({ title, value, ok }: { title: string; value: string; ok?: boolean }) {
  return (
    <div className="rounded-xl border border-slate-200/70 bg-slate-50/70 p-3">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">{title}</p>
      <div className="mt-1 flex items-center gap-2">
        {ok === false ? <WifiOff className="h-3.5 w-3.5 text-rose-500" /> : null}
        <p className="text-sm font-semibold text-slate-900">{value}</p>
      </div>
    </div>
  );
}
