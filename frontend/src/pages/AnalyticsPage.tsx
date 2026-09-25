import { useEffect, useMemo, useState } from 'react';
import { BarChart3, CheckCircle2, CreditCard, RefreshCw, TrendingUp, XCircle } from 'lucide-react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { listPayments } from '../services/paymentService';
import { PageHeader } from '../components/PageHeader';
import { CardSpinner, EmptyState } from '../components/Spinner';
import { formatCurrency } from '../lib/utils';
import type { Payment } from '../types';

const STATUS_COLORS: Record<string, string> = {
  CAPTURED: '#10b981',
  PENDING: '#f59e0b',
  PROCESSING: '#3b82f6',
  FAILED: '#ef4444',
  CANCELLED: '#94a3b8',
  AUTHORIZED: '#6366f1',
  REFUNDED: '#0ea5e9',
  CREATED: '#64748b',
};

export function AnalyticsPage() {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const page = await listPayments({ page: 0, size: 100 });
      setPayments(page.data || []);
    } catch {
      setPayments([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const analytics = useMemo(() => {
    const captured = payments.filter((p) => p.status === 'CAPTURED');
    const failed = payments.filter((p) => p.status === 'FAILED');
    const pending = payments.filter((p) => ['CREATED', 'PENDING', 'PROCESSING', 'AUTHORIZED'].includes(p.status));
    const totalVolume = captured.reduce((sum, p) => sum + p.amount, 0);
    const avgValue = captured.length ? totalVolume / captured.length : 0;
    const successRate = payments.length ? (captured.length / payments.length) * 100 : 0;

    const byDay = new Map<string, number>();
    for (const p of captured) {
      const date = new Date(p.createdAt);
      const key = Number.isNaN(date.getTime()) ? 'Unknown' : date.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' });
      byDay.set(key, (byDay.get(key) || 0) + p.amount);
    }

    const statuses = new Map<string, number>();
    for (const p of payments) statuses.set(p.status, (statuses.get(p.status) || 0) + 1);

    return {
      captured: captured.length,
      failed: failed.length,
      pending: pending.length,
      totalVolume,
      avgValue,
      successRate,
      volumeSeries: [...byDay.entries()].map(([date, volume]) => ({ date, volume })).slice(-14),
      statusSeries: [...statuses.entries()].map(([name, value]) => ({ name, value })),
    };
  }, [payments]);

  if (loading) return <CardSpinner />;

  return (
    <>
      <PageHeader
        title="Analytics"
        description="Real merchant payment analytics calculated from the Payment Service API."
        actions={
          <button type="button" onClick={load} className="btn-secondary">
            <RefreshCw className="h-4 w-4" /> Refresh
          </button>
        }
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">
        <Metric label="Captured Volume" value={formatCurrency(analytics.totalVolume)} icon={<TrendingUp className="h-5 w-5" />} />
        <Metric label="Success Rate" value={`${analytics.successRate.toFixed(1)}%`} icon={<CheckCircle2 className="h-5 w-5" />} />
        <Metric label="Captured" value={String(analytics.captured)} icon={<CreditCard className="h-5 w-5" />} />
        <Metric label="Failed" value={String(analytics.failed)} icon={<XCircle className="h-5 w-5" />} />
        <Metric label="Average Value" value={formatCurrency(analytics.avgValue)} icon={<BarChart3 className="h-5 w-5" />} />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="card p-6 lg:col-span-2">
          <h2 className="text-sm font-semibold text-slate-900">Captured Volume</h2>
          <p className="mt-1 text-xs text-slate-500">Based on up to the latest 100 payments returned by the backend.</p>
          {analytics.volumeSeries.length ? (
            <div className="mt-5 h-72">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={analytics.volumeSeries}>
                  <defs>
                    <linearGradient id="analyticsVolume" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#2563eb" stopOpacity={0.32} />
                      <stop offset="100%" stopColor="#2563eb" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis dataKey="date" axisLine={false} tickLine={false} tick={{ fontSize: 11, fill: '#64748b' }} />
                  <YAxis axisLine={false} tickLine={false} tick={{ fontSize: 11, fill: '#64748b' }} tickFormatter={(v) => `₹${Math.round(v / 1000)}k`} />
                  <Tooltip formatter={(v: number) => [formatCurrency(v), 'Captured volume']} />
                  <Area type="monotone" dataKey="volume" stroke="#2563eb" strokeWidth={2.2} fill="url(#analyticsVolume)" animationDuration={700} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <EmptyState icon={<TrendingUp className="h-6 w-6" />} title="No captured payments yet" />
          )}
        </div>

        <div className="card p-6">
          <h2 className="text-sm font-semibold text-slate-900">Payment Status</h2>
          {analytics.statusSeries.length ? (
            <>
              <div className="mt-4 h-52">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={analytics.statusSeries} dataKey="value" nameKey="name" innerRadius={48} outerRadius={82} paddingAngle={3}>
                      {analytics.statusSeries.map((entry) => (
                        <Cell key={entry.name} fill={STATUS_COLORS[entry.name] || '#94a3b8'} />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <div className="space-y-2">
                {analytics.statusSeries.map((entry) => (
                  <div key={entry.name} className="flex items-center justify-between text-xs">
                    <span className="flex items-center gap-2 text-slate-600">
                      <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: STATUS_COLORS[entry.name] || '#94a3b8' }} />
                      {entry.name}
                    </span>
                    <span className="font-semibold text-slate-900">{entry.value}</span>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <EmptyState icon={<CreditCard className="h-6 w-6" />} title="No payment data" />
          )}
        </div>
      </div>

      <div className="mt-6 glass-panel rounded-2xl p-5">
        <p className="text-xs text-slate-500">
          This page intentionally calculates analytics only from data returned by PayFlow APIs. It does not invent settlement,
          ledger, provider, or refund numbers that the current backend does not expose to the merchant frontend.
        </p>
      </div>
    </>
  );
}

function Metric({ label, value, icon }: { label: string; value: string; icon: React.ReactNode }) {
  return (
    <div className="card p-5 transition-transform duration-200 hover:-translate-y-0.5">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">{label}</span>
        <span className="text-blue-600">{icon}</span>
      </div>
      <p className="mt-3 text-2xl font-bold tracking-tight text-slate-900">{value}</p>
    </div>
  );
}
