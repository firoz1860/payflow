import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import QRCode from 'qrcode';
import { CheckCircle2, Clock, Copy, QrCode as QrIcon, RefreshCw, XCircle } from 'lucide-react';
import { createPayment, getPayment } from '../services/paymentService';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { toast } from '../components/Toast';
import { extractError } from '../api';
import { formatCurrency } from '../lib/utils';
import type { Payment } from '../types';

export function QrPaymentsPage() {
  const [amount, setAmount] = useState('250');
  const [orderId, setOrderId] = useState(() => `order_${Date.now()}`);
  const [description, setDescription] = useState('PayFlow QR checkout');
  const [payment, setPayment] = useState<Payment | null>(null);
  const [qrImage, setQrImage] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const idempotencyKey = useMemo(
    () => `qr_${orderId}_${Math.random().toString(36).slice(2, 8)}`,
    [orderId]
  );

  useEffect(() => {
    let cancelled = false;
    const buildQr = async () => {
      const data = payment?.qrCode?.data;
      if (!data) {
        setQrImage(payment?.qrCode?.image || null);
        return;
      }
      if (payment?.qrCode?.image) {
        setQrImage(payment.qrCode.image);
        return;
      }
      try {
        const image = await QRCode.toDataURL(data, { width: 320, margin: 2 });
        if (!cancelled) setQrImage(image);
      } catch {
        if (!cancelled) setQrImage(null);
      }
    };
    buildQr();
    return () => {
      cancelled = true;
    };
  }, [payment]);

  useEffect(() => {
    if (!payment || !['CREATED', 'PENDING', 'PROCESSING'].includes(payment.status)) return;
    const timer = window.setInterval(async () => {
      try {
        const updated = await getPayment(payment.paymentReference);
        setPayment(updated);
      } catch {
        // Keep the last verified state visible; manual refresh remains available.
      }
    }, 5000);
    return () => window.clearInterval(timer);
  }, [payment?.paymentReference, payment?.status]);

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault();
    const parsed = Number(amount);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      toast('error', 'Enter a valid amount greater than zero');
      return;
    }
    setCreating(true);
    try {
      const created = await createPayment(
        {
          amount: parsed,
          currency: 'INR',
          merchantOrderId: orderId,
          description,
          paymentMethod: 'QR',
        },
        idempotencyKey
      );
      setPayment(created);
      toast('success', 'QR payment created. Waiting for provider confirmation.');
    } catch (error) {
      toast('error', extractError(error));
    } finally {
      setCreating(false);
    }
  };

  const refresh = async () => {
    if (!payment) return;
    setRefreshing(true);
    try {
      setPayment(await getPayment(payment.paymentReference));
    } catch (error) {
      toast('error', extractError(error));
    } finally {
      setRefreshing(false);
    }
  };

  const copy = async (value: string, message: string) => {
    await navigator.clipboard.writeText(value);
    toast('success', message);
  };

  const reset = () => {
    setPayment(null);
    setQrImage(null);
    setOrderId(`order_${Date.now()}`);
  };

  return (
    <>
      <PageHeader
        title="QR Payments"
        description="Create a real PayFlow QR payment and track it until the provider confirms the final state."
        actions={
          payment ? (
            <button type="button" onClick={reset} className="btn-secondary">
              Create Another
            </button>
          ) : undefined
        }
      />

      {!payment ? (
        <form onSubmit={handleCreate} className="grid grid-cols-1 gap-6 lg:grid-cols-5">
          <div className="card p-6 lg:col-span-3">
            <div className="mb-5 flex items-center gap-2">
              <QrIcon className="h-5 w-5 text-blue-600" />
              <h2 className="text-sm font-bold uppercase tracking-wider text-slate-900">Payment Parameters</h2>
            </div>

            <div className="space-y-4">
              <div>
                <label className="label">Amount (INR)</label>
                <input className="input font-mono text-lg font-bold" type="number" min="1" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
              </div>
              <div>
                <label className="label">Merchant Order ID</label>
                <input className="input font-mono" value={orderId} onChange={(e) => setOrderId(e.target.value)} />
              </div>
              <div>
                <label className="label">Description</label>
                <input className="input" value={description} onChange={(e) => setDescription(e.target.value)} />
              </div>
              <div>
                <label className="label">Idempotency Key</label>
                <div className="rounded-xl border border-slate-200/80 bg-slate-50/70 px-3.5 py-2.5 font-mono text-xs text-slate-600">
                  {idempotencyKey}
                </div>
                <p className="mt-1.5 text-xs text-slate-500">
                  Reusing this key with the same request prevents an accidental duplicate payment.
                </p>
              </div>
            </div>

            <button type="submit" disabled={creating} className="btn-primary mt-6 w-full">
              {creating ? <RefreshCw className="h-4 w-4 animate-spin" /> : <QrIcon className="h-4 w-4" />}
              {creating ? 'Creating QR Payment…' : 'Create QR Payment'}
            </button>
          </div>

          <div className="glass-panel rounded-2xl p-6 lg:col-span-2">
            <h3 className="text-sm font-semibold text-slate-900">Trust model</h3>
            <div className="mt-4 space-y-3 text-sm text-slate-600">
              <p>1. PayFlow creates the payment and QR payload.</p>
              <p>2. Customer scans the QR with a supported UPI application.</p>
              <p>3. PayFlow keeps the payment pending.</p>
              <p>4. Only verified provider confirmation can move the payment to a terminal state.</p>
            </div>
          </div>
        </form>
      ) : (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
          <div className="glass-panel-elevated rounded-3xl p-8 text-center lg:col-span-3">
            <div className="mb-5 flex items-center justify-between gap-3 text-left">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">PayFlow Checkout</p>
                <h2 className="mt-1 text-2xl font-bold text-slate-900">{formatCurrency(payment.amount, payment.currency)}</h2>
              </div>
              <StatusBadge status={payment.status} />
            </div>

            {qrImage && ['CREATED', 'PENDING', 'PROCESSING'].includes(payment.status) ? (
              <div className="mx-auto inline-block rounded-2xl border-2 border-slate-200 bg-white p-4 shadow-inner">
                <img src={qrImage} alt="PayFlow payment QR" className="h-64 w-64" />
              </div>
            ) : payment.status === 'CAPTURED' ? (
              <div className="mx-auto flex h-64 w-64 flex-col items-center justify-center rounded-2xl border border-emerald-200 bg-emerald-50/80">
                <CheckCircle2 className="h-16 w-16 text-emerald-600" />
                <p className="mt-4 text-lg font-bold text-emerald-900">Payment Captured</p>
                <p className="mt-1 text-xs text-emerald-700">Verified provider confirmation received.</p>
              </div>
            ) : payment.status === 'FAILED' || payment.status === 'CANCELLED' ? (
              <div className="mx-auto flex h-64 w-64 flex-col items-center justify-center rounded-2xl border border-rose-200 bg-rose-50/80">
                <XCircle className="h-16 w-16 text-rose-600" />
                <p className="mt-4 text-lg font-bold text-rose-900">Payment {payment.status}</p>
                <p className="mt-1 text-xs text-rose-700">{payment.failureMessage || 'The provider did not confirm a successful payment.'}</p>
              </div>
            ) : (
              <div className="mx-auto flex h-64 w-64 flex-col items-center justify-center rounded-2xl border border-amber-200 bg-amber-50/80">
                <Clock className="h-14 w-14 text-amber-600" />
                <p className="mt-4 text-sm font-semibold text-amber-900">Waiting for provider state</p>
              </div>
            )}

            <div className="mt-5 flex flex-wrap items-center justify-center gap-2">
              {payment.qrCode?.data && (
                <button type="button" onClick={() => copy(payment.qrCode!.data, 'UPI payload copied')} className="btn-secondary">
                  <Copy className="h-4 w-4" /> Copy UPI Link
                </button>
              )}
              <button type="button" onClick={() => copy(payment.paymentReference, 'Payment ID copied')} className="btn-secondary">
                <Copy className="h-4 w-4" /> Copy Payment ID
              </button>
              <button type="button" onClick={refresh} disabled={refreshing} className="btn-primary">
                <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} /> Refresh
              </button>
            </div>
          </div>

          <div className="space-y-4 lg:col-span-2">
            <div className="card p-5">
              <h3 className="text-sm font-semibold text-slate-900">Payment Details</h3>
              <dl className="mt-4 space-y-3 text-sm">
                <Row label="Payment ID" value={payment.paymentReference} mono />
                <Row label="Order ID" value={payment.merchantOrderId || '—'} mono />
                <Row label="Provider" value={payment.provider || 'Waiting'} />
                <Row label="Environment" value={payment.environment || '—'} />
              </dl>
            </div>

            <div className="card p-5">
              <h3 className="text-sm font-semibold text-slate-900">Lifecycle</h3>
              <div className="mt-4 space-y-3 text-xs">
                <Step done label="Payment created" />
                <Step done={!!payment.qrCode} label="QR generated" />
                <Step done={['CAPTURED', 'FAILED', 'CANCELLED', 'REFUNDED'].includes(payment.status)} label="Provider confirmation" />
                <Step done={payment.status === 'CAPTURED'} label="Captured" />
              </div>
            </div>

            <Link to={`/payments/${payment.paymentReference}`} className="btn-primary w-full">
              View Payment Details
            </Link>
          </div>
        </div>
      )}
    </>
  );
}

function Row({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-slate-100 pb-2 last:border-0 last:pb-0">
      <dt className="text-slate-500">{label}</dt>
      <dd className={`max-w-[65%] break-all text-right font-medium text-slate-900 ${mono ? 'font-mono text-xs' : ''}`}>{value}</dd>
    </div>
  );
}

function Step({ done, label }: { done: boolean; label: string }) {
  return (
    <div className="flex items-center gap-3">
      <span className={`h-2.5 w-2.5 rounded-full ${done ? 'bg-emerald-500' : 'bg-slate-200'}`} />
      <span className={done ? 'font-medium text-slate-800' : 'text-slate-400'}>{label}</span>
    </div>
  );
}
