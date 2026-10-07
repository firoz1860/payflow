# PayFlow Copilot (ai-agent-service)

PayFlow Copilot is an operations and payment-investigation assistant. Its guiding
rule: **the LLM never determines financial truth.** It reasons only over verified
evidence retrieved through a hardcoded, read-only tool allowlist, and every answer
separates **FACT** from **INFERENCE** from **MISSING**.

- Service: `ai-agent-service` (Java 21 / Spring Boot 3.3.5), port **8096**, package
  `com.payflow.ai`, own database **`ai_db`**.
- Routed by the gateway at **`/api/v1/ai/**`**; `/internal/**` is never exposed.
- The whole platform — and `ai-agent-service` itself — **starts and runs with zero
  LLM API keys**. Without a key, AI endpoints return a structured `AI_KEY_REQUIRED`.

## Architecture

```
Merchant / Admin → React Copilot (/ai) → API Gateway (/api/v1/ai/**, JWT, strict
rate limit, circuit breaker) → ai-agent-service
   ├── resolve LLM credential (BYOK → optional server env → AI_KEY_REQUIRED)
   ├── gather evidence via READ-ONLY tool allowlist →  payment / provider / ledger
   │     / merchant  (/internal/ai/** , X-Internal-Token)
   ├── deterministic redaction of every tool result
   ├── PaymentInvestigationService → FACT / INFERENCE / MISSING + confidence
   ├── PromptBuilder (fixed system prompt + fenced UNTRUSTED-DATA evidence block)
   └── LlmClient (Anthropic / OpenAI / Gemini / xAI) → answer + evidence + confidence
```

The LLM is **not** in any payment critical path. If the AI provider, the AI
database, or n8n is down, payment APIs are unaffected.

## BYOK (Bring Your Own Key)

No project-owner LLM secret is required. Credentials resolve in priority order:

1. the authenticated user's **ephemeral** BYOK credential;
2. an optional server-side environment key (fallback);
3. otherwise `AI_KEY_REQUIRED`.

- Supported providers: **Anthropic, OpenAI, Gemini, xAI**, plus a custom
  OpenAI-compatible endpoint (SSRF-guarded: HTTPS only; loopback, RFC1918,
  link-local, and cloud-metadata hosts are rejected).
- BYOK keys live **in memory only**, per user, with a short TTL
  (`AI_BYOK_TTL_MINUTES`, default 60) and a scheduled sweep. They are **never**
  written to PostgreSQL, Redis, logs, exceptions, actuator, audit, or the browser,
  and are **never** returned after submission — only masked metadata is.

Credential API (requires `ai:use`):

| Method | Path | Body / result |
|---|---|---|
| POST | `/api/v1/ai/credentials` | `{provider, apiKey, baseUrl?}` → `{provider, configured, source, masked, expiresAt}` |
| GET | `/api/v1/ai/credentials` | masked status per provider (USER / SERVER / none) |
| DELETE | `/api/v1/ai/credentials/{provider}` | erase the user's key for that provider |

The browser submits the key once over HTTPS to this backend endpoint — it is
**never** used provider-direct from React.

## Privacy boundary & redaction

A deterministic `Redactor` runs over **every** tool result before it reaches the
model. It removes, by field name and by value pattern: authorization headers,
bearer tokens, JWTs, refresh tokens, cookies/session ids, DB URLs/connection
strings, passwords, provider/webhook/internal secrets, PEM private keys, LLM keys
(`sk-ant-`, `sk-`, `xai-`, `AIza…`), PayFlow API keys (`pk_/sk_…`), and card
PAN/CVV. Safe operational identifiers are preserved: paymentReference,
merchantOrderId, providerPaymentId, status, timestamps, currency, amount,
provider, ledger posting id, failure code. Retrieved business data is placed in a
clearly fenced "UNTRUSTED DATA" block and is **never** concatenated into the
system instruction.

## Tool allowlist (read-only in V1)

The model does **not** call arbitrary URLs/SQL/shell. Tools are server-orchestrated
over a hardcoded registry; each derives `merchantId` from the authenticated JWT
principal, enforces tenant ownership server-side (cross-tenant → "not found", no
enumeration), has a timeout and response-size cap, and records an execution row.

`get_payment`, `get_payment_attempts`, `get_provider_evidence`,
`get_ledger_evidence`, `get_current_merchant`, `get_service_capability`,
`explain_idempotency_contract`. Evidence is read from narrow internal endpoints —
`payment-service /internal/ai/payments/{ref}`, `provider-service
/internal/ai/providers/payments/{id}`, `ledger-service
/internal/ai/ledger/payments/{ref}` — guarded by `X-Internal-Token` and not exposed
through the gateway.

## Payment investigation (FACT / INFERENCE / MISSING)

`PaymentInvestigationService` aggregates payment + provider + ledger evidence into
three explicitly separated sets plus a deterministic confidence
(`HIGH|MEDIUM|LOW|UNKNOWN`). It never promotes an inference to a fact and never
fabricates missing evidence; a planned-but-unimplemented service (e.g. settlement,
reconciliation) is reported as **not implemented** (from the capability catalog),
not invented. Browser success is never treated as capture.

## Conversation API & SSE

All under `/api/v1/ai`, require `ai:use`, and are scoped to the authenticated
user/merchant (cross-user/tenant access → 404):

`POST /conversations`, `GET /conversations`, `GET /conversations/{id}`,
`DELETE /conversations/{id}`, `POST /conversations/{id}/messages` → `AnswerResponse
{messageId, conversationId, answer, evidence[], confidence, warnings[], toolCalls[]}`,
`GET /conversations/{id}/stream` (SSE: real `tool` events then a final `message`
event; `error` event carries `{code,message}`), `POST /feedback`,
`GET /capabilities` (works even with no LLM key).

## RBAC

New permissions `ai:use` and `ai:admin` (auth-service `Permission` enum +
migration `V5__ai_permissions.sql`): `MERCHANT_OWNER` and `MERCHANT_DEVELOPER` get
`ai:use`; `PAYFLOW_ADMIN` gets `ai:use` + `ai:admin` (platform-ops / cross-tenant
investigation). Permissions flow through the existing JWT `permissions` claim and
are enforced with `@PreAuthorize("hasAuthority('ai:use')")`.

## Approvals (scaffolded; no action executes in V1)

The `ai_approvals` table and statuses (PENDING/APPROVED/REJECTED/EXPIRED/EXECUTED/
FAILED) exist so a future write capability can follow PROPOSE → APPROVAL →
PERMISSION CHECK → DETERMINISTIC EXECUTION → AUDIT. V1 ships no write/action tools
and executes no approval.

## Observability, cost, retention

Micrometer: `payflow.ai.requests{,.success,.failure}`, `payflow.ai.latency`,
`payflow.ai.tool.calls{,.failures}`, `payflow.ai.tokens.{input,output}`. Cost
controls: `AI_MAX_TOOL_CALLS` (8), `AI_MAX_HISTORY_MESSAGES` (30),
`AI_REQUEST_TIMEOUT_SECONDS` (45), gateway rate limit (stricter than payment
reads). Conversations are purged after `AI_CONVERSATION_RETENTION_DAYS` (30) by a
scheduled job.

## n8n (automation layer — planned, non-authoritative)

n8n is positioned as an operational automation/orchestration layer only; it is
**never** authoritative for capture, ledger posting, payment state, idempotency,
webhook verification, or provider dedup. Signed (HMAC + timestamp/replay) outbound
integration endpoints and workflow exports under `automation/n8n/` are a planned
follow-up and are **not** part of this PR.

## Configuration (all optional; none required to start)

Server: `AI_AGENT_ENABLED`, `AI_DEFAULT_PROVIDER`, `AI_DEFAULT_MODEL`,
`AI_BYOK_TTL_MINUTES`, `AI_MAX_TOOL_CALLS`, `AI_MAX_HISTORY_MESSAGES`,
`AI_REQUEST_TIMEOUT_SECONDS`, `AI_CONVERSATION_RETENTION_DAYS`. Optional server-owned
fallback keys (never required): `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`,
`GEMINI_API_KEY`, `XAI_API_KEY`. Shared: `JWT_SECRET`, `PAYFLOW_INTERNAL_TOKEN`,
downstream `*_SERVICE_URL`/`*_SERVICE_HOSTPORT`. Frontend: only `VITE_API_URL`
(public) — **no LLM key is ever a `VITE_` variable.**

## Failure modes

- AI provider unavailable / no credential → `AI_KEY_REQUIRED` or `AI_UNAVAILABLE`;
  payment APIs continue normally.
- Tool timeout / downstream circuit open → that evidence is reported **MISSING**,
  never fabricated.
- AI database down → payment processing is unaffected (AI is isolated).
- LLM timeout / malformed response → controlled `AI_UNAVAILABLE` with no key leak.

## Run & test locally

```bash
# backend unit tests (all modules)
mvn -B clean test
# full verification incl. Testcontainers integration tests (needs Docker)
mvn -B clean verify
# frontend
cd frontend && npm ci && npm test -- --run && npm run build
# whole stack
docker compose up -d --build        # ai-agent-service on :8096, gateway on :8000
curl localhost:8000/api/v1/ai/capabilities   # works with no LLM key
```

## Known limitations

- **V1 is read-only.** No write/action tools; the approval data model exists
  (`ai_approvals`) but no action executes.
- Tools are **server-orchestrated** over the allowlist, not LLM-native
  function-calling (a deliberate V1 security choice; native tool-calling is a
  possible V2 enhancement).
- SSE streams real stage events (tool status, then the final answer), not
  per-token streaming.
- Provider HTTP clients for all four vendors are implemented but are only
  exercised when a real key is supplied; CI uses a fake client and never requires
  a paid key.
- n8n workflows and signed automation endpoints are planned, not in this PR.
- Planned PayFlow services (refund, settlement, reconciliation, risk, webhook,
  customer, notification, audit) remain unimplemented; the Copilot reports them as
  such rather than fabricating their data.
