import { useEffect, useMemo, useState } from 'react';
import { BookOpen, CheckCircle2, Database, LockKeyhole, RefreshCw, Scale } from 'lucide-react';
import { listPayments } from '../services/paymentService';
import { PageHeader } from '../components/PageHeader';
import { CardSpinner, EmptyState } from '../components/Spinner';
import { formatCurrency, formatDateTime } from '../lib/utils';
import type { Payment } from '../types';

export function LedgerPage() {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const page = await listPayments({ page: 0, size: 50 });
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

  const captured = useMemo(() => payments.filter((p) => p.status === 'CAPTURED'), [payments]);
  const capturedVolume = useMemo(() => captured.reduce((sum, p) => sum + p.amount, 0), [captured]);

  if (loading) return <CardSpinner />;

  return (
    <>
      <PageHeader
        title="Ledger"
        description="A safe merchant-facing view of ledger architecture and captured source payments. Direct ledger mutation and internal ledger APIs stay private."
        actions={
          <button type="button" onClick={load} className="btn-secondary">
            <RefreshCw className="h-4 w-4" /> Refresh Sources
          </button>
        }
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <Summary title="Captured source volume" value={formatCurrency(capturedVolume)} icon={<Scale className="h-5 w-5" />} />
        <Summary title="Captured source payments" value={String(captured.length)} icon={<CheckCircle2 className="h-5 w-5" />} />
        <Summary title="Ledger API exposure" value="Internal only" icon={<LockKeyhole className="h-5 w-5" />} />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-5">
        <div className="card p-6 lg:col-span-3">
          <div className="flex items-center gap-2">
            <BookOpen className="h-5 w-5 text-blue-600" />
            <h2 className="text-sm font-bold text-slate-900">Captured Payments Feeding the Ledger</h2>
          </div>
          <p className="mt-1 text-xs text-slate-500">
            These records come from the Payment Service. This screen does not invent debit/credit postings that the current public API does not expose.
          </p>

          {captured.length ? (
            <div className="mt-5 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-xs uppercase tracking-wider text-slate-400">
                    <th className="py-3 pr-4">Payment</th>
                    <th className="py-3 pr-4">Amount</th>
                    <th className="py-3 pr-4">Provider</th>
                    <th className="py-3">Captured</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {captured.map((payment) => (
                    <tr key={payment.paymentReference} className="table-row-hover">
                      <td className="py-3 pr-4 font-mono text-xs text-blue-700">{payment.paymentReference}</td>
                      <td className="py-3 pr-4 font-semibold text-slate-900">{formatCurrency(payment.amount, payment.currency)}</td>
                      <td className="py-3 pr-4 text-slate-600">{payment.provider || '—'}</td>
                      <td className="py-3 text-xs text-slate-500">{formatDateTime(payment.updatedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState icon={<Database className="h-6 w-6" />} title="No captured source payments" />
          )}
        </div>

        <div className="space-y-4 lg:col-span-2">
          <div className="glass-panel-elevated rounded-2xl p-5">
            <h3 className="text-sm font-bold text-slate-900">Double-entry rule</h3>
            <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50/80 p-4 text-center">
              <p className="text-xs font-semibold uppercase tracking-wider text-emerald-700">Invariant</p>
              <p className="mt-1 text-xl font-black text-emerald-900">Total Debit = Total Credit</p>
            </div>
            <p className="mt-3 text-xs leading-5 text-slate-500">
              The Ledger Service owns postings and entries. Corrections are represented by reversing postings rather than mutating existing entries.
            </p>
          </div>

          <div className="card p-5">
            <h3 className="text-sm font-bold text-slate-900">Why entries are not editable here</h3>
            <p className="mt-2 text-xs leading-5 text-slate-500">
              The current backend exposes ledger operations only under internal service routes. The API Gateway blocks internal paths, so the browser cannot bypass service boundaries or financial controls.
            </p>
          </div>
        </div>
      </div>
    </>
  );
}

function Summary({ title, value, icon }: { title: string; value: string; icon: React.ReactNode }) {
  return (
    <div className="card p-5">
      <div className="text-blue-600">{icon}</div>
      <p className="mt-3 text-xs font-semibold uppercase tracking-wider text-slate-400">{title}</p>
      <p className="mt-1 text-xl font-bold text-slate-900">{value}</p>
    </div>
  );
}
