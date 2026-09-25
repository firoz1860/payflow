import { useEffect, useMemo, useState } from 'react';
import { BarChart3, CheckCircle2, CreditCard, TrendingUp, XCircle } from 'lucide-react';
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { listPayments } from '../services/paymentService';
import { PageHeader } from '../components/PageHeader';
import { CardSpinner } from '../components/Spinner';
import { formatCurrency } from '../lib/utils';
import { StaggerContainer, StaggerItem } from '../lib/motion';
import type { Payment } from '../types';

const COLORS = ['#2563eb', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4'];

export function AnalyticsPage() {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    listPayments({ page: 0, size: 100 })
      .then((r) => setPayments(r.data || []))
      .catch(() => setPayments([]))
      .finally(() => setLoading(false));
  }, []);

  const model = useMemo(() => buildModel(payments), [payments]);

  if (loading) return <CardSpinner />;

  return (
    <>
      <PageHeader title="Analytics" description="Real payment performance calculated from your PayFlow payment records" />

      <StaggerContainer className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-6">
        <StaggerItem><Metric icon={<CreditCard className="w-5 h-5" />} label="Gross Volume" value={formatCurrency(model.volume, model.currency)} /></StaggerItem>
        <StaggerItem><Metric icon={<CheckCircle2 className="w-5 h-5" />} label="Captured" value={model.captured.toLocaleString()} /></StaggerItem>
        <StaggerItem><Metric icon={<TrendingUp className="w-5 h-5" />} label="Success Rate" value={`${model.successRate.toFixed(1)}%`} /></StaggerItem>
        <StaggerItem><Metric icon={<XCircle className="w-5 h-5" />} label="Failed" value={model.failed.toLocaleString()} /></StaggerItem>
      </StaggerContainer>

      <div className="grid lg:grid-cols-3 gap-6 mb-6">
        <div className="card glass-card p-6 lg:col-span-2">
          <h2 className="font-semibold text-slate-950 mb-4">Payment volume over time</h2>
          <ResponsiveContainer width="100%" height={300}>
            <AreaChart data={model.daily}>
              <defs>
                <linearGradient id="analyticsVolume" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#2563eb" stopOpacity={0.35} />
                  <stop offset="100%" stopColor="#2563eb" stopOpacity={0.02} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="day" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip formatter={(v: number) => formatCurrency(v, model.currency)} />
              <Area type="monotone" dataKey="volume" stroke="#2563eb" strokeWidth={2.5} fill="url(#analyticsVolume)" />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        <div className="card glass-card p-6">
          <h2 className="font-semibold text-slate-950 mb-4">Status mix</h2>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie data={model.statuses} dataKey="value" nameKey="name" innerRadius={70} outerRadius={105} paddingAngle={3}>
                {model.statuses.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="grid lg:grid-cols-2 gap-6">
        <div className="card glass-card p-6">
          <h2 className="font-semibold text-slate-950 mb-4">Payment methods</h2>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={model.methods}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="name" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="value" fill="#2563eb" radius={[8, 8, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="card glass-card p-6">
          <h2 className="font-semibold text-slate-950 mb-4">Failure reasons</h2>
          {model.failures.length === 0 ? (
            <div className="h-[260px] flex items-center justify-center text-sm text-slate-500">No failure reasons in the current payment sample.</div>
          ) : (
            <div className="space-y-3">
              {model.failures.map((f) => (
                <div key={f.name} className="p-4 rounded-xl bg-white/55 border border-white/70 flex items-center justify-between">
                  <span className="text-sm text-slate-700">{f.name}</span>
                  <span className="font-semibold text-slate-950">{f.value}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  );
}

function Metric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="card glass-card p-5">
      <div className="w-10 h-10 rounded-xl bg-brand-100 text-brand-700 flex items-center justify-center mb-4">{icon}</div>
      <p className="text-xs uppercase tracking-wider text-slate-500">{label}</p>
      <p className="text-2xl font-bold text-slate-950 mt-1">{value}</p>
    </div>
  );
}

function buildModel(payments: Payment[]) {
  const currency = payments[0]?.currency || 'INR';
  const capturedPayments = payments.filter((p) => p.status === 'CAPTURED');
  const volume = capturedPayments.reduce((sum, p) => sum + Number(p.amount || 0), 0);
  const captured = capturedPayments.length;
  const failed = payments.filter((p) => p.status === 'FAILED').length;
  const successRate = payments.length ? (captured / payments.length) * 100 : 0;

  const days = new Map<string, number>();
  payments.forEach((p) => {
    const day = new Date(p.createdAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
    days.set(day, (days.get(day) || 0) + (p.status === 'CAPTURED' ? Number(p.amount || 0) : 0));
  });

  const statusMap = new Map<string, number>();
  const methodMap = new Map<string, number>();
  const failureMap = new Map<string, number>();
  payments.forEach((p) => {
    statusMap.set(p.status, (statusMap.get(p.status) || 0) + 1);
    const method = p.attempts?.[0]?.paymentMethod || (p.qrCode ? 'QR' : 'UNKNOWN');
    methodMap.set(method, (methodMap.get(method) || 0) + 1);
    if (p.failureCode) failureMap.set(p.failureCode, (failureMap.get(p.failureCode) || 0) + 1);
  });

  return {
    currency,
    volume,
    captured,
    failed,
    successRate,
    daily: Array.from(days, ([day, value]) => ({ day, volume: value })),
    statuses: Array.from(statusMap, ([name, value]) => ({ name, value })),
    methods: Array.from(methodMap, ([name, value]) => ({ name, value })),
    failures: Array.from(failureMap, ([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value),
  };
}
