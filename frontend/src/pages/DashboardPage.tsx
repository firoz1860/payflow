import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  TrendingUp, DollarSign, CreditCard, CheckCircle2, Clock, ArrowUpRight, Plus,
} from 'lucide-react';
import {
  ResponsiveContainer, AreaChart, Area, XAxis, YAxis, Tooltip, CartesianGrid,
  PieChart, Pie, Cell, BarChart, Bar,
} from 'recharts';
import { listPayments } from '../services/paymentService';
import { getMyMerchant } from '../services/merchantService';
import { useAuthStore } from '../store/auth';
import { PageHeader } from '../components/PageHeader';
import { CardSpinner, EmptyState } from '../components/Spinner';
import { StatusBadge } from '../components/StatusBadge';
import { formatCurrency, formatDateTime, timeAgo } from '../lib/utils';
import type { Payment, Merchant } from '../types';

const COLORS = {
  CAPTURED: '#10b981',
  PENDING: '#f59e0b',
  PROCESSING: '#3b82f6',
  FAILED: '#ef4444',
  CANCELLED: '#94a3b8',
  AUTHORIZED: '#6366f1',
  REFUNDED: '#a855f7',
  PARTIALLY_REFUNDED: '#06b6d4',
  CREATED: '#64748b',
};

export function DashboardPage() {
  const { user } = useAuthStore();
  const [payments, setPayments] = useState<Payment[]>([]);
  const [merchant, setMerchant] = useState<Merchant | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      listPayments({ page: 0, size: 100 }).catch(() => ({ data: [] })),
      getMyMerchant().catch(() => null),
    ]).then(([page, merch]) => {
      setPayments(page.data || []);
      setMerchant(merch);
    }).finally(() => setLoading(false));
  }, []);

  const stats = computeStats(payments);
  const chartData = buildChartData(payments);
  const statusBreakdown = buildStatusBreakdown(payments);

  if (loading) return <Layout><CardSpinner /></Layout>;

  return (
    <Layout>
      <PageHeader
        title={`Welcome, ${user?.fullName?.split(' ')[0] || 'there'}`}
        description="Overview of your payment activity and account status"
        actions={
          <Link to="/payments/create" className="btn-primary">
            <Plus className="w-4 h-4" /> New Payment
          </Link>
        }
      />

      {/* Stat cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <StatCard
          icon={<DollarSign className="w-5 h-5" />}
          label="Total Volume"
          value={formatCurrency(stats.totalVolume, merchant?.defaultCurrency || 'INR')}
          trend={stats.volumeTrend}
          color="emerald"
        />
        <StatCard
          icon={<CheckCircle2 className="w-5 h-5" />}
          label="Captured"
          value={formatCurrency(stats.capturedAmount, merchant?.defaultCurrency || 'INR')}
          sublabel={`${stats.capturedCount} payments`}
          color="blue"
        />
        <StatCard
          icon={<CreditCard className="w-5 h-5" />}
          label="Total Payments"
          value={String(stats.totalCount)}
          sublabel={`${stats.pendingCount} pending`}
          color="amber"
        />
        <StatCard
          icon={<TrendingUp className="w-5 h-5" />}
          label="Success Rate"
          value={`${stats.successRate}%`}
          trend={stats.successRate >= 90 ? 'up' : 'down'}
          color="purple"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        {/* Volume chart */}
        <div className="card p-6 lg:col-span-2">
          <h3 className="text-sm font-semibold text-slate-900 mb-4">Payment Volume (Last 14 Days)</h3>
          {chartData.length > 0 ? (
            <ResponsiveContainer width="100%" height={280}>
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="volumeGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#3b82f6" stopOpacity={0.3} />
                    <stop offset="100%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                <XAxis dataKey="date" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false}
                  tickFormatter={(v) => `₹${(v / 1000).toFixed(0)}k`} />
                <Tooltip
                  contentStyle={{ borderRadius: '8px', border: '1px solid #e2e8f0', fontSize: '13px' }}
                  formatter={(v: number) => [formatCurrency(v), 'Volume']}
                />
                <Area type="monotone" dataKey="volume" stroke="#3b82f6" strokeWidth={2} fill="url(#volumeGradient)" />
              </AreaChart>
            </ResponsiveContainer>
          ) : (
            <EmptyState icon={<TrendingUp className="w-6 h-6" />} title="No payment data yet" description="Create your first payment to see analytics" />
          )}
        </div>

        {/* Status breakdown */}
        <div className="card p-6">
          <h3 className="text-sm font-semibold text-slate-900 mb-4">Status Breakdown</h3>
          {statusBreakdown.length > 0 ? (
            <ResponsiveContainer width="100%" height={280}>
              <PieChart>
                <Pie data={statusBreakdown} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius={50} outerRadius={90} paddingAngle={2}>
                  {statusBreakdown.map((entry, i) => (
                    <Cell key={i} fill={COLORS[entry.status as keyof typeof COLORS] || '#94a3b8'} />
                  ))}
                </Pie>
                <Tooltip contentStyle={{ borderRadius: '8px', border: '1px solid #e2e8f0', fontSize: '13px' }} />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <EmptyState icon={<CreditCard className="w-6 h-6" />} title="No data" />
          )}
          {statusBreakdown.length > 0 && (
            <div className="mt-3 space-y-1.5">
              {statusBreakdown.map((s) => (
                <div key={s.status} className="flex items-center justify-between text-sm">
                  <span className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full" style={{ background: COLORS[s.status as keyof typeof COLORS] || '#94a3b8' }} />
                    <span className="text-slate-600">{s.name}</span>
                  </span>
                  <span className="font-medium text-slate-900">{s.value}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Recent payments */}
      <div className="card p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold text-slate-900">Recent Payments</h3>
          <Link to="/payments" className="text-sm text-brand-600 hover:text-brand-700 font-medium flex items-center gap-1">
            View all <ArrowUpRight className="w-4 h-4" />
          </Link>
        </div>
        {payments.length > 0 ? (
          <div className="overflow-x-auto -mx-6 px-6">
            <table className="w-full">
              <thead>
                <tr className="text-xs text-slate-500 uppercase tracking-wider border-b border-slate-200">
                  <th className="text-left font-medium py-2 pr-4">Reference</th>
                  <th className="text-left font-medium py-2 pr-4">Amount</th>
                  <th className="text-left font-medium py-2 pr-4">Status</th>
                  <th className="text-left font-medium py-2 pr-4">Method</th>
                  <th className="text-left font-medium py-2">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {payments.slice(0, 8).map((p) => (
                  <tr key={p.paymentReference} className="table-row-hover">
                    <td className="py-3 pr-4">
                      <Link to={`/payments/${p.paymentReference}`} className="font-mono text-sm text-brand-600 hover:text-brand-700">
                        {p.paymentReference.substring(0, 20)}…
                      </Link>
                    </td>
                    <td className="py-3 pr-4 text-sm font-medium text-slate-900">{formatCurrency(p.amount, p.currency)}</td>
                    <td className="py-3 pr-4"><StatusBadge status={p.status} /></td>
                    <td className="py-3 pr-4 text-sm text-slate-600">{p.attempts?.[0]?.paymentMethod || '—'}</td>
                    <td className="py-3 text-sm text-slate-500">{timeAgo(p.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState
            icon={<CreditCard className="w-6 h-6" />}
            title="No payments yet"
            description="Create your first payment to get started"
            action={<Link to="/payments/create" className="btn-primary"><Plus className="w-4 h-4" /> Create Payment</Link>}
          />
        )}
      </div>
    </Layout>
  );
}

function Layout({ children }: { children: React.ReactNode }) {
  return <div className="animate-fade-in">{children}</div>;
}

function StatCard({ icon, label, value, sublabel, trend, color }: {
  icon: React.ReactNode; label: string; value: string; sublabel?: string; trend?: string; color: string;
}) {
  const colors: Record<string, string> = {
    emerald: 'bg-emerald-50 text-emerald-600',
    blue: 'bg-blue-50 text-blue-600',
    amber: 'bg-amber-50 text-amber-600',
    purple: 'bg-purple-50 text-purple-600',
  };
  return (
    <div className="card p-5">
      <div className="flex items-start justify-between">
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${colors[color]}`}>{icon}</div>
        {trend && (
          <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${trend === 'up' ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'}`}>
            {trend === 'up' ? '↑' : '↓'}
          </span>
        )}
      </div>
      <p className="mt-3 text-2xl font-bold text-slate-900 tracking-tight">{value}</p>
      <p className="text-sm text-slate-500">{label}</p>
      {sublabel && <p className="text-xs text-slate-400 mt-1">{sublabel}</p>}
    </div>
  );
}

function computeStats(payments: Payment[]) {
  const totalVolume = payments.reduce((s, p) => s + Number(p.amount), 0);
  const captured = payments.filter((p) => p.status === 'CAPTURED');
  const capturedAmount = captured.reduce((s, p) => s + Number(p.amount), 0);
  const pendingCount = payments.filter((p) => ['PENDING', 'PROCESSING', 'AUTHORIZED', 'CREATED'].includes(p.status)).length;
  const failedCount = payments.filter((p) => p.status === 'FAILED').length;
  const successRate = payments.length > 0
    ? Math.round((captured.length / (captured.length + failedCount || 1)) * 100)
    : 0;
  return {
    totalVolume,
    capturedAmount,
    capturedCount: captured.length,
    totalCount: payments.length,
    pendingCount,
    successRate,
    volumeTrend: 'up' as const,
  };
}

function buildChartData(payments: Payment[]) {
  const days: Record<string, { date: string; volume: number }> = {};
  for (let i = 13; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const key = d.toISOString().substring(0, 10);
    days[key] = { date: d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }), volume: 0 };
  }
  payments.forEach((p) => {
    const key = p.createdAt.substring(0, 10);
    if (days[key]) days[key].volume += Number(p.amount);
  });
  return Object.values(days);
}

function buildStatusBreakdown(payments: Payment[]) {
  const counts: Record<string, number> = {};
  payments.forEach((p) => { counts[p.status] = (counts[p.status] || 0) + 1; });
  return Object.entries(counts).map(([status, value]) => ({
    status,
    name: status.charAt(0) + status.slice(1).toLowerCase().replace(/_/g, ' '),
    value,
  }));
}
