import { useEffect, useState } from 'react';
import { Bell, Building2, Save, ShieldCheck, SlidersHorizontal } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { CardSpinner } from '../components/Spinner';
import { getMyMerchant, updateMyMerchant } from '../services/merchantService';
import { extractError } from '../api';
import { toast } from '../components/Toast';
import type { Merchant } from '../types';

export function SettingsPage() {
  const [merchant, setMerchant] = useState<Merchant | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ businessName: '', phone: '', defaultCurrency: 'INR' });
  const [notifications, setNotifications] = useState({
    paymentFailures: true,
    webhookFailures: true,
    providerIncidents: true,
    securityAlerts: true,
  });

  useEffect(() => {
    getMyMerchant()
      .then((m) => {
        setMerchant(m);
        setForm({ businessName: m.businessName, phone: m.phone || '', defaultCurrency: m.defaultCurrency });
      })
      .catch((err) => toast('error', extractError(err)))
      .finally(() => setLoading(false));
  }, []);

  const save = async () => {
    setSaving(true);
    try {
      const updated = await updateMyMerchant(form);
      setMerchant(updated);
      toast('success', 'Merchant settings saved');
    } catch (err) {
      toast('error', extractError(err));
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <CardSpinner />;

  return (
    <>
      <PageHeader title="Settings" description="Merchant profile, preferences and security controls" />

      <div className="grid xl:grid-cols-2 gap-6">
        <Section icon={<Building2 className="w-5 h-5" />} title="Merchant Details">
          <div className="grid sm:grid-cols-2 gap-4">
            <Field label="Business Name"><input className="input" value={form.businessName} onChange={(e) => setForm({ ...form, businessName: e.target.value })} /></Field>
            <Field label="Merchant ID"><input className="input bg-slate-50/70 font-mono text-xs" readOnly value={merchant?.merchantCode || ''} /></Field>
            <Field label="Support Email"><input className="input bg-slate-50/70" readOnly value={merchant?.email || ''} /></Field>
            <Field label="Phone"><input className="input" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></Field>
            <Field label="Default Currency">
              <select className="input" value={form.defaultCurrency} onChange={(e) => setForm({ ...form, defaultCurrency: e.target.value })}>
                <option value="INR">INR</option><option value="USD">USD</option><option value="EUR">EUR</option><option value="GBP">GBP</option>
              </select>
            </Field>
            <Field label="Environment"><input className="input bg-slate-50/70" readOnly value={merchant?.liveModeEnabled ? 'LIVE enabled' : 'TEST only'} /></Field>
          </div>
          <button onClick={save} disabled={saving} className="btn-primary mt-5"><Save className="w-4 h-4" /> {saving ? 'Saving…' : 'Save Changes'}</button>
        </Section>

        <Section icon={<SlidersHorizontal className="w-5 h-5" />} title="Payment Preferences">
          <div className="grid sm:grid-cols-2 gap-4">
            <Field label="Default Provider"><input className="input bg-slate-50/70" readOnly value="Provider selected by backend routing" /></Field>
            <Field label="Default Method"><input className="input bg-slate-50/70" readOnly value="Merchant chooses per payment" /></Field>
            <Field label="Settlement Delay"><input className="input bg-slate-50/70" readOnly value={`${merchant?.settlementDelayDays ?? 0} days`} /></Field>
            <Field label="Pricing"><input className="input bg-slate-50/70" readOnly value={`${merchant?.feePercentage ?? 0}% + fixed fee`} /></Field>
          </div>
          <p className="text-xs text-slate-500 mt-4">Financial pricing and settlement controls remain admin-owned and are not edited from this merchant page.</p>
        </Section>

        <Section icon={<ShieldCheck className="w-5 h-5" />} title="Security">
          <div className="space-y-3">
            <SecurityRow title="API secrets" description="Existing API keys remain masked after creation." />
            <SecurityRow title="JWT sessions" description="Access tokens are refreshed by the existing authenticated frontend flow." />
            <SecurityRow title="LIVE mode" description="Production mode is enabled by a platform admin, not by this client-side form." />
          </div>
        </Section>

        <Section icon={<Bell className="w-5 h-5" />} title="Notifications">
          <div className="space-y-3">
            {Object.entries(notifications).map(([key, enabled]) => (
              <label key={key} className="flex items-center justify-between p-3 rounded-xl bg-white/55 border border-white/70">
                <span className="text-sm font-medium text-slate-700">{labelFor(key)}</span>
                <input type="checkbox" checked={enabled} onChange={() => setNotifications({ ...notifications, [key]: !enabled })} className="h-4 w-4 accent-blue-600" />
              </label>
            ))}
          </div>
          <p className="text-xs text-slate-500 mt-4">These notification preferences are UI-only until a notification service is added to the backend.</p>
        </Section>
      </div>

      <div className="card glass-card p-6 mt-6 border-red-200/70">
        <h2 className="font-semibold text-red-800">Danger Zone</h2>
        <p className="text-sm text-slate-600 mt-1">Merchant disablement remains a platform-admin action. The frontend does not expose a client-side shortcut for it.</p>
      </div>
    </>
  );
}

function Section({ icon, title, children }: { icon: React.ReactNode; title: string; children: React.ReactNode }) {
  return <section className="card glass-card p-6"><div className="flex items-center gap-2 text-slate-950 mb-5">{icon}<h2 className="font-semibold">{title}</h2></div>{children}</section>;
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label className="label">{label}</label>{children}</div>;
}

function SecurityRow({ title, description }: { title: string; description: string }) {
  return <div className="p-4 rounded-xl bg-white/55 border border-white/70"><p className="text-sm font-medium text-slate-900">{title}</p><p className="text-xs text-slate-500 mt-1">{description}</p></div>;
}

function labelFor(key: string) {
  return ({ paymentFailures: 'Payment failures', webhookFailures: 'Webhook failures', providerIncidents: 'Provider incidents', securityAlerts: 'Security alerts' } as Record<string, string>)[key] || key;
}
