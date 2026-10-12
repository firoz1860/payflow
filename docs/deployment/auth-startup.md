# Waiting for authentication services

A live cold-start guest request on 12 October 2026 timed out in the frontend at
45 seconds. Render logs showed the gateway took 117 seconds to start. Increasing
only the POST timeout would still leave downstream startup failures and an
uncertain account-creation outcome.

The login and registration pages now check `GET /api/v1/auth/readiness` before
submitting registration, normal login, or guest creation. This gateway-owned
endpoint probes the configured auth and merchant `/actuator/health` endpoints
in parallel. Both must return healthy JSON. Each probe is bounded to twenty
seconds, including connection setup and reading the response; concurrent callers
share each completed result for three seconds. A short connection deadline can
expire before a sleeping service receives its health request. The response is
only `{ "ready": true|false }` with `Cache-Control: no-store`. It discloses no
credentials, user data, upstream addresses, or dependency details. The endpoint
uses existing `AUTH_SERVICE_URL` and `MERCHANT_SERVICE_URL` values (or existing
local host/port defaults), so no new Vercel variables are needed.

The frontend allows up to twenty-five seconds per read request, capped by the
remaining overall budget, and pauses five seconds between checks. It waits up to eight minutes,
showing progress and a cancel action while services start. Polling runs only
during that user-initiated operation; there is no permanent keep-alive or
scheduled warm-up. Cancelling or leaving the page stops the wait and prevents
late responses from signing the visitor in. Once ready, the actual auth POST
is sent once with its existing timeout. Auth POSTs are not retried automatically,
including by the previous-session 401 refresh interceptor. Both auth buttons
are disabled during an operation. Cancellation is available before submission;
after submission an interrupted request may already have committed server-side.

Gateway CORS applies to the local readiness endpoint for both production
origins. Global website administration and payment endpoints remain protected.
Deploy the gateway before accepting the frontend startup flow. During rolling
deployment, an older gateway's 404 or a transient unavailable response keeps
the client in its bounded startup wait; it does not trigger account creation.

Tests cover delayed readiness, transient GET failure, exactly one POST,
deadline exhaustion, cancellation during active checks and guest submission,
competing submissions, previous-session 401 behavior, both auth pages,
cached failure recovery, concurrent health checks, timeout bounds, and the
gateway's real public route/CORS behavior.

The production-constructor regression uses two real HTTP services with delayed
healthy responses. A separate test verifies that cancelling one caller leaves
shared health probes running for another caller. Safe debug diagnostics record
only a service alias, exception type and HTTP status; they omit addresses,
response bodies and credentials.

The fix makes cold-start behavior understandable and avoids repeated account
creation requests. It does not make free hosting always available. Reliable
production response times still require always-on service capacity and verified
database/Kafka capacity. Keep cold-start failures in acceptance records rather
than counting a warmed-up success as an availability guarantee.
