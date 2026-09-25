import { useState } from 'react';
import { Check, Copy, KeyRound, QrCode, ShieldCheck, Webhook } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';

const createPayment = `POST /api/v1/payments

Authorization: Bearer sk_test_xxxxx
Idempotency-Key: order-123-payment

{
  "amount": 1000,
  "currency": "INR",
  "merchantOrderId": "order_123",
  "paymentMethod": "QR"
}`;

const response = `{
  "paymentReference": "pay_123",
  "status": "PENDING"
}`;

export function DeveloperDocsPage() {
  return (
    <>
      <PageHeader title="Developers" description="Integrate PayFlow safely using the same rules enforced by the backend" />

      <div className="grid lg:grid-cols-[240px_1fr] gap-6">
        <aside className="card glass-card p-4 h-fit lg:sticky lg:top-6">
          {['Quick Start', 'Authentication', 'Idempotency', 'QR Payments', 'Webhooks', 'Error Codes', 'Going Live'].map((x, i) => (
            <a key={x} href={`#section-${i}`} className="block px-3 py-2 rounded-lg text-sm text-slate-600 hover:bg-white/60 hover:text-slate-950 transition">{x}</a>
          ))}
        </aside>

        <div className="space-y-6">
          <Section id="section-0" title="Quick Start">
            <ol className="space-y-3 text-sm text-slate-700 list-decimal pl-5">
              <li>Create a TEST API key.</li>
              <li>Create a payment with one Idempotency-Key per logical payment.</li>
              <li>Store the returned payment reference.</li>
              <li>Reuse the same key and same body when retrying the same operation.</li>
              <li>Never treat frontend success as financial confirmation.</li>
              <li>Wait for the signature-verified provider webhook.</li>
            </ol>
            <CodeBlock value={createPayment} />
            <CodeBlock value={response} />
          </Section>

          <Section id="section-1" title="Authentication" icon={<KeyRound className="w-5 h-5" />}>
            <p className="text-sm text-slate-600">Use TEST keys while developing and LIVE keys only in production. Existing secrets are masked and never displayed again after creation.</p>
          </Section>

          <Section id="section-2" title="Idempotency" icon={<ShieldCheck className="w-5 h-5" />}>
            <div className="grid md:grid-cols-3 gap-3 text-sm">
              <Rule title="Same request + same key" value="Existing payment is replayed safely." good />
              <Rule title="Different request + same key" value="409 IDEMPOTENCY_KEY_REUSED." />
              <Rule title="Concurrent retries" value="DB uniqueness decides the single winner." good />
            </div>
          </Section>

          <Section id="section-3" title="QR Payments" icon={<QrCode className="w-5 h-5" />}>
            <p className="text-sm text-slate-600">Create a QR payment, render the returned QR image, keep the payment PENDING, then wait for the provider webhook before treating it as CAPTURED.</p>
            <div className="mt-4 p-4 rounded-xl bg-white/55 border border-white/70 font-mono text-xs text-slate-700">
              CREATED → PENDING → provider webhook verified → CAPTURED
            </div>
          </Section>

          <Section id="section-4" title="Webhooks" icon={<Webhook className="w-5 h-5" />}>
            <p className="text-sm text-slate-600">Verify the provider signature over the raw request body. Duplicate provider event IDs must not repeat the financial state transition.</p>
          </Section>

          <Section id="section-5" title="Error Codes">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead><tr className="text-left text-xs uppercase tracking-wider text-slate-500"><th className="py-2">Code</th><th>Meaning</th><th>Action</th></tr></thead>
                <tbody className="divide-y divide-white/60">
                  <ErrorRow code="PAYMENT_NOT_FOUND" meaning="Payment is unavailable in this merchant scope." action="Check the payment reference." />
                  <ErrorRow code="IDEMPOTENCY_KEY_REUSED" meaning="Same key was reused with another body." action="Use the original body or a new key." />
                  <ErrorRow code="INVALID_API_KEY" meaning="Merchant API key was rejected." action="Use an active TEST/LIVE key." />
                  <ErrorRow code="PROVIDER_TIMEOUT" meaning="Provider outcome is unknown." action="Do not blindly retry a charge." />
                  <ErrorRow code="UNAUTHORIZED" meaning="Authentication is missing or invalid." action="Refresh credentials." />
                </tbody>
              </table>
            </div>
          </Section>

          <Section id="section-6" title="Going Live">
            <div className="grid sm:grid-cols-2 gap-3">
              {['LIVE API key created', 'Webhook verification tested', 'Idempotency implemented', 'Error handling tested', 'Monitoring enabled', 'Secrets stored outside source control'].map((x) => (
                <div key={x} className="p-3 rounded-xl bg-white/55 border border-white/70 flex items-center gap-2 text-sm text-slate-700">
                  <Check className="w-4 h-4 text-emerald-600" /> {x}
                </div>
              ))}
            </div>
          </Section>
        </div>
      </div>
    </>
  );
}

function Section({ id, title, icon, children }: { id: string; title: string; icon?: React.ReactNode; children: React.ReactNode }) {
  return <section id={id} className="card glass-card p-6 scroll-mt-6"><div className="flex items-center gap-2 mb-4 text-slate-950">{icon}<h2 className="text-lg font-semibold">{title}</h2></div>{children}</section>;
}

function CodeBlock({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => { await navigator.clipboard.writeText(value); setCopied(true); setTimeout(() => setCopied(false), 1500); };
  return <div className="relative mt-4 rounded-2xl bg-slate-950 text-slate-100 p-5 overflow-x-auto"><button onClick={copy} className="absolute right-3 top-3 p-2 rounded-lg bg-white/10 hover:bg-white/20">{copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}</button><pre className="text-xs leading-6 whitespace-pre-wrap">{value}</pre></div>;
}

function Rule({ title, value, good }: { title: string; value: string; good?: boolean }) {
  return <div className={`p-4 rounded-xl border ${good ? 'bg-emerald-50/70 border-emerald-200' : 'bg-white/55 border-white/70'}`}><p className="font-medium text-slate-900">{title}</p><p className="text-slate-600 mt-1">{value}</p></div>;
}

function ErrorRow({ code, meaning, action }: { code: string; meaning: string; action: string }) {
  return <tr><td className="py-3 pr-4 font-mono text-xs text-brand-700">{code}</td><td className="py-3 pr-4 text-slate-600">{meaning}</td><td className="py-3 text-slate-600">{action}</td></tr>;
}
