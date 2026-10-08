# PayFlow AI Agent — Current-State Audit

> Stage 0 deliverable. Everything below is grounded in the actual `origin/main`
> source tree (commit `46c91a8`), not in README diagrams or ROADMAP prose.
> Audited 2026-10-07 across `payflow-common`, `api-gateway`, `auth-service`,
> `merchant-service`, `payment-service`, `provider-service`, `ledger-service`,
> and `frontend/`.

---

## IMPLEMENTED TODAY

### Platform / build
- **Java 21, Spring Boot 3.3.5, Maven multi-module** (`pom.xml`). Modules:
  `payflow-common, api-gateway, auth-service, merchant-service, payment-service,
  provider-service, ledger-service`. Spring Cloud `2023.0.3`, resilience4j, jjwt
  `0.12.6`, springdoc, zxing (QR). Frontend is a **separate** React/Vite app
  (not a Maven module).
- **CI** (`.github/workflows/ci.yml`): `build-and-test` (compile → unit tests →
  `verify` with Testcontainers → package), `security-scan` (OWASP dependency-check
  + gitleaks), `docker-build` matrix over the 6 service images (push only),
  `migration-check` (fails the build if an already-applied Flyway migration is
  modified). There is also `frontend-ci.yml` and `render-blueprint.yml`.
- **Deployment config present on main:** `render.yaml` (backend blueprint:
  Postgres `payflow-db`, Redis, a Dockerised Kafka pserv, the 5 core services as
  private `pserv`, `api-gateway` as the only public `web`, Singapore, free/low
  tiers), `docs/DEPLOYMENT_RENDER_VERCEL.md`, `docs/DEPLOYMENT_AWS_FREE.md`,
  `infrastructure/render/`. Frontend is documented as deployed separately on
  Vercel from `./frontend`.
- **Per-service Postgres** created by `infrastructure/scripts/init-databases.sh`:
  `auth_db, merchant_db, customer_db, payment_db, provider_db, transaction_db,
  ledger_db, refund_db, webhook_db, settlement_db, reconciliation_db, risk_db,
  audit_db`. **There is no `ai_agent_db`.**

### Shared kernel (`payflow-common`)
- `security/PayFlowPrincipal` (record: authType USER/API_KEY, userId, merchantId,
  keyId, environment, permissions; `hasPermission`).
- `security/JwtTokenValidator` (HS512, shared secret ≥64 bytes, requires issuer
  `payflow-auth`, maps `permissions` claim) + `security/BearerJwtAuthenticationFilter`
  (maps permissions → `SimpleGrantedAuthority`, no `ROLE_` prefix).
- `security/ApiKeyAuthenticationFilter` + `security/ApiKeyVerifier` SPI.
- `security/InternalAuthFilter` — guards `/internal/**` with header
  **`X-Internal-Token`** (constant-time compare vs `payflow.internal.token` /
  env `PAYFLOW_INTERNAL_TOKEN`), auto-registered for `/internal/*`, exempts
  `/internal/webhooks/providers/**`.
- `security/TenantGuard` — `requireMerchant`, `assertOwnership` (**mismatch →
  404, not 403**, to avoid existence leaks), `requirePermission`.
- `money/Money` (`normalize`, `RoundingMode.UNNECESSARY`), `error/PayFlowException`
  + `error/GlobalExceptionHandler` (`@RestControllerAdvice`, `ApiErrorResponse`),
  `outbox/OutboxRecorder` (`@Transactional(MANDATORY)`), `client/ServiceClientFactory`
  (WebClient that auto-attaches `X-Internal-Token` + correlation id),
  `crypto/Hashing` (`hmacSha256Hex`, `constantTimeEquals`), `event/Topics`.
- **Outbox auto-config** (`PayFlowOutboxAutoConfiguration`) `@EntityScan`s a
  hard-coded package allow-list `com.payflow.{auth,merchant,payment,provider,ledger}.*`
  — **`com.payflow.ai.*` is not included.**

### api-gateway (Spring Cloud Gateway, reactive, YAML routes)
- Routes (`application.yml`): `/api/v1/auth/**`→auth, `/api/v1/merchants/**` +
  `/api/v1/api-keys/**`→merchant, `/api/v1/customers/**`→customer(planned),
  `/api/v1/payments/**`→payment, `/api/v1/refunds/**`→refund(planned),
  `/api/v1/settlements/**`→settlement(planned), `/api/v1/webhook-endpoints/**`→webhook(planned).
- Redis token-bucket `RequestRateLimiter` per route (auth 5/10 by IP; payment
  100/200; merchant 20/40; etc.), key resolvers in `RateLimitConfig`
  (`apiKeyOrIpKeyResolver` primary, `ipKeyResolver` for auth).
- `JwtPreCheckFilter` verifies the bearer JWT (HS512, shared secret, **no issuer
  check**), lets `sk_`/`pk_` API keys pass through, and injects
  `X-PayFlow-User-Id` / `X-PayFlow-Merchant-Id` / `X-PayFlow-Permissions` downstream.
- `InternalPathBlockFilter` returns **404** for any external `/internal/**` or
  `/actuator/**` (except gateway's own health) and **strips** inbound
  `X-PayFlow-*` / `X-Internal-Token` so identity cannot be spoofed.
- CORS terminated at the gateway (`PAYFLOW_CORS_ORIGINS`, default includes
  `http://localhost:5173`).

### auth-service
- Permission enum `domain/Permission.java` — exact values:
  `payments:create, payments:read, refunds:create, refunds:read, customers:manage,
  customers:read, webhooks:manage, settlements:read, api_keys:manage,
  merchant:manage, merchant:read, team:manage, ledger:read, platform:admin`.
  **No `ai:*` permission exists.**
- Roles `domain/RoleName.java`: `PAYFLOW_ADMIN, MERCHANT_OWNER, MERCHANT_DEVELOPER,
  MERCHANT_FINANCE, MERCHANT_SUPPORT`; seeded with fixed UUIDs in
  `db/migration/V3__seed_roles.sql` (roles are seed-only, never created at runtime).
- JWT built by `security/JwtService` (HS512) with claims `sub, iss, email,
  merchantId, roles, permissions, jti, iat, exp`. A `User` has a nullable scalar
  `merchant_id` column (one user → at most one merchant; platform admins null).
- `@EnableMethodSecurity` + `@PreAuthorize("hasAuthority('<perm>')")`.
- Highest migration: **`V4__user_onboarding.sql`** (next free = `V5`).

### merchant-service
- Public `/api/v1/merchants/**`: `me` (merchant:read), `PATCH me`
  (merchant:manage), admin create/status/pricing/live-mode (platform:admin),
  `me/api-keys` issue/list/revoke (api_keys:manage). **API-key secret is returned
  exactly once at creation**; list returns only `maskedKey`.
- Internal `/internal/merchants/**`: `POST /api-keys/verify`,
  `GET /internal/merchants/{merchantId}`.
- `api_keys`: `lookup_hash = SHA-256(pepper:raw)` + `secret_hash = BCrypt(raw)`;
  raw secret never persisted. Merchant status `PENDING/ACTIVE/SUSPENDED/BLOCKED`.
- Highest migration: `V3__registration_retry_key.sql`.

### payment-service
- Public `/api/v1/payments/**`: `POST` (payments:create, `Idempotency-Key`
  required, secret API key), **`GET /{paymentReference}`** (payments:read,
  tenant-scoped, returns full DTO incl. `attempts`), **`GET /` list**
  (payments:read, paginated, `attempts` empty in list), `POST /{ref}/cancel`.
- Internal `/internal/payments/**`: `GET /{ref}` (lean refund projection:
  paymentReference, merchantId, amount, refundedAmount, refundableAmount,
  currency, status, provider, providerPaymentId, refundable — **no attempts /
  timestamps / failure / metadata**), `POST /refunds/register`.
- `Payment` entity: `payment_reference` `pay_`+24 rand; `PaymentStatus`
  = `CREATED, PENDING, PROCESSING, AUTHORIZED, CAPTURED, FAILED, CANCELLED,
  PARTIALLY_REFUNDED, REFUNDED` with an enforced transition map; `environment`
  TEST/LIVE; fields incl. provider, providerPaymentId, failureCode/Message,
  risk_decision/score, timestamps, `@Version`. DB enforces `refunded_amount ≤ amount`.
- `PaymentAttempt`: provider, providerPaymentId, `PaymentMethod` (CARD/UPI/QR/
  NET_BANKING/WALLET), masked instrument (cardLast4/network/token, never PAN),
  amount, status (INITIATED/PENDING/SUCCEEDED/FAILED/CANCELLED), failure fields.
- Idempotency (`IdempotencyService`): `(merchant_id, idempotency_key)` unique;
  replay returns stored body + `Idempotent-Replay: true`; different body for same
  key → **409 `IDEMPOTENCY_KEY_REUSED`**; in-flight → 409; stale (>2 min) take-over.
- Verified state applied **only** via `PaymentService.applyProviderStatus` (driven
  by the provider event consumer), honouring the transition map; illegal/duplicate
  transitions are ignored, not thrown. Outbox events: `payment.created/processing/
  authorized/captured/failed`, `refund.completed`, `audit.event`.
- Remote clients: `MerchantApiKeyVerifier` (→ merchant verify, `@CircuitBreaker`
  fail-closed), `ProviderClient` (→ provider, fallback 503 `PROVIDER_UNAVAILABLE`),
  `RiskClient` (→ **risk-service, which is not implemented**, fail-open under
  ₹5000 else REVIEW). Highest migration: `V4__razorpay_payment_identity.sql`.

### provider-service
- **All endpoints are `/internal/**`, `@Hidden`, `permitAll` + deny-all-else**
  (cluster-internal, no per-caller auth, no merchant scoping).
  `POST /internal/providers/payments`, `GET /internal/providers/{provider}/payments/{id}`
  (hits the **live gateway**, sandbox returns a PENDING stub — *not* stored evidence),
  `POST /internal/providers/refunds`, and the webhook receiver
  `POST /internal/webhooks/providers/{provider}` (raw body + signature header).
- `provider_events`: `uk_provider_event UNIQUE (provider, provider_event_id)`
  (dedup), indexed by `(provider, provider_payment_id)`, `payload` JSONB,
  `processing_status RECEIVED/PROCESSED/FAILED/IGNORED`. **No column named
  "verified"** — a persisted `PROCESSED` row *is* the evidence (it only exists
  after HMAC + ±5-min timestamp checks and dedup pass).
- HMAC: sandbox `HMAC_SHA256(secret, ts + "." + body)`; Razorpay
  `HMAC_SHA256(secret, body)`; constant-time compare. **Nuance:** body is bound as
  `@RequestBody String` then re-encoded UTF-8 — not a byte-exact raw-body capture
  (fine for UTF-8 JSON; worth stating precisely rather than claiming "raw bytes").
- `PaymentGateway` abstraction + `SandboxPaymentGateway` (always on) + Razorpay
  (`@ConditionalOnExpression`, only when key configured).
- **Sandbox deterministic hook: only `.99` → declines at create** (`card_declined`),
  for every method incl. QR. **There is no `.13` hook anywhere.** Non-`.99`
  amounts → PENDING (QR adds a UPI-intent PNG data-URI).
- **No read endpoint returns stored `provider_events`**, and they are keyed by
  `provider_payment_id`, not by PayFlow `paymentReference`. Highest migration:
  `V3__razorpay_order_attempts.sql`.

### ledger-service
- **All endpoints are `/internal/ledger/**`, `@Hidden`** (no public/merchant-facing
  auth; merchantId is a trusted path param). Reads: account balance, a merchant's
  accounts, a posting's entries, a merchant's recent postings (limit ≤200).
  Writes: create posting, reverse posting.
- Double-entry: `ledger_accounts` (owner_type/id, account_type, currency, no
  stored balance — always derived), `ledger_postings` (source_type + source_id
  with `uk_posting_source`, `chk_posting_balanced CHECK (total_debit = total_credit)`,
  self-ref `reverses_posting_id`), `ledger_entries` (entry_type DEBIT/CREDIT,
  positive amount, **immutable via DB triggers** — no update/delete).
- Integrity: per-posting balance check at write + a `@Scheduled` (every 10 min)
  `LedgerIntegrityJob.verifyGlobalBalance` per currency (**job, not an HTTP
  endpoint**). Consumes `payment.captured`, `refund.completed`,
  `settlement.completed` → postings.
- **No endpoint looks up postings/entries by payment reference**, although
  `LedgerPostingRepository.findBySourceTypeAndSourceId` exists (just unexposed).
  Highest migration: `V2__outbox.sql`.

### frontend (`frontend/`, `origin/main`)
- React 18 + TypeScript + Vite 5 + Tailwind 3; Zustand (`persist`, localStorage
  key `payflow-auth`) for auth/permissions; Axios wrapper `src/api.ts`
  (base `VITE_API_URL || /api/v1`, bearer from store, 401 auto-refresh);
  Framer Motion, lucide-react, recharts. **No test runner configured**
  (`package.json` has only `dev`/`build`/`preview`); vitest must be added for
  Copilot tests.
- Routing `src/App.tsx`; guard `components/ProtectedRoute.tsx` (`permission` prop);
  nav arrays in `components/Sidebar.tsx`. **Pages actually present** (21):
  Dashboard, Payments, PaymentDetail (`/payments/:reference`, has a PageHeader
  actions slot), CreatePayment, ApiKeys, MerchantProfile, Analytics, **Ledger**,
  LedgerPostingDetail, **Monitoring**, QrPayments, Webhooks, Developers, Admin
  (Merchants/MerchantDetail/Roles), and the auth pages. (The older
  `feat/frontend-backend-connection` branch has fewer pages; `origin/main` is the
  richer, correct base.)
- Design system: component classes in `src/index.css` (`.card`, `.btn-primary`,
  `.btn-secondary`, `.btn-ghost`, `.input`, `.badge`), brand blue `brand-600`,
  Inter/JetBrains Mono, `Modal`, `Spinner`/`EmptyState`, imperative `toast()`,
  `PageHeader`, Framer `motion` helpers. Rule (verbatim in `frontend/.env.example`):
  browser vars must be `VITE_`-prefixed and are **public — never put secrets there**.

---

## PLANNED / NOT IMPLEMENTED

These appear in README diagrams, `docs/ROADMAP.md`, `docker-compose.yml`,
`api-gateway` routes, `init-databases.sh`, and/or client stubs, but **have no
service module and no running code**. The Copilot must say so honestly and must
never fabricate their data.

| Service | Reserved port | Evidence it is only planned |
|---|---|---|
| `refund-service` | 8087 | ROADMAP §1; gateway route; `refund_db`; no module |
| `customer-service` | 8084 | ROADMAP §6; gateway route; `customer_db`; no module |
| `settlement-service` | 8089 | ROADMAP §4; gateway route; `settlement_db`; no module |
| `webhook-service` (merchant-outbound) | 8091 | ROADMAP §2; gateway route; `webhook_db`; no module |
| `reconciliation-service` | 8092 | ROADMAP §5; `reconciliation_db`; no module |
| `risk-service` | 8093 | ROADMAP §3; **payment-service `RiskClient` already calls it**; `risk_db`; no module |
| `notification-service` | 8094 | ROADMAP §7; `notification.requested` topic; no module |
| `audit-service` | **8095** | ROADMAP §8; `audit.event` topic emitted; `audit_db`; no module |

**Consequence for naming:** the prompt suggested port **8095** for the AI
service, but **8095 is reserved for the planned `audit-service`**. The AI service
will use **8096** to avoid a future collision.

### Not present anywhere in the repo
- No AI / LLM / Anthropic / OpenAI / agent / Copilot code (repo-wide search: zero
  hits). This feature is genuinely greenfield.
- No n8n integration, workflows, or signed automation endpoints.
- No `ai_agent_db`, no `ai:*` permission, no AI route on the gateway.

### Facts the Copilot must NOT assert (fabrication traps found during audit)
1. A `.13` sandbox decline hook — **does not exist** (only `.99`).
2. That provider webhooks are HMAC'd over the literal raw request bytes — it is
   the UTF-8 re-encoding of the bound string body (equivalent for UTF-8 JSON).
3. That any refund/reconciliation/settlement/risk/webhook/notification/audit
   result exists — those services are not implemented.
