import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CreditCard, Smartphone, QrCode, Building2, Wallet, ArrowRight, CheckCircle2, XCircle, Clock, ExternalLink, Copy } from 'lucide-react';
import { createPayment } from '../services/paymentService';
import { PageHeader } from '../components/PageHeader';
import { Spinner } from '../components/Spinner';
import { StatusBadge } from '../components/StatusBadge';
import { toast } from '../components/Toast';
import { extractError } from '../api';
import { generateIdempotencyKey, formatCurrency } from '../lib/utils';
import { motion, AnimatePresence } from 'framer-motion';
import type { Payment, PaymentMethod } from '../types';

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
  const [result, setResult] = useState<Payment | null>(null);
  const [copied, setCopied] = useState(false);
  const [form, setForm] = useState({
    amount: '', currency: 'INR', merchantOrderId: '',
    description: '', paymentMethod: 'CARD' as PaymentMethod, returnUrl: '',
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setResult(null);
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
      setResult(payment);
      toast('success', 'Payment created successfully');
    } catch (err) {
      toast('error', extractError(err));
    } finally {
      setLoading(false);
    }
  };

  const copyRef = () => {
    if (!result) return;
    navigator.clipboard.writeText(result.paymentReference);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const isPending = result && ['CREATED', 'PENDING', 'PROCESSING'].includes(result.status);
  const isFailed = result && result.status === 'FAILED';

  return (
    <>
      <PageHeader title="Create Payment" description="Create a new payment transaction" />

      <AnimatePresence mode="wait">
        {!result ? (
          <motion.form
            key="form"
            exit={{ opacity: 0, y: -20 }}
            onSubmit={handleSubmit}
            className="max-w-2xl space-y-6"
          >
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
          </motion.form>
        ) : (
          <motion.div
            key="result"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4 }}
            className="max-w-2xl space-y-6"
          >
            <motion.div
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ type: 'spring', damping: 20, stiffness: 300 }}
              className={`card p-6 ${isFailed ? 'border-red-200' : ''}`}
            >
              <div className="flex items-center gap-3 mb-4">
                {isFailed ? (
                  <XCircle className="w-10 h-10 text-red-500" />
                ) : isPending ? (
                  <Clock className="w-10 h-10 text-amber-500" />
                ) : (
                  <CheckCircle2 className="w-10 h-10 text-emerald-500" />
                )}
                <div>
                  <h3 className="text-lg font-semibold text-slate-900">
                    {isFailed ? 'Payment Failed' : isPending ? 'Payment Created' : 'Payment Created'}
                  </h3>
                  <p className="text-sm text-slate-500">
                    {isFailed ? result.failureMessage || 'The payment was declined' : 'Your payment has been initiated'}
                  </p>
                </div>
                <div className="ml-auto">
                  <StatusBadge status={result.status} />
                </div>
              </div>

              <div className="flex items-center justify-between p-4 rounded-lg bg-slate-50 mb-4">
                <div>
                  <p className="text-xs text-slate-500 mb-1">Amount</p>
                  <p className="text-2xl font-bold text-slate-900">{formatCurrency(result.amount, result.currency)}</p>
                </div>
                <button onClick={copyRef} className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-brand-600 transition">
                  <span className="font-mono">{result.paymentReference.substring(0, 20)}…</span>
                  {copied ? <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>

              <dl className="grid grid-cols-2 gap-4 text-sm mb-4">
                <Info label="Order ID" value={result.merchantOrderId || '—'} />
                <Info label="Provider" value={result.provider || '—'} />
                <Info label="Environment" value={result.environment || '—'} />
                <Info label="Description" value={result.description || '—'} />
              </dl>

              {isFailed && result.failureCode && (
                <div className="p-3 rounded-lg bg-red-50 border border-red-200 mb-4">
                  <p className="text-sm text-red-700"><strong>{result.failureCode}</strong></p>
                  {result.failureMessage && <p className="text-xs text-red-600 mt-1">{result.failureMessage}</p>}
                </div>
              )}
            </motion.div>

            {result.qrCode && (
              <motion.div
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ type: 'spring', damping: 25, stiffness: 300, delay: 0.1 }}
                className="card p-6 text-center"
              >
                <h3 className="text-sm font-semibold text-slate-900 mb-4 flex items-center justify-center gap-2">
                  <QrCode className="w-4 h-4" /> QR Code
                </h3>
                <motion.div
                  whileHover={{ scale: 1.05, rotate: 1 }}
                  transition={{ type: 'spring', damping: 15 }}
                  className="inline-block p-4 bg-white border-2 border-slate-200 rounded-xl"
                >
                  <img src={result.qrCode.image} alt="QR Code" className="w-48 h-48" />
                </motion.div>
                <p className="mt-3 text-xs text-slate-500 font-mono break-all">{result.qrCode.data}</p>
                {isPending && (
                  <motion.div
                    animate={{ opacity: [1, 0.5, 1] }}
                    transition={{ duration: 2, repeat: Infinity }}
                    className="mt-4 flex items-center justify-center gap-2 text-sm text-amber-600"
                  >
                    <Clock className="w-4 h-4" /> Waiting for payment…
                  </motion.div>
                )}
              </motion.div>
            )}

            {result.checkoutUrl && (
              <motion.div
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.15, duration: 0.4 }}
                className="card p-6"
              >
                <h3 className="text-sm font-semibold text-slate-900 mb-3">Checkout URL</h3>
                <div className="p-3 bg-slate-50 rounded-lg border border-slate-100">
                  <p className="text-xs text-slate-500 font-mono break-all mb-3">{result.checkoutUrl}</p>
                  <a href={result.checkoutUrl} target="_blank" rel="noopener noreferrer" className="btn-primary w-full text-sm">
                    <ExternalLink className="w-4 h-4" /> Open Checkout
                  </a>
                </div>
              </motion.div>
            )}

            <div className="flex items-center justify-between gap-3">
              <button onClick={() => setResult(null)} className="btn-secondary">
                Create Another
              </button>
              <button onClick={() => navigate(`/payments/${result.paymentReference}`)} className="btn-primary">
                View Details <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-slate-500 mb-0.5">{label}</dt>
      <dd className="text-sm font-medium text-slate-900">{value}</dd>
    </div>
  );
}
