#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-$HOME/payflow}"
cd "$APP_DIR"

git fetch origin main
git reset --hard origin/main
sudo docker compose --env-file .env.oracle -f docker-compose.oracle.yml build --parallel
sudo docker compose --env-file .env.oracle -f docker-compose.oracle.yml up -d --remove-orphans

DOMAIN="$(grep '^PAYFLOW_API_DOMAIN=' .env.oracle | cut -d= -f2-)"
echo "Updated PayFlow. API: https://$DOMAIN/api/v1"
