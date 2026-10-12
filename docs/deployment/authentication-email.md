# Authentication email over HTTPS

Auth service can deliver password-reset links, email-verification links, and
password-change notices through Resend's HTTPS API. It is disabled by default.
Render Free blocks SMTP ports 25, 465, and 587; an SMTP credential in n8n does
not configure this backend delivery path.

Official references: <https://render.com/docs/free> and
<https://resend.com/docs/api-reference/emails/send-email>.

## Configuration and activation

Configure these values only on the existing Render **payflow-auth** service in
Firoz's Workspace. Never put the key in Vercel frontend variables or source
control. Use an existing Resend account and an authorized, verified sender.

| Variable | Value |
| --- | --- |
| `RESEND_API_KEY` | Secret Resend sending key |
| `PAYFLOW_AUTH_EMAIL_FROM` | Verified sender address, optionally with display name |
| `PAYFLOW_FRONTEND_URL` | `https://payflow-b5r1.vercel.app` |
| `PAYFLOW_AUTH_EMAIL_ENABLED` | `true` after the key and sender are configured |

Enabling delivery with a missing key, missing sender, or invalid frontend origin
fails startup. Requests use the fixed `https://api.resend.com/emails` endpoint,
a five-second timeout, and no redirects. The frontend origin must be HTTPS,
without credentials, a query, or a fragment. Enable the feature only after all
five outbox migrations have deployed successfully.

Keep the existing automatic registration verification policy until a fresh
verification link has been delivered and consumed in the production frontend.
Then disable automatic verification separately if verified email is required
for registration. Do not lock new users behind untested delivery.

## Delivery and retry semantics

The auth outbox routes `notification.requested` directly to the email sender
when enabled. Other events keep their existing Kafka routing. An email failure
does not fall back to Kafka and is never reported as successfully delivered.
When delivery is disabled, notification events retain legacy Kafka routing;
this repository does not contain a separate Kafka email consumer.

Each request uses the outbox row ID as a stable provider idempotency key.
Retries preserve the recipient, link, and message body. Tokens must belong to
the stored user, match their purpose, and remain unexpired and unused. A missing
user, changed address, obsolete token, synthetic guest address, or event older
than 23 hours is suppressed without sending. These processed rows are marked
`PUBLISHED`; that status alone is not proof of inbox delivery. Provider errors
and exception messages exclude recipients, credentials, links, and response
bodies.

All publishing services persist retry eligibility. Delays are 30, 60, 120,
240, then 300 seconds, with a ten-attempt cutoff. Deferred rows cannot block
new eligible rows. The maximum automatic retry horizon is about 32.5 minutes
plus request/poll time; expired reset tokens are suppressed instead of mailed.
Ten unsuccessful attempts move a row to `FAILED` for operator investigation.
Do not blanket-requeue failed notifications: request a fresh link for expired
tokens, and preserve the original event ID for any reviewed eligible replay.
The migration does not requeue historic failed events or change their status.

With email enabled, the auth publisher handles one event per transaction and
keeps the default one-second polling delay. A single instance sends at most
one email per second under that default. Multiple instances need a shared rate
limiter before scaling beyond provider limits. Provider acceptance marks the
event processed; spam filtering or a bounce still requires provider delivery
tracking and a real inbox check.

## Acceptance and rollback

Use a controlled account and the production frontend. Confirm that requesting
reset for a known and unknown address gives the same public response and never
returns a token. Verify the real email arrives, the HTTPS link opens the reset
page, the password changes, the old password fails, the new password works,
and the consumed link fails on reuse. Verify a password-change notice and a
fresh registration verification link. Confirm delivery in the provider account
without copying any secret into logs or issue comments.

Automated coverage exercises a real local HTTP server, PostgreSQL persistence,
deferred retry selection, stable idempotency, reset-link consumption, expired
tokens, recipient/owner checks, safe errors, redirects, feature configuration,
and unchanged Kafka routing. These checks do not establish live provider or
inbox delivery.

For rollback, set `PAYFLOW_AUTH_EMAIL_ENABLED=false` and redeploy auth. Retain
the additive migrations and all existing outbox rows. Legacy Kafka notification
publishing resumes and does not itself deliver email. If registration has
already been switched to mandatory verification, restore its verified working
policy before disabling email delivery.

Status on 12 October 2026: no live Resend key or verified sender is available
to this execution session. Live email acceptance is pending, and production
delivery remains disabled. A configured n8n SMTP connection does not satisfy
this acceptance check.
