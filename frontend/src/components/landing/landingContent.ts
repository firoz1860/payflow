import {
  BarChart3,
  BookOpen,
  CreditCard,
  KeyRound,
  LayoutDashboard,
  type LucideIcon,
  QrCode,
  Scale,
  Search,
  Sparkles,
} from 'lucide-react';

/**
 * Landing-page content for PayFlow.
 *
 * Every claim is checked against the repository (README service table, the
 * payment-flow sequence, the outbox, refund concurrency, the security table,
 * the documented frontend scope). Deliberately precise:
 *  - payment COMPLETION is confirmed by a verified provider event — but not
 *    every state change comes from a webhook (creation/processing have other
 *    triggers), so the copy says "provider-confirmed", not "webhook-only".
 *  - event delivery is "at-least-once" with idempotent consumers, never
 *    "exactly-once".
 *  - "balanced ledger" and "investigation" are described as such, never as
 *    bank-statement reconciliation or settlement.
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
    title: 'Payments & hosted checkout',
    icon: CreditCard,
    summary: 'Create a payment request and give your customer a checkout link.',
    detail:
      'Submit the amount and required details through the API with an Idempotency-Key. PayFlow creates the payment record, connects it to the configured checkout provider, and returns a checkout URL you can track from the merchant workspace.',
  },
  {
    id: 'qr',
    title: 'UPI QR checkout',
    icon: QrCode,
    summary: 'Offer a QR code tied to a specific payment request.',
    detail:
      'Generate a fixed-amount, single-use UPI QR through the supported provider integration — the payee and amount are set server-side. Track its status through verified provider updates rather than a customer screenshot.',
  },
  {
    id: 'ledger',
    title: 'Double-entry ledger',
    icon: Scale,
    summary: 'See the financial entries associated with payment activity.',
    detail:
      'Supported payment events are recorded as balanced debit and credit postings. Every entry traces back to its originating transaction, which supports investigation and internal accounting checks. It is a ledger of record, not a full accounting suite.',
  },
  {
    id: 'analytics',
    title: 'Merchant analytics',
    icon: BarChart3,
    summary: 'Understand payment activity from actual transaction records.',
    detail:
      'Review captured volume, success rate and status breakdowns computed from real Payment API data. Totals and charts stay consistent with the selected filters, and an empty workspace shows a clear “no activity yet” state.',
  },
  {
    id: 'api-keys',
    title: 'API key management',
    icon: KeyRound,
    summary: 'Connect your application using merchant-scoped credentials.',
    detail:
      'Authenticate payment requests with merchant-scoped TEST and LIVE keys. The environment is explicit, so developers always know whether an operation uses simulated (sandbox) or real provider processing. Publishable keys are rejected on money-moving endpoints.',
  },
  {
    id: 'copilot',
    title: 'PayFlow Copilot',
    icon: Sparkles,
    summary: 'Investigate payment activity with a read-only assistant.',
    detail:
      'Ask questions about supported payment records and get explanations grounded in the evidence the assistant can read, with transaction references kept visible. Copilot explains records — it does not move money, approve refunds, or change payment status.',
  },
];

export interface ProblemSolution {
  problem: string;
  approach: string;
}

export const WHY_PROBLEMS: ProblemSolution[] = [
  {
    problem: 'Repeated requests can create duplicate payments.',
    approach:
      'An idempotency key identifies a payment request, so a retry returns the existing result instead of creating another payment.',
  },
  {
    problem: 'A checkout screen does not prove that money was received.',
    approach:
      'PayFlow verifies the provider’s notification before recording the corresponding confirmed payment outcome.',
  },
  {
    problem: 'Payment statuses and financial records can drift apart.',
    approach:
      'Payment events feed a double-entry ledger, creating traceable debit and credit entries tied to each transaction.',
  },
];

export interface HowStep {
  title: string;
  body: string;
}

export const HOW_STEPS: HowStep[] = [
  { title: 'Create the request', body: 'Your application submits the payment details with its credentials and an idempotency key.' },
  { title: 'Validate and record', body: 'PayFlow checks the request, applies supported validation and risk rules, and creates or retrieves the payment record.' },
  { title: 'Open checkout', body: 'The provider integration returns the checkout details the customer needs to continue.' },
  { title: 'Receive provider confirmation', body: 'After processing, the provider sends a notification. PayFlow verifies its authenticity and applies duplicate-event checks.' },
  { title: 'Update the payment record', body: 'An accepted provider event moves the payment to its corresponding status. A delayed response or timeout is not treated as success or failure.' },
  { title: 'Record the financial entries', body: 'Supported payment events are processed into balanced ledger postings, with references connecting the entries to the transaction. The ledger may update shortly after the payment status.' },
  { title: 'Review and investigate', body: 'The merchant inspects status, transaction details, and financial records from the dashboard — and can ask Copilot to explain them.' },
];

/** The internal service-level sequence, shown inside a "Technical details" disclosure. */
export const TECHNICAL_FLOW: string[] = [
  'Gateway rate-limits the request, strips client identity headers, and pre-checks the JWT.',
  'payment-service verifies the API key (cached briefly in Redis) and claims the idempotency key in its own transaction.',
  'A risk check runs, then the payment is persisted as CREATED alongside an outbox event — in one transaction.',
  'provider-service creates the charge at the gateway outside any database transaction, returning a reference and checkout URL.',
  'A signed provider webhook is verified (HMAC over the raw body, timestamp window, unique event id) and de-duplicated.',
  'The event flows through Kafka (at-least-once, idempotent consumers); the payment moves PENDING → CAPTURED and the ledger posts balanced entries.',
];

export interface Reliability {
  id: string;
  title: string;
  summary: string;
  detail: string;
}

export const RELIABILITY: Reliability[] = [
  {
    id: 'retry',
    title: 'Retry without creating another payment',
    summary: 'The same idempotency key returns the existing payment instead of a new one.',
    detail:
      'A UNIQUE (merchant_id, idempotency_key) constraint means concurrent retries race to INSERT and exactly one wins; the others read the stored result. Reusing a key with a different request body is rejected with 409 rather than replaying the wrong object.',
  },
  {
    id: 'delays',
    title: 'Handle provider delays explicitly',
    summary: 'A timeout records a failed attempt but leaves the payment non-terminal.',
    detail:
      'The charge may have been created before the socket gave up, so a timeout is never shown as a confirmed decline, and the UI does not tell you to immediately create a second payment. The true outcome is resolved once the provider confirms.',
  },
  {
    id: 'dedup',
    title: 'Process repeated events safely',
    summary: 'Duplicate provider events are recognised and ignored.',
    detail:
      'Provider events are de-duplicated by a unique (provider, event id), and every Kafka consumer is idempotent because delivery is at-least-once by design — a redelivered event must not post twice. This is at-least-once with idempotent handling, not exactly-once delivery.',
  },
  {
    id: 'ledger',
    title: 'Keep ledger postings balanced',
    summary: 'Debits always equal credits — enforced in the database, not just the app.',
    detail:
      'The service rejects unbalanced postings, a Postgres CHECK enforces equal total debits and credits, entries are immutable (corrections are reversing postings), and an integrity job re-proves global balance on a schedule.',
  },
  {
    id: 'verify',
    title: 'Verify payment updates at the server',
    summary: 'A browser redirect is never accepted as proof of payment.',
    detail:
      'Provider notifications are verified by HMAC over the raw request body, with a timestamp window and a unique event id to stop replay. A client-side “success” screen does not change a payment’s recorded outcome on its own.',
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
  { name: 'api-gateway', port: '8080', group: 'Edge', responsibility: 'Routes incoming requests and applies the configured entry-point controls — rate limiting, JWT pre-check and security headers.' },
  { name: 'auth-service', port: '8081', group: 'Identity', responsibility: 'Manages authentication, sessions and supported token workflows, including refresh-token rotation.' },
  { name: 'merchant-service', port: '8082', group: 'Identity', responsibility: 'Manages merchant identity, ownership and configuration, and verifies TEST/LIVE API keys for other services.' },
  { name: 'payment-service', port: '8085', group: 'Money', responsibility: 'Owns payment records, lifecycle transitions, request idempotency and the supported processing rules.' },
  { name: 'provider-service', port: '8086', group: 'Money', responsibility: 'Communicates with configured providers (sandbox + Razorpay) and verifies provider webhooks.' },
  { name: 'ledger-service', port: '8088', group: 'Money', responsibility: 'Records and exposes supported financial accounts, postings and entries.' },
  { name: 'ai-agent-service', port: '8096', group: 'Intelligence', responsibility: 'Provides read-only investigation over the evidence made available to it.' },
];

export const SERVICE_GROUPS: { group: ServiceGroup; blurb: string }[] = [
  { group: 'Edge', blurb: 'The single public entry point.' },
  { group: 'Identity', blurb: 'Who is calling, and may they.' },
  { group: 'Money', blurb: 'Payment records, providers and the ledger.' },
  { group: 'Intelligence', blurb: 'Read-only investigation.' },
];

export interface Threat {
  threat: string;
  defence: string;
}

export const SECURITY: Threat[] = [
  { threat: 'Cross-tenant access', defence: 'Payment and ledger records are merchant-scoped; a request for someone else’s id returns 404, not 403.' },
  { threat: 'Credential exposure', defence: 'API-key secrets are hashed, not stored in the clear, and verified on every money-moving request.' },
  { threat: 'Session & refresh tokens', defence: 'Refresh tokens are rotated with reuse detection — a replayed token revokes the whole family.' },
  { threat: 'Webhook forgery', defence: 'Provider notifications are verified by signature over the raw request body before anything is recorded.' },
  { threat: 'Duplicate & replayed events', defence: 'A timestamp window plus a unique event id stop replays; consumers are idempotent.' },
  { threat: 'Sensitive payment data', defence: 'Card numbers are never stored — provider tokens and last-4 only; input is validated and rate-limited at the edge.' },
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
      'Create supported payment requests and UPI QR',
      'Share checkout details with customers',
      'Review payment activity and analytics',
      'Inspect the associated ledger records',
    ],
  },
  {
    icon: BookOpen,
    title: 'For developers',
    points: [
      'Integrate retry-safe payment creation',
      'Work with explicit TEST / LIVE credentials',
      'Handle verified provider events',
      'Trace processing across APIs and events',
    ],
  },
  {
    icon: Search,
    title: 'For operations',
    points: [
      'Investigate pending or failed activity',
      'Follow transaction references and statuses',
      'Use evidence available in the system',
      'Ask Copilot to explain a record',
    ],
  },
];

export interface Faq {
  q: string;
  a: string;
}

export const FAQ: Faq[] = [
  {
    q: 'What does PayFlow do?',
    a: 'It orchestrates payments for merchants — creating payment requests, offering hosted or UPI QR checkout, tracking each transaction, and recording the financial entries in a double-entry ledger.',
  },
  {
    q: 'Does PayFlow replace a payment provider?',
    a: 'No. PayFlow integrates with configured providers (a sandbox gateway and Razorpay). It coordinates and records payments; it does not operate banking rails itself.',
  },
  {
    q: 'What happens if the same payment request is retried?',
    a: 'A request carries an idempotency key. A retry with the same key returns the existing payment instead of creating a second one; reusing a key with a different body is rejected rather than silently replaying.',
  },
  {
    q: 'How is payment completion confirmed?',
    a: 'After the provider processes the payment it sends a notification, which PayFlow verifies before recording the confirmed outcome. The dashboard then shows the resulting status.',
  },
  {
    q: 'Can I test without moving real money?',
    a: 'Yes. TEST keys route to the sandbox gateway, which simulates the full payment lifecycle without real funds. The environment (test vs live) is always explicit.',
  },
  {
    q: 'Does a balanced ledger mean funds have settled in my bank?',
    a: 'No. The ledger is PayFlow’s internal record of debits and credits. Bank settlement is a separate concern and is not represented as settled funds here.',
  },
  {
    q: 'Can Copilot change a payment?',
    a: 'No. Copilot is read-only. It explains records from the evidence it can read; it cannot move money, approve refunds, or change a payment’s status.',
  },
  {
    q: 'What is needed for live processing?',
    a: 'Live processing needs real provider credentials configured for the Razorpay integration. The hosted demo runs against the sandbox; without live credentials, real charges are unavailable.',
  },
];

export const RELIABILITY_STATS: { value: string; label: string }[] = [
  { value: '7', label: 'focused services' },
  { value: 'at-least-once', label: 'event delivery, idempotent consumers' },
  { value: '1 DB', label: 'per service, no shared tables' },
  { value: 'read-only', label: 'Copilot investigation' },
];
