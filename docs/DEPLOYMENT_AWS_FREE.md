# AWS Free-Tier Deployment

PayFlow can run on one ARM-based EC2 instance for a portfolio/demo deployment while preserving the core microservice architecture.

## Current production-style demo topology

```text
Internet
  |
  | HTTPS over IPv6
  v
Caddy
  |-- /                -> React/Vite frontend static files
  |-- /api/*           -> API Gateway :8080
  |-- /actuator/*      -> API Gateway actuator
                         |
                         +-> Auth Service :8081
                         +-> Merchant Service :8082
                         +-> Payment Service :8085
                         +-> Provider Service :8086
                         +-> Ledger Service :8088
                         |
                         +-> PostgreSQL
                         +-> Redis
                         +-> Kafka
```

Only ports 80/443 are exposed publicly. PostgreSQL, Redis, Kafka and service ports stay private.

## EC2 shape

The deployment is designed for:

```text
Instance type: t4g.small
Architecture: ARM64 / Graviton
OS: Ubuntu 24.04
Root disk: 10 GiB gp3
Public ingress: IPv6 only
Management: AWS Systems Manager
```

The bootstrap also configures compressed RAM swap because the full Java + Kafka stack is memory intensive on a 2 GiB instance.

AWS currently documents a t4g.small free trial of up to 750 hours/month through December 31, 2026. Always verify your own Billing/Free Tier page before leaving any AWS resource running.

## Application bootstrap

The instance bootstrap is:

```text
infrastructure/aws/bootstrap-free.sh
```

It:

1. installs Java 21, Maven, Docker, Node/npm and utilities;
2. creates 2 GiB zram swap;
3. generates strong random runtime credentials;
4. builds all Spring Boot services;
5. builds the React frontend with `VITE_API_URL=/api/v1`;
6. runs PostgreSQL, Redis and Kafka in Docker;
7. runs the six Java services under systemd;
8. serves the frontend and API through Caddy;
9. obtains HTTPS automatically using the instance IPv6-based sslip.io hostname.

Generated secrets are stored only on the VM:

```text
/etc/payflow/common.env
/root/payflow-admin-credentials.txt
```

Do not commit those files.

## Updating credentials

After deployment, edit:

```bash
sudo nano /etc/payflow/common.env
```

Then restart affected services:

```bash
sudo systemctl restart auth-service merchant-service payment-service provider-service ledger-service api-gateway
```

If you change PostgreSQL or Redis credentials after persistent data exists, update the data-store configuration carefully instead of changing only the application environment value.

## Health check

```text
https://<ipv6-with-colons-replaced-by-dashes>.sslip.io/actuator/health
```

Expected:

```json
{"status":"UP","groups":["liveness","readiness"]}
```

## Cost-safety notes

This setup deliberately avoids:

- Application Load Balancer
- NAT Gateway
- RDS
- ElastiCache
- MSK
- a permanent Elastic IPv4 address

A temporary Elastic IPv4 can be attached only during initial package/image downloads, then released. The final application is IPv6-only.

The account may contain other AWS resources unrelated to PayFlow; those can still produce charges. Check AWS Billing and Free Tier usage separately.
