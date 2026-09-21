import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CreditCard, Smartphone, QrCode, Building2, Wallet, ArrowRight } from 'lucide-react';
import { createPayment } from '../services/paymentService';
import { PageHeader } from '../components/PageHeader';
import { Spinner } from '../components/Spinner';
import { toast } from '../components/Toast';
import { extractError } from '../api';
import { generateIdempotencyKey } from '../lib/utils';
import { motion, AnimatePresence } from 'framer-motion';
import type { PaymentMethod } from '../types';

const METHODS: { value: PaymentMethod; label: string; icon: any; desc: string }[] = [
  { value: 'CARD', label: 'Card', icon: CreditCard, desc: 'Credit or debit card' },
  { value: 'UPI', label: 'UPI', icon: Smartphone, desc: 'UPI payment' },
  { value: 'QR', label: 'QR Code', icon: QrCode, desc: 'Generate a QR for customer to scan' },
  { value: 'NET_BANKING', label: 'Net Banking', icon: Building2, desc: 'Bank transfer' },
  { value: 'WALLET', label: 'Wallet', icon: Wallet, desc: 'Mobile wallet' },
];

export function CreatePaymentPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState({
    amount: '', currency: 'INR', merchantOrderId: '',
    description: '', paymentMethod: 'CARD' as PaymentMethod, returnUrl: '',
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const idempotencyKey = generateIdempotencyKey();
      const payload: any = {
        amount: parseFloat(form.amount),
        currency: form.currency,
        paymentMethod: form.paymentMethod,
      };
      if (form.merchantOrderId) payload.merchantOrderId = form.merchantOrderId;
      if (form.description) payload.description = form.description;
      if (form.returnUrl) payload.returnUrl = form.returnUrl;

      const payment = await createPayment(payload, idempotencyKey);
      toast('success', 'Payment created successfully');
      navigate(`/payments/${payment.paymentReference}`);
    } catch (err) {
      toast('error', extractError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <PageHeader title="Create Payment" description="Create a new payment transaction" />

      <form onSubmit={handleSubmit} className="max-w-2xl space-y-6">
        <motion.div
          initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}
          className="card p-6"
        >
          <label className="label">Payment Method</label>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mt-2">
            {METHODS.map((m, i) => {
              const Icon = m.icon;
              const selected = form.paymentMethod === m.value;
              return (
                <motion.button
                  key={m.value}
                  type="button"
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: i * 0.05 }}
                  whileHover={{ y: -2 }}
                  whileTap={{ scale: 0.97 }}
                  onClick={() => setForm({ ...form, paymentMethod: m.value })}
                  className={`relative p-4 rounded-xl border-2 text-left transition-all overflow-hidden ${
                    selected ? 'border-brand-500 bg-brand-50' : 'border-slate-200 hover:border-slate-300'
                  }`}
                >
                  {selected && (
                    <motion.div
                      layoutId="method-selected"
                      className="absolute inset-0 bg-brand-50"
                      style={{ zIndex: -1 }}
                      transition={{ type: 'spring', damping: 25, stiffness: 400 }}
                    />
                  )}
                  <Icon className={`w-6 h-6 mb-2 relative z-10 ${selected ? 'text-brand-600' : 'text-slate-400'}`} />
                  <p className="text-sm font-medium text-slate-900 relative z-10">{m.label}</p>
                  <p className="text-xs text-slate-500 mt-0.5 relative z-10">{m.desc}</p>
                </motion.button>
              );
            })}
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1, duration: 0.3 }}
          className="card p-6"
        >
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="sm:col-span-2">
              <label className="label">Amount</label>
              <div className="relative">
                <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 font-medium">₹</span>
                <input type="number" step="0.01" min="0.01" required
                  value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })}
                  placeholder="1000.00" className="input pl-8" />
              </div>
            </div>
            <div>
              <label className="label">Currency</label>
              <select value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })} className="input">
                <option value="INR">INR</option>
                <option value="USD">USD</option>
                <option value="EUR">EUR</option>
                <option value="GBP">GBP</option>
              </select>
            </div>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15, duration: 0.3 }}
          className="card p-6 space-y-4"
        >
          <div>
            <label className="label">Order ID (optional)</label>
            <input value={form.merchantOrderId} onChange={(e) => setForm({ ...form, merchantOrderId: e.target.value })}
              placeholder="order_1234" className="input" />
          </div>
          <div>
            <label className="label">Description (optional)</label>
            <input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })}
              placeholder="Order #1234" className="input" />
          </div>
          <div>
            <label className="label">Return URL (optional)</label>
            <input value={form.returnUrl} onChange={(e) => setForm({ ...form, returnUrl: e.target.value })}
              placeholder="https://your-store.com/return" className="input" />
          </div>
        </motion.div>

        <div className="flex items-center justify-end gap-3">
          <button type="button" onClick={() => navigate('/payments')} className="btn-secondary">Cancel</button>
          <motion.button type="submit" disabled={loading} whileTap={{ scale: 0.98 }} className="btn-primary">
            {loading ? <Spinner size="sm" /> : <>Create Payment <ArrowRight className="w-4 h-4" /></>}
          </motion.button>
        </div>

        <motion.div
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.3 }}
          className="p-4 rounded-lg bg-amber-50 border border-amber-200"
        >
          <p className="text-sm text-amber-800">
            <strong>Test mode:</strong> Amounts ending in <code className="px-1 py-0.5 bg-amber-100 rounded font-mono">.99</code> decline at creation,{' '}
            <code className="px-1 py-0.5 bg-amber-100 rounded font-mono">.13</code> decline at webhook stage.
          </p>
        </motion.div>
      </form>
    </>
  );
}
