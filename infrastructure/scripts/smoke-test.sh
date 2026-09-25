#!/usr/bin/env bash
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
PROVIDER="${PROVIDER:-http://localhost:8086}"
LEDGER="${LEDGER:-http://localhost:8088}"
INTERNAL_TOKEN="${PAYFLOW_INTERNAL_TOKEN:?set PAYFLOW_INTERNAL_TOKEN}"
SANDBOX_SECRET="${SANDBOX_WEBHOOK_SECRET:-sandbox-webhook-secret}"

green() { printf '\033[32mÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œ %s\033[0m\n' "$1"; }
fail()  { printf '\033[31mÃƒÆ’Ã‚Â¢Ãƒâ€¦Ã¢â‚¬Å“ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â %s\033[0m\n' "$1"; exit 1; }

jqr() { jq -r "$1"; }

echo "== 1. Register and verify an admin user"
ADMIN_EMAIL="admin+$(date +%s)@payflow.local"
curl -sf -X POST "$GATEWAY/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"AdminPassw0rd123\",\"fullName\":\"Ops Admin\"}" \
  >/dev/null || fail "registration failed"
green "registered $ADMIN_EMAIL"

echo "== 2. Create a merchant (admin API)"
TOKEN=$(curl -sf -X POST "$GATEWAY/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${SEED_ADMIN_EMAIL:-admin@payflow.local}\",\"password\":\"${SEED_ADMIN_PASSWORD:?set SEED_ADMIN_PASSWORD}\"}" \
  | jqr '.accessToken')
[ "$TOKEN" != "null" ] || fail "admin login failed"

MERCHANT=$(curl -sf -X POST "$GATEWAY/api/v1/merchants" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"businessName":"Smoke Test Co","email":"merchant+'"$(date +%s)"'@test.local",
       "country":"IN","defaultCurrency":"INR"}')
MERCHANT_ID=$(echo "$MERCHANT" | jqr '.id')
green "merchant $MERCHANT_ID created"

curl -sf -X PATCH "$GATEWAY/api/v1/merchants/$MERCHANT_ID/status" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"ACTIVE","reason":"smoke test"}' >/dev/null
green "merchant activated"

echo "== 3. Create a merchant owner and sign them in"
OWNER_EMAIL="owner+$(date +%s)@test.local"
curl -sf -X POST "$GATEWAY/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$OWNER_EMAIL\",\"password\":\"OwnerPassw0rd123\",
       \"fullName\":\"Merchant Owner\",\"merchantId\":\"$MERCHANT_ID\",
       \"role\":\"MERCHANT_OWNER\"}" >/dev/null || fail "owner registration failed"

VERIFY_TOKEN=$(docker compose exec -T postgres psql -qtAX -U "${POSTGRES_USER:-payflow}" -d auth_db \
  -c "SELECT payload->'data'->'variables'->>'token' FROM outbox_events
      WHERE event_type='notification.requested'
        AND payload->'data'->>'recipient'='$OWNER_EMAIL'
      ORDER BY created_at DESC LIMIT 1" | tr -d '[:space:]')
[ -n "$VERIFY_TOKEN" ] || fail "could not read the verification token"

curl -sf -X POST "$GATEWAY/api/v1/auth/verify-email/$VERIFY_TOKEN" >/dev/null \
  || fail "email verification failed"

MERCHANT_TOKEN=$(curl -sf -X POST "$GATEWAY/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$OWNER_EMAIL\",\"password\":\"OwnerPassw0rd123\"}" | jqr '.accessToken')
[ "$MERCHANT_TOKEN" != "null" ] || fail "merchant owner login failed"
green "merchant owner signed in"

echo "== 4. Issue a TEST secret key"
SECRET_KEY=$(curl -sf -X POST "$GATEWAY/api/v1/merchants/me/api-keys" \
  -H "Authorization: Bearer $MERCHANT_TOKEN" -H 'Content-Type: application/json' \
  -d '{"environment":"TEST","keyType":"SECRET","label":"smoke"}' | jqr '.secret')
[ "${SECRET_KEY:0:8}" = "sk_test_" ] || fail "expected an sk_test_ key"
green "issued ${SECRET_KEY:0:12}..."

echo "== 5. Create a payment"
IDEM_KEY="smoke-$(date +%s)"
PAYMENT=$(curl -sf -X POST "$GATEWAY/api/v1/payments" \
  -H "Authorization: Bearer $SECRET_KEY" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"amount":1000.00,"currency":"INR","merchantOrderId":"order-'"$(date +%s)"'",
       "description":"Smoke test payment","paymentMethod":"CARD"}')
PAYMENT_REF=$(echo "$PAYMENT" | jqr '.paymentReference')
green "created $PAYMENT_REF (status $(echo "$PAYMENT" | jqr '.status'))"

echo "== 6. Replay the SAME request with the SAME Idempotency-Key"
REPLAY=$(curl -sf -X POST "$GATEWAY/api/v1/payments" \
  -H "Authorization: Bearer $SECRET_KEY" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"amount":1000.00,"currency":"INR","merchantOrderId":"order-same",
       "description":"Smoke test payment","paymentMethod":"CARD"}' || true)
REPLAY_REF=$(echo "$REPLAY" | jqr '.paymentReference // empty')
[ "$REPLAY_REF" = "$PAYMENT_REF" ] \
  && green "replay returned the ORIGINAL payment - no double charge" \
  || fail "idempotency broken: replay produced $REPLAY_REF"

echo "== 7. Deliver a signed provider webhook"
PROVIDER_PAYMENT_ID=$(curl -sf "$GATEWAY/api/v1/payments/$PAYMENT_REF" \
  -H "Authorization: Bearer $SECRET_KEY" | jqr '.providerPaymentId')
TS=$(date +%s)
EVENT_ID="evt_smoke_$TS"
BODY="{\"eventId\":\"$EVENT_ID\",\"type\":\"payment.captured\",\"providerPaymentId\":\"$PROVIDER_PAYMENT_ID\",\"status\":\"CAPTURED\",\"paymentMethod\":\"CARD\",\"cardLast4\":\"4242\"}"
SIG=$(printf '%s.%s' "$TS" "$BODY" | openssl dgst -sha256 -hmac "$SANDBOX_SECRET" -r | cut -d' ' -f1)

curl -sf -X POST "$PROVIDER/internal/webhooks/providers/sandbox" \
  -H "X-Internal-Token: $INTERNAL_TOKEN" \
  -H "X-PayFlow-Sandbox-Signature: $SIG" \
  -H "X-PayFlow-Timestamp: $TS" \
  -H 'Content-Type: application/json' \
  -d "$BODY" >/dev/null || fail "webhook rejected"
green "webhook accepted"

echo "== 8. Deliver the SAME webhook again"
DUP=$(curl -sf -X POST "$PROVIDER/internal/webhooks/providers/sandbox" \
  -H "X-Internal-Token: $INTERNAL_TOKEN" \
  -H "X-PayFlow-Sandbox-Signature: $SIG" \
  -H "X-PayFlow-Timestamp: $TS" \
  -H 'Content-Type: application/json' \
  -d "$BODY" | jqr '.status')
[ "$DUP" = "duplicate" ] \
  && green "duplicate event deduplicated" \
  || fail "expected 'duplicate', got '$DUP'"

echo "== 9. Wait for the event to flow through Kafka"
for i in $(seq 1 20); do
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
