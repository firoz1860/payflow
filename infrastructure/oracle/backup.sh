#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-$HOME/payflow}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/payflow-backups}"
mkdir -p "$BACKUP_DIR"
cd "$APP_DIR"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
POSTGRES_USER="$(grep '^POSTGRES_USER=' .env.oracle | cut -d= -f2-)"

sudo docker compose --env-file .env.oracle -f docker-compose.oracle.yml exec -T postgres   pg_dumpall -U "$POSTGRES_USER" | gzip > "$BACKUP_DIR/postgres-$STAMP.sql.gz"

find "$BACKUP_DIR" -type f -name 'postgres-*.sql.gz' -mtime +14 -delete
echo "Backup written to $BACKUP_DIR/postgres-$STAMP.sql.gz"
