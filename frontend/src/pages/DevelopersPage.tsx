import { useState } from 'react';
import { Check, Copy, KeyRound, QrCode, ShieldCheck, Webhook } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { toast } from '../components/Toast';

const requestExample = `POST /api/v1/payments
Authorization: Bearer sk_test_xxxxx
Idempotency-Key: order-123-payment
Content-Type: application/json

{
  "amount": 1000,
  "currency": "INR",
  "merchantOrderId": "order_123",
  "paymentMethod": "QR"
}`;

const responseExample = `{
  "paymentReference": "pay_123",
  "status": "PENDING",
  "qrCode": {
    "data": "upi://pay?...",
    "image": "data:image/png;base64,..."
  }
}`;

export function DevelopersPage() {
  return (
    <>
      <PageHeader
        title="Developer Documentation"
        description="Integration guidance grounded in the APIs and reliability rules implemented by this PayFlow repository."
      />

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-4">
        <aside className="card p-4 xl:col-span-1">
          <nav className="space-y-1 text-sm">
            {['Quick Start', 'Authentication', 'Idempotency', 'QR Payments', 'Webhooks', 'Error Handling', 'Going Live'].map((label) => (
              <a key={label} href={`#${label.toLowerCase().replace(/\\s+/g, '-')}`} className="block rounded-lg px-3 py-2 text-slate-600 hover:bg-blue-50 hover:text-blue-700">
                {label}
              </a>
            ))}
          </nav>
        </aside>

        <div className="space-y-6 xl:col-span-3">
          <DocSection id="quick-start" title="Quick Start" icon={<ShieldCheck className="h-5 w-5" />}>
            <ol className="list-decimal space-y-2 pl-5 text-sm text-slate-600">
              <li>Create a TEST API key from the API Keys page.</li>
              <li>Create a payment with a unique idempotency key.</li>
              <li>Store the returned payment reference.</li>
              <li>For a retry of the same logical operation, reuse the same idempotency key and body.</li>
              <li>Do not treat browser success as financial truth.</li>
              <li>Use the provider-confirmed PayFlow state as the authoritative outcome.</li>
            </ol>
          </DocSection>

          <DocSection id="authentication" title="Authentication" icon={<KeyRound className="h-5 w-5" />}>
            <p className="text-sm text-slate-600">
              Dashboard users authenticate with JWT access and refresh tokens. Merchant integrations use TEST or LIVE API keys.
              Existing key secrets are never shown again after creation.
            </p>
          </DocSection>

          <DocSection id="idempotency" title="Create Payment + Idempotency" icon={<ShieldCheck className="h-5 w-5" />}>
            <CodeBlock value={requestExample} />
            <div className="mt-3">
              <CodeBlock value={responseExample} />
            </div>
            <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-3">
              <Rule title="Same key + same body" body="Returns the existing logical payment instead of creating a duplicate." />
              <Rule title="Same key + different body" body="Rejected because reusing an idempotency key for another operation is an integration error." />
              <Rule title="Provider timeout" body="Does not automatically mean the customer's payment definitively failed." />
            </div>
          </DocSection>

          <DocSection id="qr-payments" title="QR Payments" icon={<QrCode className="h-5 w-5" />}>
            <p className="text-sm text-slate-600">
              Use <code className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs">paymentMethod: "QR"</code>. PayFlow can return
              a QR payload and image. The payment remains pending until a trusted provider event confirms the final state.
            </p>
          </DocSection>

          <DocSection id="webhooks" title="Provider Webhooks" icon={<Webhook className="h-5 w-5" />}>
            <p className="text-sm text-slate-600">
              Provider webhook ingestion verifies signatures over the raw body and deduplicates provider events. The current core build
              does not yet expose a complete merchant webhook-management service; the gateway route is reserved for that planned service.
            </p>
          </DocSection>

          <DocSection id="error-handling" title="Error Handling" icon={<ShieldCheck className="h-5 w-5" />}>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="text-xs uppercase tracking-wider text-slate-400">
                  <tr><th className="py-2 pr-4">Code / class</th><th className="py-2">What to do</th></tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  <tr><td className="py-3 pr-4 font-mono text-xs">401 / UNAUTHORIZED</td><td className="py-3 text-slate-600">Refresh the dashboard session or verify the integration credential.</td></tr>
                  <tr><td className="py-3 pr-4 font-mono text-xs">409 idempotency conflict</td><td className="py-3 text-slate-600">Reuse the original request body or create a new key for a different logical payment.</td></tr>
                  <tr><td className="py-3 pr-4 font-mono text-xs">Provider timeout</td><td className="py-3 text-slate-600">Check the payment state before assuming no money moved.</td></tr>
                </tbody>
              </table>
            </div>
          </DocSection>

          <DocSection id="going-live" title="Going Live" icon={<Check className="h-5 w-5" />}>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {[
                'Create a LIVE API key only when needed',
                'Configure real provider credentials on the backend',
                'Verify provider webhook signatures',
                'Use idempotency for payment creation',
                'Keep secrets out of browser code and Git',
                'Enable monitoring and review health checks',
              ].map((item) => (
                <div key={item} className="flex items-start gap-2 rounded-xl bg-slate-50/80 px-3 py-2 text-sm text-slate-600">
                  <Check className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" /> {item}
                </div>
              ))}
            </div>
          </DocSection>
        </div>
      </div>
    </>
  );
}

function DocSection({ id, title, icon, children }: { id: string; title: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <section id={id} className="card scroll-mt-28 p-6">
      <div className="mb-4 flex items-center gap-2 text-blue-600">
        {icon}
        <h2 className="text-base font-bold text-slate-900">{title}</h2>
      </div>
      {children}
    </section>
  );
}

function CodeBlock({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    toast('success', 'Code copied');
    window.setTimeout(() => setCopied(false), 1500);
  };
  return (
    <div className="relative overflow-hidden rounded-xl bg-slate-950 p-4 text-slate-100">
      <button type="button" onClick={copy} className="absolute right-3 top-3 rounded-lg bg-white/10 p-2 text-slate-300 hover:bg-white/15 hover:text-white">
        {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
      </button>
      <pre className="overflow-x-auto pr-10 text-xs leading-6"><code>{value}</code></pre>
    </div>
  );
}

function Rule({ title, body }: { title: string; body: string }) {
  return (
    <div className="rounded-xl border border-slate-200/70 bg-slate-50/70 p-3">
      <p className="text-xs font-semibold text-slate-900">{title}</p>
      <p className="mt-1 text-xs text-slate-500">{body}</p>
    </div>
  );
}
