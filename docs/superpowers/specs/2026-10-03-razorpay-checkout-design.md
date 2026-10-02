# Payflow: verified Razorpay test-mode checkout

Date: 2026-10-03 (Asia/Calcutta)
Status: written design awaiting user review; conceptual scope approved.

## Outcome and scope

Firoz wants the deployed Payflow project to support real payment-provider integrations and to be verified for production. This first sub-project proves Razorpay's real test environment through the existing website, payment service, provider service, Kafka outbox, and ledger. Success means a provider-created order is paid through Razorpay Checkout, confirmed by the backend, and posted exactly once to a balanced ledger. A local mock or Payflow sandbox result does not count as real-provider evidence.

Merchant webhook delivery, refund orchestration, settlement services, Stripe, and always-on infrastructure remain subsequent sub-projects. Completing this design does not make the entire platform production-ready. No real-money charge or paid infrastructure upgrade is part of this execution scope.

## Existing behavior and defects

PaymentGatewayRegistry.selectFor always selects sandbox for TEST payments. RazorpayPaymentGateway creates an order but returns checkout.js as a navigable checkout URL. The frontend opens that URL rather than constructing Razorpay Checkout. The stored providerPaymentId represents an order for ordinary checkout but a QR identifier for QR payments. Refund APIs need a distinct actual payment identifier. Provider events already use a durable outbox and payment state transitions feed the ledger. Preserve those boundaries and extend them deliberately.

## Provider routing and credentials

Introduce explicit server configuration for TEST routing, allowing sandbox or razorpay, with sandbox as the backwards-compatible default. TEST Razorpay requests require a test key ID and corresponding test secret. Missing or inconsistent configured credentials fail startup; provider errors never cause silent sandbox fallback. This stage rejects LIVE payment creation at the provider boundary until separately configured live-mode readiness is implemented. Existing TEST sandbox remains supported and clearly labelled.

Razorpay secrets and webhook secrets live in backend environment configuration. The browser receives only the public key ID and order-specific checkout options. Do not request secrets through chat, commit credentials, or log authentication headers, signatures, raw card details, or unredacted webhook payloads. The public key ID must correspond to the credentials used to create the order. Configure real-provider testing through the secure deployment settings when available; absent access or credentials is a recorded blocker, not a passing test.

## API and persistence boundaries

Keep existing payment creation and its idempotency header. Add tenant-owned GET /api/v1/payments/{reference}/checkout requiring payments:read and POST /api/v1/payments/{reference}/verify requiring payments:create. Both resolve the merchant from the authenticated principal and load the payment within that tenant. Cross-tenant references return 404. Internal provider calls retain internal-token authentication.

Checkout options contain provider, mode, public key ID, stored order ID, amount in minor units, and currency. All values derive from the stored payment and validated provider order; browser-supplied amount or order replacement is never accepted. Only eligible pending Razorpay payments can start checkout. Captured or cancelled payments cannot start a new attempt.

Verification accepts Razorpay payment ID, order ID, and signature with bounded field lengths. It compares the order against the stored order, verifies HMAC of stored order ID + separator + supplied payment ID in constant time, then retrieves the payment from Razorpay to validate order, amount, currency, and status. It returns the current Payflow payment response. Invalid evidence never emits a capture event. Provider timeout returns a retryable unavailable response without marking payment failed.

Add a separate nullable Razorpay payment-entity ID to PaymentAttempt using a forward Flyway migration. Keep the legacy providerPaymentId association for existing orders and QR records. Record verified payment IDs only after binding validation. Concurrent callback/webhook processing must use transactional locking and database constraints so a single captured transition and ledger posting occur. Repeated verification is safe and returns the existing result. No external HTTP request runs while holding a database row lock.

## Checkout user experience

Use a shared Razorpay checkout component from CreatePaymentPage and PaymentDetailPage. Load the official script once with explicit loading, error, and retry states. Instantiate Checkout with backend options; payment instruments are entered only in provider-owned Checkout. Disable duplicate clicks while opening or verifying.

The handler submits signed evidence to Payflow. A browser callback alone never displays CAPTURED. Show verification progress followed by the backend status. Dismissal closes the modal and leaves the order pending: it does not prove cancellation or failure. Provider failure is shown with a retry option; authoritative state comes from verified provider evidence. Poll detail for a bounded 60-second period, then show processing with a manual refresh option. Stop polling on unmount or terminal state.

Use INR for the first real-provider acceptance run. Provider-supported card, UPI, net banking, and wallet options appear according to the account's Checkout configuration. Standalone QR remains its distinct provider API path; do not present sandbox QR success as a Razorpay test. Unsupported or unavailable provider methods are explicitly recorded and cannot be counted as tested.

## Incoming provider events and reconciliation

Verify webhook signatures over the exact raw body. Retain durable provider event deduplication, using the provider event-ID header where supplied and a stable derived fallback where necessary. Preserve original event identity through outbox delivery. Validate supported event type and association before changing a payment. Compare provider amount and currency against the stored payment; mismatches are rejected and surfaced for investigation.

Handle authorization followed by capture, capture before delayed authorization, duplicate captures, and concurrent verification/webhook delivery without downgrading a captured payment or posting twice. Ignore unsupported event types without synthesizing a payment failure. Retain the provider order identifier and actual payment identifier separately.

A user-initiated verify/refresh reconciliation retrieves authoritative provider status after a delayed webhook. Reuse the same validated state-application path for callbacks, webhook events, and reconciliation. An order marked paid is not sufficient to determine its refund lifecycle; refunds remain outside this sub-project.

## Idempotency and uncertain provider responses

Do not assume the current custom Razorpay idempotency header guarantees Orders API idempotency. Validate current provider documentation during implementation. Persist one creation attempt per Payflow idempotency claim. A provider timeout with an unknown outcome must not automatically create another order: keep the attempt in an explicit reconciliation-required condition and prevent blind replay creation. Resolve the existing order using supported provider lookup and persisted reference evidence; when that cannot establish a unique order, surface an operational recovery requirement. Test this boundary before release.

## Verification and acceptance

| Layer | Required evidence |
|---|---|
| Unit/contract | Explicit provider routing, missing keys, mode mismatch, amount conversion, correct/incorrect signature, ownership, bound order, currency/amount mismatch, provider timeout |
| Database/Kafka integration | Migration validation, duplicate and concurrent processing, capture-before-authorization, outbox publication, exactly one balanced ledger posting |
| Frontend | Script failure, duplicate click, dismissal, declined payment, verification failure, pending then capture, navigation/poll cleanup |
| Real Razorpay test environment | Actual order ID, provider payment ID, successful Checkout, authoritative captured status, signed webhook reception, duplicate delivery, final ledger proof |
| Regression | Signup/login, API-key authorization, existing sandbox methods, CI builds and existing integration suite |

Evidence records deployed commit, test timestamp, method, provider mode, sanitized order/payment references, HTTP outcome, Payflow status, and ledger debit/credit totals. Never include credentials or payment instrument data. Each required scenario is PASS, FAIL, or BLOCKED. A missing test key, inaccessible dashboard, unavailable method, or provider test-environment limitation is BLOCKED. Fake Checkout tests complement but cannot replace real-provider evidence.

## Rollout and operational limits

Use an isolated branch and PR, forward-compatible migration, CI verification, test-mode deployment, then real-provider acceptance before merge promotion. Release frontend and backend compatibly: new endpoints are additive and the frontend only opens Razorpay for explicit Razorpay options. Rollback disables Razorpay TEST routing and retains migration data; do not delete transaction records or reclassify provider payments as sandbox.

Free Render cold starts remain a release risk. Preserve the verified two-connection backend pool configuration; a one-connection pool broke Flyway startup. Always-on hosting, database capacity, backups, risk policy, email verification, monitoring, and load testing are separate production-release gates. No whole-project readiness claim is allowed until those gates and the remaining subsystems have verified evidence.

## Sources and review

Primary integration reference, checked 2026-10-03:
https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/integration-steps

Self-review completed: scope boundaries are explicit; browser success is not capture authority; order and payment identifiers are distinct; credentials are blocked dependencies rather than invented values; retries do not assume provider idempotency; production readiness remains separate. Next stage is user review of this written spec, followed by a written implementation plan and execution-method selection.
