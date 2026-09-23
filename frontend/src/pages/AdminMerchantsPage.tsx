import { useEffect, useState, useCallback } from 'react';
import { Plus, Building2, ShieldCheck, RefreshCw, Zap, ExternalLink, ChevronLeft, ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { createMerchant, changeMerchantStatus, enableLiveMode, listMerchants } from '../services/merchantService';
import { PageHeader } from '../components/PageHeader';
import { Modal } from '../components/Modal';
import { CardSpinner, EmptyState } from '../components/Spinner';
import { StatusBadge } from '../components/StatusBadge';
import { merchantStatusConfig } from '../lib/status';
import { toast } from '../components/Toast';
import { extractError } from '../api';
import { formatDateTime } from '../lib/utils';
import { motion, AnimatePresence } from 'framer-motion';
import type { Merchant, MerchantStatus, PageResponse } from '../types';

export function AdminMerchantsPage() {
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [merchants, setMerchants] = useState<Merchant[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ businessName: '', email: '', phone: '', country: 'IN', defaultCurrency: 'INR' });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res: PageResponse<Merchant> = await listMerchants(page, 20);
      setMerchants(res.data);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (err) {
      toast('error', extractError(err));
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => { load(); }, [load]);

  const handleCreate = async () => {
    setCreating(true);
    try {
      const m = await createMerchant(form);
      setCreateOpen(false);
      setForm({ businessName: '', email: '', phone: '', country: 'IN', defaultCurrency: 'INR' });
      toast('success', `Merchant "${m.businessName}" created`);
      load();
    } catch (err) { toast('error', extractError(err)); }
    finally { setCreating(false); }
  };

  const handleStatus = async (id: string, status: MerchantStatus) => {
    try {
      await changeMerchantStatus(id, status);
      toast('success', `Merchant ${status.toLowerCase()}`);
      load();
    } catch (err) { toast('error', extractError(err)); }
  };

  const handleLive = async (id: string) => {
    if (!confirm('Enable live mode for this merchant? This allows real payments.')) return;
    try {
      await enableLiveMode(id);
      toast('success', 'Live mode enabled');
      load();
    } catch (err) { toast('error', extractError(err)); }
  };

  return (
    <>
      <PageHeader title="Merchant Management" description="Onboard and manage merchants (Platform Admin)"
        actions={<button onClick={() => setCreateOpen(true)} className="btn-primary"><Plus className="w-4 h-4" /> Onboard Merchant</button>}
      />

      <div className="card overflow-hidden">
        {loading ? (
          <CardSpinner />
        ) : merchants.length === 0 ? (
          <EmptyState icon={<Building2 className="w-6 h-6" />} title="No merchants yet"
            description="Onboard your first merchant to get started"
            action={<button onClick={() => setCreateOpen(true)} className="btn-primary"><Plus className="w-4 h-4" /> Onboard Merchant</button>} />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-slate-50">
                  <tr className="text-xs text-slate-500 uppercase tracking-wider">
                    <th className="text-left font-medium px-6 py-3">Business</th>
                    <th className="text-left font-medium px-6 py-3">Code</th>
                    <th className="text-left font-medium px-6 py-3">Status</th>
                    <th className="text-left font-medium px-6 py-3">Currency</th>
                    <th className="text-left font-medium px-6 py-3">Live Mode</th>
                    <th className="text-left font-medium px-6 py-3">Created</th>
                    <th className="text-right font-medium px-6 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  <AnimatePresence>
                    {merchants.map((m, i) => (
                      <motion.tr
                        key={m.id}
                        initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, x: -20 }}
                        transition={{ delay: i * 0.05, duration: 0.2 }}
                        className="table-row-hover"
                      >
                        <td className="px-6 py-4">
                          <Link to={`/admin/merchants/${m.id}`} className="text-sm font-medium text-slate-900 hover:text-brand-600 transition">
                            {m.businessName}
                          </Link>
                          <p className="text-xs text-slate-500">{m.email}</p>
                        </td>
                        <td className="px-6 py-4 text-sm font-mono text-slate-600">{m.merchantCode}</td>
                        <td className="px-6 py-4"><StatusBadge status={m.status} config={merchantStatusConfig} /></td>
                        <td className="px-6 py-4 text-sm text-slate-600">{m.defaultCurrency}</td>
                        <td className="px-6 py-4">
                          <span className={`badge ${m.liveModeEnabled ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                            {m.liveModeEnabled ? 'Enabled' : 'Test'}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-sm text-slate-500">{formatDateTime(m.createdAt)}</td>
                        <td className="px-6 py-4 text-right">
                          <div className="flex justify-end gap-1">
                            <Link to={`/admin/merchants/${m.id}`} className="p-1.5 rounded-lg text-slate-500 hover:bg-slate-100 transition" title="View Details">
                              <ExternalLink className="w-4 h-4" />
                            </Link>
                            {m.status === 'ACTIVE' && !m.liveModeEnabled && (
                              <motion.button whileTap={{ scale: 0.9 }} onClick={() => handleLive(m.id)}
                                className="p-1.5 rounded-lg text-emerald-600 hover:bg-emerald-50 transition" title="Enable Live Mode">
                                <Zap className="w-4 h-4" />
                              </motion.button>
                            )}
                            {m.status === 'ACTIVE' && (
                              <motion.button whileTap={{ scale: 0.9 }} onClick={() => handleStatus(m.id, 'SUSPENDED')}
                                className="p-1.5 rounded-lg text-orange-500 hover:bg-orange-50 transition" title="Suspend">
                                <ShieldCheck className="w-4 h-4" />
                              </motion.button>
                            )}
                            {m.status === 'SUSPENDED' && (
                              <motion.button whileTap={{ scale: 0.9 }} onClick={() => handleStatus(m.id, 'ACTIVE')}
                                className="p-1.5 rounded-lg text-emerald-600 hover:bg-emerald-50 transition" title="Reactivate">
                                <RefreshCw className="w-4 h-4" />
                              </motion.button>
                            )}
                          </div>
                        </td>
                      </motion.tr>
                    ))}
                  </AnimatePresence>
                </tbody>
              </table>
            </div>
            {totalPages > 1 && (
              <div className="flex items-center justify-between px-6 py-3 border-t border-slate-100">
                <span className="text-sm text-slate-500">
                  {totalElements} merchant{totalElements !== 1 ? 's' : ''} total
                </span>
                <div className="flex items-center gap-2">
                  <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
                    className="p-1.5 rounded-lg text-slate-500 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed transition">
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <span className="text-sm text-slate-600">Page {page + 1} of {totalPages}</span>
                  <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
                    className="p-1.5 rounded-lg text-slate-500 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed transition">
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Onboard Merchant" size="md">
        <div className="space-y-4">
          <div>
            <label className="label">Business Name</label>
            <input required value={form.businessName} onChange={(e) => setForm({ ...form, businessName: e.target.value })}
              placeholder="Acme Corp" className="input" />
          </div>
          <div>
            <label className="label">Email</label>
            <input type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })}
              placeholder="contact@acme.com" className="input" />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="label">Phone (optional)</label>
              <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })}
                placeholder="+91 98765 43210" className="input" />
            </div>
            <div>
              <label className="label">Country</label>
              <select value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value })} className="input">
                <option value="IN">India</option>
                <option value="US">United States</option>
                <option value="GB">United Kingdom</option>
                <option value="SG">Singapore</option>
              </select>
            </div>
          </div>
          <div>
            <label className="label">Default Currency</label>
            <select value={form.defaultCurrency} onChange={(e) => setForm({ ...form, defaultCurrency: e.target.value })} className="input">
              <option value="INR">INR</option>
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="GBP">GBP</option>
            </select>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button onClick={() => setCreateOpen(false)} className="btn-secondary">Cancel</button>
            <button onClick={handleCreate} disabled={creating} className="btn-primary">
              {creating ? 'Creating…' : 'Create Merchant'}
            </button>
          </div>
        </div>
      </Modal>
    </>
  );
}
