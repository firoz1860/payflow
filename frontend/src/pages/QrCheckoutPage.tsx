import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, Copy, ExternalLink, Loader2, RefreshCw, XCircle } from 'lucide-react';
import { cancelPayment, getPayment } from '../services/paymentService';
import { CardSpinner } from '../components/Spinner';
import { StatusBadge } from '../components/StatusBadge';
import { toast } from '../components/Toast';
import { extractError } from '../api';
import { formatCurrency } from '../lib/utils';
import { motion } from 'framer-motion';
import type { Payment } from '../types';

const terminal = new Set(['CAPTURED', 'FAILED', 'CANCELLED', 'REFUNDED']);

export function QrCheckoutPage() {
  const { reference = '' } = useParams();
  const navigate = useNavigate();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [loading, setLoading] = useState(true);
  const [cancelling, setCancelling] = useState(false);

  const load = async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const data = await getPayment(reference);
      setPayment(data);
    } catch (err) {
      if (!silent) toast('error', extractError(err));
    } finally {
      if (!silent) setLoading(false);
    }
  };

  useEffect(() => {
    if (!reference) return;
    load();
  }, [reference]);

  useEffect(() => {
    if (!payment || terminal.has(payment.status)) return;
    const timer = window.setInterval(() => load(true), 2500);
    return () => window.clearInterval(timer);
  }, [payment?.status, reference]);

  const step = useMemo(() => {
    if (!payment) return 0;
    if (payment.status === 'CAPTURED') return 4;
    if (payment.status === 'FAILED' || payment.status === 'CANCELLED') return 3;
    if (payment.status === 'PENDING' || payment.status === 'PROCESSING' || payment.status === 'AUTHORIZED') return 2;
    return 1;
  }, [payment]);

  const copy = async (value?: string | null, label = 'Copied') => {
    if (!value) return;
    await navigator.clipboard.writeText(value);
    toast('success', label);
  };

  const onCancel = async () => {
    if (!payment || terminal.has(payment.status)) return;
    if (!confirm('Cancel this payment?')) return;
    setCancelling(true);
    try {
      const updated = await cancelPayment(payment.paymentReference, 'Cancelled from QR checkout');
      setPayment(updated);
      toast('success', 'Payment cancelled');
    } catch (err) {
      toast('error', extractError(err));
    } finally {
      setCancelling(false);
    }
  };

  if (loading) return <CardSpinner />;
  if (!payment) return <div className="card p-8 text-center text-slate-500">Payment not found.</div>;

  return (
    <div className="max-w-5xl mx-auto">
      <div className="mb-6 flex items-center justify-between gap-4">
        <button onClick={() => navigate(-1)} className="btn-ghost"><ArrowLeft className="w-4 h-4" /> Back</button>
        <Link to={`/payments/${payment.paymentReference}`} className="btn-secondary">View Payment Details</Link>
      </div>

      <div className="grid lg:grid-cols-[1.1fr_.9fr] gap-6">
        <motion.section initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} className="card glass-card p-7">
          <div className="flex items-start justify-between gap-4 mb-6">
            <div>
              <p className="text-xs uppercase tracking-[0.18em] text-slate-500">PayFlow Checkout</p>
              <h1 className="text-3xl font-bold text-slate-950 mt-2">{formatCurrency(payment.amount, payment.currency)}</h1>
              <p className="text-sm text-slate-500 mt-1">{payment.description || 'Secure payment'}</p>
            </div>
            <StatusBadge status={payment.status} />
          </div>

          <div className="rounded-3xl border border-white/70 bg-white/75 backdrop-blur-xl p-6 flex flex-col items-center text-center shadow-xl shadow-blue-950/5">
            {payment.qrCode?.image ? (
              <motion.img
                initial={{ opacity: 0, scale: 0.96 }}
                animate={{ opacity: 1, scale: 1 }}
                src={payment.qrCode.image}
                alt="UPI payment QR code"
                className="w-64 h-64 object-contain rounded-2xl bg-white p-3 border border-slate-200"
              />
            ) : (
              <div className="w-64 h-64 rounded-2xl bg-slate-100 border border-dashed border-slate-300 flex items-center justify-center text-slate-500">
                QR code is not available for this payment.
              </div>
            )}

            <div className="mt-5">
              {payment.status === 'CAPTURED' ? (
                <div className="flex items-center justify-center gap-2 text-emerald-700 font-semibold">
                  <CheckCircle2 className="w-5 h-5" /> Payment successful
                </div>
              ) : payment.status === 'FAILED' || payment.status === 'CANCELLED' ? (
                <div className="flex items-center justify-center gap-2 text-red-700 font-semibold">
                  <XCircle className="w-5 h-5" /> Payment {payment.status.toLowerCase()}
                </div>
              ) : (
                <div className="flex items-center justify-center gap-2 text-amber-700 font-semibold">
                  <span className="relative flex h-3 w-3">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-60" />
                    <span className="relative inline-flex rounded-full h-3 w-3 bg-amber-500" />
                  </span>
                  Waiting for provider confirmation
                </div>
              )}
              <p className="text-xs text-slate-500 mt-2">Scanning the QR does not mark the payment successful. Provider confirmation does.</p>
            </div>

            <div className="flex flex-wrap justify-center gap-2 mt-5">
              <button onClick={() => copy(payment.qrCode?.data, 'UPI link copied')} disabled={!payment.qrCode?.data} className="btn-secondary">
                <Copy className="w-4 h-4" /> Copy UPI Link
              </button>
              <button onClick={() => copy(payment.paymentReference, 'Payment ID copied')} className="btn-secondary">
                <Copy className="w-4 h-4" /> Copy Payment ID
              </button>
              {payment.checkoutUrl && (
                <a href={payment.checkoutUrl} target="_blank" rel="noreferrer" className="btn-secondary">
                  <ExternalLink className="w-4 h-4" /> Open Provider
                </a>
              )}
            </div>
          </div>
        </motion.section>

        <section className="space-y-6">
          <div className="card glass-card p-6">
            <h2 className="font-semibold text-slate-950">Payment details</h2>
            <dl className="mt-4 space-y-3 text-sm">
              <Row label="Payment ID" value={payment.paymentReference} mono />
              <Row label="Order ID" value={payment.merchantOrderId || '—'} mono />
              <Row label="Environment" value={payment.environment} />
              <Row label="Method" value={payment.attempts?.[0]?.paymentMethod || (payment.qrCode ? 'QR' : '—')} />
              <Row label="Provider" value={payment.provider || 'Pending assignment'} />
            </dl>
          </div>

          <div className="card glass-card p-6">
            <h2 className="font-semibold text-slate-950 mb-4">Payment progress</h2>
            <div className="space-y-4">
              {['Payment Created', 'QR Generated', 'Waiting for Customer', 'Provider Confirmation', 'Payment Captured'].map((label, index) => {
                const done = index <= step;
                const active = index === step && !terminal.has(payment.status);
                return (
                  <div key={label} className="flex items-center gap-3">
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center border ${done ? 'bg-brand-600 text-white border-brand-600' : 'bg-white/60 text-slate-400 border-slate-200'}`}>
                      {active ? <Loader2 className="w-4 h-4 animate-spin" /> : index + 1}
                    </div>
                    <span className={done ? 'text-sm font-medium text-slate-900' : 'text-sm text-slate-400'}>{label}</span>
                  </div>
                );
              })}
            </div>
          </div>

          {!terminal.has(payment.status) && (
            <div className="flex gap-2">
              <button onClick={() => load()} className="btn-secondary flex-1"><RefreshCw className="w-4 h-4" /> Refresh</button>
              <button onClick={onCancel} disabled={cancelling} className="btn-danger flex-1">
                {cancelling ? 'Cancelling…' : 'Cancel Payment'}
              </button>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

function Row({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className={`font-medium text-slate-900 text-right break-all ${mono ? 'font-mono text-xs' : ''}`}>{value}</dd>
    </div>
  );
}
