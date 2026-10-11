# Production integration

Verified deployment targets (11 October 2026):

| Component | Target |
| --- | --- |
| Vercel frontend | `https://payflow-b5r1.vercel.app` |
| Vercel frontend | `https://online-payflow.vercel.app` |
| Gateway | `https://payflow-api-gateway-muyp.onrender.com` |
| n8n | `https://firoz1860.app.n8n.cloud` |

## Frontend and gateway

Set `VITE_API_URL=https://payflow-api-gateway-muyp.onrender.com/api/v1`
in each Vercel production project, then rebuild. This value is public config.
Never place database credentials, JWT secrets, internal tokens, payment provider
secrets, Discord webhook URLs, or LLM keys in a `VITE_` variable.

The production gateway CORS allowlist is:
`PAYFLOW_CORS_ORIGINS=https://payflow-b5r1.vercel.app,https://online-payflow.vercel.app`.
The gateway requires redeployment after changing it. Validate OPTIONS requests
from both origins; receiving HTML from Vercel is not proof login works.

## Copilot deployment requirements

The existing Render workspace has six Payflow services; Copilot is not deployed.
The `render.yaml` Copilot entry uses a paid private service and new Blueprint
database/Redis references. Applying that entire Blueprint is not a migration of
the existing free Oregon services.

Before creating Copilot, supply the existing production `DATABASE_URL`,
`PAYFLOW_DB_SCHEMA=ai`, and the same `JWT_SECRET` and `PAYFLOW_INTERNAL_TOKEN`
used by the other backend services through Render's secret configuration.
Set the four `MERCHANT_SERVICE_URL`, `PAYMENT_SERVICE_URL`,
`PROVIDER_SERVICE_URL`, and `LEDGER_SERVICE_URL` values to their deployed
services, and `AI_AGENT_ENABLED=true`. For the current shared database use
maximum pool size 2 and minimum idle 0, consistent with the deployed services.
Set the gateway `AI_AGENT_SERVICE_URL` to the new service's reachable URL only
after its health check succeeds. Provider keys are optional for startup;
real model requests require a supported server credential or an authenticated
merchant's BYOK credential.

## n8n integration state

The global `PAYFLOW_BASE_URL` variable now points to the production gateway.
The Deployment Health Check's HTTP request and interpretation were manually
tested: HTTP 200, body status UP, `healthy=true`. The health endpoint is public;
this test does not verify HMAC authentication or other backend dependencies.

`N8N_SHARED_SECRET` and the Discord destination are not configured. Do not
store secrets in ordinary n8n Variables or source control. A header called
`X-PayFlow-Signature` does not grant access: there is currently no matching
signed automation endpoint implementation in the backend. The existing daily
workflow's `/api/v1/ai/capabilities` request requires a dashboard JWT and cannot
be authenticated by these HMAC headers. Do not use a long-lived dashboard JWT
as the automation credential.

Keep unfinished scheduled workflows unpublished until their credential,
destination, backend authorization, failure handling, and live execution are
verified. Notification delivery must be tested separately. The n8n account
displayed 3 trial days remaining on 11 October 2026.

## Release gates still open

- Vercel configuration access returns 403 for the selected team.
- Copilot needs a deployment and secure shared-secret/database wiring.
- Signed automation endpoints and notification delivery remain unfinished.
- Email verification/reset delivery is unfinished; never expose reset tokens.
- Dependency and secret scanning are mandatory and passed on releases #28 and #29; continue enforcing them on future changes.
- Free Render cold starts and downstream timeout failures remain observable.
- Sandbox payment success does not establish live provider readiness.

Do not call the project production-ready until these gates are resolved and
the affected user flows pass live tests.

## Editing the landing hero

Sign in as a platform administrator (`platform:admin`), then open
**Admin → Website content** at `/admin/content`. Edit the headline, highlighted
headline, badge, descriptions, button labels, and trust points. **Save and
publish** commits the text to the authentication database; visitors load it on
opening `/`, and authenticated users can preview it at `/website`. Text is
rendered without HTML. Button destinations remain registration and the payment
explanation section.

Migration `auth/V7__site_hero_content.sql` seeds the existing copy. A content
service outage leaves the existing default hero visible. Concurrent editors
receive a conflict and must explicitly reload before overwriting another save.
Merchant accounts and guest accounts cannot change global website content.

A platform admin must already exist. For an installation without one,
`PAYFLOW_ADMIN_EMAIL` and `PAYFLOW_ADMIN_PASSWORD` (at least 12 characters) can
bootstrap a new admin through Render's secure environment settings and an auth
redeployment. Existing accounts are deliberately left untouched; entering an
existing merchant email does not promote it. Never commit or share the password
in chat. The admin password should be changed after initial sign-in.
