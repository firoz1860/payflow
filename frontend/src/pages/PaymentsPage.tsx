import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Search, Plus, Filter, Download, CreditCard } from 'lucide-react';
import { listPayments } from '../services/paymentService';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { CardSpinner, EmptyState } from '../components/Spinner';
import { formatCurrency, formatDateTime } from '../lib/utils';
import type { Payment, PageResponse } from '../types';

const STATUS_FILTERS = ['ALL', 'CAPTURED', 'PENDING', 'PROCESSING', 'FAILED', 'CANCELLED', 'REFUNDED'];

export function PaymentsPage() {
  const [data, setData] = useState<PageResponse<Payment> | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('ALL');
  const [search, setSearch] = useState('');
  const pageSize = 20;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listPayments({
        status: status !== 'ALL' ? status : undefined,
        page,
        size: pageSize,
      });
      setData(res);
    } catch {
      setData({ data: [], page: 0, size: pageSize, totalElements: 0, totalPages: 0 });
    } finally {
      setLoading(false);
    }
  }, [page, status]);

  useEffect(() => { load(); }, [load]);

  const filtered = data?.data.filter((p) =>
    !search || p.paymentReference.toLowerCase().includes(search.toLowerCase()) ||
    p.merchantOrderId?.toLowerCase().includes(search.toLowerCase())
  ) || [];

  return (
    <>
      <PageHeader
        title="Payments"
        description="View and manage all payment transactions"
        actions={
          <Link to="/payments/create" className="btn-primary">
            <Plus className="w-4 h-4" /> New Payment
          </Link>
        }
      />

      {/* Filters */}
      <div className="card p-4 mb-6">
        <div className="flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by reference or order ID…"
              className="input pl-10"
            />
          </div>
          <div className="flex items-center gap-2 overflow-x-auto">
            <Filter className="w-4 h-4 text-slate-400 flex-shrink-0" />
            {STATUS_FILTERS.map((s) => (
              <button
                key={s}
                onClick={() => { setStatus(s); setPage(0); }}
                className={`px-3 py-1.5 rounded-lg text-sm font-medium transition whitespace-nowrap ${
                  status === s ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                {s}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="card overflow-hidden">
        {loading ? (
          <CardSpinner />
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={<CreditCard className="w-6 h-6" />}
            title="No payments found"
            description={search ? 'Try adjusting your search' : 'Create a payment to get started'}
            action={!search && <Link to="/payments/create" className="btn-primary"><Plus className="w-4 h-4" /> Create Payment</Link>}
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-slate-50">
                  <tr className="text-xs text-slate-500 uppercase tracking-wider">
                    <th className="text-left font-medium px-6 py-3">Reference</th>
                    <th className="text-left font-medium px-6 py-3">Order ID</th>
                    <th className="text-left font-medium px-6 py-3">Amount</th>
                    <th className="text-left font-medium px-6 py-3">Status</th>
                    <th className="text-left font-medium px-6 py-3">Method</th>
                    <th className="text-left font-medium px-6 py-3">Date</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filtered.map((p) => (
                    <tr key={p.paymentReference} className="table-row-hover cursor-pointer"
                      onClick={() => window.location.href = `/payments/${p.paymentReference}`}>
                      <td className="px-6 py-4">
                        <Link to={`/payments/${p.paymentReference}`} className="font-mono text-sm text-brand-600 hover:text-brand-700">
                          {p.paymentReference.substring(0, 24)}
                        </Link>
                      </td>
                      <td className="px-6 py-4 text-sm text-slate-600">{p.merchantOrderId || '—'}</td>
                      <td className="px-6 py-4 text-sm font-medium text-slate-900">{formatCurrency(p.amount, p.currency)}</td>
                      <td className="px-6 py-4"><StatusBadge status={p.status} /></td>
                      <td className="px-6 py-4 text-sm text-slate-600">{p.attempts?.[0]?.paymentMethod || '—'}</td>
                      <td className="px-6 py-4 text-sm text-slate-500">{formatDateTime(p.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {data && data.totalPages > 1 && (
              <div className="flex items-center justify-between px-6 py-3 border-t border-slate-200">
                <p className="text-sm text-slate-500">
                  Showing {page * pageSize + 1}–{Math.min((page + 1) * pageSize, data.totalElements)} of {data.totalElements}
                </p>
                <div className="flex gap-2">
                  <button onClick={() => setPage(Math.max(0, page - 1))} disabled={page === 0}
                    className="btn-secondary px-3 py-1.5 text-sm disabled:opacity-50">Previous</button>
                  <button onClick={() => setPage(Math.min(data.totalPages - 1, page + 1))} disabled={page >= data.totalPages - 1}
                    className="btn-secondary px-3 py-1.5 text-sm disabled:opacity-50">Next</button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </>
  );
}
