# CLAUDE.md — Modus License Platform

This file gives Claude Code the context needed to work accurately in this codebase. Read it before writing or modifying any code.

---

## Project Identity

**Root project name**: `modus-license-platform` (see `settings.gradle`)
**Group**: `com.modus.license`
**Java version**: 21 with `--enable-preview` enabled everywhere
**Framework**: Spring Boot 3.3.6, Spring Cloud 2023.0.3
**Build**: Gradle 8 with a version catalog at `gradle/libs.versions.toml`

---

## Module Structure

```
platform/
  platform-core          Pure Java domain primitives. NO Spring, NO JPA.
  platform-events        Avro schemas + generated classes. Single source of truth for all Kafka events.
  platform-security      JWT helpers, TenantContextHolder, servlet + WebFlux security base config.
  platform-audit         AuditEvent builder and publisher interface.
  platform-observability Micrometer + tracing autoconfiguration.
  platform-test          IntegrationTest base class, Testcontainers config.

services/
  api-gateway            :8080  Spring Cloud Gateway (WebFlux, reactive)
  tenant-management      :8081  Servlet stack + JPA + Kafka
  subscription-plan      :8082  Servlet stack + JPA + Kafka
  entitlement            :8083  Servlet stack + JPA + Kafka
  feature-control        :8084  Servlet stack + JPA + Kafka
  named-user-license     :8085  Servlet stack + JPA + Kafka
  user-management        :8086  Servlet stack + JPA + Kafka
  session                :8087  WebFlux + Redis + Kafka + gRPC server (:9097)
  enforcement-engine     :8088  WebFlux + Redis + Kafka + gRPC client+server (:9098)
  usage-metering         :8089  Servlet stack + Kafka only (no DB)
  reporting-analytics    :8090  Servlet stack + JPA + Kafka
  notification           :8091  Servlet stack + JPA + Kafka + SMTP
  audit                  :8092  Servlet stack + JPA + Kafka
  scheduler-workflow     :8093  Servlet stack + Kafka + Temporal.io (no DB)
```

---

## Critical Patterns — Always Follow These

### 1. JpaBaseEntity — declared per service, not in platform-core

Every JPA service declares its **own** `JpaBaseEntity` in `domain/entity/JpaBaseEntity.java`. Do NOT move it to platform-core — platform-core must remain free of JPA dependencies.

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class JpaBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)   // ← always UUID, never SEQUENCE or IDENTITY
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 36)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "last_modified_by", length = 36)
    private String lastModifiedBy;

    @Version
    @Column(nullable = false)
    private Long version;
}
```

### 2. Domain-ID-as-PK entities (snapshot / read-model entities)

Some entities use the **domain event's UUID as the primary key** (no `@GeneratedValue`):

```java
@Entity
public class EntitlementSnapshotEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID entitlementId;   // ← from EntitlementEvent, NOT generated
    // no @GeneratedValue
}
```

Use this pattern for:
- Snapshot / read-model entities populated from Kafka events
- Entities that represent a 1:1 mapping to a domain record in another service
- `TenantContactEntity` in notification (keyed by tenantId)
- `EntitlementSnapshotEntity`, `SubscriptionSnapshotEntity` in reporting-analytics

### 3. KafkaConsumerConfig — ALWAYS set `specific.avro.reader=true`

Every service that consumes Avro events MUST have a `KafkaConsumerConfig` that sets this property, or you get `GenericData.Record` instead of the typed Avro class:

```java
@Configuration
public class KafkaConsumerConfig {
    @Bean
    public ConsumerFactory<String, Object> consumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true); // ← mandatory
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
```

### 4. Avro timestamp fields → `java.time.Instant`

Avro schema uses `"logicalType": "timestamp-millis"` on `long` fields. The generated class stores them as `long` (epoch millis). When mapping to entities or DTOs, **always convert explicitly**:

```java
// In MapStruct mapper or consumer:
Instant.ofEpochMilli(event.getCreatedAt())   // Avro long → Instant
event.getCreatedAt().toEpochMilli()          // Instant → Avro long
```

Never treat Avro `long` timestamp fields as plain numbers.

### 5. SecurityConfig — servlet vs WebFlux

**Servlet services** (tenant-management, subscription-plan, entitlement, feature-control, named-user-license, user-management, notification, audit, reporting-analytics, usage-metering, scheduler-workflow):

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter gac = new JwtGrantedAuthoritiesConverter();
        gac.setAuthoritiesClaimName("roles");   // ← use setters — class is final
        gac.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(gac);

        return http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
            .build();
    }
}
```

**WebFlux services** (api-gateway, enforcement-engine, session):

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(auth -> auth
                .pathMatchers("/actuator/health").permitAll()
                .anyExchange().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthConverter())))
            .build();
    }

    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter gac = new JwtGrantedAuthoritiesConverter();
        gac.setAuthoritiesClaimName("roles");
        gac.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(gac);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);  // ← WebFlux wrapper
    }
}
```

### 6. JpaAuditingConfig — fallback to "system" for Kafka consumer writes

Kafka consumers run without a user/tenant context. The `AuditorAware` bean must fall back to `"system"` so `@CreatedBy` / `@LastModifiedBy` fields are never null:

```java
@Bean
public AuditorAware<String> auditorProvider() {
    return () -> Optional.ofNullable(TenantContextHolder.get())
            .map(ctx -> ctx.userId().toString())
            .or(() -> Optional.of("system"));   // ← mandatory fallback
}
```

### 7. MapStruct — `List<String>` ↔ comma-separated TEXT

Feature keys are stored in PostgreSQL as comma-separated TEXT (not as an array). Use a `default` method in the mapper:

```java
@Mapper(componentModel = "spring")
public interface ReportingMapper {
    @Mapping(target = "featureKeys", expression = "java(splitFeatureKeys(entity.getFeatureKeys()))")
    EntitlementSnapshotResponse toResponse(EntitlementSnapshotEntity entity);

    default List<String> splitFeatureKeys(String s) {
        if (s == null || s.isBlank()) return Collections.emptyList();
        return Arrays.asList(s.split(","));
    }
}
```

Reverse: `String.join(",", event.getFeatureKeys().stream().map(Object::toString).toList())`

### 8. Optional dependencies with `@ConditionalOnBean`

Services with optional integrations (Azure Blob, Temporal) use `@ConditionalOnBean` so tests and minimal deployments work without them:

```java
// ReportExportService only exists when BlobServiceClient is in context
@Service
@ConditionalOnBean(BlobServiceClient.class)
public class ReportExportService { ... }

// Inject as Optional — never @Autowired directly
@Service
public class ReportingService {
    private final Optional<ReportExportService> exportService;
    // constructor injection of Optional<ReportExportService>
}
```

### 9. Temporal — `@ConditionalOnProperty` + `@ConditionalOnBean`

```java
// All Temporal infrastructure gated by this property:
@Configuration
@ConditionalOnProperty(name = "temporal.enabled", havingValue = "true", matchIfMissing = true)
public class TemporalConfig { ... }

// Components that need WorkflowClient (sweepers, controller) use @ConditionalOnBean:
@Component
@ConditionalOnBean(WorkflowClient.class)
public class RenewalSweeper { ... }
```

In `application-test.yml` for scheduler-workflow: `temporal.enabled: false`

### 10. Temporal — workflow vs activity registration

```java
// Workflows registered by CLASS (Temporal creates a new instance per execution):
worker.registerWorkflowImplementationTypes(SubscriptionLifecycleWorkflowImpl.class);

// Activities registered as INSTANCE (Spring-managed singleton, injected dependencies):
worker.registerActivitiesImplementations(activities);  // Spring @Component instance
```

Never register a workflow as an instance or an activity by class.

---

## Anti-Patterns — Never Do These

### `JwtGrantedAuthoritiesConverter` is final
Do **NOT** subclass or use anonymous subclasses. Use the setter methods:
```java
// WRONG:
new JwtGrantedAuthoritiesConverter() {{ setAuthoritiesClaimName("roles"); }};

// RIGHT:
JwtGrantedAuthoritiesConverter gac = new JwtGrantedAuthoritiesConverter();
gac.setAuthoritiesClaimName("roles");
gac.setAuthorityPrefix("");
```

### `ModusException` is not abstract
Instantiate directly:
```java
// WRONG: new ModusException(ErrorCode.X) {}  (anonymous subclass)
// RIGHT: new ModusException(ErrorCode.X, "message")
throw new ModusException(ErrorCode.NOT_FOUND, "Tenant not found: " + id);
```

### Do not put `annotationProcessor` in individual service `build.gradle` files
Lombok and MapStruct `annotationProcessor` entries are declared once in the root `build.gradle` `subprojects` block. Adding them again in a service's `build.gradle` causes duplicate processing errors.

### Do not use `@GeneratedValue` on domain-ID PK entities
Snapshot entities (populated from Kafka events) use the event's UUID as PK. There is no `@GeneratedValue` annotation. Hibernate will throw on persist if you accidentally add it.

### Do not share a PostgreSQL instance across services
The platform follows database-per-service strictly. Never point two services at the same DB URL, even in tests.

### Do not use `spring-boot-starter-web` in WebFlux services
Session and enforcement-engine are fully reactive. Adding the servlet stack creates a classpath conflict. Check the service's `build.gradle` — if it has `libs.bundles.webflux.service` or `libs.spring.boot.webflux`, do not add `libs.spring.boot.web`.

---

## Build System

All dependency versions are in `gradle/libs.versions.toml`. **Never hardcode a version string** in a service's `build.gradle` — always reference the catalog:

```groovy
implementation libs.spring.boot.web          // single dependency
implementation libs.bundles.database.jpa     // bundle
```

Key bundles:
| Bundle | Contents |
|---|---|
| `libs.bundles.web.service` | web, validation, actuator, oauth2-resource-server, prometheus, springdoc-webmvc |
| `libs.bundles.webflux.service` | webflux, validation, actuator, oauth2-resource-server, prometheus, springdoc-webflux |
| `libs.bundles.kafka` | spring-kafka, kafka-avro-serializer, avro |
| `libs.bundles.database.jpa` | spring-data-jpa, postgresql-jdbc, flyway-core, flyway-postgresql |
| `libs.bundles.redis` | spring-boot-data-redis-reactive, lettuce-core |
| `libs.bundles.azure` | spring-cloud-azure-starter, spring-cloud-azure-keyvault, azure-identity |
| `libs.bundles.grpc.server` | grpc-spring-boot-starter, grpc-stub, grpc-protobuf, protobuf-java |
| `libs.bundles.grpc.client` | grpc-spring-boot-client, grpc-stub, grpc-protobuf, protobuf-java |
| `libs.bundles.testing` | spring-boot-test, reactor-test, mockito-core, assertj-core |

---

## application.yml Conventions

Every service `application.yml` follows this structure (in this order):

```yaml
server:
  port: ${PORT_SERVICE_NAME:XXXX}

spring:
  application:
    name: service-name
  config:
    import: "optional:azure-app-configuration:"

  # datasource (if JPA service)
  # jpa + flyway (if JPA service)
  # kafka (if Kafka service)
  # data.redis (if Redis service)
  # security.oauth2.resourceserver.jwt

# custom topic keys block (e.g. kafka.topics.*)
# service-specific config block (e.g. notification.*, scheduler.*)
# grpc (if gRPC service)

# azure.keyvault.secret.endpoint
# management.endpoints / metrics / tracing
# logging.level
# springdoc (if HTTP service)
```

All runtime values use `${ENV_VAR:default}` notation. The default must work for local development (`localhost`, `changeme`, `http://localhost:...`).

---

## application-test.yml Conventions

Each service has `src/test/resources/application-test.yml`. Standard structure:

```yaml
spring:
  config:
    import: ""   # disable azure-app-configuration

  datasource:    # JPA services: Testcontainers JDBC URL
    url: jdbc:tc:postgresql:16-alpine:///modus_X_test
    username: test
    password: test

  jpa:
    hibernate:
      ddl-auto: none   # Flyway owns the schema

  flyway:
    enabled: true
    baseline-on-migrate: false

  kafka:
    bootstrap-servers: ${TEST_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    properties:
      schema.registry.url: mock://test   # ← mock schema registry — no real SR needed

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri:  http://localhost:9090/realms/modus   # test-only issuer

management:
  tracing:
    enabled: false

# For api-gateway / enforcement-engine (no Redis in tests):
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
      - org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration

# For scheduler-workflow:
temporal:
  enabled: false
```

---

## Kafka Event Patterns

### Publishing

Events are published via a dedicated `*EventPublisher` component in each service:

```java
@Component
public class TenantEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(TenantEvent event) {
        kafkaTemplate.send(topics.getTenantEvents(), event.getTenantId().toString(), event);
    }
}
```

The **Kafka message key is always the primary domain ID** (tenantId, subscriptionId, etc.) as a `String`. This ensures partition ordering per entity.

### Consuming

Consumer classes follow this structure:

```java
@Component
public class TenantEventConsumer {

    @KafkaListener(topics = "${kafka.topics.tenant-events}")
    @Transactional
    public void onEvent(TenantEvent event) {
        String eventType = event.getEventType().toString();
        switch (eventType) {
            case EventTypes.Tenant.CREATED    -> handleCreated(event);
            case EventTypes.Tenant.ACTIVATED  -> handleActivated(event);
            // ...
            default -> log.debug("Skipping unhandled event type: {}", eventType);
        }
    }
}
```

Always use `switch` with `default -> log.debug(...)` for unhandled event types — never throw on unknown types.

### Event type constants

All event type string constants live in `platform-core` under `EventTypes`:

```java
EventTypes.Tenant.CREATED
EventTypes.Subscription.ACTIVATED
EventTypes.Entitlement.REVOKED
// etc.
```

Never use string literals for event types in business logic. Always reference `EventTypes.*`.

---

## Package Structure per Service

```
com.modus.license.{service}/
  {Service}Application.java
  config/
    SecurityConfig.java
    JpaAuditingConfig.java      (JPA services)
    KafkaConsumerConfig.java    (Kafka consumer services)
    {Service}Properties.java    (if @ConfigurationProperties used)
  domain/
    entity/
      JpaBaseEntity.java        (local copy — not from platform)
      {Entity}.java
    repository/
      {Entity}Repository.java
    event/
      {Domain}EventPublisher.java
  consumer/
    {Topic}EventConsumer.java
  service/
    {Domain}Service.java
  api/
    {Domain}Controller.java
    GlobalExceptionHandler.java
    dto/
      {Entity}Request.java
      {Entity}Response.java
    mapper/
      {Domain}Mapper.java
  db/
    migration/
      V1__create_{service}_tables.sql
      V2__...sql
```

---

## GlobalExceptionHandler

Every service has a `GlobalExceptionHandler`. Servlet services use `@RestControllerAdvice` + `@ExceptionHandler`. The standard handlers are:

```java
@ExceptionHandler(ModusException.class)
// → ResponseEntity with ErrorResponse, mapped HTTP status from ErrorCode

@ExceptionHandler(MethodArgumentNotValidException.class)
// → 422 UNPROCESSABLE_ENTITY with field validation errors

@ExceptionHandler(Exception.class)
// → 500 with ErrorResponse.internal("Unexpected error")
```

Add domain-specific handlers (`NotFoundException`, `ConflictException`, `IllegalArgumentException`) only when needed by the service.

---

## Database Migration

All schema changes go in `src/main/resources/db/migration/` as Flyway versioned scripts:
- `V1__create_{service}_tables.sql` — initial schema
- `V2__add_{column}.sql` — additive changes only

SQL conventions:
- Table names: `snake_case`, plural
- Column names: `snake_case`
- UUIDs stored as `UUID` type (PostgreSQL native)
- Timestamps stored as `TIMESTAMPTZ`
- Comma-separated feature keys stored as `TEXT` (not `TEXT[]`)
- Always add indexes for: tenant_id lookups, status filters, created_at ordering

---

## Adding a New Service — Checklist

When adding a new microservice:

1. Add `include 'services:my-service'` to `settings.gradle`
2. Create `services/my-service/build.gradle` — use catalog bundles, no hardcoded versions
3. Add Jib configuration in `build.gradle`
4. Create `{Service}Application.java` with appropriate annotations
5. Declare a local `JpaBaseEntity.java` (if JPA service)
6. Create `config/SecurityConfig.java` — servlet OR WebFlux pattern (see above)
7. Create `config/JpaAuditingConfig.java` with `"system"` fallback (if JPA service)
8. Create `config/KafkaConsumerConfig.java` with `SPECIFIC_AVRO_READER_CONFIG=true` (if Kafka consumer)
9. Write `src/main/resources/application.yml` following the established structure
10. Write `src/test/resources/application-test.yml` with `mock://test` schema registry
11. Add the service to `docker-compose.yml` (application services section)
12. Add `profiles: ["app"]` override in `docker-compose.override.yml`
13. Add routes in `services/api-gateway/src/main/resources/application.yml`
14. Add PostgreSQL service and volume in `docker-compose.yml` (if JPA service)
15. Add all new env vars to `.env.example` with comments

---

## Local Development URLs

| Service | URL |
|---|---|
| api-gateway | http://localhost:8080 |
| Keycloak admin | http://localhost:8180 |
| Kafka UI | http://localhost:9080 |
| Mailpit (email UI) | http://localhost:8025 |
| Temporal UI | http://localhost:9988 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |
| Zipkin | http://localhost:9411 |
| Schema Registry | http://localhost:8081 |

---

## Key Files Quick Reference

| File | Purpose |
|---|---|
| `gradle/libs.versions.toml` | All dependency versions and bundles — edit here, nowhere else |
| `build.gradle` | Root build: Java 21, `--enable-preview`, subproject `annotationProcessor` declarations |
| `settings.gradle` | Module declarations + repository configuration (Maven Central + Confluent) |
| `docker-compose.yml` | Full stack: infra + application services |
| `docker-compose.override.yml` | Local dev: gates app services behind `--profile app`, adds Kafka UI + Mailpit |
| `.env.example` | Reference for all env vars — copy to `.env` for local dev |
| `services/api-gateway/src/main/resources/application.yml` | Gateway route table — update when adding/changing service paths |
| `platform/platform-events/src/main/avro/` | Avro schema files — single source of truth for all Kafka event contracts |

---

## Things That Are Intentionally Absent

- **No shared database** — each service has its own PostgreSQL instance
- **No service discovery (Eureka/Consul)** — service hosts are configured via env vars; API gateway handles routing
- **No saga orchestrator** — services are choreography-based (react to Kafka events), except subscription lifecycle which uses Temporal
- **No Dockerfile** — images are built with Jib (`./gradlew jib`)
- **No Spring Cloud Config Server** — centralised config uses Azure App Configuration in production; env vars for local dev
- **No `@Transactional` on Kafka consumers that do not touch the DB** — only add `@Transactional` when the consumer writes to PostgreSQL
