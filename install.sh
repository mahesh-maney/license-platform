#!/usr/bin/env bash
# =============================================================================
# Modus License Platform — Zero-Touch Local Installation
#
# Usage:
#   chmod +x install.sh && ./install.sh
#
# What this script does:
#   1. Checks prerequisites (Java 21, Docker, Docker Compose)
#   2. Creates .env from defaults (safe local values)
#   3. Creates required infra directories
#   4. Starts the infrastructure stack (Postgres × 9, Redis, Kafka, Keycloak,
#      Temporal, Prometheus, Grafana, Zipkin, Schema Registry)
#   5. Builds all 14 service images into the local Docker daemon via Jib
#   6. Starts all application services
#   7. Polls every service health endpoint until the platform is fully up
#   8. Prints a summary of URLs
#
# Tear down:
#   docker compose --profile app down -v
# =============================================================================

set -euo pipefail

# ── Colours ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo -e "${CYAN}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}[OK]${RESET}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
fatal()   { echo -e "${RED}[ERROR]${RESET} $*" >&2; exit 1; }
step()    { echo -e "\n${BOLD}==> $*${RESET}"; }

# ── Helpers ───────────────────────────────────────────────────────────────────
require_cmd() {
    command -v "$1" &>/dev/null || fatal "'$1' is not installed or not on PATH. $2"
}

java_version() {
    java -version 2>&1 | grep -oE '[0-9]+' | head -1
}

wait_healthy() {
    local name="$1" url="$2" retries="${3:-60}" interval="${4:-5}"
    local count=0
    info "Waiting for $name to be healthy ($url) ..."
    until curl -sf "$url" &>/dev/null; do
        count=$((count + 1))
        if [[ $count -ge $retries ]]; then
            fatal "$name did not become healthy after $((retries * interval))s. Check logs: docker compose logs $name"
        fi
        sleep "$interval"
    done
    success "$name is healthy"
}

wait_container_healthy() {
    local container="$1" retries="${2:-60}" interval="${3:-5}"
    local count=0
    info "Waiting for container $container ..."
    until [[ "$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null)" == "healthy" ]]; do
        # containers without a healthcheck show no Health key — treat running as ok
        local status
        status=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "missing")
        if [[ "$status" == "exited" ]]; then
            fatal "Container $container exited unexpectedly. Run: docker compose logs $container"
        fi
        count=$((count + 1))
        if [[ $count -ge $retries ]]; then
            fatal "Container $container did not become healthy after $((retries * interval))s."
        fi
        sleep "$interval"
    done
    success "Container $container is healthy"
}

# ── Script root ───────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "\n${BOLD}${CYAN}Modus License Platform — Zero-Touch Installer${RESET}\n"

# =============================================================================
# STEP 1 — Prerequisite checks
# =============================================================================
step "Checking prerequisites"

require_cmd git   "Install git: https://git-scm.com"
require_cmd java  "Install Java 21 (Temurin): https://adoptium.net"
require_cmd docker "Install Docker Desktop: https://www.docker.com/products/docker-desktop"
require_cmd curl  "Install curl via your package manager"

JAVA_VER=$(java_version)
if [[ "$JAVA_VER" -lt 21 ]]; then
    fatal "Java 21+ required (found $JAVA_VER). Install Temurin 21: https://adoptium.net"
fi
success "Java $JAVA_VER"

# Docker daemon running?
docker info &>/dev/null || fatal "Docker daemon is not running. Start Docker Desktop and try again."
success "Docker daemon"

# docker compose v2
if ! docker compose version &>/dev/null; then
    fatal "Docker Compose v2 not found. Upgrade Docker Desktop or install the Compose plugin."
fi
success "Docker Compose $(docker compose version --short)"

# Gradle wrapper
[[ -x "./gradlew" ]] || chmod +x ./gradlew
success "Gradle wrapper"

# =============================================================================
# STEP 2 — Environment file
# =============================================================================
step "Setting up .env"

if [[ -f ".env" ]]; then
    warn ".env already exists — skipping creation. Delete it to reset to defaults."
else
    # Copy from example then patch for pure-local operation
    cp .env.example .env

    # Use local Docker images instead of Azure ACR
    sed -i.bak 's|^CONTAINER_REGISTRY=.*|CONTAINER_REGISTRY=modus-local|' .env

    # Disable Azure App Configuration (not available locally)
    # (application.yml already uses optional: prefix — this is just a signal)
    sed -i.bak 's|^AZURE_APPCONFIGURATION_ENDPOINT=.*|AZURE_APPCONFIGURATION_ENDPOINT=|' .env

    # Set replication factor to 1 for single-broker local Kafka
    sed -i.bak 's|^KAFKA_TOPIC_REPLICATION_FACTOR=.*|KAFKA_TOPIC_REPLICATION_FACTOR=1|' .env

    # Point Mailpit as local SMTP (override.yml already does this for the container;
    # this matters for bootRun dev)
    sed -i.bak 's|^NOTIFICATION_EMAIL_HOST=.*|NOTIFICATION_EMAIL_HOST=localhost|' .env
    sed -i.bak 's|^NOTIFICATION_EMAIL_PORT=.*|NOTIFICATION_EMAIL_PORT=1025|' .env
    sed -i.bak 's|^NOTIFICATION_EMAIL_TLS_ENABLED=.*|NOTIFICATION_EMAIL_TLS_ENABLED=false|' .env

    # gRPC negotiation — plaintext locally
    grep -q '^GRPC_NEGOTIATION_TYPE' .env || echo 'GRPC_NEGOTIATION_TYPE=PLAINTEXT' >> .env

    rm -f .env.bak
    success "Created .env with local defaults"
fi

# Load env so variable substitutions work in this script
set -a; source .env; set +a

export CONTAINER_REGISTRY="${CONTAINER_REGISTRY:-modus-local}"
export APP_VERSION="${APP_VERSION:-1.0.0-SNAPSHOT}"

# =============================================================================
# STEP 3 — Required infra directories
# =============================================================================
step "Creating required infra directories"

mkdir -p infra/keycloak infra/prometheus infra/grafana/provisioning

# Minimal Keycloak realm import — creates the 'modus' realm with
# a platform-admin client and a test user.
if [[ ! -f "infra/keycloak/modus-realm.json" ]]; then
    cat > infra/keycloak/modus-realm.json <<'REALM_JSON'
{
  "realm": "modus",
  "enabled": true,
  "sslRequired": "none",
  "registrationAllowed": false,
  "loginWithEmailAllowed": true,
  "clients": [
    {
      "clientId": "modus-license-api",
      "enabled": true,
      "publicClient": false,
      "bearerOnly": true,
      "protocol": "openid-connect"
    },
    {
      "clientId": "modus-cli",
      "enabled": true,
      "publicClient": true,
      "protocol": "openid-connect",
      "redirectUris": ["*"],
      "directAccessGrantsEnabled": true
    }
  ],
  "roles": {
    "realm": [
      { "name": "PLATFORM_ADMIN" },
      { "name": "TENANT_ADMIN" },
      { "name": "TENANT_USER" }
    ]
  },
  "users": [
    {
      "username": "admin@modus.local",
      "enabled": true,
      "email": "admin@modus.local",
      "credentials": [{ "type": "password", "value": "admin", "temporary": false }],
      "realmRoles": ["PLATFORM_ADMIN"]
    },
    {
      "username": "tenant@modus.local",
      "enabled": true,
      "email": "tenant@modus.local",
      "credentials": [{ "type": "password", "value": "tenant", "temporary": false }],
      "realmRoles": ["TENANT_ADMIN"]
    }
  ],
  "attributes": {
    "frontendUrl": "http://localhost:8180"
  }
}
REALM_JSON
    success "Created infra/keycloak/modus-realm.json"
fi

# Minimal Prometheus config
if [[ ! -f "infra/prometheus/prometheus.yml" ]]; then
    cat > infra/prometheus/prometheus.yml <<'PROM_YML'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'api-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['api-gateway:8080']
  - job_name: 'tenant-management'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['tenant-management:8081']
  - job_name: 'subscription-plan'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['subscription-plan:8082']
  - job_name: 'entitlement'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['entitlement:8083']
  - job_name: 'feature-control'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['feature-control:8084']
  - job_name: 'named-user-license'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['named-user-license:8085']
  - job_name: 'user-management'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['user-management:8086']
  - job_name: 'session'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['session:8087']
  - job_name: 'enforcement-engine'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['enforcement-engine:8088']
  - job_name: 'usage-metering'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['usage-metering:8089']
  - job_name: 'reporting-analytics'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['reporting-analytics:8090']
  - job_name: 'notification'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['notification:8091']
  - job_name: 'audit'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['audit:8092']
  - job_name: 'scheduler-workflow'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['scheduler-workflow:8093']
PROM_YML
    success "Created infra/prometheus/prometheus.yml"
fi

# Grafana datasource provisioning
if [[ ! -f "infra/grafana/provisioning/datasources/prometheus.yml" ]]; then
    mkdir -p infra/grafana/provisioning/datasources
    cat > infra/grafana/provisioning/datasources/prometheus.yml <<'GRAFANA_DS'
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
GRAFANA_DS
    success "Created infra/grafana/provisioning datasources"
fi

# =============================================================================
# STEP 4 — Start infrastructure
# =============================================================================
step "Starting infrastructure containers"

# Pull images first so progress is visible before compose up
info "Pulling infrastructure images (this may take a few minutes on first run)..."
docker compose pull --ignore-pull-failures 2>/dev/null || true

docker compose up -d \
    postgres-tenant postgres-subscription postgres-entitlement \
    postgres-feature postgres-named-license postgres-user \
    postgres-audit postgres-reporting postgres-notification \
    redis zookeeper kafka schema-registry kafka-init \
    keycloak temporal temporal-ui \
    prometheus grafana zipkin \
    kafka-ui mailpit

success "Infrastructure containers started"

# =============================================================================
# STEP 5 — Wait for critical infra to be healthy
# =============================================================================
step "Waiting for infrastructure to be healthy"

INFRA_CONTAINERS=(
    modus-postgres-tenant
    modus-postgres-subscription
    modus-postgres-entitlement
    modus-postgres-feature
    modus-postgres-named-license
    modus-postgres-user
    modus-postgres-audit
    modus-postgres-reporting
    modus-postgres-notification
    modus-redis
    modus-kafka
    modus-schema-registry
)

for c in "${INFRA_CONTAINERS[@]}"; do
    wait_container_healthy "$c" 60 5
done

# Keycloak takes longer
wait_container_healthy "modus-keycloak" 90 5

# kafka-init is a one-shot — wait for it to exit 0
info "Waiting for Kafka topics to be created..."
timeout=120; elapsed=0
until [[ "$(docker inspect --format='{{.State.Status}}' modus-kafka-init 2>/dev/null)" == "exited" ]]; do
    exit_code=$(docker inspect --format='{{.State.ExitCode}}' modus-kafka-init 2>/dev/null || echo "1")
    if [[ "$exit_code" == "1" ]]; then
        fatal "kafka-init failed. Run: docker compose logs kafka-init"
    fi
    elapsed=$((elapsed + 3)); sleep 3
    [[ $elapsed -ge $timeout ]] && fatal "kafka-init timed out after ${timeout}s"
done
success "Kafka topics created"

# =============================================================================
# STEP 6 — Build service images into local Docker daemon
# =============================================================================
step "Building service images with Jib (local Docker daemon)"

info "This compiles all 14 services and packages them as Docker images."
info "Duration: 3-8 minutes depending on machine and Gradle cache state."

# jibDockerBuild targets the local Docker daemon — no registry push required.
# CONTAINER_REGISTRY is set to 'modus-local' so images are named
# modus-local/<service>:1.0.0-SNAPSHOT, matching docker-compose.yml.
./gradlew \
    :services:api-gateway:jibDockerBuild \
    :services:tenant-management:jibDockerBuild \
    :services:subscription-plan:jibDockerBuild \
    :services:entitlement:jibDockerBuild \
    :services:feature-control:jibDockerBuild \
    :services:named-user-license:jibDockerBuild \
    :services:user-management:jibDockerBuild \
    :services:session:jibDockerBuild \
    :services:enforcement-engine:jibDockerBuild \
    :services:usage-metering:jibDockerBuild \
    :services:reporting-analytics:jibDockerBuild \
    :services:notification:jibDockerBuild \
    :services:audit:jibDockerBuild \
    :services:scheduler-workflow:jibDockerBuild \
    --parallel \
    -PCONTAINER_REGISTRY="${CONTAINER_REGISTRY}" \
    -PAPP_VERSION="${APP_VERSION}" \
    2>&1 | grep -v '^Download\|^Progress\|^\s*$' || fatal "Jib build failed. Run ./gradlew jibDockerBuild for details."

success "All service images built"

# =============================================================================
# STEP 7 — Start application services
# =============================================================================
step "Starting application services"

docker compose --profile app up -d

success "Application services started"

# =============================================================================
# STEP 8 — Wait for all service health endpoints
# =============================================================================
step "Waiting for all services to pass health checks"

declare -A SERVICES=(
    ["api-gateway"]="http://localhost:8080/actuator/health"
    ["tenant-management"]="http://localhost:8081/actuator/health"
    ["subscription-plan"]="http://localhost:8082/actuator/health"
    ["entitlement"]="http://localhost:8083/actuator/health"
    ["feature-control"]="http://localhost:8084/actuator/health"
    ["named-user-license"]="http://localhost:8085/actuator/health"
    ["user-management"]="http://localhost:8086/actuator/health"
    ["session"]="http://localhost:8087/actuator/health"
    ["enforcement-engine"]="http://localhost:8088/actuator/health"
    ["usage-metering"]="http://localhost:8089/actuator/health"
    ["reporting-analytics"]="http://localhost:8090/actuator/health"
    ["notification"]="http://localhost:8091/actuator/health"
    ["audit"]="http://localhost:8092/actuator/health"
    ["scheduler-workflow"]="http://localhost:8093/actuator/health"
)

for svc in "${!SERVICES[@]}"; do
    # 90 retries × 5s = 7.5 min per service (JVM cold start)
    wait_healthy "$svc" "${SERVICES[$svc]}" 90 5
done

# =============================================================================
# STEP 9 — Summary
# =============================================================================
echo -e "\n${BOLD}${GREEN}Platform is up and running!${RESET}\n"

echo -e "${BOLD}Application endpoints (all via API Gateway):${RESET}"
echo -e "  Gateway (all APIs)      ${CYAN}http://localhost:8080${RESET}"
echo ""
echo -e "${BOLD}Service health endpoints:${RESET}"
echo -e "  tenant-management       http://localhost:8081/actuator/health"
echo -e "  subscription-plan       http://localhost:8082/actuator/health"
echo -e "  entitlement             http://localhost:8083/actuator/health"
echo -e "  feature-control         http://localhost:8084/actuator/health"
echo -e "  named-user-license      http://localhost:8085/actuator/health"
echo -e "  user-management         http://localhost:8086/actuator/health"
echo -e "  session                 http://localhost:8087/actuator/health"
echo -e "  enforcement-engine      http://localhost:8088/actuator/health"
echo -e "  usage-metering          http://localhost:8089/actuator/health"
echo -e "  reporting-analytics     http://localhost:8090/actuator/health"
echo -e "  notification            http://localhost:8091/actuator/health"
echo -e "  audit                   http://localhost:8092/actuator/health"
echo -e "  scheduler-workflow      http://localhost:8093/actuator/health"
echo ""
echo -e "${BOLD}Infrastructure UIs:${RESET}"
echo -e "  Keycloak admin          ${CYAN}http://localhost:8180${RESET}  admin / ${KEYCLOAK_ADMIN_PASSWORD:-admin}"
echo -e "  Kafka UI                ${CYAN}http://localhost:9080${RESET}"
echo -e "  Schema Registry         http://localhost:8081/subjects"
echo -e "  Mailpit (email UI)      ${CYAN}http://localhost:8025${RESET}"
echo -e "  Temporal UI             ${CYAN}http://localhost:9988${RESET}"
echo -e "  Prometheus              ${CYAN}http://localhost:9090${RESET}"
echo -e "  Grafana                 ${CYAN}http://localhost:3000${RESET}  admin / ${GRAFANA_ADMIN_PASSWORD:-admin}"
echo -e "  Zipkin (tracing)        ${CYAN}http://localhost:9411${RESET}"
echo ""
echo -e "${BOLD}Test credentials (Keycloak realm: modus):${RESET}"
echo -e "  Platform admin          admin@modus.local / admin"
echo -e "  Tenant admin            tenant@modus.local / tenant"
echo ""
echo -e "${BOLD}Get a token:${RESET}"
echo -e "  ${CYAN}curl -s -X POST http://localhost:8180/realms/modus/protocol/openid-connect/token \\"
echo -e "    -d 'client_id=modus-cli&grant_type=password&username=admin@modus.local&password=admin' \\"
echo -e "    | jq -r .access_token${RESET}"
echo ""
echo -e "${BOLD}API docs (Swagger UI):${RESET}"
echo -e "  http://localhost:8080/swagger-ui.html  (via gateway)"
echo ""
echo -e "${BOLD}Tear down:${RESET}"
echo -e "  ${CYAN}docker compose --profile app down -v${RESET}   # stop + delete volumes"
echo -e "  ${CYAN}docker compose --profile app down${RESET}      # stop, keep data"
echo ""
