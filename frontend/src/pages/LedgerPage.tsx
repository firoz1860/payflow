import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Landmark, Scale, WalletCards } from 'lucide-react';
import { listLedgerAccounts, listLedgerPostings } from '../services/ledgerService';
import { PageHeader } from '../components/PageHeader';
import { CardSpinner, EmptyState } from '../components/Spinner';
import { extractError } from '../api';
import { toast } from '../components/Toast';
import { formatCurrency, formatDateTime } from '../lib/utils';
import type { LedgerAccount, LedgerPosting } from '../types';

export function LedgerPage() {
  const [accounts, setAccounts] = useState<LedgerAccount[]>([]);
  const [postings, setPostings] = useState<LedgerPosting[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([listLedgerAccounts(), listLedgerPostings(100)])
      .then(([a, p]) => { setAccounts(a); setPostings(p); })
      .catch((err) => toast('error', extractError(err)))
      .finally(() => setLoading(false));
  }, []);

  const summary = useMemo(() => {
    const currency = accounts[0]?.currency || postings[0]?.currency || 'INR';
    const payable = accounts.find((a) => a.accountType === 'MERCHANT_PAYABLE')?.balance || 0;
    const totalDebit = postings.reduce((s, p) => s + Number(p.totalDebit || 0), 0);
    const totalCredit = postings.reduce((s, p) => s + Number(p.totalCredit || 0), 0);
    return { currency, payable, totalDebit, totalCredit, balanced: Math.abs(totalDebit - totalCredit) < 0.0001 };
  }, [accounts, postings]);

  if (loading) return <CardSpinner />;

  return (
    <>
      <PageHeader title="Ledger" description="Immutable, tenant-scoped double-entry financial records" />

      <div className="grid sm:grid-cols-2 xl:grid-cols-4 gap-4 mb-6">
        <Summary icon={<WalletCards className="w-5 h-5" />} label="Merchant Payable" value={formatCurrency(summary.payable, summary.currency)} />
        <Summary icon={<Landmark className="w-5 h-5" />} label="Postings" value={postings.length.toLocaleString()} />
        <Summary icon={<Scale className="w-5 h-5" />} label="Total Debit" value={formatCurrency(summary.totalDebit, summary.currency)} />
        <div className="card glass-card p-5">
          <p className="text-xs uppercase tracking-wider text-slate-500">Ledger Health</p>
          <div className={`mt-3 inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-semibold ${summary.balanced ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'}`}>
            <span className={`w-2 h-2 rounded-full ${summary.balanced ? 'bg-emerald-500' : 'bg-red-500'}`} />
            {summary.balanced ? 'BALANCED' : 'REVIEW REQUIRED'}
          </div>
        </div>
      </div>

      <div className="card glass-card overflow-hidden">
        <div className="px-6 py-5 border-b border-white/70">
          <h2 className="font-semibold text-slate-950">Recent postings</h2>
          <p className="text-sm text-slate-500 mt-1">Debit and credit totals are returned by the Ledger Service, not calculated by the UI.</p>
        </div>
        {postings.length === 0 ? (
          <EmptyState icon={<Landmark className="w-6 h-6" />} title="No ledger postings yet" description="Captured payments will create ledger postings asynchronously." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-white/45">
                <tr className="text-xs uppercase tracking-wider text-slate-500">
                  <th className="px-6 py-3 text-left">Posting</th>
                  <th className="px-6 py-3 text-left">Source</th>
                  <th className="px-6 py-3 text-left">Type</th>
                  <th className="px-6 py-3 text-right">Debit</th>
                  <th className="px-6 py-3 text-right">Credit</th>
                  <th className="px-6 py-3 text-left">Status</th>
                  <th className="px-6 py-3 text-left">Created</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/60">
                {postings.map((p) => {
                  const balanced = Number(p.totalDebit) === Number(p.totalCredit);
                  return (
                    <tr key={p.id} className="table-row-hover">
                      <td className="px-6 py-4"><Link to={`/ledger/${p.id}`} className="font-mono text-xs text-brand-700 hover:underline">{p.id.slice(0, 8)}…</Link></td>
                      <td className="px-6 py-4 font-mono text-xs text-slate-700">{p.sourceId}</td>
                      <td className="px-6 py-4 text-sm text-slate-700">{p.sourceType}</td>
                      <td className="px-6 py-4 text-right text-sm font-medium">{formatCurrency(Number(p.totalDebit), p.currency)}</td>
                      <td className="px-6 py-4 text-right text-sm font-medium">{formatCurrency(Number(p.totalCredit), p.currency)}</td>
                      <td className="px-6 py-4"><span className={`badge ${balanced ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'}`}>{balanced ? 'BALANCED' : 'UNBALANCED'}</span></td>
                      <td className="px-6 py-4 text-sm text-slate-500">{formatDateTime(p.createdAt)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}

function Summary({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="card glass-card p-5">
      <div className="w-10 h-10 rounded-xl bg-brand-100 text-brand-700 flex items-center justify-center mb-4">{icon}</div>
      <p className="text-xs uppercase tracking-wider text-slate-500">{label}</p>
      <p className="text-xl font-bold text-slate-950 mt-1">{value}</p>
    </div>
  );
}
