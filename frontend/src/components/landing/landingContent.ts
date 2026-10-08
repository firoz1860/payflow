import {
  BarChart3,
  BookOpen,
  CreditCard,
  KeyRound,
  LayoutDashboard,
  type LucideIcon,
  QrCode,
  Scale,
  Sparkles,
} from 'lucide-react';

/**
 * Landing-page content for PayFlow.
 *
 * Every line here is grounded in the repository's README and service code — the
 * service table (ports/responsibilities), the "five decisions that matter", the
 * payment-flow sequence, the transactional outbox, refund concurrency, the
 * security table, and the documented frontend scope. Nothing here claims a
 * capability the repo does not implement (e.g. merchant-managed webhook
 * endpoints, which the README marks as not-yet-built, are intentionally absent).
 */

export interface Capability {
  id: string;
  title: string;
  icon: LucideIcon;
  summary: string;
  detail: string;
}

export const CAPABILITIES: Capability[] = [
  {
    id: 'payments',
    title: 'Payments & checkout',
    icon: CreditCard,
    summary: 'Create a payment with one idempotent API call and return a hosted checkout URL.',
    detail:
      'A single POST /api/v1/payments with an Idempotency-Key creates the payment and returns a checkout URL. Retrying the same key never creates a second charge — the loser of the race reads the winner’s stored response.',
  },
  {
    id: 'qr',
    title: 'QR / UPI checkout',
    icon: QrCode,
    summary: 'Single-use, fixed-amount UPI QR codes generated server-side.',
    detail:
      'PayFlow fixes the payee VPA, name and amount inside the upi:// intent, so a customer cannot alter what they pay. The payment stays PENDING until a signature-verified webhook confirms the scan was paid.',
  },
  {
    id: 'ledger',
    title: 'Double-entry ledger',
    icon: Scale,
    summary: 'Every payment, fee and refund posts to a ledger that is balanced by construction.',
    detail:
      'Debits always equal credits — enforced by the service, a Postgres CHECK(total_debit = total_credit), an immutable-entry trigger, and an integrity job that re-proves global balance every 10 minutes. There is no stored balance column; balances are derived from entries.',
  },
  {
    id: 'analytics',
    title: 'Analytics',
    icon: BarChart3,
    summary: 'Live merchant analytics computed only from real Payment API data.',
    detail:
      'Captured volume, success rate and payment-status breakdowns are derived from what the API actually returns — the dashboard deliberately does not invent settlement or reconciliation numbers the core does not expose.',
  },
  {
    id: 'api-keys',
    title: 'API keys',
    icon: KeyRound,
    summary: 'TEST and LIVE keys, verified on every money-moving request.',
    detail:
      'Secrets are BCrypt-hashed with a separate peppered SHA-256 for lookup. Publishable pk_ keys are rejected outright on money-moving endpoints, and merchant, fee and status are derived from the key — never accepted from the request body.',
  },
  {
    id: 'copilot',
    title: 'PayFlow Copilot',
    icon: Sparkles,
    summary: 'A read-only AI assistant that investigates payments over verified evidence.',
    detail:
      'Bring-your-own-key and vendor-abstracted. The agent answers only from read-only evidence tools and labels every claim FACT, INFERENCE or MISSING — it never mutates data.',
  },
];

export interface FlowStep {
  label: string;
  note: string;
}

export const PAYMENT_FLOW: FlowStep[] = [
  { label: 'Merchant', note: 'POST /payments with an sk_live_… key and an Idempotency-Key.' },
  { label: 'API Gateway', note: 'Rate-limits, strips client identity headers, pre-checks the JWT.' },
  { label: 'Payment service', note: 'Verifies the key, claims the idempotency key, runs a risk check, persists CREATED.' },
  { label: 'Provider service', note: 'Creates the charge at the gateway — outside any database transaction.' },
  { label: 'Razorpay / Sandbox', note: 'The real provider returns a reference and a checkout URL.' },
  { label: 'Signed webhook', note: 'Customer pays; the provider HMAC, timestamp and event id are all verified.' },
  { label: 'Kafka → Ledger', note: 'PENDING → CAPTURED, then balanced double-entry postings land in the ledger.' },
];

export interface Decision {
  id: string;
  title: string;
  summary: string;
  detail: string;
}

export const DECISIONS: Decision[] = [
  {
    id: 'idempotency',
    title: 'Idempotency is a database constraint',
    summary: 'A UNIQUE (merchant_id, idempotency_key) lets concurrent retries race to INSERT — exactly one wins.',
    detail:
      'The claim commits in its own REQUIRES_NEW transaction before any business work starts, so a later rollback cannot erase it and let a retry create a second payment. Reusing a key with a different body returns 409 IDEMPOTENCY_KEY_REUSED instead of replaying the wrong object.',
  },
  {
    id: 'no-remote-in-tx',
    title: 'Remote calls never run inside a transaction',
    summary: 'Provider calls happen between transactions, never while a database connection is held open.',
    detail:
      'Holding a connection across a third-party network round trip exhausts the pool under provider latency and takes down every unrelated payment. A leak-detection threshold fires if the mistake is ever reintroduced.',
  },
  {
    id: 'timeout',
    title: 'A provider timeout is not a decline',
    summary: 'A timeout records a failed attempt but leaves the payment non-terminal.',
    detail:
      'The provider may have created the charge before the socket gave up. Marking the payment FAILED would tell the merchant “no money moved” while the customer’s card was charged. There is deliberately no auto-retry — the retry decision belongs to the merchant who holds the key. Reconciliation resolves the true state.',
  },
  {
    id: 'ledger-balance',
    title: 'The ledger cannot be unbalanced',
    summary: 'Debits always equal credits — even against direct SQL.',
    detail:
      'Enforcement is layered: the service rejects unbalanced postings, a Postgres CHECK enforces equal debits and credits, a trigger makes entries immutable (corrections are reversing postings), and an integrity job re-proves global balance every 10 minutes.',
  },
  {
    id: 'trust',
    title: 'Nothing trusts the client',
    summary: 'Only a signature-verified provider webhook can change payment state.',
    detail:
      'The HMAC is computed over the raw body, stale timestamps are rejected to stop replay, and merchantId, fees and status are derived from the authenticated key. Cross-tenant reads return 404, not 403 — confirming an id exists but belongs to someone else is itself a leak.',
  },
];

export type ServiceGroup = 'Edge' | 'Identity' | 'Money' | 'Intelligence';

export interface Service {
  name: string;
  port: string;
  group: ServiceGroup;
  responsibility: string;
}

export const SERVICES: Service[] = [
  { name: 'api-gateway', port: '8080', group: 'Edge', responsibility: 'Routing, Redis rate limiting, JWT pre-check, security headers, circuit breakers.' },
  { name: 'auth-service', port: '8081', group: 'Identity', responsibility: 'Users, JWT, rotating refresh tokens with reuse detection, permission-based RBAC.' },
  { name: 'merchant-service', port: '8082', group: 'Identity', responsibility: 'Merchant lifecycle, TEST/LIVE API keys, and key verification for other services.' },
  { name: 'payment-service', port: '8085', group: 'Money', responsibility: 'Payment lifecycle, attempts, idempotency, the risk gate, and the outbox.' },
  { name: 'provider-service', port: '8086', group: 'Money', responsibility: 'Gateway adapters (sandbox + Razorpay), webhook verification and dedup.' },
  { name: 'ledger-service', port: '8088', group: 'Money', responsibility: 'Double-entry accounts, postings, entries, reversals, and the integrity job.' },
  { name: 'ai-agent-service', port: '8096', group: 'Intelligence', responsibility: 'PayFlow Copilot — BYOK, read-only evidence tools, FACT / INFERENCE / MISSING.' },
];

export const SERVICE_GROUPS: { group: ServiceGroup; blurb: string }[] = [
  { group: 'Edge', blurb: 'The single public entry point.' },
  { group: 'Identity', blurb: 'Who is calling, and may they.' },
  { group: 'Money', blurb: 'The payment lifecycle and its books.' },
  { group: 'Intelligence', blurb: 'Read-only investigation.' },
];

export interface Threat {
  threat: string;
  defence: string;
}

export const SECURITY: Threat[] = [
  { threat: 'Stolen database', defence: 'API-key secrets are BCrypt-hashed; refresh tokens are stored as SHA-256 only.' },
  { threat: 'Refresh-token theft', defence: 'Rotation with reuse detection — a replayed token revokes the whole family.' },
  { threat: 'Cross-tenant / IDOR', defence: 'TenantGuard, tenant-scoped queries, and 404-not-403 responses.' },
  { threat: 'Webhook forgery', defence: 'HMAC over the raw request body with constant-time comparison.' },
  { threat: 'Webhook replay', defence: 'Timestamp window plus a unique (provider, event id).' },
  { threat: 'Card data', defence: 'Never stored — provider tokens and last-4 only, enforced by a CHECK constraint.' },
];

export interface AudienceColumn {
  icon: LucideIcon;
  title: string;
  points: string[];
}

export const AUDIENCES: AudienceColumn[] = [
  {
    icon: LayoutDashboard,
    title: 'For merchants',
    points: [
      'Create payments and single-use UPI QR codes',
      'Track payment lists, details and live status',
      'Analytics from real payment data',
      'Read-only ledger accounts, postings and entries',
    ],
  },
  {
    icon: BookOpen,
    title: 'For developers',
    points: [
      'Idempotent REST API with TEST / LIVE keys',
      'Signature-verified provider webhooks',
      'Correlation IDs across gateway → service → Kafka',
      'One database per service, Flyway-validated schemas',
    ],
  },
];

export const RELIABILITY_STATS: { value: string; label: string }[] = [
  { value: '7', label: 'Spring Boot services' },
  { value: 'at-least-once', label: 'outbox delivery, idempotent consumers' },
  { value: '10 min', label: 'ledger integrity re-proof' },
  { value: '1 DB', label: 'per service, no shared tables' },
];
