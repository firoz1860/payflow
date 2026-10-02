import { useState } from 'react';
import { CheckCircle2, Code2, CreditCard, ExternalLink, ShieldCheck } from 'lucide-react';

export function FirstPaymentStep({
  hasPayment,
  canCreate,
  onOpenCreatePayment,
}: {
  hasPayment: boolean;
  canCreate: boolean;
  onOpenCreatePayment: () => void;
}) {
  const [showExample, setShowExample] = useState(false);

  return (
    <div className="space-y-5">
      {hasPayment ? (
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50/70 p-5">
          <div className="flex items-center gap-2 text-emerald-800">
            <CheckCircle2 className="h-5 w-5" />
            <h3 className="font-semibold">First payment already created</h3>
          </div>
          <p className="mt-1 text-sm text-emerald-700">
            PayFlow detected a real payment record for this merchant.
          </p>
        </div>
      ) : (
        <div className="rounded-2xl border border-blue-200 bg-blue-50/60 p-5">
          <div className="flex items-center gap-2 text-blue-900">
            <CreditCard className="h-5 w-5" />
            <h3 className="font-semibold">You are ready to send your first test payment</h3>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-3 text-center text-sm">
            <div className="rounded-xl bg-white/80 p-3"><p className="text-xs text-slate-500">Environment</p><p className="mt-1 font-semibold">TEST</p></div>
            <div className="rounded-xl bg-white/80 p-3"><p className="text-xs text-slate-500">Example</p><p className="mt-1 font-semibold">₹100</p></div>
            <div className="rounded-xl bg-white/80 p-3"><p className="text-xs text-slate-500">Provider</p><p className="mt-1 font-semibold">Sandbox</p></div>
          </div>
          <div className="mt-4 flex items-start gap-2 text-sm text-blue-800">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" />
            <p>No real money moves in the TEST sandbox environment.</p>
          </div>
        </div>
      )}

      <div className="rounded-xl border border-slate-200 bg-white/60 p-4">
        <p className="text-sm font-medium text-slate-900">Idempotency in one line</p>
        <p className="mt-1 text-sm leading-6 text-slate-600">
          Use one <code className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs">Idempotency-Key</code> for one logical payment so retries do not create a duplicate payment.
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        {!hasPayment && canCreate && (
          <button type="button" onClick={onOpenCreatePayment} className="btn-primary">
            <ExternalLink className="h-4 w-4" /> Create test payment
          </button>
        )}
        {!hasPayment && !canCreate && (
          <p className="rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-600">
            Your role does not have permission to create payments.
          </p>
        )}
        <button type="button" onClick={() => setShowExample((value) => !value)} className="btn-secondary">
          <Code2 className="h-4 w-4" /> {showExample ? 'Hide API example' : 'Show API example'}
        </button>
      </div>

      {showExample && (
        <pre className="overflow-x-auto rounded-xl bg-slate-950 p-4 text-xs leading-6 text-slate-100"><code>{`POST /api/v1/payments
Authorization: Bearer sk_test_...
Idempotency-Key: order-123-payment
Content-Type: application/json

{
  "amount": 100,
  "currency": "INR",
  "merchantOrderId": "order_123",
  "paymentMethod": "QR"
}`}</code></pre>
      )}
    </div>
  );
}
