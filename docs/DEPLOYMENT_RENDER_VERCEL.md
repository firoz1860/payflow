# PayFlow deployment readiness: Render + Vercel

This document covers the current core architecture only:

```text
Vercel: frontend/
        |
        v
Render: payflow-api-gateway
        |
        +--> payflow-auth
        +--> payflow-merchant
        +--> payflow-payment
               |
               +--> payflow-provider
        +--> Kafka --> payflow-ledger
        |
        +--> Render Postgres
        +--> Render Key Value
```

Only the API Gateway should be public. Auth, Merchant, Payment, Provider, Ledger and Kafka are private Render services.

## 1. Frontend/backend audit

| Feature | Frontend | Backend connection | Deployment status |
|---|---|---|---|
| Login | `/login` | `POST /api/v1/auth/login` | Connected |
| Registration | `/register` | `POST /api/v1/auth/register` | Connected |
| Session refresh | Axios interceptor | `POST /api/v1/auth/refresh` | Connected |
| Logout / logout all | auth service | auth endpoints | Connected |
| Current user | protected route | `GET /api/v1/auth/me` | Connected |
| Email verification endpoint | verify page | `POST /api/v1/auth/verify-email/{token}` | Connected; email delivery worker is not part of the current core |
| Forgot/reset password endpoints | reset pages | password-reset endpoints | Connected; email delivery worker is not part of the current core |
| Dashboard | dashboard | payment + merchant APIs | Connected |
| Payment list/detail | payments pages | Payment Service | Connected |
| Create payment | create page | `POST /api/v1/payments` + idempotency | Connected |
| Cancel payment | payment detail | cancel endpoint | Connected |
| QR payment | QR page | Payment -> Provider | Connected |
| API keys | API Keys page | Merchant Service | Connected |
| Merchant profile/settings | profile page | Merchant Service | Connected |
| Admin merchant list | admin page | Spring Data merchant page | Connected; frontend normalizes `content` to its page model |
| Admin merchant status/pricing/live mode | admin detail | Merchant Service admin endpoints | Connected |
| Admin role assignment | roles page | Auth Service role endpoint | Connected; user UUID must currently be entered manually |
| Analytics | analytics page | calculated from real Payment API data | Connected |
| Ledger screen | ledger page | merchant-safe view from captured payments | Connected; internal ledger mutation APIs intentionally stay private |
| Monitoring | monitoring page | public Gateway health endpoint | Connected for gateway health |
| Provider webhooks | external provider -> Gateway -> Provider | `/api/v1/provider-webhooks/{provider}` | Connected and signature-verified |
| Merchant webhook management | webhooks page | reserved `/api/v1/webhook-endpoints/**` route | Backend service is not in the current core; page reports unavailable instead of showing fake data |
| Developer docs | developers page | static integration documentation | Ready |

### Payment-provider behavior

The default provider is `sandbox`.

- TEST payments use the sandbox provider.
- Sandbox QR generation is real application logic and returns a scannable QR payload/image, but it is a test QR and does not move real money.
- The Render Blueprint enables `SANDBOX_AUTO_CAPTURE=true`. After a short delay, the sandbox emits a signed provider event through the same webhook/outbox/Kafka path used by real provider confirmations. Normal test amounts become `CAPTURED`; amounts ending in `.13` fail during provider confirmation, and amounts ending in `.99` fail at creation.
- Local Docker keeps sandbox auto-capture disabled by default so the existing manual webhook smoke test remains deterministic.
- There is no production-grade hosted sandbox checkout UI; deployed demo payments complete through the signed sandbox provider simulator instead.
- Razorpay support is implemented in the Provider Service for real provider integration.
- Stripe configuration fields exist, but a Stripe `PaymentGateway` implementation is not present in the current core. Do not select Stripe yet.
- The Risk Service is not part of this core deployment. Payment Service has an intentional fallback: requests up to the configured fail-open ceiling (default 5000) can continue; larger requests go to REVIEW when risk is unavailable.

## 2. Render Blueprint

The repository root contains:

```text
render.yaml
```

It creates:

- public `payflow-api-gateway`
- private `payflow-auth`
- private `payflow-merchant`
- private `payflow-payment`
- private `payflow-provider`
- private `payflow-ledger`
- private single-node `payflow-kafka`
- one Render Postgres database
- one Render Key Value instance

The five database-owning services use separate PostgreSQL schemas inside the same Render database: `auth`, `merchant`, `payment`, `provider`, and `ledger`. Local Docker development still uses the existing separate local databases.

> Render free Postgres expires after 30 days and only one free Postgres instance is allowed per workspace. Free Key Value is non-persistent. For a long-running deployment, upgrade those resources. Render private services do not have a Free plan.

### Secrets Render will ask you for

Generate the JWT secret locally:

```bash
openssl rand -base64 64
```

Use that value for:

```text
JWT_SECRET
```

It must be at least 64 bytes because PayFlow signs JWTs with HS512.

Choose a strong admin password for:

```text
PAYFLOW_ADMIN_PASSWORD
```

For the initial CORS value use the exact Vercel production origin when known:

```text
PAYFLOW_CORS_ORIGINS=https://YOUR-VERCEL-DOMAIN.vercel.app
```

Do not put a trailing slash on the origin.

Other internal secrets such as `PAYFLOW_INTERNAL_TOKEN`, `API_KEY_PEPPER`, and the sandbox webhook secret are generated by the Blueprint or copied securely between Render services.

### Optional Razorpay environment variables

The Blueprint starts in sandbox mode and does not require provider credentials.

To enable Razorpay after the core deployment is healthy, add these only to `payflow-provider`:

```text
PAYFLOW_PROVIDER_DEFAULT=razorpay
RAZORPAY_KEY_ID=<your Razorpay key id>
RAZORPAY_KEY_SECRET=<your Razorpay key secret>
RAZORPAY_WEBHOOK_SECRET=<your Razorpay webhook secret>
```

Configure the Razorpay webhook URL as:

```text
https://YOUR-GATEWAY.onrender.com/api/v1/provider-webhooks/razorpay
```

Do not put Razorpay or any other secret in Vercel/Vite variables.

## 3. Vercel frontend

Create a Vercel project from `firoz1860/payflow`.

Use:

```text
Root Directory: frontend
Framework Preset: Vite
Build Command: npm run build
Output Directory: dist
```

`frontend/vercel.json` already contains the SPA rewrite needed for React Router deep links.

Set exactly this required production environment variable:

```text
VITE_API_URL=https://YOUR-GATEWAY.onrender.com/api/v1
```

No JWT secret, database password, Redis credential, internal token, provider secret, or API-key pepper belongs in Vercel. Every `VITE_*` value is browser-visible.

For local development only:

```text
VITE_DEV_API_PROXY=http://localhost:8000
```

## 4. Recommended deployment order

1. Create/connect the Vercel project with Root Directory `frontend` so you know the intended production domain. You can finish its environment variable after the backend URL exists.
2. In Render, create a new Blueprint from the repository's root `render.yaml`.
3. Enter `JWT_SECRET`, `PAYFLOW_ADMIN_PASSWORD`, and the exact Vercel origin for `PAYFLOW_CORS_ORIGINS`.
4. Wait until Postgres, Key Value, Kafka and all private services are healthy, then confirm the public API Gateway is healthy:
   ```text
   https://YOUR-GATEWAY.onrender.com/actuator/health
   ```
5. In Vercel set:
   ```text
   VITE_API_URL=https://YOUR-GATEWAY.onrender.com/api/v1
   ```
   and redeploy the frontend.
6. Test registration -> login -> dashboard -> create QR payment. In Render sandbox mode it should move from `PENDING` to `CAPTURED` after roughly five seconds, then test payment detail -> API keys -> merchant settings.
7. If you enable Razorpay, add its three provider secrets and then configure the signed webhook URL shown above.

## 5. Smoke checks after deployment

Use browser flows for authenticated functionality. For infrastructure, verify:

```text
GET https://YOUR-GATEWAY.onrender.com/actuator/health
```

Expected response contains:

```json
{"status":"UP"}
```

Then test, in order:

```text
Register
Login
Dashboard
Create QR payment
Refresh QR/payment status
Payment list
Payment detail
API key create/revoke
Merchant profile update
Admin merchant list (admin account)
Monitoring (admin account)
```

Do not expose PostgreSQL, Key Value, Kafka, Auth, Merchant, Payment, Provider, or Ledger publicly just to test them.

## 6. Known core limitations

These are backend capability gaps, not missing frontend API wiring:

1. There is no notification-delivery service in the current core, so password-reset and verification events are produced but no email sender consumes them. The Render Blueprint keeps `PAYFLOW_AUTO_VERIFY_EMAIL=true` so new registrations can log in during this deployment.
2. There is no merchant webhook-management service behind `/api/v1/webhook-endpoints/**`; the frontend intentionally shows service availability rather than fabricated webhook records.
3. Stripe is not implemented as a Provider gateway.
4. The standalone Risk Service is absent; the Payment Service's defined fail-open/fail-review fallback remains active.
5. The sandbox provider is for functional testing, not real money movement.

These limitations should not be represented as completed production features until their backend services are added.
