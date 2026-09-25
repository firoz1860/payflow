#!/usr/bin/env bash
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/firoz1860/payflow.git}"
APP_DIR="${APP_DIR:-$HOME/payflow}"
BRANCH="${BRANCH:-main}"
PUBLIC_IP="${PAYFLOW_PUBLIC_IP:-}"
VERCEL_ORIGIN="${PAYFLOW_VERCEL_ORIGIN:-https://payflow-firozs-projects-70dbf044.vercel.app}"

if [ -z "$PUBLIC_IP" ]; then
  PUBLIC_IP="$(curl -fsS https://api.ipify.org || true)"
fi
if [ -z "$PUBLIC_IP" ]; then
  echo "Could not determine the public IP. Set PAYFLOW_PUBLIC_IP and retry." >&2
  exit 1
fi

API_DOMAIN="${PAYFLOW_API_DOMAIN:-$PUBLIC_IP.sslip.io}"

if [ ! -d "$APP_DIR/.git" ]; then
  git clone --depth 1 --branch "$BRANCH" "$REPO_URL" "$APP_DIR"
else
  git -C "$APP_DIR" fetch origin "$BRANCH"
  git -C "$APP_DIR" checkout "$BRANCH"
  git -C "$APP_DIR" reset --hard "origin/$BRANCH"
fi

cd "$APP_DIR"

if [ ! -f .env.oracle ]; then
  umask 077
  cat > .env.oracle <<EOF
POSTGRES_USER=payflow
POSTGRES_PASSWORD=$(openssl rand -hex 24)
REDIS_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -base64 72 | tr -d '\n')
API_KEY_PEPPER=$(openssl rand -hex 32)
PAYFLOW_INTERNAL_TOKEN=$(openssl rand -hex 32)

PAYFLOW_ADMIN_EMAIL=admin@payflow.local
PAYFLOW_ADMIN_PASSWORD=$(openssl rand -base64 24 | tr -d '\n/+=' | head -c 24)
PAYFLOW_AUTO_VERIFY_EMAIL=true

PAYFLOW_PROVIDER_DEFAULT=sandbox
SANDBOX_WEBHOOK_SECRET=$(openssl rand -hex 32)
SANDBOX_AUTO_CAPTURE=true
SANDBOX_AUTO_CAPTURE_DELAY_MS=5000

PAYFLOW_CORS_ORIGINS=$VERCEL_ORIGIN
PAYFLOW_API_DOMAIN=$API_DOMAIN

RAZORPAY_KEY_ID=
RAZORPAY_KEY_SECRET=
RAZORPAY_WEBHOOK_SECRET=
STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SECRET=
EOF
  echo "Generated secure .env.oracle at $APP_DIR/.env.oracle"
fi

sudo docker compose --env-file .env.oracle -f docker-compose.oracle.yml pull postgres redis kafka caddy
sudo docker compose --env-file .env.oracle -f docker-compose.oracle.yml build --parallel
sudo docker compose --env-file .env.oracle -f docker-compose.oracle.yml up -d --remove-orphans

echo
echo "Waiting for PayFlow API Gateway..."
for i in $(seq 1 60); do
  if curl -fsS "https://$API_DOMAIN/actuator/health" >/dev/null 2>&1; then
    echo "PayFlow is healthy."
    echo "API base: https://$API_DOMAIN/api/v1"
    exit 0
  fi
  sleep 5
done

echo "Deployment started but health did not become ready in time." >&2
sudo docker compose --env-file .env.oracle -f docker-compose.oracle.yml ps
exit 1
