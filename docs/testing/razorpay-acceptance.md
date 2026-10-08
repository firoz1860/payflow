# Real Razorpay TEST acceptance

The sandbox and mocked HTTP tests are separate from real-provider acceptance. Use a controlled TEST deployment; do not enable LIVE or spend money as part of this procedure.

## Secure setup

1. Configure RAZORPAY_KEY_ID with a test-mode key, RAZORPAY_KEY_SECRET and RAZORPAY_WEBHOOK_SECRET in provider-service's secret environment settings. Never paste credentials in chat or commit them.
2. Configure PAYFLOW_PROVIDER_TEST_PROVIDER=razorpay only in the controlled TEST deployment. Sandbox remains the default elsewhere.
3. Configure the Razorpay test dashboard webhook to the TEST gateway's /api/v1/provider-webhooks/razorpay endpoint with the matching webhook secret. Enable payment.authorized, payment.captured and payment.failed. QR requires separate provider capability validation.
4. Run forward Flyway migrations, then verify backend health. Preserve Hikari maximum-pool-size=2, minimum-idle=0, idle-timeout=10000, connection-timeout=30000 on the existing constrained database. A one-connection pool prevented Flyway startup.
5. Confirm the website displays Razorpay (TEST), loads official Checkout on demand, and sends only public options to the browser. Secret keys never belong in VITE variables.

## Cases and proof

| Case | Required result |
|---|---|
| Successful provider-supported payment | Real order and payment IDs; backend-confirmed CAPTURED; one capture event; one balanced ledger posting |
| Declined provider attempt | Order remains retryable; no capture ledger entry until a later attempt is authoritatively captured |
| Checkout dismissal | No fabricated failure or capture; current backend state remains authoritative; unsupported local Razorpay cancellation returns conflict |
| Repeat verify / webhook redelivery | Same state; no duplicate capture or ledger posting |
| Capture before delayed authorization | CAPTURED remains unchanged |
| Invalid signature / substituted order | Rejected before state mutation |
| Wrong merchant | 404 with no provider request or state change |
| Amount/currency mismatch | Rejected; no capture posting |
| Lost order creation response | No automatic second order; recovery uses the original persisted reference |
| Delayed webhook | Manual reconciliation fetches provider evidence and converges once |
| Provider timeout | Retryable verification error; no fabricated payment failure |
| Browser script failure | Visible error and safe retry |

Record commit, deployment URL, timestamp, method, TEST mode, sanitized order/payment references, HTTP outcomes, final state, outbox publication and ledger debit/credit totals. Exclude keys, signatures, tokens, card details and customer contact data. Use PASS, FAIL or BLOCKED. Missing credentials, dashboard access, or a disabled test payment method is BLOCKED.

Automated checks: mvn -B -ntp verify; frontend npm test and npm run build. New PostgreSQL tests cover concurrent capture, durable order replay/uncertainty, and balanced ledger deduplication. Existing Kafka outbox IT verifies publication. These checks do not replace a real provider→website→backend→ledger run.

Rollback: stop opening new Razorpay TEST checkout and restore TEST routing to sandbox for new payments. Keep existing Razorpay records and verification/webhook support while outstanding orders reconcile. Never delete payment data or rewrite Razorpay transactions as sandbox transactions.

Razorpay TEST success is one release gate. Merchant delivery webhooks, refund orchestration, settlements, Stripe, email verification, risk policy, always-on availability, backup/recovery and load testing remain separate whole-project gates.
