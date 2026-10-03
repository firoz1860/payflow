export type CheckoutEvidence = { orderId: string; paymentId: string; signature: string };
export type RazorpayOptions = { key: string; order_id: string; amount: number; currency: string; name: string; handler: (response: { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }) => void; modal: { ondismiss: () => void } };
export interface RazorpayInstance { open(): void; close(): void; on(event: 'payment.failed', handler: () => void): void }
export type RazorpayConstructor = new (options: RazorpayOptions) => RazorpayInstance;
declare global { interface Window { Razorpay?: RazorpayConstructor } }
let loading: Promise<RazorpayConstructor> | undefined;
export function loadRazorpay(): Promise<RazorpayConstructor> {
  if (window.Razorpay) return Promise.resolve(window.Razorpay);
  if (loading) return loading;
  loading = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js'; script.async = true;
    const fail = () => { clearTimeout(timeout); script.remove(); loading = undefined; reject(new Error('Unable to load Razorpay checkout. Please retry.')); };
    const timeout = setTimeout(fail, 10000);
    script.onerror = fail;
    script.onload = () => { clearTimeout(timeout); if (window.Razorpay) resolve(window.Razorpay); else fail(); };
    document.head.appendChild(script);
  });
  return loading;
}
