# Oracle Cloud Always Free Deployment

This is the zero-monthly-cost deployment path for the current PayFlow architecture.

## Target

Use one **Always Free eligible OCI Ampere A1 Flex** VM in your Oracle home region:

- 2 OCPUs
- 12 GB RAM
- Ubuntu 22.04 or 24.04
- at least the default boot volume
- public IPv4 address

Oracle documents the Always Free A1 allowance as 2 OCPUs and 12 GB RAM total for an Always Free tenancy. Always Free capacity can temporarily be unavailable in a region.

## Network rules

In the OCI subnet/security list (or NSG), allow inbound:

- TCP 22 — SSH
- TCP 80 — HTTP, required for Caddy certificate issuance
- TCP 443 — HTTPS

Do **not** expose PostgreSQL, Redis, Kafka, or Spring service ports.

The Oracle compose file exposes only Caddy on 80/443. Every other component stays on the private Docker network.

## Architecture

```text
Vercel frontend
      |
      | HTTPS
      v
<public-ip>.sslip.io
      |
    Caddy
      |
 API Gateway
      |
 +----+--------+---------+----------+
 Auth Merchant Payment Provider Ledger
              |
        PostgreSQL
        Redis
        Kafka
```

The deployment uses `confluentinc/cp-kafka:7.7.12`, which has ARM64 support.

## Preferred deployment method: GitHub Actions

After creating the VM, add these GitHub repository secrets:

```text
OCI_HOST=<VM public IPv4>
OCI_USER=ubuntu
OCI_SSH_KEY=<contents of the private SSH key used for the VM>
```

Optionally add this GitHub Actions repository variable:

```text
PAYFLOW_VERCEL_ORIGIN=https://your-vercel-domain.vercel.app
```

Then run:

```text
Actions -> Deploy PayFlow to Oracle Free VM -> Run workflow
```

The first run installs Docker, clones `firoz1860/payflow`, generates a private `.env.oracle` with strong random credentials, builds the ARM-compatible images, starts the complete stack, and waits for the public HTTPS health endpoint.

The default API domain is automatically:

```text
https://<PUBLIC-IP>.sslip.io
```

and the frontend API base becomes:

```text
https://<PUBLIC-IP>.sslip.io/api/v1
```

## Manual deployment

On the VM:

```bash
git clone https://github.com/firoz1860/payflow.git
cd payflow
bash infrastructure/oracle/install.sh
PAYFLOW_PUBLIC_IP=<PUBLIC-IP> bash infrastructure/oracle/deploy.sh
```

## Environment file

The first deployment creates:

```text
~/payflow/.env.oracle
```

It contains generated database, Redis, JWT, internal service, API-key pepper, admin, and sandbox secrets. It is not committed to Git.

To change credentials later:

```bash
cd ~/payflow
nano .env.oracle
sudo docker compose --env-file .env.oracle -f docker-compose.oracle.yml up -d
```

Changing some credentials after data already exists (especially PostgreSQL credentials) may require a controlled migration rather than simply editing the file.

## Updating

```bash
cd ~/payflow
bash infrastructure/oracle/update.sh
```

## Backup

```bash
cd ~/payflow
bash infrastructure/oracle/backup.sh
```

Database dumps are written to `~/payflow-backups` and old backups beyond 14 days are removed.

## Vercel

After Oracle is healthy, set the Vercel production variable:

```text
VITE_API_URL=https://<PUBLIC-IP>.sslip.io/api/v1
```

Then redeploy the Vercel frontend.

Do not put JWT secrets, database passwords, Redis credentials, provider secrets, or `PAYFLOW_INTERNAL_TOKEN` into Vercel.
