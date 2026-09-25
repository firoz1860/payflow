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
      .then(([postings, lines]) => {
        setPosting(postings.find((item) => item.id === postingId) || null);
        setEntries(lines);
      })
      .catch((err) => toast('error', extractError(err)))
      .finally(() => setLoading(false));
  }, [postingId]);

  if (loading) return <CardSpinner />;
  if (!posting) {
    return (
      <div className="card p-8 text-center text-slate-500">
        Ledger posting not found.
      </div>
    );
  }

  const debit = entries
    .filter((entry) => entry.entryType === 'DEBIT')
    .reduce((sum, entry) => sum + Number(entry.amount), 0);
  const credit = entries
    .filter((entry) => entry.entryType === 'CREDIT')
    .reduce((sum, entry) => sum + Number(entry.amount), 0);
  const balanced = Math.abs(debit - credit) < 0.0001;

  return (
    <>
      <div className="mb-6">
        <Link to="/ledger" className="btn-secondary">
          <ArrowLeft className="h-4 w-4" /> Back to Ledger
        </Link>
      </div>

      <div className="glass-panel-elevated mb-6 rounded-2xl p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Ledger Posting</p>
            <h1 className="mt-1 break-all font-mono text-xl font-bold text-slate-950 sm:text-2xl">{posting.id}</h1>
            <p className="mt-2 text-sm text-slate-500">{posting.description || posting.sourceType}</p>
          </div>
          <span className={`badge px-3 py-1.5 text-sm ${
            balanced ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'
          }`}>
            <Scale className="h-4 w-4" /> {balanced ? 'BALANCED' : 'UNBALANCED'}
          </span>
        </div>

        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Info label="Source" value={posting.sourceId} mono />
          <Info label="Type" value={posting.sourceType} />
          <Info label="Currency" value={posting.currency} />
          <Info label="Created" value={formatDateTime(posting.createdAt)} />
        </div>
      </div>

      <div className="card mb-6 overflow-hidden">
        <div className="border-b border-slate-100 px-6 py-5">
          <h2 className="font-semibold text-slate-950">Double-entry lines</h2>
          <p className="mt-1 text-sm text-slate-500">
            These immutable entries come directly from the Ledger Service.
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-slate-50/70">
              <tr className="text-xs uppercase tracking-wider text-slate-500">
                <th className="px-6 py-3 text-left">Entry</th>
                <th className="px-6 py-3 text-left">Account</th>
                <th className="px-6 py-3 text-left">Type</th>
                <th className="px-6 py-3 text-right">Amount</th>
                <th className="px-6 py-3 text-left">Description</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {entries.map((entry) => (
                <tr key={entry.id} className="table-row-hover">
                  <td className="px-6 py-4 font-mono text-xs text-slate-600">{entry.id.slice(0, 8)}…</td>
                  <td className="px-6 py-4 font-mono text-xs text-slate-600">{entry.accountId.slice(0, 8)}…</td>
                  <td className="px-6 py-4">
                    <span className={`badge ${
                      entry.entryType === 'DEBIT'
                        ? 'bg-blue-100 text-blue-700'
                        : 'bg-emerald-100 text-emerald-700'
                    }`}>
                      {entry.entryType}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right font-semibold">
                    {formatCurrency(Number(entry.amount), entry.currency)}
                  </td>
                  <td className="px-6 py-4 text-sm text-slate-600">{entry.description || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Summary label="Total Debit" value={formatCurrency(debit, posting.currency)} />
        <Summary label="Total Credit" value={formatCurrency(credit, posting.currency)} />
        <Summary label="Difference" value={formatCurrency(Math.abs(debit - credit), posting.currency)} good={balanced} />
      </div>
    </>
  );
}

function Info({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <p className="text-xs text-slate-500">{label}</p>
      <p className={`mt-1 break-all font-medium text-slate-950 ${mono ? 'font-mono text-xs' : ''}`}>{value}</p>
    </div>
  );
}

function Summary({ label, value, good }: { label: string; value: string; good?: boolean }) {
  return (
    <div className="card p-5">
      <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">{label}</p>
      <p className={`mt-1 text-xl font-bold ${good ? 'text-emerald-700' : 'text-slate-950'}`}>{value}</p>
    </div>
  );
}
