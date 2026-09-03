# Remaining services

The seven modules in the repository establish every pattern the rest of the
platform needs. This document specifies the eight remaining services precisely
enough to implement without re-deciding anything: the contracts they attach to
already exist in code.

Build them in this order — each depends only on what precedes it.

---

## 1. refund-service (port 8087)

The most concurrency-sensitive service in the platform.

**Schema**

```sql
CREATE TABLE refunds (
    id                  UUID PRIMARY KEY,
    refund_reference    VARCHAR(40)   NOT NULL UNIQUE,
    payment_reference   VARCHAR(40)   NOT NULL,
    merchant_id         UUID          NOT NULL,
    amount              NUMERIC(19,4) NOT NULL,
    currency            CHAR(3)       NOT NULL,
    reason              VARCHAR(500),
    status              VARCHAR(16)   NOT NULL,
    provider_refund_id  VARCHAR(128),
    failure_code        VARCHAR(64),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_refund_amount CHECK (amount > 0),
    CONSTRAINT chk_refund_status CHECK (status IN
        ('REQUESTED','PROCESSING','COMPLETED','FAILED','CANCELLED'))
);
```

Plus `idempotency_records` and `outbox_events` — copy both migrations verbatim
from `payment-service`.

**Flow** (`POST /api/v1/refunds`, `Idempotency-Key` required)

1. Claim the idempotency key — reuse `IdempotencyService` unchanged.
2. Call `payment-service` `GET /internal/payments/{ref}` to read the refundable
   balance and the provider reference.
3. Persist the refund as `REQUESTED` + outbox `refund.created`. **Commit.**
4. Call `provider-service` `POST /internal/providers/refunds`, outside any
   transaction, with `refundReference` as the provider idempotency key.
5. On success: mark `COMPLETED`, then call `payment-service`
   `POST /internal/payments/refunds/register` — **that** call is where the
   over-refund invariant is enforced, under a pessimistic row lock, and it may
   legitimately reject you. Handle the rejection by marking the refund `FAILED`.
6. Emit `refund.completed` via the outbox; `ledger-service` already consumes it.

**The critical property.** `payment-service` is the single serialisation point
for the refundable balance. Do not cache it, do not re-derive it locally, and do
not check it before step 5 and then assume it still holds. The check and the
decrement must be the same locked operation — which they are, inside
`PaymentService.registerRefund`.

**Test that must pass:** two concurrent ₹700 refunds against a ₹1000 payment —
exactly one `COMPLETED`, one `FAILED` with `REFUND_AMOUNT_EXCEEDED`, and
`refunded_amount = 700.00`. Run it against real PostgreSQL.

---

## 2. webhook-service (port 8091)

Delivers PayFlow events **to** merchants (distinct from `provider-service`, which
receives events **from** providers).

```sql
CREATE TABLE webhook_endpoints (
    id          UUID PRIMARY KEY,
    merchant_id UUID         NOT NULL,
    url         VARCHAR(1000) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,   -- never returned after creation
    enabled_events TEXT[]    NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE webhook_deliveries (
    id             UUID PRIMARY KEY,
    event_id       VARCHAR(64) NOT NULL,
    endpoint_id    UUID        NOT NULL REFERENCES webhook_endpoints (id),
    attempt_number INTEGER     NOT NULL,
    http_status    INTEGER,
    response_time_ms INTEGER,
    status         VARCHAR(16) NOT NULL,
    next_retry_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_delivery_attempt UNIQUE (event_id, endpoint_id, attempt_number)
);
```

**Signing** — mirror the scheme `provider-service` already verifies:

```
signature = HMAC_SHA256(endpoint_secret, timestamp + "." + rawBody)
```

Headers: `X-PayFlow-Event-ID`, `X-PayFlow-Signature`, `X-PayFlow-Timestamp`.
The secret never appears in a payload, and it is shown to the merchant exactly
once at creation.

**Retry schedule:** immediate, 1m, 5m, 30m, 2h → `DEAD`. Use a scheduled poller
over `next_retry_at`, not `Thread.sleep` on a consumer thread — sleeping blocks
the partition and stalls every other merchant's webhooks behind one slow endpoint.

**SSRF is the real risk here.** Merchants supply the URL. Before every delivery:
require HTTPS, resolve the hostname and reject private/loopback/link-local ranges
(`10/8`, `172.16/12`, `192.168/16`, `127/8`, `169.254/16`, `::1`, unique-local),
re-check after redirects, and cap redirects at zero. Otherwise a merchant can
point a webhook at your cloud metadata endpoint and read your instance
credentials.

---

## 3. risk-service (port 8093)

`payment-service` already calls this; the contract is
`POST /internal/risk/evaluate` → `{ decision, score, triggeredRules, reason }`.
`RiskClient` defines both records — implement against them exactly.

Deterministic rules to start:

| Rule | Signal |
|---|---|
| `MERCHANT_BLOCKED` | merchant status is not ACTIVE |
| `AMOUNT_ABOVE_THRESHOLD` | above the merchant's configured ceiling |
| `VELOCITY_COUNT` | more than N payments per customer per hour (Redis sliding window) |
| `VELOCITY_AMOUNT` | more than X total per card fingerprint per day |
| `FAILURE_RATE` | more than N consecutive failures from one IP |
| `DUPLICATE_BEHAVIOUR` | identical amount + customer within 60s |

Return `BLOCK` only on high-confidence rules; prefer `REVIEW`. Persist every
decision with its triggered rules — risk decisions must be auditable and
explainable months later, when a merchant disputes a decline.

Keep p99 under 100ms. `payment-service` allows 2s and then fails open below
₹5000 / closed above; a slow risk service degrades revenue directly.

---

## 4. settlement-service (port 8089)

Scheduled worker that calculates and pays out merchant balances.

```sql
CREATE TABLE settlements (
    id                    UUID PRIMARY KEY,
    settlement_reference  VARCHAR(40) NOT NULL UNIQUE,
    merchant_id           UUID        NOT NULL,
    gross_amount          NUMERIC(19,4) NOT NULL,
    refund_amount         NUMERIC(19,4) NOT NULL DEFAULT 0,
    fee_amount            NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_amount            NUMERIC(19,4) NOT NULL DEFAULT 0,
    net_amount            NUMERIC(19,4) NOT NULL,
    currency              CHAR(3)     NOT NULL,
    status                VARCHAR(16) NOT NULL,
    period_start          TIMESTAMPTZ NOT NULL,
    period_end            TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    settled_at            TIMESTAMPTZ,
    CONSTRAINT chk_settlement_net CHECK (net_amount = gross_amount - refund_amount - fee_amount - tax_amount)
);

CREATE TABLE settlement_items (
    settlement_id  UUID        NOT NULL REFERENCES settlements (id),
    transaction_id VARCHAR(64) NOT NULL,
    -- The double-settlement guarantee: a transaction can belong to exactly one
    -- settlement, enforced by the database rather than by a query the worker runs.
    CONSTRAINT uk_settlement_item UNIQUE (transaction_id)
);
```

That unique constraint is the whole design. Do not rely on
`WHERE settled = false` — two overlapping worker runs will both see the same rows.

Select eligible transactions with `SELECT … FOR UPDATE SKIP LOCKED` so
concurrent workers partition the work rather than collide, and emit
`settlement.completed`; `ledger-service` already posts it.

---

## 5. reconciliation-service (port 8092)

Compares PayFlow's records against the provider's. This is the safety net that
catches everything the happy path missed — most importantly the payments left
non-terminal by a provider timeout.

Detects: status mismatch, amount mismatch, missing at provider, missing at
PayFlow, duplicates, stale pending (created over 1h ago and still non-terminal).

```sql
CREATE TABLE reconciliation_runs (...);
CREATE TABLE reconciliation_issues (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    issue_type VARCHAR(32) NOT NULL,
    payment_reference VARCHAR(40),
    payflow_state JSONB,
    provider_state JSONB,
    status VARCHAR(24) NOT NULL,   -- OPEN, AUTO_RESOLVED, MANUAL_REVIEW, RESOLVED
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**Auto-resolve only in the safe direction.** Provider `SUCCESS` + PayFlow
`PENDING` → apply the capture via `PaymentService.applyProviderStatus`, which is
idempotent and validates the transition. Anything that would *reduce* a merchant's
balance goes to `MANUAL_REVIEW`. An automated system that can silently take money
back from merchants is a system nobody will trust.

---

## 6. customer-service (port 8084)

The simplest service, and a clean place to demonstrate tenant isolation. Customer
entity per the spec, unique on `(merchant_id, external_customer_id)`. Every query
takes `merchantId`; every load asserts ownership through `TenantGuard`. Store no
payment instruments — only provider tokens.

## 7. notification-service (port 8094)

Consumes `notification.requested`. `auth-service` and `merchant-service` already
emit it with `{ template, channel, recipient, variables }`. Define an
`EmailProvider` interface with SES/SendGrid implementations. Dedupe on
`eventId` — the outbox is at-least-once and users should not get five copies of a
password reset. Never block a payment on an SMTP round trip.

## 8. audit-service (port 8095)

Consumes `audit.event`, already emitted by auth, merchant and payment with a
consistent shape. Append-only, same immutability trigger as `ledger_entries`.
Never persist secrets, tokens or key material — the emitters already exclude them,
and the consumer should assert it rather than assume it.

---

## Patterns to reuse verbatim

Do not re-derive these; they are already written and tested:

| Need | Reuse |
|---|---|
| Idempotent endpoint | `IdempotencyService` + the `idempotency_records` migration |
| Event publishing | `OutboxRecorder` (auto-configured; just inject it) |
| API key authentication | `ApiKeyAuthenticationFilter` + `MerchantApiKeyVerifier` |
| Tenant isolation | `TenantGuard.assertOwnership` |
| Money handling | `Money.normalize` — never `double`, never bare `BigDecimal` |
| Resilient remote call | `ServiceClientFactory` + `@CircuitBreaker` with an explicit fallback |
| Kafka retry/DLT | `KafkaConfig.kafkaErrorHandler` |
| Errors | Throw `PayFlowException`; `GlobalExceptionHandler` handles the rest |

## Non-negotiables for every new service

1. Flyway migrations, `ddl-auto: validate`, one database per service.
2. `BigDecimal` for money, `NUMERIC(19,4)` in the schema, UTC timestamps.
3. Business invariants expressed as database constraints, not only Java checks.
4. Kafka consumers idempotent on a natural key.
5. No remote call inside an open database transaction.
6. No blind retry of a financial mutation.
7. Public identifiers random, never sequential.
8. Cross-tenant access returns 404.
9. `/internal/**` endpoints only, unless a merchant genuinely needs the surface.
10. Every money-moving path has a concurrency test against real PostgreSQL.
