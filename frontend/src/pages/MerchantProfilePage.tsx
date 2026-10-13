import { useCallback, useEffect, useRef, useState } from 'react';
import { Building2, Save, RefreshCw } from 'lucide-react';
import { getMyMerchant, updateMyMerchant } from '../services/merchantService';
import { PageHeader } from '../components/PageHeader';
import { CardSpinner } from '../components/Spinner';
import { LoadErrorState } from '../components/LoadErrorState';
import { toast } from '../components/Toast';
import { extractError } from '../api';
import { StatusBadge } from '../components/StatusBadge';
import { merchantStatusConfig } from '../lib/status';
import { formatDateTime, formatCurrency } from '../lib/utils';
import { StaggerContainer, StaggerItem } from '../lib/motion';
import type { Merchant } from '../types';

export function MerchantProfilePage() {
  const [merchant, setMerchant] = useState<Merchant | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const loadSequence = useRef(0);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ businessName: '', phone: '', defaultCurrency: 'INR' });

  const load = useCallback(async () => {
    const sequence = ++loadSequence.current;
    setLoading(true);
    setLoadError(false);
    try {
      const m = await getMyMerchant();
      if (sequence !== loadSequence.current) return;
      setMerchant(m);
      setForm({ businessName: m.businessName, phone: m.phone ?? '', defaultCurrency: m.defaultCurrency });
    } catch (err) {
      if (sequence !== loadSequence.current) return;
      setMerchant(null);
      setLoadError((err as { response?: { status?: number } })?.response?.status !== 404);
    } finally {
      if (sequence === loadSequence.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    return () => { loadSequence.current++; };
  }, [load]);

  const handleSave = async () => {
    const sequence = loadSequence.current;
    setSaving(true);
    try {
      const updated = await updateMyMerchant(form);
      if (sequence !== loadSequence.current) return;
      setMerchant(updated);
      toast('success', 'Merchant profile updated');
    } catch (err) {
      if (sequence === loadSequence.current) toast('error', extractError(err));
    } finally {
      if (sequence === loadSequence.current) setSaving(false);
    }
  };

  if (loading) return <CardSpinner />;
  if (loadError) return <LoadErrorState title="Merchant profile is unavailable"
    description="Your merchant details could not be loaded. Retry before editing the profile."
    onRetry={() => { void load(); }} />;
  if (!merchant) return <p className="text-slate-500">No merchant account found.</p>;

  return (
    <>
      <PageHeader title="Merchant Profile" description="View and update your merchant details" />

      <StaggerContainer className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <StaggerItem>
          <div className="card p-6 h-full">
            <div className="flex items-center gap-3 mb-5">
              <div className="w-12 h-12 rounded-xl bg-brand-100 flex items-center justify-center">
                <Building2 className="w-6 h-6 text-brand-600" />
              </div>
              <div>
                <h3 className="text-lg font-semibold text-slate-900">{merchant.businessName}</h3>
                <p className="text-sm text-slate-500">{merchant.merchantCode}</p>
              </div>
            </div>
            <div className="space-y-3 text-sm">
              <Row label="Status" value={<StatusBadge status={merchant.status} config={merchantStatusConfig} />} />
              <Row label="Email" value={merchant.email} />
              <Row label="Country" value={merchant.country} />
              <Row label="Currency" value={merchant.defaultCurrency} />
              <Row label="Live Mode" value={
                <span className={`badge ${merchant.liveModeEnabled ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                  {merchant.liveModeEnabled ? 'Enabled' : 'Test Only'}
                </span>} />
              <Row label="Created" value={formatDateTime(merchant.createdAt)} />
            </div>
          </div>
        </StaggerItem>

        <StaggerItem>
          <div className="card p-6 h-full">
            <h3 className="text-sm font-semibold text-slate-900 mb-4">Pricing</h3>
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
          </div>
        </StaggerItem>

        <StaggerItem>
          <div className="card p-6 h-full">
            <h3 className="text-sm font-semibold text-slate-900 mb-4">Edit Details</h3>
            <div className="space-y-4">
              <div>
                <label className="label">Business Name</label>
                <input value={form.businessName} onChange={(e) => setForm({ ...form, businessName: e.target.value })} className="input" />
              </div>
              <div>
                <label className="label">Phone</label>
                <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} placeholder="+91 98765 43210" className="input" />
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
              <button onClick={handleSave} disabled={saving} className="btn-primary w-full">
                {saving ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />} Save Changes
              </button>
            </div>
          </div>
        </StaggerItem>
      </StaggerContainer>
    </>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-slate-500">{label}</span>
      <span className="font-medium text-slate-900">{value}</span>
    </div>
  );
}
