# API reference

Base URL with the repository Docker Compose setup: `http://localhost:8000`
(the API Gateway is published from container port 8080 to host port 8000).
Every response carries
`X-Correlation-Id`; quote it in support requests.

## Authentication

Two credential types, both presented as `Authorization: Bearer …`:

| Credential | Looks like | Used by | Verified by |
|---|---|---|---|
| Dashboard JWT | `eyJhbGci…` | humans in the dashboard | each service, independently |
| Secret API key | `sk_live_…` / `sk_test_…` | merchant servers | merchant-service |
| Publishable key | `pk_live_…` / `pk_test_…` | browsers | rejected on money-moving endpoints |

Access tokens live 15 minutes. Refresh tokens rotate on every use; presenting an
already-rotated token revokes the entire family, because it means the token leaked.

## Errors

```json
{
  "code": "REFUND_AMOUNT_EXCEEDED",
  "message": "Refund of 700.00 exceeds the refundable balance of 300.00",
  "correlationId": "cid_8f2a…",
  "timestamp": "2026-08-29T10:15:00Z"
}
```

`code` is stable and machine-readable; `message` is for humans and may change.

| Code | HTTP | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Request body or headers invalid |
| `UNAUTHORIZED` | 401 | Missing or invalid credentials |
| `FORBIDDEN` | 403 | Authenticated but lacking the permission |
| `NOT_FOUND` | 404 | Does not exist, or belongs to another merchant |
| `CONFLICT` | 409 | Duplicate order, or a request with this key is in flight |
| `IDEMPOTENCY_KEY_REUSED` | 409 | Same key, different body |
| `MERCHANT_NOT_ACTIVE` | 422 | Merchant cannot process payments |
| `RISK_BLOCKED` | 422 | Declined by risk checks |
| `REFUND_AMOUNT_EXCEEDED` | 422 | Would exceed the refundable balance |
| `RATE_LIMITED` | 429 | Slow down |
| `PROVIDER_UNAVAILABLE` | 503 | Retry with the **same** Idempotency-Key |

## Create a payment

```http
POST /api/v1/payments
Authorization: Bearer sk_test_…
Idempotency-Key: checkout_9382
Content-Type: application/json

{
  "amount": 1000.00,
  "currency": "INR",
  "merchantOrderId": "order_1234",
  "customerId": "8f14e45f-…",
  "description": "Order #1234",
  "paymentMethod": "CARD",
  "returnUrl": "https://merchant.example/return",
  "metadata": { "cart_id": "c_991" }
}
```

`Idempotency-Key` is **required**. A timeout on this endpoint is indistinguishable
from a failure, so you must be able to retry — and you can only retry safely
because the key guarantees the retry will not create a second charge.

Response `201`:

```json
{
  "paymentReference": "pay_k3n8x2m9q7w1e5r4t6y8u0i2",
  "amount": 1000.00,
  "currency": "INR",
  "refundedAmount": 0.00,
  "refundableAmount": 1000.00,
  "status": "PENDING",
  "provider": "sandbox",
  "providerPaymentId": "sbx_pay_…",
  "checkoutUrl": null,
  "attempts": [
    { "attemptNumber": 1, "provider": "sandbox", "paymentMethod": "CARD",
      "status": "PENDING", "amount": 1000.00 }
  ]
}
```

A replayed request returns the identical body plus `Idempotent-Replay: true`.

**Do not treat browser success or a checkout redirect as payment.** Status only
becomes `CAPTURED` after PayFlow processes a trusted provider confirmation.
Poll `GET /api/v1/payments/{reference}` for the current core build. The planned
merchant webhook-delivery service is not implemented yet, so this repository
does not currently promise outbound `payment.succeeded` delivery to merchant URLs.

### Statuses

```
CREATED → PENDING → PROCESSING → AUTHORIZED → CAPTURED
                                                  ↓
                                    PARTIALLY_REFUNDED → REFUNDED
any non-terminal → FAILED | CANCELLED
```

Transitions are enforced. A late webhook cannot move a `CAPTURED` payment back.

## Webhooks

### Provider ingress — implemented

Payment-provider callbacks enter through:

```http
POST /api/v1/provider-webhooks/{provider}
```

The Provider Service verifies the provider signature over the raw request body,
deduplicates provider events, writes them to its outbox, and publishes the
result through Kafka. The sandbox uses
`X-PayFlow-Sandbox-Signature` + `X-PayFlow-Timestamp`; Razorpay uses
`X-Razorpay-Signature`.

This endpoint is for configured payment providers, not for merchant browser code.
Provider webhook secrets stay on the backend.

### Merchant outbound webhooks — planned

The gateway reserves `/api/v1/webhook-endpoints/**`, but the merchant
webhook-management/delivery service is not part of the current core. The React
dashboard therefore reports that capability as unavailable instead of showing
fake endpoint or delivery data.

## Rate limits

Token bucket in Redis, keyed per API key (falling back to IP).

| Endpoint group | Sustained | Burst |
|---|---:|---:|
| `/api/v1/auth/**` | 5/s per IP | 10 |
| `/api/v1/payments/**` | 100/s | 200 |
| `/api/v1/refunds/**` | 30/s | 60 |
| `/api/v1/merchants/**` | 20/s | 40 |
| `/api/v1/settlements/**` | 10/s | 20 |

## Test mode

TEST keys route to the sandbox gateway, which has deterministic hooks:

| Amount ends in | Behaviour |
|---|---|
| `.99` | declines at creation |
| `.13` | fails during provider confirmation when sandbox auto-confirm is enabled |
| anything else | starts as `PENDING` |

The Render Blueprint enables sandbox auto-confirmation for the deployed demo:
after roughly five seconds a signed sandbox provider event moves a normal test
payment to `CAPTURED`. Local Docker keeps auto-confirmation disabled by default
so manual webhook and deduplication tests remain deterministic.

Sandbox provider events are HMAC-signed and pass through the same Provider
Service verification, outbox, Kafka, Payment Service, and Ledger Service flow.
