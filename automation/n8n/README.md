# PayFlow email operations templates

These templates are inactive and **not deployed or delivery-tested**. They
replace the undefined Discord destination with n8n's Send Email node. Use the
existing SMTP credential; passwords are never part of exports or variables.

## Configure and verify

1. Set non-secret n8n Variables: `PAYFLOW_BASE_URL` to the production gateway,
   `PAYFLOW_EMAIL_FROM` to the SMTP sender, and `PAYFLOW_ALERT_EMAIL` to the
   explicitly chosen recipient.
2. Import `payflow-email-smoke-test.json`; select the existing SMTP credential
   in Send Email. Run once, verify the node reports the recipient accepted,
   and check inbox/spam before calling delivery successful.
3. Import the health alert and daily summary templates, select the SMTP
   credential in each, and execute manually while unpublished. Health alerts
   send once on an unhealthy/unavailable incident and once on recovery. Daily summaries send
   every execution and make no claims about untested services.
4. Publish after verification. The health schedule is every 15 minutes and
   the daily summary is 08:00 UTC (13:30 India time). Publishing enables
   scheduled email; use the intended recipient only. Monitor execution usage.

These workflows read only the public gateway health endpoint. They do not use
HMAC headers, JWTs, internal tokens, payment data, or LLM credentials. HTTP 200
without a JSON health status of UP is not healthy. Transport failures are
reported as unavailable; raw error bodies are not copied into email.

This does not implement signed incident ingestion, unresolved-payment review,
or authenticated Copilot checks. Those require dedicated backend authorization
and must not be simulated with a dashboard JWT or a made-up signing header.
Free-service sleep can trigger unavailable alerts and is not resolved by this
monitor. Health-alert incident state is recorded only after SMTP succeeds, so
failed delivery remains eligible for retry. n8n static data is persisted by
successful active executions, not manual editor tests; verify deduplication
with scheduled executions. Concurrent executions can race, so this is not an
exactly-once notification guarantee. These emails never change payment state.

Regenerate with `python3 automation/n8n/generate-workflows.py` and test with
`node --test automation/n8n/*.test.cjs`. The local tests do
not substitute for live n8n import and SMTP delivery tests.
