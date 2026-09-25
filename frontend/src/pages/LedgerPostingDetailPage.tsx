import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, Scale } from 'lucide-react';
import { getLedgerEntries, listLedgerPostings } from '../services/ledgerService';
import { CardSpinner } from '../components/Spinner';
import { toast } from '../components/Toast';
import { extractError } from '../api';
import { formatCurrency, formatDateTime } from '../lib/utils';
import type { LedgerEntry, LedgerPosting } from '../types';

export function LedgerPostingDetailPage() {
  const { postingId = '' } = useParams();
  const [posting, setPosting] = useState<LedgerPosting | null>(null);
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([listLedgerPostings(200), getLedgerEntries(postingId)])
      .then(([postings, e]) => {
        setPosting(postings.find((p) => p.id === postingId) || null);
        setEntries(e);
      })
      .catch((err) => toast('error', extractError(err)))
      .finally(() => setLoading(false));
  }, [postingId]);

  if (loading) return <CardSpinner />;
  if (!posting) return <div className="card p-8 text-center text-slate-500">Ledger posting not found.</div>;

  const debit = entries.filter((e) => e.entryType === 'DEBIT').reduce((s, e) => s + Number(e.amount), 0);
  const credit = entries.filter((e) => e.entryType === 'CREDIT').reduce((s, e) => s + Number(e.amount), 0);
  const balanced = Math.abs(debit - credit) < 0.0001;

  return (
    <>
      <div className="mb-6">
        <Link to="/ledger" className="btn-ghost"><ArrowLeft className="w-4 h-4" /> Back to Ledger</Link>
      </div>

      <div className="card glass-card p-6 mb-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-wider text-slate-500">Ledger Posting</p>
            <h1 className="text-2xl font-bold text-slate-950 mt-1 font-mono">{posting.id}</h1>
            <p className="text-sm text-slate-500 mt-2">{posting.description || posting.sourceType}</p>
          </div>
          <span className={`badge text-sm px-3 py-1.5 ${balanced ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'}`}>
            <Scale className="w-4 h-4" /> {balanced ? 'BALANCED' : 'UNBALANCED'}
          </span>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4 mt-6">
          <Info label="Source" value={posting.sourceId} mono />
          <Info label="Type" value={posting.sourceType} />
          <Info label="Currency" value={posting.currency} />
          <Info label="Created" value={formatDateTime(posting.createdAt)} />
        </div>
      </div>

      <div className="card glass-card overflow-hidden mb-6">
        <div className="px-6 py-5 border-b border-white/70">
          <h2 className="font-semibold text-slate-950">Double-entry lines</h2>
          <p className="text-sm text-slate-500 mt-1">These values come from the immutable ledger entries stored by Ledger Service.</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-white/45">
              <tr className="text-xs uppercase tracking-wider text-slate-500">
                <th className="px-6 py-3 text-left">Entry</th>
                <th className="px-6 py-3 text-left">Account</th>
                <th className="px-6 py-3 text-left">Type</th>
                <th className="px-6 py-3 text-right">Amount</th>
                <th className="px-6 py-3 text-left">Description</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/60">
              {entries.map((e) => (
                <tr key={e.id} className="table-row-hover">
                  <td className="px-6 py-4 font-mono text-xs text-slate-600">{e.id.slice(0, 8)}…</td>
                  <td className="px-6 py-4 font-mono text-xs text-slate-600">{e.accountId.slice(0, 8)}…</td>
                  <td className="px-6 py-4"><span className={`badge ${e.entryType === 'DEBIT' ? 'bg-blue-100 text-blue-700' : 'bg-emerald-100 text-emerald-700'}`}>{e.entryType}</span></td>
                  <td className="px-6 py-4 text-right font-semibold">{formatCurrency(Number(e.amount), e.currency)}</td>
                  <td className="px-6 py-4 text-sm text-slate-600">{e.description || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="grid sm:grid-cols-3 gap-4">
        <Summary label="Total Debit" value={formatCurrency(debit, posting.currency)} />
        <Summary label="Total Credit" value={formatCurrency(credit, posting.currency)} />
        <Summary label="Difference" value={formatCurrency(Math.abs(debit - credit), posting.currency)} good={balanced} />
      </div>
    </>
  );
}

function Info({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return <div><p className="text-xs text-slate-500">{label}</p><p className={`mt-1 font-medium text-slate-950 break-all ${mono ? 'font-mono text-xs' : ''}`}>{value}</p></div>;
}

function Summary({ label, value, good }: { label: string; value: string; good?: boolean }) {
  return <div className="card glass-card p-5"><p className="text-xs uppercase tracking-wider text-slate-500">{label}</p><p className={`text-xl font-bold mt-1 ${good ? 'text-emerald-700' : 'text-slate-950'}`}>{value}</p></div>;
}
