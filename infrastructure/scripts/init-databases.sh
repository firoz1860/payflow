#!/bin/bash
set -euo pipefail

for db in auth_db merchant_db customer_db payment_db provider_db transaction_db \
          ledger_db refund_db webhook_db settlement_db reconciliation_db risk_db audit_db; do
  echo "Creating database ${db}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${POSTGRES_DB:-postgres}" <<-EOSQL
      SELECT 'CREATE DATABASE ${db}'
      WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${db}')\gexec
      GRANT ALL PRIVILEGES ON DATABASE ${db} TO ${POSTGRES_USER};
EOSQL
done
