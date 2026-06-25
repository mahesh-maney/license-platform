# License Platform

A multi-tenant software licensing platform built on Spring Boot 3.3 and Java 21. It handles tenant onboarding, subscription and plan management, feature-based entitlements, named-user seat allocation, concurrent session enforcement, usage metering, audit, scheduling, notifications, and analytics — all as independent microservices communicating over Kafka (Avro) and gRPC.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Repository Layout](#repository-layout)
- [Platform Modules](#platform-modules)
- [Services](#services)
- [Infrastructure](#infrastructure)
- [Kafka Topics](#kafka-topics)
- [API Gateway](#api-gateway)
- [Getting Started](#getting-started)
- [Running a Service](#running-a-service)
- [Running All Services in Containers](#running-all-services-in-containers)
- [Building Container Images](#building-container-images)
- [Running Tests](#running-tests)
- [Environment Variables](#environment-variables)
- [Production Deployment](#production-deployment)
- [Technology Stack](#technology-stack)

---

## Architecture Overview

```
                          ┌─────────────────────────────┐
   Browser / Client ───▶  │       api-gateway :8080      │
                          │  Spring Cloud Gateway        │
                          │  JWT auth · rate limit       │
                          │  circuit breaker · CORS      │
                          └──────────────┬──────────────┘
                                         │ HTTP (routes)
          ┌──────────────────────────────┼──────────────────────────────┐
          │                              │                              │
   ┌──────▼──────┐  ┌──────────┐  ┌─────▼──────┐  ┌────────────────┐  │
   │  tenant-    │  │subscription│ │entitlement │  │ feature-control│  │
   │ management  │  │  -plan   │  │  :8083     │  │   :8084        │  │
   │  :8081      │  │  :8082   │  └────────────┘  └────────────────┘  │
   └──────┬──────┘  └────┬─────┘                                       │
          │              │                                              │
          │   ┌──────────▼──────────────────────────────────────────┐  │
          │   │                  Apache Kafka                        │  │
          │   │   (Avro + Confluent Schema Registry)                 │  │
          │   └───────────────────┬─────────────────────────────────┘  │
          │                       │                                     │
   ┌──────▼──────┐  ┌─────────────▼──┐  ┌─────────────┐  ┌──────────┐ │
   │  session    │  │ enforcement-   │  │   usage-    │  │  audit   │ │
   │  :8087      │◀─▶  engine :8088  │  │  metering   │  │  :8092   │ │
   │  gRPC :9097 │  │  gRPC :9098    │  │  :8089      │  └──────────┘ │
   └─────────────┘  └────────────────┘  └─────────────┘               │
                                                                        │
   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐  │
   │ reporting-  │  │notification │  │ scheduler-  │  │  named-user│  │
   │ analytics   │  │  :8091      │  │  workflow   │  │  license   │  │
   │ :8090       │  └─────────────┘  │  :8093      │  │  :8085     │  │
   └─────────────┘                   │  Temporal.io│  └────────────┘  │
                                     └─────────────┘                   │
                                                                        │
   ┌─────────────┐                                                      │
   │    user-    │◀─────────────────────────────────────────────────────┘
   │  management │
   │  :8086      │
   └─────────────┘
```

**Key design principles:**

- **Database-per-service** — each service owns its schema; no cross-service DB joins
- **Event-driven** — services publish and consume Avro events over Kafka; no direct HTTP calls for asynchronous flows
- **Synchronous hot-path via gRPC** — enforcement decisions (sub-200 ms SLA) use gRPC between enforcement-engine and session
- **Cache-first enforcement** — Redis holds entitlement data; PostgreSQL is the fallback
- **Durable workflows** — subscription lifecycle (reminders, expiry, renewal) runs on Temporal.io, surviving restarts
- **Defence-in-depth security** — JWT validated at gateway and independently at each service

---

## Repository Layout

```
license-platform/
├── platform/                        # Shared library modules
│   ├── platform-core/               # Domain primitives, error types, web wrappers
│   ├── platform-events/             # Avro schemas — single source of truth for all Kafka events
│   ├── platform-security/           # JWT converter, TenantContextHolder, RBAC helpers
│   ├── platform-audit/              # AuditEvent helpers, audit publisher
│   ├── platform-observability/      # Micrometer / tracing configuration
│   └── platform-test/               # Test utilities, Testcontainers base classes
│
├── services/
│   ├── api-gateway/                 # :8080  Spring Cloud Gateway
│   ├── tenant-management/           # :8081  Tenant CRUD + lifecycle events
│   ├── subscription-plan/           # :8082  Plans, SKUs, subscriptions
│   ├── entitlement/                 # :8083  Feature entitlements per subscription
│   ├── feature-control/             # :8084  Feature flag definitions
│   ├── named-user-license/          # :8085  Named-seat allocation
│   ├── user-management/             # :8086  User CRUD
│   ├── session/                     # :8087  Concurrent session management (gRPC :9097)
│   ├── enforcement-engine/          # :8088  License enforcement decisions (gRPC :9098)
│   ├── usage-metering/              # :8089  Usage event aggregation
│   ├── reporting-analytics/         # :8090  Reporting queries + export
│   ├── notification/                # :8091  Email + webhook notifications
│   ├── audit/                       # :8092  Immutable audit trail
│   └── scheduler-workflow/          # :8093  Temporal.io lifecycle workflows
│
├── infra/
│   ├── keycloak/                    # Realm import JSON for local Keycloak
│   ├── prometheus/                  # prometheus.yml scrape config
│   └── grafana/                     # Dashboard provisioning
│
├── gradle/
│   └── libs.versions.toml           # Version catalog — all dependency versions
├── build.gradle                     # Root build — plugins, subproject config
├── settings.gradle                  # Module declarations
├── docker-compose.yml               # Full stack (infra + application services)
├── docker-compose.override.yml      # Local bootRun overrides (profiles, debug ports)
└── .env.example                     # All environment variable reference values
```

---

## Platform Modules

Shared libraries consumed by every service. They are **not deployed** — they are compiled into each service JAR.

| Module | Purpose |
|--------|---------|
| `platform-core` | `ApiResponse<T>`, `PageResponse<T>`, `ErrorResponse`, `ModusException`, `ErrorCode`, `TenantContext` value objects. No Spring dependency. |
| `platform-events` | Avro schema definitions for every Kafka event. Code-generated with the Avro Gradle plugin. Single source of truth — all services import the same schema classes. |
| `platform-security` | `TenantContextHolder`, `TenantContextFilter` (servlet), `JwtGrantedAuthoritiesConverter` helpers, `ReactiveJwtAuthenticationConverterAdapter` for WebFlux. |
| `platform-audit` | `AuditEvent` builder, `AuditPublisher` interface used by services to emit audit records. |
| `platform-observability` | Common Micrometer and Zipkin autoconfiguration. |
| `platform-test` | `ModusIntegrationTest` base class with Testcontainers PostgreSQL and Kafka, mock schema registry, common `@ActiveProfiles("test")` setup. |

---

## Services

### api-gateway — Port 8080

Spring Cloud Gateway (WebFlux). The single entry point for all external traffic.

- **JWT authentication** via Spring Security OAuth2 Resource Server; roles extracted from the `roles` claim
- **Tenant propagation** — `TenantPropagationFilter` adds `X-Tenant-Id`, `X-User-Id`, `X-Correlation-Id` headers to every proxied request, extracted from the validated JWT
- **Rate limiting** — Redis-backed `RequestRateLimiter` per authenticated principal (100 req/s replenish, 200 burst)
- **Circuit breakers** — Resilience4j `CircuitBreaker` on every route; fallback returns HTTP 503 via `FallbackController`
- **CORS** — configurable via `GATEWAY_CORS_ALLOWED_ORIGINS`
- **Access logging** — method, path, status, latency, correlation ID on every request

Routes:

| Path prefix | Upstream service |
|---|---|
| `/api/v1/tenants/**` | tenant-management :8081 |
| `/api/v1/plans/**`, `/api/v1/subscriptions/**`, `/api/v1/skus/**` | subscription-plan :8082 |
| `/api/v1/entitlements/**` | entitlement :8083 |
| `/api/v1/features/**` | feature-control :8084 |
| `/api/v1/named-licenses/**` | named-user-license :8085 |
| `/api/v1/users/**` | user-management :8086 |
| `/api/v1/enforce/**` | enforcement-engine :8088 |
| `/api/v1/usage/**` | usage-metering :8089 |
| `/api/v1/reports/**`, `/api/v1/dashboards/**` | reporting-analytics :8090 |
| `/api/v1/notifications/**` | notification :8091 |
| `/api/v1/audit/**` | audit :8092 |
| `/api/v1/scheduler/**` | scheduler-workflow :8093 |

### tenant-management — Port 8081

Manages tenants (create, update, suspend, activate, delete). Publishes `TenantEvent` to `license.tenant.events` on every state change. Keycloak realm provisioning is triggered on tenant activation.

### subscription-plan — Port 8082

Manages plan definitions (tiers, features, pricing), SKUs, and tenant subscriptions. On subscription activation, calls `scheduler-workflow` REST API to start the `SubscriptionLifecycleWorkflow` in Temporal. Publishes `SubscriptionEvent` to `license.subscription.events`.

### entitlement — Port 8083

Maintains entitlement records (which features a tenant is licensed for, seat limits, validity dates). Consumes `SubscriptionEvent` to create or revoke entitlements automatically. Publishes `EntitlementEvent` to `license.entitlement.events`.

### feature-control — Port 8084

Defines available features and which plan tiers include them. Acts as the catalogue consumed by entitlement and enforcement.

### named-user-license — Port 8085

Allocates named-user seats within an entitlement. Tracks seat assignments per user per feature.

### user-management — Port 8086

Tenant-scoped user CRUD. Publishes `UserEvent` to `license.user.events`. Enforces user count against entitlement seat limits.

### session — Port 8087 · gRPC 9097

Manages concurrent user sessions stored in Redis. Enforces per-tenant concurrent session limits from the entitlement. Exposes a gRPC API consumed by enforcement-engine on the hot path. Publishes `SessionEvent` to `license.session.events`.

### enforcement-engine — Port 8088 · gRPC 9098

The license enforcement decision engine. Sub-200 ms SLA enforced via:

1. Redis entitlement cache (primary path)
2. PostgreSQL fallback on cache miss (`ENFORCEMENT_CACHE_MISS_FALLBACK_DB=true`)
3. gRPC call to session service for concurrent-session check

Decision result published to `license.enforcement.decisions`. Consumes `EntitlementEvent` to warm and invalidate the cache.

### usage-metering — Port 8089

Consumes `SessionEvent` and `EnforcementDecision` events. Aggregates usage into time-windowed metrics and publishes `UsageEvent` to `license.usage.events`.

### reporting-analytics — Port 8090

Query service for usage metrics, entitlement snapshots, and subscription snapshots. Consumes events from Kafka into its own read-model (PostgreSQL). Supports optional export to Azure Blob Storage (`POST /api/v1/reports/export`). Export is disabled if `BlobServiceClient` is not configured.

### notification — Port 8091

Consumes `TenantEvent` and `SubscriptionEvent` to send transactional emails (via Spring Mail / SMTP) and webhooks. Stores a notification log per tenant. Maintains a `tenant_contacts` table populated from `TenantEvent` to resolve admin email addresses.

### audit — Port 8092

Consumes all Kafka topics and persists an immutable audit trail. Records are hashed (SHA-256) for tamper detection. Supports WORM via Azure Blob Storage immutable storage tiers. Optional SIEM egress (Sentinel / Splunk webhook).

### scheduler-workflow — Port 8093

Hosts Temporal.io workers and REST management API for subscription lifecycle workflows.

**`SubscriptionLifecycleWorkflow`** flow:

1. Subscription activated → `POST /api/v1/scheduler/subscription-lifecycle` called by subscription-plan
2. Workflow waits, sending renewal reminders at 30 / 14 / 7 / 1 day(s) before expiry (via Kafka activity)
3. On renewal signal → `Workflow.newContinueAsNewStub()` restarts the workflow cleanly with the new end date
4. On cancellation signal → workflow terminates, publishes expiry event
5. On expiry timeout → publishes `SUBSCRIPTION_EXPIRED` event, gracePeriodDays applied

Evergreen subscriptions wait indefinitely until a cancellation or renewal signal arrives.

---

## Infrastructure

| Component | Image | Local port | Production |
|---|---|---|---|
| PostgreSQL × 9 | `postgres:16-alpine` | 5432–5440 | Azure Database for PostgreSQL Flexible Server |
| Redis | `redis:7.2-alpine` | 6379 | Azure Cache for Redis |
| Kafka | `confluentinc/cp-kafka:7.6.1` | 9092 | Azure Event Hubs (Kafka protocol) |
| Schema Registry | `confluentinc/cp-schema-registry:7.6.1` | 8081 | Confluent Cloud Schema Registry |
| Keycloak | `quay.io/keycloak/keycloak:24.0.4` | 8180 | Azure AD / Entra ID external IdP |
| Temporal | `temporalio/auto-setup:1.24.2` | 7233 | Temporal Cloud |
| Temporal UI | `temporalio/ui:2.26.2` | 9988 | — |
| Prometheus | `prom/prometheus:v2.52.0` | 9090 | Azure Monitor / Managed Prometheus |
| Grafana | `grafana/grafana:10.4.3` | 3000 | Azure Managed Grafana |
| Zipkin | `openzipkin/zipkin:3.4` | 9411 | Azure Application Insights |
| Kafka UI | `provectuslabs/kafka-ui:v0.7.2` | 9080 | local dev only |
| Mailpit | `axllent/mailpit:v1.19` | 8025 (UI) / 1025 (SMTP) | local dev only |

---

## Kafka Topics

All events use Avro serialisation with Confluent Schema Registry. Schemas are defined in `platform-events`.

| Topic | Key | Publisher(s) | Consumer(s) |
|---|---|---|---|
| `license.tenant.events` | tenantId | tenant-management | notification, reporting-analytics, audit |
| `license.subscription.events` | subscriptionId | subscription-plan, scheduler-workflow | entitlement, notification, reporting-analytics, audit |
| `license.entitlement.events` | entitlementId | entitlement | enforcement-engine (cache warm), reporting-analytics, audit |
| `license.feature.events` | featureId | feature-control | audit |
| `license.session.events` | sessionId | session | usage-metering, audit |
| `license.enforcement.decisions` | tenantId | enforcement-engine | usage-metering, audit |
| `license.named-license.events` | licenseId | named-user-license | audit |
| `license.user.events` | userId | user-management | audit |
| `license.usage.events` | tenantId | usage-metering | reporting-analytics, audit |
| `license.audit.events` | tenantId | all services (via platform-audit) | audit |
| `license.notification.events` | tenantId | notification | audit |
| `license.billing.events` | tenantId | subscription-plan | (future: billing service) |

Default: 12 partitions, replication factor 3 (1 for local dev).

---

## API Gateway

The gateway is the only service exposed externally. All routes require a valid Bearer JWT except:

```
GET  /actuator/health
GET  /actuator/info
GET  /api-docs/**
GET  /swagger-ui.html
GET  /swagger-ui/**
ANY  /fallback/**
```

**Request headers added by the gateway** (visible to all downstream services):

| Header | Value |
|---|---|
| `X-Tenant-Id` | `tenant_id` claim from JWT |
| `X-User-Id` | `sub` claim from JWT |
| `X-Correlation-Id` | Echoed from request, or a new UUID if absent |

---

## Getting Started

### Prerequisites

| Tool | Minimum version |
|---|---|
| Java | 21 |
| Docker + Docker Compose | 25 / 2.24 |
| Gradle Wrapper | included (`./gradlew`) |

### 1. Clone and configure

```bash
git clone <repo-url>
cd license-platform
cp .env.example .env
# Edit .env if needed — defaults work for local dev
```

### 2. Start infrastructure

```bash
docker compose up -d
```

This starts all infrastructure services plus the local dev tools (Kafka UI, Mailpit). Application services are excluded by the `app` profile.

Wait for everything to be healthy (≈ 60 s on first run due to Kafka init and Keycloak startup):

```bash
docker compose ps
```

### 3. Import the Keycloak realm

On first run only. Either place your realm export JSON at `infra/keycloak/` (Keycloak imports `data/import` on `start-dev`) or use the admin console at `http://localhost:8180` (admin / admin).

Create a realm named `license`, a client `license-api`, and a test user with the `TENANT_ADMIN` role and a `tenant_id` claim.

### 4. Start a service

```bash
./gradlew :services:tenant-management:bootRun
```

OpenAPI docs are available at `http://localhost:{port}/swagger-ui.html`.

---

## Running a Service

Each service reads configuration from `src/main/resources/application.yml` with environment variable overrides. All env vars have sensible local defaults pointing at `localhost`.

```bash
# Run one service
./gradlew :services:subscription-plan:bootRun

# Run with a specific profile
SPRING_PROFILES_ACTIVE=debug ./gradlew :services:notification:bootRun

# Run with overridden env var
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 ./gradlew :services:entitlement:bootRun
```

**Service startup order for local development** (dependencies must be running first):

```
Infrastructure (docker compose up)
  └── tenant-management          (no service dependencies)
  └── subscription-plan          (no service dependencies)
  └── entitlement                (no service dependencies)
  └── feature-control            (no service dependencies)
  └── named-user-license         (no service dependencies)
  └── user-management            (no service dependencies)
  └── session                    (no service dependencies)
  └── enforcement-engine         (needs session running for gRPC)
  └── usage-metering             (no service dependencies)
  └── reporting-analytics        (no service dependencies)
  └── notification               (no service dependencies)
  └── audit                      (no service dependencies)
  └── scheduler-workflow         (needs Temporal running)
  └── api-gateway                (all services should be running)
```

---

## Running All Services in Containers

Build all images first (see [Building Container Images](#building-container-images)), then:

```bash
# Full stack — infra + all application services
docker compose --profile app up

# Tail logs for a specific service
docker compose logs -f subscription-plan

# Stop everything
docker compose --profile app down
```

Remote-debug ports are exposed at `508x` when running via the override:

| Service | Debug port |
|---|---|
| api-gateway | 5080 |
| tenant-management | 5081 |
| subscription-plan | 5082 |
| entitlement | 5083 |
| feature-control | 5084 |
| named-user-license | 5085 |
| user-management | 5086 |
| session | 5087 |
| enforcement-engine | 5088 |
| usage-metering | 5089 |
| reporting-analytics | 5090 |
| notification | 5091 |
| audit | 5092 |
| scheduler-workflow | 5093 |

Attach your IDE to `localhost:508x` with transport `dt_socket`.

---

## Building Container Images

Images are built with [Jib](https://github.com/GoogleContainerTools/jib) — no Dockerfile required.

```bash
# Build and push all service images to the configured registry
./gradlew jib

# Build a single service
./gradlew :services:api-gateway:jib

# Build to local Docker daemon (no push, useful for testing)
./gradlew :services:api-gateway:jibDockerBuild
```

The registry is controlled by `CONTAINER_REGISTRY` (default: `myacr.azurecr.io`).
The image tag is controlled by `APP_VERSION` (default: `1.0.0-SNAPSHOT`).

```bash
CONTAINER_REGISTRY=myacr.azurecr.io APP_VERSION=2.1.0 ./gradlew jib
```

Base image: `eclipse-temurin:21-jre-alpine`. JVM flags: `-XX:+UseZGC -XX:+ZGenerational --enable-preview`.

---

## Running Tests

```bash
# All tests across all modules
./gradlew test

# Single module
./gradlew :services:tenant-management:test

# With test logging
./gradlew test --info
```

Tests use Testcontainers (PostgreSQL, Kafka) via `platform-test`. The `application-test.yml` in each service configures:
- Testcontainers PostgreSQL (replaces datasource)
- Embedded Kafka (replaces Kafka broker)
- Mock schema registry URL
- `temporal.enabled=false` (prevents Temporal connection in scheduler-workflow)
- Redis autoconfiguration excluded (no Redis in tests for gateway/enforcement-engine)

No external dependencies are required to run tests.

---

## Environment Variables

Copy `.env.example` to `.env` and adjust values for your environment. All variables have local-dev defaults and the services will start without a `.env` file for most scenarios.

Key variables by category:

| Category | Key variables |
|---|---|
| IAM | `IAM_ISSUER_URI`, `IAM_JWK_SET_URI` |
| Database | `{SERVICE}_DB_HOST`, `{SERVICE}_DB_PASSWORD` |
| Redis | `REDIS_HOST`, `REDIS_PASSWORD`, `REDIS_SSL_ENABLED` |
| Kafka | `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SCHEMA_REGISTRY_URL`, `KAFKA_SECURITY_PROTOCOL` |
| Temporal | `TEMPORAL_SERVICE_ADDRESS`, `TEMPORAL_NAMESPACE` |
| Azure | `AZURE_KEYVAULT_URI`, `AZURE_APPCONFIGURATION_ENDPOINT`, `CONTAINER_REGISTRY` |
| Gateway | `GATEWAY_RATE_LIMIT_REPLENISH_RATE`, `GATEWAY_CORS_ALLOWED_ORIGINS` |
| Notification | `NOTIFICATION_EMAIL_HOST`, `NOTIFICATION_EMAIL_PASSWORD` |
| Observability | `TRACING_ENABLED`, `TRACING_ENDPOINT`, `METRICS_PROMETHEUS_ENABLED` |

See `.env.example` for the full reference with comments.

---

## Production Deployment

The platform is designed for Azure Kubernetes Service (AKS).

| Component | Azure service |
|---|---|
| Container registry | Azure Container Registry |
| Kubernetes | Azure Kubernetes Service (AKS) |
| PostgreSQL | Azure Database for PostgreSQL Flexible Server |
| Redis | Azure Cache for Redis |
| Kafka | Azure Event Hubs (Kafka protocol) |
| Schema Registry | Confluent Cloud Schema Registry |
| IAM | Azure AD / Entra ID (external IdP replacing Keycloak) |
| Workflow | Temporal Cloud |
| Secrets | Azure Key Vault (via Spring Cloud Azure) |
| Centralised config | Azure App Configuration |
| Blob / WORM | Azure Blob Storage (immutable tiers for audit) |
| Metrics | Azure Monitor + Managed Prometheus + Managed Grafana |
| Tracing | Azure Application Insights |
| Service mesh / mTLS | Istio on AKS |

**Configuration in production** — each service sets:

```yaml
spring:
  config:
    import: "optional:azure-app-configuration:"
```

Azure App Configuration loads all properties centrally. Secrets (passwords, keys) are referenced as Key Vault references in App Configuration and resolved transparently at startup via the Spring Cloud Azure integration.

**CI/CD pipeline** (example GitHub Actions / Azure Pipelines):

```
1. ./gradlew test
2. ./gradlew jib  (push to ACR with APP_VERSION=${{ github.sha }})
3. helm upgrade --install license-platform ./infra/helm \
     --set image.tag=${{ github.sha }} \
     --set environment=prod
```

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (preview features enabled) |
| Framework | Spring Boot 3.3.6 |
| Reactive gateway | Spring Cloud Gateway (WebFlux) |
| Messaging | Apache Kafka + Avro + Confluent Schema Registry |
| Schema evolution | Avro (backward compatible) |
| RPC | gRPC (session ↔ enforcement hot path) |
| Workflow engine | Temporal.io 1.24.2 |
| Persistence | PostgreSQL 16 via Spring Data JPA + Flyway |
| Cache / session store | Redis 7.2 via Spring Data Redis (reactive) |
| Security | Spring Security OAuth2 Resource Server (JWT/JWKS) |
| Circuit breaker | Resilience4j |
| Metrics | Micrometer + Prometheus |
| Tracing | Micrometer Tracing + Zipkin (Brave) |
| API docs | SpringDoc OpenAPI 3 |
| Mapping | MapStruct |
| Build | Gradle 8 with version catalog (`libs.versions.toml`) |
| Containerisation | Jib (no Dockerfile) |
| Local IAM | Keycloak 24 |
| Testing | JUnit 5 + Testcontainers + Mockito + AssertJ |
