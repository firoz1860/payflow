#!/bin/bash
set -euxo pipefail
exec > >(tee -a /var/log/payflow-bootstrap.log) 2>&1
export DEBIAN_FRONTEND=noninteractive

for i in $(seq 1 120); do
  if curl -4 -fsS --max-time 3 https://github.com/ >/dev/null 2>&1; then break; fi
  sleep 2
done

apt-get update
apt-get install -y git curl ca-certificates openjdk-21-jdk maven docker.io docker-compose-v2 openssl jq
systemctl enable --now docker

modprobe zram || true
if [ -e /sys/block/zram0/disksize ]; then
  echo 2147483648 > /sys/block/zram0/disksize
  mkswap /dev/zram0 || true
  swapon -p 100 /dev/zram0 || true
fi

cd /opt/payflow
umask 077
mkdir -p /etc/payflow

POSTGRES_PASSWORD="$(openssl rand -hex 20)"
REDIS_PASSWORD="$(openssl rand -hex 20)"
JWT_SECRET="$(openssl rand -base64 72 | tr -d '\n')"
API_KEY_PEPPER="$(openssl rand -hex 32)"
PAYFLOW_INTERNAL_TOKEN="$(openssl rand -hex 32)"
SANDBOX_WEBHOOK_SECRET="$(openssl rand -hex 32)"
ADMIN_PASSWORD="$(openssl rand -base64 24 | tr -d '\n/+=' | head -c 24)"

cat >/etc/payflow/common.env <<EOF
POSTGRES_HOST=127.0.0.1
POSTGRES_PORT=5432
POSTGRES_USER=payflow
POSTGRES_PASSWORD=$POSTGRES_PASSWORD
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=$REDIS_PASSWORD
KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092
JWT_SECRET=$JWT_SECRET
JWT_PUBLIC_SECRET=$JWT_SECRET
API_KEY_PEPPER=$API_KEY_PEPPER
PAYFLOW_INTERNAL_TOKEN=$PAYFLOW_INTERNAL_TOKEN
PAYFLOW_ADMIN_EMAIL=admin@payflow.local
PAYFLOW_ADMIN_PASSWORD=$ADMIN_PASSWORD
PAYFLOW_AUTO_VERIFY_EMAIL=true
PAYFLOW_PROVIDER_DEFAULT=sandbox
SANDBOX_WEBHOOK_SECRET=$SANDBOX_WEBHOOK_SECRET
SANDBOX_AUTO_CAPTURE=true
SANDBOX_AUTO_CAPTURE_DELAY_MS=5000
MERCHANT_SERVICE_URL=http://127.0.0.1:8082
PAYMENT_SERVICE_URL=http://127.0.0.1:8085
PROVIDER_SERVICE_URL=http://127.0.0.1:8086
LEDGER_SERVICE_URL=http://127.0.0.1:8088
AUTH_SERVICE_URL=http://127.0.0.1:8081
RISK_SERVICE_URL=http://127.0.0.1:8093
PAYFLOW_CORS_ORIGINS=https://payflow-k3vcnmcdm-firozs-projects-70dbf044.vercel.app
JAVA_TOOL_OPTIONS=-Xms48m -Xmx144m -XX:MaxMetaspaceSize=96m -XX:+UseSerialGC -Xss384k -XX:TieredStopAtLevel=1 -Dspring.jmx.enabled=false
EOF
chmod 600 /etc/payflow/common.env

cat >/root/payflow-admin-credentials.txt <<EOF
PAYFLOW_ADMIN_EMAIL=admin@payflow.local
PAYFLOW_ADMIN_PASSWORD=$ADMIN_PASSWORD
EOF
chmod 600 /root/payflow-admin-credentials.txt

cat >/opt/payflow/docker-compose.aws-infra.yml <<'EOF'
services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_USER: payflow
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: postgres
    command: ["postgres","-c","shared_buffers=64MB","-c","max_connections=50"]
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./infrastructure/scripts/init-databases.sh:/docker-entrypoint-initdb.d/init-databases.sh:ro
    ports:
      - "127.0.0.1:5432:5432"
    mem_limit: 180m
  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: ["redis-server","--requirepass","${REDIS_PASSWORD}","--appendonly","yes","--maxmemory","64mb","--maxmemory-policy","allkeys-lru"]
    volumes:
      - redis-data:/data
    ports:
      - "127.0.0.1:6379:6379"
    mem_limit: 96m
  kafka:
    image: confluentinc/cp-kafka:7.7.12
    restart: unless-stopped
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:29093
      KAFKA_LISTENERS: INTERNAL://0.0.0.0:29092,HOST://0.0.0.0:9092,CONTROLLER://0.0.0.0:29093
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,HOST://127.0.0.1:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,HOST:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_NUM_PARTITIONS: 3
      KAFKA_HEAP_OPTS: "-Xms128m -Xmx256m"
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
    volumes:
      - kafka-data:/var/lib/kafka/data
    ports:
      - "127.0.0.1:9092:9092"
    mem_limit: 420m
volumes:
  postgres-data:
  redis-data:
  kafka-data:
EOF

export MAVEN_OPTS="-Xms128m -Xmx768m -XX:+UseSerialGC"
mvn -q -DskipTests package
rm -rf /root/.m2/repository || true

mkdir -p /opt/payflow/jars
for svc in api-gateway auth-service merchant-service payment-service provider-service ledger-service; do
  jar="$(find "/opt/payflow/$svc/target" -maxdepth 1 -type f -name '*.jar' ! -name 'original-*' | head -n1)"
  cp "$jar" "/opt/payflow/jars/$svc.jar"
done

set -a
. /etc/payflow/common.env
set +a
docker compose --env-file /etc/payflow/common.env -f /opt/payflow/docker-compose.aws-infra.yml up -d
sleep 20

cat >/usr/local/bin/payflow-service-runner <<'EOF'
#!/bin/bash
set -a
. /etc/payflow/common.env
set +a
svc="$1"
case "$svc" in
  auth-service) export POSTGRES_DB=auth_db ;;
  merchant-service) export POSTGRES_DB=merchant_db ;;
  payment-service) export POSTGRES_DB=payment_db ;;
  provider-service) export POSTGRES_DB=provider_db ;;
  ledger-service) export POSTGRES_DB=ledger_db ;;
esac
exec /usr/bin/java $JAVA_TOOL_OPTIONS -jar "/opt/payflow/jars/$svc.jar"
EOF
chmod 755 /usr/local/bin/payflow-service-runner

for svc in merchant-service provider-service ledger-service payment-service auth-service api-gateway; do
cat >"/etc/systemd/system/$svc.service" <<EOF
[Unit]
Description=PayFlow $svc
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=simple
EnvironmentFile=/etc/payflow/common.env
ExecStart=/usr/local/bin/payflow-service-runner $svc
Restart=always
RestartSec=10
WorkingDirectory=/opt/payflow
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
done

systemctl daemon-reload
systemctl enable merchant-service provider-service ledger-service payment-service auth-service api-gateway
systemctl start merchant-service
sleep 10
systemctl start provider-service ledger-service
sleep 10
systemctl start payment-service
sleep 10
systemctl start auth-service
sleep 10
systemctl start api-gateway

TOKEN="$(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 21600')"
IPV6="$(curl -fsS -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/ipv6)"
HOST="$(printf '%s' "$IPV6" | tr ':' '-').sslip.io"
echo "$HOST" >/etc/payflow/api-hostname

cat >/opt/payflow/Caddyfile.aws <<EOF
$HOST {
  encode zstd gzip
  reverse_proxy 127.0.0.1:8080
  header {
    -Server
    X-Content-Type-Options "nosniff"
    X-Frame-Options "DENY"
    Referrer-Policy "strict-origin-when-cross-origin"
  }
}
EOF

docker rm -f payflow-caddy >/dev/null 2>&1 || true
docker run -d --name payflow-caddy --restart unless-stopped --network host \
  -v /opt/payflow/Caddyfile.aws:/etc/caddy/Caddyfile:ro \
  -v payflow-caddy-data:/data \
  -v payflow-caddy-config:/config \
  caddy:2-alpine

for i in $(seq 1 120); do
  if curl -6 -fsS --max-time 5 "https://$HOST/actuator/health" >/dev/null 2>&1; then
    echo "READY https://$HOST/api/v1" | tee /etc/payflow/deployment-result
    touch /var/lib/payflow-bootstrap-complete
    exit 0
  fi
  sleep 5
done

echo "Bootstrap finished but HTTPS health is not ready" | tee /etc/payflow/deployment-result
exit 1
