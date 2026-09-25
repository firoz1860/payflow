import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Landmark, RefreshCw, Scale, WalletCards } from 'lucide-react';
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

  const load = async () => {
    setLoading(true);
    try {
      const [a, p] = await Promise.all([listLedgerAccounts(), listLedgerPostings(100)]);
      setAccounts(a);
      setPostings(p);
    } catch (err) {
      toast('error', extractError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const summary = useMemo(() => {
    const currency = accounts[0]?.currency || postings[0]?.currency || 'INR';
    const payable = Number(accounts.find((a) => a.accountType === 'MERCHANT_PAYABLE')?.balance || 0);
    const totalDebit = postings.reduce((sum, p) => sum + Number(p.totalDebit || 0), 0);
    const totalCredit = postings.reduce((sum, p) => sum + Number(p.totalCredit || 0), 0);
    return {
      currency,
      payable,
      totalDebit,
      totalCredit,
      balanced: Math.abs(totalDebit - totalCredit) < 0.0001,
    };
  }, [accounts, postings]);

  if (loading) return <CardSpinner />;

  return (
    <>
      <PageHeader
        title="Ledger"
        description="Tenant-scoped, read-only double-entry financial records from the Ledger Service."
        actions={
          <button type="button" onClick={load} className="btn-secondary">
            <RefreshCw className="h-4 w-4" /> Refresh
          </button>
        }
      />

      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Summary icon={<WalletCards className="h-5 w-5" />} label="Merchant Payable" value={formatCurrency(summary.payable, summary.currency)} />
        <Summary icon={<Landmark className="h-5 w-5" />} label="Postings" value={postings.length.toLocaleString()} />
        <Summary icon={<Scale className="h-5 w-5" />} label="Total Debit" value={formatCurrency(summary.totalDebit, summary.currency)} />
        <div className="card p-5">
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Ledger Health</p>
          <div className={`mt-3 inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-sm font-semibold ${
            summary.balanced ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'
          }`}>
            <span className={`h-2 w-2 rounded-full ${summary.balanced ? 'bg-emerald-500' : 'bg-rose-500'}`} />
            {summary.balanced ? 'BALANCED' : 'REVIEW REQUIRED'}
          </div>
        </div>
      </div>

      <div className="card overflow-hidden">
        <div className="border-b border-slate-100 px-6 py-5">
          <h2 className="font-semibold text-slate-950">Recent postings</h2>
          <p className="mt-1 text-sm text-slate-500">
            Debit and credit totals are calculated and returned by the Ledger Service.
          </p>
        </div>

        {postings.length === 0 ? (
          <EmptyState
            icon={<Landmark className="h-6 w-6" />}
            title="No ledger postings yet"
            description="Captured payments will create ledger postings asynchronously through Kafka."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-slate-50/70">
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
              <tbody className="divide-y divide-slate-100">
                {postings.map((posting) => {
                  const balanced = Math.abs(Number(posting.totalDebit) - Number(posting.totalCredit)) < 0.0001;
                  return (
                    <tr key={posting.id} className="table-row-hover">
                      <td className="px-6 py-4">
                        <Link to={`/ledger/${posting.id}`} className="font-mono text-xs text-blue-700 hover:underline">
                          {posting.id.slice(0, 8)}…
                        </Link>
                      </td>
                      <td className="px-6 py-4 font-mono text-xs text-slate-700">{posting.sourceId}</td>
                      <td className="px-6 py-4 text-sm text-slate-700">{posting.sourceType}</td>
                      <td className="px-6 py-4 text-right text-sm font-medium">{formatCurrency(Number(posting.totalDebit), posting.currency)}</td>
                      <td className="px-6 py-4 text-right text-sm font-medium">{formatCurrency(Number(posting.totalCredit), posting.currency)}</td>
                      <td className="px-6 py-4">
                        <span className={`badge ${balanced ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'}`}>
                          {balanced ? 'BALANCED' : 'UNBALANCED'}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-sm text-slate-500">{formatDateTime(posting.createdAt)}</td>
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

function Summary({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="card p-5">
      <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-xl bg-blue-100 text-blue-700">{icon}</div>
      <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">{label}</p>
      <p className="mt-1 text-xl font-bold text-slate-950">{value}</p>
    </div>
  );
}
