import { useEffect, useRef, useState } from 'react';
import type { Payment } from '../types';
import { extractError } from '../api';
import { getCheckout, verifyCheckout, getPayment, reconcileCheckout } from '../services/paymentService';
import { loadRazorpay, type RazorpayInstance } from '../lib/razorpay';
const complete = (payment: Payment) => ['CAPTURED', 'FAILED', 'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED'].includes(payment.status);
export function RazorpayCheckout({ paymentReference, onUpdated }: { paymentReference: string; onUpdated: (payment: Payment) => void }) {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('Razorpay TEST payments use the provider’s test environment.');
  const mounted = useRef(true); const inFlight = useRef(false); const verifying = useRef(false);
  const instance = useRef<RazorpayInstance>(); const timer = useRef<ReturnType<typeof setTimeout>>();
  const callback = useRef(onUpdated); callback.current = onUpdated;
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; clearTimeout(timer.current); instance.current?.close(); };
  }, [paymentReference]);
  const release = () => { inFlight.current = false; if (mounted.current) setBusy(false); };
  const poll = (deadline: number) => {
    timer.current = setTimeout(async () => {
      if (!mounted.current) return;
      if (Date.now() >= deadline) { setMessage('Payment is still processing. Refresh payment status to check again.'); release(); return; }
      try {
        const payment = await getPayment(paymentReference);
        if (!mounted.current) return;
        callback.current(payment);
        if (complete(payment)) { setMessage(`Provider-confirmed status: ${payment.status}`); release(); return; }
      } catch { if (!mounted.current) return; }
      poll(deadline);
    }, Math.min(3000, Math.max(0, deadline - Date.now())));
  };
  const launch = async () => {
    if (inFlight.current) return;
    inFlight.current = true; setBusy(true); setMessage('Opening Razorpay checkout…'); clearTimeout(timer.current);
    try {
      const [options, Constructor] = await Promise.all([getCheckout(paymentReference), loadRazorpay()]);
      if (!mounted.current) return;
      if (options.provider !== 'razorpay' || options.mode !== 'TEST' || !options.keyId.startsWith('rzp_test_')) throw new Error('Invalid provider checkout configuration');
      let completed = false;
      instance.current = new Constructor({ key: options.keyId, order_id: options.orderId, amount: options.amountMinor, currency: options.currency, name: 'Payflow',
        modal: { ondismiss: () => { if (!mounted.current || completed) return; setMessage('Checkout dismissed. Payment status is unchanged.'); release(); } },
        handler: async (response) => {
          if (!mounted.current || verifying.current) return;
          completed = true; verifying.current = true; setMessage('Verifying payment with the provider…');
          try {
            const payment = await verifyCheckout(paymentReference, { orderId: response.razorpay_order_id, paymentId: response.razorpay_payment_id, signature: response.razorpay_signature });
            if (!mounted.current) return;
            callback.current(payment); setMessage(`Provider-confirmed status: ${payment.status}`);
            if (complete(payment)) release(); else poll(Date.now() + 60000);
          } catch (error) { if (mounted.current) { setMessage(extractError(error)); release(); } }
          finally { verifying.current = false; }
        },
      });
      instance.current.on('payment.failed', () => { if (!mounted.current || completed) return; completed = true; setMessage('Payment was declined. Refresh payment status before starting another payment.'); release(); instance.current?.close(); });
      instance.current.open(); setMessage('Complete the payment in Razorpay Checkout.');
    } catch (error) { if (mounted.current) { setMessage(extractError(error)); release(); } }
  };
  const refresh = async () => {
    if (inFlight.current) return;
    inFlight.current = true; setBusy(true);
    try { const payment = await reconcileCheckout(paymentReference); if (mounted.current) { callback.current(payment); setMessage(`Provider-confirmed status: ${payment.status}`); } }
    catch (error) { if (mounted.current) setMessage(extractError(error)); }
    finally { release(); }
  };
  return <div className="rounded-xl border border-blue-200 bg-blue-50 p-4">
    <p role="status" aria-live="polite" className="mb-3 text-sm text-slate-700">{message}</p>
    <div className="flex flex-wrap gap-3">
      <button type="button" className="btn-primary" disabled={busy} onClick={launch}>Pay with Razorpay (TEST)</button>
      <button type="button" className="btn-secondary" disabled={busy} onClick={refresh}>Refresh payment status</button>
    </div>
  </div>;
}
