#!/usr/bin/env bash
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8000}"
PROVIDER="${PROVIDER:-http://localhost:8086}"
LEDGER="${LEDGER:-http://localhost:8088}"
INTERNAL_TOKEN="${PAYFLOW_INTERNAL_TOKEN:?set PAYFLOW_INTERNAL_TOKEN}"
SANDBOX_SECRET="${SANDBOX_WEBHOOK_SECRET:-}"
if [ -z "$SANDBOX_SECRET" ] && [ -f .env ]; then
  SANDBOX_SECRET=$(grep '^SANDBOX_WEBHOOK_SECRET=' .env | head -n1 | cut -d= -f2- || true)
fi
SANDBOX_SECRET="${SANDBOX_SECRET:-sandbox-webhook-secret}"

green() { printf '\033[32m✓ %s\033[0m\n' "$1"; }
fail()  { printf '\033[31m✗ %s\033[0m\n' "$1"; exit 1; }
jqr() { jq -r "$1"; }

echo "== 1. Sign in with the seeded platform admin"
TOKEN=$(curl -sf -X POST "$GATEWAY/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${SEED_ADMIN_EMAIL:-admin@payflow.local}\",\"password\":\"${SEED_ADMIN_PASSWORD:?set SEED_ADMIN_PASSWORD}\"}" \
  | jqr '.accessToken')
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || fail "admin login failed"
green "platform admin signed in"

echo "== 2. Exercise platform-admin merchant lifecycle"
ADMIN_MERCHANT=$(curl -sf -X POST "$GATEWAY/api/v1/merchants" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"businessName":"Admin Smoke Co","email":"admin-merchant+'"$(date +%s)"'@test.local",
       "country":"IN","defaultCurrency":"INR"}')
ADMIN_MERCHANT_ID=$(echo "$ADMIN_MERCHANT" | jqr '.id')
[ -n "$ADMIN_MERCHANT_ID" ] && [ "$ADMIN_MERCHANT_ID" != "null" ] || fail "admin merchant creation failed"

curl -sf -X PATCH "$GATEWAY/api/v1/merchants/$ADMIN_MERCHANT_ID/status" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"ACTIVE","reason":"smoke test"}' >/dev/null
green "platform admin created and activated merchant $ADMIN_MERCHANT_ID"

echo "== 3. Self-register a merchant owner safely"
OWNER_EMAIL="owner+$(date +%s)@test.local"
OWNER_PASSWORD="OwnerPassw0rd123"
OWNER=$(curl -sf -X POST "$GATEWAY/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$OWNER_EMAIL\",\"password\":\"$OWNER_PASSWORD\",\"fullName\":\"Smoke Test Owner\",\"businessName\":\"Smoke Test Merchant\"}") \
  || fail "owner registration failed"
MERCHANT_ID=$(echo "$OWNER" | jqr '.merchantId')
[ -n "$MERCHANT_ID" ] && [ "$MERCHANT_ID" != "null" ] || fail "self-registration did not create a merchant"

if ! MERCHANT_LOGIN=$(curl -sf -X POST "$GATEWAY/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$OWNER_EMAIL\",\"password\":\"$OWNER_PASSWORD\"}"); then
  echo "Account is not auto-verified; reading local verification token for smoke test"
  VERIFY_TOKEN=$(docker compose exec -T postgres psql -qtAX -U "${POSTGRES_USER:-payflow}" -d auth_db \
    -c "SELECT payload->'data'->'variables'->>'token' FROM outbox_events
        WHERE event_type='notification.requested'
          AND payload->'data'->>'recipient'='$OWNER_EMAIL'
        ORDER BY created_at DESC LIMIT 1" | tr -d '[:space:]')
  [ -n "$VERIFY_TOKEN" ] || fail "could not read the verification token"
  curl -sf -X POST "$GATEWAY/api/v1/auth/verify-email/$VERIFY_TOKEN" >/dev/null \
    || fail "email verification failed"
  MERCHANT_LOGIN=$(curl -sf -X POST "$GATEWAY/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$OWNER_EMAIL\",\"password\":\"$OWNER_PASSWORD\"}") \
    || fail "merchant owner login failed after verification"
fi

MERCHANT_TOKEN=$(echo "$MERCHANT_LOGIN" | jqr '.accessToken')
[ -n "$MERCHANT_TOKEN" ] && [ "$MERCHANT_TOKEN" != "null" ] || fail "merchant owner login failed"
green "merchant owner signed in for merchant $MERCHANT_ID"

echo "== 4. Issue a TEST secret key"
SECRET_KEY=$(curl -sf -X POST "$GATEWAY/api/v1/merchants/me/api-keys" \
  -H "Authorization: Bearer $MERCHANT_TOKEN" -H 'Content-Type: application/json' \
  -d '{"environment":"TEST","keyType":"SECRET","label":"smoke"}' | jqr '.secret')
[ "${SECRET_KEY:0:8}" = "sk_test_" ] || fail "expected an sk_test_ key"
green "issued ${SECRET_KEY:0:12}..."

echo "== 5. Create a payment"
NOW=$(date +%s)
IDEM_KEY="smoke-$NOW"
PAYMENT_BODY='{"amount":1000.00,"currency":"INR","merchantOrderId":"order-'"$NOW"'","description":"Smoke test payment","paymentMethod":"CARD"}'
PAYMENT=$(curl -sf -X POST "$GATEWAY/api/v1/payments" \
  -H "Authorization: Bearer $SECRET_KEY" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -H 'Content-Type: application/json' \
  -d "$PAYMENT_BODY") || fail "payment creation failed"
PAYMENT_REF=$(echo "$PAYMENT" | jqr '.paymentReference')
PROVIDER_PAYMENT_ID=$(echo "$PAYMENT" | jqr '.providerPaymentId')
[ -n "$PAYMENT_REF" ] && [ "$PAYMENT_REF" != "null" ] || fail "missing payment reference"
[ -n "$PROVIDER_PAYMENT_ID" ] && [ "$PROVIDER_PAYMENT_ID" != "null" ] || fail "missing provider payment id"
green "created $PAYMENT_REF (provider payment $PROVIDER_PAYMENT_ID)"

echo "== 6. Replay the exact SAME request with the SAME Idempotency-Key"
REPLAY=$(curl -sf -X POST "$GATEWAY/api/v1/payments" \
  -H "Authorization: Bearer $SECRET_KEY" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -H 'Content-Type: application/json' \
  -d "$PAYMENT_BODY") || fail "idempotent replay failed"
REPLAY_REF=$(echo "$REPLAY" | jqr '.paymentReference')
[ "$REPLAY_REF" = "$PAYMENT_REF" ] \
  && green "replay returned the original payment - no duplicate charge" \
  || fail "idempotency broken: replay produced $REPLAY_REF"

echo "== 7. Deliver a signed sandbox provider webhook"
TS=$(date +%s)
EVENT_ID="evt_smoke_$TS"
BODY="{\"eventId\":\"$EVENT_ID\",\"type\":\"payment.captured\",\"providerPaymentId\":\"$PROVIDER_PAYMENT_ID\",\"status\":\"CAPTURED\",\"paymentMethod\":\"CARD\",\"cardLast4\":\"4242\"}"
SIG=$(printf '%s.%s' "$TS" "$BODY" | openssl dgst -sha256 -hmac "$SANDBOX_SECRET" -r | cut -d' ' -f1)

curl -sf -X POST "$PROVIDER/internal/webhooks/providers/sandbox" \
  -H "X-PayFlow-Sandbox-Signature: $SIG" \
  -H "X-PayFlow-Timestamp: $TS" \
  -H 'Content-Type: application/json' \
  -d "$BODY" >/dev/null || fail "webhook rejected"
green "webhook accepted"

echo "== 8. Deliver the SAME webhook again"
DUP=$(curl -sf -X POST "$PROVIDER/internal/webhooks/providers/sandbox" \
  -H "X-PayFlow-Sandbox-Signature: $SIG" \
  -H "X-PayFlow-Timestamp: $TS" \
  -H 'Content-Type: application/json' \
  -d "$BODY" | jqr '.status')
[ "$DUP" = "duplicate" ] \
  && green "duplicate provider event deduplicated" \
  || fail "expected duplicate webhook acknowledgement, got '$DUP'"

echo "== 9. Wait for Kafka to update the payment"
STATUS=""
for _ in $(seq 1 20); do
  STATUS=$(curl -sf "$GATEWAY/api/v1/payments/$PAYMENT_REF" \
    -H "Authorization: Bearer $SECRET_KEY" | jqr '.status')
  [ "$STATUS" = "CAPTURED" ] && break
  sleep 1
done
[ "$STATUS" = "CAPTURED" ] && green "payment CAPTURED" || fail "payment stuck in $STATUS"

echo "== 10. Verify the ledger balanced"
ACCOUNTS=$(curl -sf "$LEDGER/internal/ledger/merchants/$MERCHANT_ID/accounts" \
  -H "X-Internal-Token: $INTERNAL_TOKEN")
PAYABLE=$(echo "$ACCOUNTS" | jq -r '.[] | select(.accountType=="MERCHANT_PAYABLE") | .balance')
[ "$(printf '%.2f' "$PAYABLE")" = "980.00" ] \
  && green "merchant payable is 980.00 (1000 gross - 20 fee)" \
  || fail "expected a payable of 980.00, got $PAYABLE"

printf '\n\033[32mAll smoke checks passed.\033[0m\n'
