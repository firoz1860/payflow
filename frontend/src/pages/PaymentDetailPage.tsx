import { useEffect, useState, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft, Copy, CheckCircle2, XCircle, Clock, QrCode, RefreshCw, XCircle as Cancel,
} from 'lucide-react';
import { getPayment, cancelPayment } from '../services/paymentService';
import { PageHeader } from '../components/PageHeader';
import { StatusBadge } from '../components/StatusBadge';
import { CardSpinner, EmptyState } from '../components/Spinner';
import { Modal } from '../components/Modal';
import { toast } from '../components/Toast';
import { extractError } from '../api';
import { formatCurrency, formatDateTime } from '../lib/utils';
import { motion, AnimatePresence } from 'framer-motion';
import type { Payment } from '../types';

export function PaymentDetailPage() {
  const { reference } = useParams<{ reference: string }>();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [loading, setLoading] = useState(true);
  const [copied, setCopied] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelLoading, setCancelLoading] = useState(false);

  const load = useCallback(async () => {
    if (!reference) return;
    setLoading(true);
    try {
      setPayment(await getPayment(reference));
    } catch {
      setPayment(null);
    } finally {
      setLoading(false);
    }
  }, [reference]);

  useEffect(() => { load(); }, [load]);

  const copyRef = () => {
    navigator.clipboard.writeText(reference || '');
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleCancel = async () => {
    if (!reference) return;
    setCancelLoading(true);
    try {
      const updated = await cancelPayment(reference, cancelReason || undefined);
      setPayment(updated);
      setCancelOpen(false);
      setCancelReason('');
      toast('success', 'Payment cancelled');
    } catch (err) {
      toast('error', extractError(err));
    } finally {
      setCancelLoading(false);
    }
  };

  if (loading) return <CardSpinner />;
  if (!payment) return <EmptyState icon={<XCircle className="w-6 h-6" />} title="Payment not found" />;

  const canCancel = ['CREATED', 'PENDING', 'PROCESSING', 'AUTHORIZED'].includes(payment.status);
  const isPending = ['CREATED', 'PENDING', 'PROCESSING'].includes(payment.status);

  return (
    <>
      <PageHeader
        title="Payment Details"
        breadcrumbs={[
          { label: 'Payments', to: '/payments' },
          { label: payment.paymentReference.substring(0, 20) + '…' },
        ]}
        actions={
          <div className="flex gap-2">
            <button onClick={load} className="btn-secondary"><RefreshCw className="w-4 h-4" /> Refresh</button>
            {canCancel && (
              <button onClick={() => setCancelOpen(true)} className="btn-danger">
                <Cancel className="w-4 h-4" /> Cancel Payment
              </button>
            )}
          </div>
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <motion.div
            initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4 }}
            className="card p-6"
          >
            <div className="flex items-start justify-between mb-4">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <h3 className="text-lg font-semibold text-slate-900">{formatCurrency(payment.amount, payment.currency)}</h3>
                  <StatusBadge status={payment.status} />
                </div>
                <button onClick={copyRef} className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-brand-600 transition">
                  <span className="font-mono">{payment.paymentReference}</span>
                  {copied ? <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>
              {payment.environment && (
                <span className="badge bg-slate-100 text-slate-600">{payment.environment}</span>
              )}
            </div>

            <dl className="grid grid-cols-2 gap-4 text-sm">
              <Info label="Order ID" value={payment.merchantOrderId || '—'} />
              <Info label="Provider" value={payment.provider || '—'} />
              <Info label="Description" value={payment.description || '—'} />
              <Info label="Created" value={formatDateTime(payment.createdAt)} />
              <Info label="Updated" value={formatDateTime(payment.updatedAt)} />
              <Info label="Expires" value={payment.expiresAt ? formatDateTime(payment.expiresAt) : '—'} />
              <Info label="Refunded Amount" value={formatCurrency(payment.refundedAmount, payment.currency)} />
              <Info label="Refundable Amount" value={formatCurrency(payment.refundableAmount, payment.currency)} />
            </dl>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1, duration: 0.4 }}
            className="card p-6"
          >
            <h3 className="text-sm font-semibold text-slate-900 mb-4">Payment Attempts</h3>
            {payment.attempts && payment.attempts.length > 0 ? (
              <div className="space-y-3">
                {payment.attempts.map((a, i) => (
                  <motion.div
                    key={a.attemptNumber}
                    initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.2 + i * 0.1 }}
                    className="flex items-center gap-4 p-3 rounded-lg bg-slate-50 border border-slate-100"
                  >
                    <div className="w-8 h-8 rounded-full bg-white border border-slate-200 flex items-center justify-center text-xs font-medium text-slate-600">
                      {a.attemptNumber}
                    </div>
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-slate-900">{a.provider}</span>
                        <span className="text-xs text-slate-500">{a.paymentMethod}</span>
                      </div>
                      {a.cardLast4 && <p className="text-xs text-slate-500 mt-0.5">•••• {a.cardLast4} {a.cardNetwork}</p>}
                      {a.failureCode && <p className="text-xs text-red-500 mt-0.5">{a.failureCode}: {a.failureMessage}</p>}
                    </div>
                    <StatusBadge status={a.status} />
                    <span className="text-xs text-slate-400 hidden sm:inline">{formatDateTime(a.createdAt)}</span>
                  </motion.div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-slate-500">No attempts recorded</p>
            )}
          </motion.div>

          {payment.metadata && Object.keys(payment.metadata).length > 0 && (
            <motion.div
              initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2, duration: 0.4 }}
              className="card p-6"
            >
              <h3 className="text-sm font-semibold text-slate-900 mb-4">Metadata</h3>
              <div className="space-y-2">
                {Object.entries(payment.metadata).map(([k, v]) => (
                  <div key={k} className="flex items-center justify-between text-sm">
                    <span className="text-slate-500 font-mono">{k}</span>
                    <span className="text-slate-900 font-mono">{v}</span>
                  </div>
                ))}
              </div>
            </motion.div>
          )}
        </div>

        <div className="space-y-6">
          {payment.qrCode && (
            <motion.div
              initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} transition={{ type: 'spring', damping: 25, stiffness: 300 }}
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
                <img src={payment.qrCode.image} alt="QR Code" className="w-48 h-48" />
              </motion.div>
              <p className="mt-3 text-xs text-slate-500 font-mono break-all">{payment.qrCode.data}</p>
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

          {payment.checkoutUrl && (
            <motion.div
              initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.15, duration: 0.4 }}
              className="card p-6"
            >
              <h3 className="text-sm font-semibold text-slate-900 mb-3">Checkout URL</h3>
              <div className="p-3 bg-slate-50 rounded-lg border border-slate-100">
                <p className="text-xs text-slate-500 font-mono break-all mb-3">{payment.checkoutUrl}</p>
                <a href={payment.checkoutUrl} target="_blank" rel="noopener noreferrer" className="btn-primary w-full text-sm">
                  Open Checkout
                </a>
              </div>
            </motion.div>
          )}

          {payment.failureCode && (
            <motion.div
              initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.2, duration: 0.4 }}
              className="card p-6 border-red-200"
            >
              <div className="flex items-start gap-3">
                <XCircle className="w-5 h-5 text-red-500 flex-shrink-0 mt-0.5" />
                <div>
                  <h3 className="text-sm font-semibold text-red-900">Payment Failed</h3>
                  <p className="text-sm text-red-600 mt-1">{payment.failureCode}</p>
                  {payment.failureMessage && <p className="text-xs text-red-500 mt-1">{payment.failureMessage}</p>}
                </div>
              </div>
            </motion.div>
          )}
        </div>
      </div>

      <Modal open={cancelOpen} onClose={() => setCancelOpen(false)} title="Cancel Payment" size="sm">
        <p className="text-sm text-slate-600 mb-4">
          Are you sure you want to cancel this payment? This action cannot be undone.
        </p>
        <div className="mb-4">
          <label className="label">Reason (optional)</label>
          <textarea
            value={cancelReason} onChange={(e) => setCancelReason(e.target.value)}
            placeholder="Reason for cancellation…"
            className="input min-h-[80px] resize-none"
          />
        </div>
        <div className="flex justify-end gap-2">
          <button onClick={() => setCancelOpen(false)} className="btn-secondary">Cancel</button>
          <button onClick={handleCancel} className="btn-danger">
            {cancelLoading ? <RefreshCw className="w-4 h-4 animate-spin" /> : 'Confirm Cancel'}
          </button>
        </div>
      </Modal>
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
