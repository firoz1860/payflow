import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, Building2, DollarSign, Zap, ShieldCheck, RefreshCw, Save } from 'lucide-react';
import { getMerchant, changeMerchantStatus, updateMerchantPricing, enableLiveMode } from '../services/merchantService';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { CardSpinner } from '../components/Spinner';
import { Modal } from '../components/Modal';
import { toast } from '../components/Toast';
import { extractError } from '../api';
import { merchantStatusConfig } from '../lib/status';
import { formatDateTime, formatCurrency } from '../lib/utils';
import { motion } from 'framer-motion';
import { useEffect } from 'react';
import type { Merchant, MerchantStatus } from '../types';

export function AdminMerchantDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [merchant, setMerchant] = useState<Merchant | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusModal, setStatusModal] = useState<MerchantStatus | null>(null);
  const [statusReason, setStatusReason] = useState('');
  const [pricingModal, setPricingModal] = useState(false);
  const [pricing, setPricing] = useState({ feePercentage: 0, fixedFee: 0, settlementDelayDays: 7 });
  const [actionLoading, setActionLoading] = useState(false);

  const load = async () => {
    if (!id) return;
    setLoading(true);
    try {
      const m = await getMerchant(id);
      setMerchant(m);
      setPricing({ feePercentage: m.feePercentage, fixedFee: m.fixedFee, settlementDelayDays: m.settlementDelayDays });
    } catch {
      setMerchant(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [id]);

  const handleStatusChange = async () => {
    if (!id || !statusModal) return;
    setActionLoading(true);
    try {
      const m = await changeMerchantStatus(id, statusModal, statusReason || undefined);
      setMerchant(m);
      setStatusModal(null);
      setStatusReason('');
      toast('success', `Merchant ${statusModal.toLowerCase()}`);
    } catch (err) { toast('error', extractError(err)); }
    finally { setActionLoading(false); }
  };

  const handlePricingSave = async () => {
    if (!id) return;
    setActionLoading(true);
    try {
      const m = await updateMerchantPricing(id, pricing);
      setMerchant(m);
      setPricingModal(false);
      toast('success', 'Pricing updated');
    } catch (err) { toast('error', extractError(err)); }
    finally { setActionLoading(false); }
  };

  const handleLiveMode = async () => {
    if (!id) return;
    if (!confirm('Enable live mode? This allows real payments.')) return;
    setActionLoading(true);
    try {
      const m = await enableLiveMode(id);
      setMerchant(m);
      toast('success', 'Live mode enabled');
    } catch (err) { toast('error', extractError(err)); }
    finally { setActionLoading(false); }
  };

  if (loading) return <CardSpinner />;
  if (!merchant) return (
    <div className="flex flex-col items-center justify-center py-16">
      <p className="text-slate-500 mb-4">Merchant not found</p>
      <Link to="/admin/merchants" className="btn-primary">Back to merchants</Link>
    </div>
  );

  return (
    <>
      <PageHeader
        title={merchant.businessName}
        description={`Merchant Code: ${merchant.merchantCode}`}
        breadcrumbs={[
          { label: 'Admin', to: '/admin/merchants' },
          { label: merchant.businessName },
        ]}
        actions={
          <button onClick={() => navigate('/admin/merchants')} className="btn-secondary">
            <ArrowLeft className="w-4 h-4" /> Back
          </button>
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <motion.div
          initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}
          className="card p-6"
        >
          <div className="flex items-center gap-3 mb-5">
            <div className="w-12 h-12 rounded-xl bg-brand-100 flex items-center justify-center">
              <Building2 className="w-6 h-6 text-brand-600" />
            </div>
            <div className="flex-1">
              <h3 className="text-lg font-semibold text-slate-900">{merchant.businessName}</h3>
              <div className="flex items-center gap-2 mt-1">
                <StatusBadge status={merchant.status} config={merchantStatusConfig} />
                {merchant.liveModeEnabled && (
                  <span className="badge bg-emerald-100 text-emerald-700">Live Mode</span>
                )}
              </div>
            </div>
          </div>

          <dl className="space-y-3 text-sm">
            <Row label="Email" value={merchant.email} />
            <Row label="Phone" value={merchant.phone || '—'} />
            <Row label="Country" value={merchant.country} />
            <Row label="Currency" value={merchant.defaultCurrency} />
            <Row label="Created" value={formatDateTime(merchant.createdAt)} />
            <Row label="Updated" value={formatDateTime(merchant.updatedAt)} />
          </dl>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1, duration: 0.3 }}
          className="card p-6"
        >
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold text-slate-900 flex items-center gap-2">
              <DollarSign className="w-4 h-4" /> Pricing
            </h3>
            <button onClick={() => setPricingModal(true)} className="btn-secondary text-sm">
              <Save className="w-4 h-4" /> Edit
            </button>
          </div>
          <div className="space-y-3">
            <div className="p-3 rounded-lg bg-slate-50">
              <p className="text-xs text-slate-500">Fee Percentage</p>
              <p className="text-xl font-bold text-slate-900">{merchant.feePercentage}%</p>
            </div>
            <div className="p-3 rounded-lg bg-slate-50">
              <p className="text-xs text-slate-500">Fixed Fee</p>
              <p className="text-xl font-bold text-slate-900">{formatCurrency(merchant.fixedFee, merchant.defaultCurrency)}</p>
            </div>
            <div className="p-3 rounded-lg bg-slate-50">
              <p className="text-xs text-slate-500">Settlement Delay</p>
              <p className="text-xl font-bold text-slate-900">{merchant.settlementDelayDays} days</p>
            </div>
          </div>
        </motion.div>
      </div>

      <motion.div
        initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2, duration: 0.3 }}
        className="card p-6 mt-6"
      >
        <h3 className="text-sm font-semibold text-slate-900 mb-4">Actions</h3>
        <div className="flex flex-wrap gap-3">
          {merchant.status === 'ACTIVE' && !merchant.liveModeEnabled && (
            <button onClick={handleLiveMode} disabled={actionLoading} className="btn-primary">
              <Zap className="w-4 h-4" /> Enable Live Mode
            </button>
          )}
          {merchant.status === 'ACTIVE' && (
            <button onClick={() => setStatusModal('SUSPENDED')} disabled={actionLoading} className="btn-secondary">
              <ShieldCheck className="w-4 h-4 text-orange-500" /> Suspend
            </button>
          )}
          {merchant.status === 'SUSPENDED' && (
            <button onClick={() => setStatusModal('ACTIVE')} disabled={actionLoading} className="btn-primary">
              <RefreshCw className="w-4 h-4" /> Reactivate
            </button>
          )}
          {merchant.status !== 'BLOCKED' && (
            <button onClick={() => setStatusModal('BLOCKED')} disabled={actionLoading} className="btn-danger">
              <ShieldCheck className="w-4 h-4" /> Block
            </button>
          )}
        </div>
      </motion.div>

      {/* Status change modal */}
      <Modal open={!!statusModal} onClose={() => { setStatusModal(null); setStatusReason(''); }}
        title={`Change Status to ${statusModal || ''}`} size="sm">
        <p className="text-sm text-slate-600 mb-4">
          {statusModal === 'ACTIVE' ? 'This will reactivate the merchant and restore all API keys.' :
           statusModal === 'SUSPENDED' ? 'This will suspend the merchant and revoke all API keys temporarily.' :
           'This will permanently block the merchant. All API keys will be revoked.'}
        </p>
        <div className="mb-4">
          <label className="label">Reason (optional)</label>
          <textarea
            value={statusReason} onChange={(e) => setStatusReason(e.target.value)}
            placeholder="Reason for status change…"
            className="input min-h-[80px] resize-none"
          />
        </div>
        <div className="flex justify-end gap-2">
          <button onClick={() => { setStatusModal(null); setStatusReason(''); }} className="btn-secondary">Cancel</button>
          <button onClick={handleStatusChange} disabled={actionLoading} className="btn-primary">
            {actionLoading ? 'Updating…' : 'Confirm'}
          </button>
        </div>
      </Modal>

      {/* Pricing modal */}
      <Modal open={pricingModal} onClose={() => setPricingModal(false)} title="Update Pricing" size="sm">
        <div className="space-y-4">
          <div>
            <label className="label">Fee Percentage (%)</label>
            <input type="number" step="0.01" min="0" max="100"
              value={pricing.feePercentage}
              onChange={(e) => setPricing({ ...pricing, feePercentage: parseFloat(e.target.value) })}
              className="input" />
          </div>
          <div>
            <label className="label">Fixed Fee</label>
            <input type="number" step="0.0001" min="0"
              value={pricing.fixedFee}
              onChange={(e) => setPricing({ ...pricing, fixedFee: parseFloat(e.target.value) })}
              className="input" />
          </div>
          <div>
            <label className="label">Settlement Delay (days)</label>
            <input type="number" min="0"
              value={pricing.settlementDelayDays}
              onChange={(e) => setPricing({ ...pricing, settlementDelayDays: parseInt(e.target.value) })}
              className="input" />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button onClick={() => setPricingModal(false)} className="btn-secondary">Cancel</button>
            <button onClick={handlePricingSave} disabled={actionLoading} className="btn-primary">
              {actionLoading ? 'Saving…' : 'Save Pricing'}
            </button>
          </div>
        </div>
      </Modal>
    </>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <dt className="text-slate-500">{label}</dt>
      <dd className="font-medium text-slate-900">{value}</dd>
    </div>
  );
}
