# Payflow repository and deployment audit — 2026-10-11

Baseline: main f3a5e2b157f379d9605c4f07be27630b681a8957 (PR23). PR16 through PR23 are merged. Latest PR23 CI and both Vercel deployment statuses are green.

## Verified defects and repairs

- Auth startup fails: Render logs on October 8 and October 11 report two Flyway migrations with version 5. Preserve already deployed V5 merchant-owner ledger permission migration; move never-applied AI permission migration to V6 without changing SQL contents. No production schema-history edits or database resets.
- Public password reset returns bearer reset credentials by default when an email exists. An HTTP regression reproduced token disclosure. Remove the expose-token switch and response field entirely; keep generic 202 acknowledgement and existing out-of-band notification event. The frontend no longer builds a reset URL from a public response or asserts email delivery succeeded. Real email delivery remains unfinished.
- Auth integration test was excluded from unit tests but Maven Failsafe was not enabled for the module. Restore the plugin and test V5-to-latest upgrade plus owner ledger/AI grants. Actual PostgreSQL execution requires CI Docker, unavailable locally.
- Add per-service migration-version collision validation to CI. A temporary V05/V5 duplicate probe fails; current versions pass.

## Environment and remaining limitations

Render workspace: Firoz's Workspace. Six existing Payflow web services found; Copilot ai-agent-service is not deployed. The repository blueprint specifies a paid private service; no paid service was created. Repository n8n endpoints/workflows are explicitly planned, not implemented. No n8n connector is exposed in this session.

Set PAYFLOW_EXPOSE_RESET_TOKEN=false on Render auth as containment. Its automatic redeploy still encountered the duplicate migration, so this setting alone does not establish a successful mitigation deployment. The code repair must be merged/deployed and fresh login/ledger/AI-capability acceptance rerun.

Vercel connector returns403 for team firozs-projects-70dbf044. No authenticated Vercel CLI is installed. Both online-payflow.vercel.app and payflow-b5r1.vercel.app public HTML returned200. GitHub reports successful Vercel deploys for main. These checks do not establish authenticated frontend end-to-end correctness. Gateway health request timed out at35 seconds; free-plan cold starts remain an availability limitation.

Spring Boot remains3.3.5. The optional OWASP job allows failure, so green workflow status is not a clean vulnerability audit. Provider TEST acceptance, live payments, email delivery, n8n, always-on hosting, restore/load testing and whole-project production readiness remain unverified or unfinished.

## Validation

Password-reset HTTP regression: RED with real token in public JSON; GREEN after removal. Common/auth unit suite passes. Frontend21 tests and TypeScript/Vite build pass. Full nine-module Maven reactor unit suite passes (45.860 seconds). Actual PostgreSQL CI acceptance remains pending in PR24. Independent review found no Critical/Important regressions; trailing-zero collision normalization and stale config comments were corrected.
