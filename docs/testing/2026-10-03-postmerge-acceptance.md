# Payflow post-merge acceptance

Merged PR: https://github.com/firoz1860/payflow/pull/15
Main commit: 9e8146b2f05dd4c9839f121ac73ac6c182d18bc4
Date: 2026-10-03 UTC (Asia/Calcutta local date also October 3).

PR checks passed before the user-authorized merge. This includes unit tests, actual PostgreSQL/Kafka integration tests, frontend tests/build, all seven container builds, blueprint validation and the security workflow. A successful security workflow does not establish a clean dependency audit: OWASP is configured continue-on-error; inspect its report before treating dependencies as approved.

Both Vercel projects report success for the merge commit. Public frontend HTML and its JavaScript asset returned HTTP 200; the asset contains the official Razorpay SDK loader and verification endpoint. This is asset verification, not a completed real Checkout transaction. Direct Vercel connector access returned a scope authorization error; GitHub deployment status was used instead.

Production database verification confirms provider migration V3 (durable order attempts) and payment migration V4 (nullable payment identity) succeeded. Sandbox remains the default. No LIVE payment or paid hosting change was made.

Post-deployment API acceptance is running with disposable merchant accounts and TEST keys. Secret credentials remain in a transient restricted file; only sanitized outcomes and payment references are retained. Initial rollout-time registration timed out; a later attempt succeeded. Preserve that availability failure alongside the successful warmed test.

## Remaining production gates

| Gate | Status | Required evidence or action |
|---|---|---|
| Real Razorpay TEST acceptance | BLOCKED | Secure backend TEST key ID/secret, webhook secret, dashboard configuration and actual website payment → verified capture → one balanced ledger posting |
| Always-on availability | BLOCKED | Replace sleeping production consumers/critical APIs with reviewed always-on hosting; verify cost and database capacity before changing paid plans |
| Merchant delivery webhooks | NOT IMPLEMENTED | Tenant-owned endpoint management, signed delivery, safe destinations, durable retries, delivery inspection |
| Refund orchestration | NOT IMPLEMENTED | Owned refund API, provider outcome reconciliation and one balanced reversal; provider adapter alone is insufficient |
| Settlements | NOT IMPLEMENTED | Approved payout/settlement workflow, authoritative reconciliation and balanced postings |
| Recovery and load acceptance | NOT VERIFIED | Backups, restore drill, backlog replay, latency/error targets and representative traffic |
| Dependency findings | NEEDS REPORT REVIEW | Security workflow is green, but the existing OWASP step allows failure |

No claim of whole-project production readiness follows from merging PR #15.

## Failures discovered and follow-up fixes

Live requests reproduced ledger accounts/postings HTTP 500 for a merchant owner. Logs show AuthorizationDeniedException; the production role table confirms the owner lacks LEDGER_READ while finance/admin have it. A forward auth V5 migration adds only the owner's ledger-read permission. Existing merchant ownership filters remain in place; developers/support receive no additional authority. Existing access tokens need a fresh login or refresh to acquire the corrected grant.

The common MVC error handler now returns 403 for denied authorization and 401 for authentication failure, with generic messages. Regression requests produced 500 before the change and the expected statuses afterward.

A valid API key was initially rejected as 401 when the merchant verifier timed out, then worked unchanged on retry. A typed dependency-unavailable result now produces HTTP 503 AUTHENTICATION_UNAVAILABLE; invalid keys remain 401. The filter stops before the protected handler and does not expose dependency details. A Spring-proxy fallback regression also passes.

All eight modules passed local Java 21 unit tests after these fixes. The role-migration PostgreSQL test compiles and requires CI container execution. All five deployed sandbox methods captured; creation and confirmation decline cases failed as expected, with idempotent replay preserving the payment reference. Ledger API verification awaits the permission fix deployment.
