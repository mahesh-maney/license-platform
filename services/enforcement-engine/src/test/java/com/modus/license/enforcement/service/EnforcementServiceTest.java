package com.modus.license.enforcement.service;

import com.modus.license.enforcement.api.dto.CheckAccessRequest;
import com.modus.license.enforcement.client.SessionGrpcClient;
import com.modus.license.enforcement.config.EnforcementProperties;
import com.modus.license.enforcement.domain.cache.EntitlementCacheService;
import com.modus.license.enforcement.domain.event.EnforcementDecisionPublisher;
import com.modus.license.enforcement.domain.model.CachedEntitlement;
import com.modus.license.events.EventTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnforcementService")
class EnforcementServiceTest {

    @Mock EntitlementCacheService cacheService;
    @Mock SessionGrpcClient       sessionClient;
    @Mock EnforcementDecisionPublisher publisher;

    // 5 s timeout — more than enough for synchronous mocks
    EnforcementProperties props = new EnforcementProperties(false, 5_000L, false);
    EnforcementService service;

    static final String TENANT_ID      = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    static final String USER_ID        = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    static final String ENTITLEMENT_ID = UUID.randomUUID().toString();
    static final String FEATURE_KEY    = "feature.export";

    @BeforeEach
    void setUp() {
        service = new EnforcementService(cacheService, sessionClient, publisher, props);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CheckAccessRequest request() {
        return new CheckAccessRequest(UUID.fromString(USER_ID), FEATURE_KEY, null);
    }

    private CachedEntitlement activeEntitlement(String licenseType, Integer seatLimit) {
        return new CachedEntitlement(
                ENTITLEMENT_ID, TENANT_ID, licenseType, "ACTIVE",
                seatLimit, List.of(FEATURE_KEY, "feature.read"), "PROFESSIONAL", Instant.now());
    }

    // ── checkAccess ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkAccess")
    class CheckAccess {

        @Test
        @DisplayName("ALLOWED for named-user license with matching feature")
        void allowed_namedUser() {
            when(cacheService.get(TENANT_ID)).thenReturn(Mono.just(activeEntitlement("NAMED_USER", null)));

            StepVerifier.create(service.checkAccess(TENANT_ID, request()))
                    .assertNext(resp -> {
                        assertThat(resp.allowed()).isTrue();
                        assertThat(resp.decision()).isEqualTo(EventTypes.Enforcement.ALLOWED);
                        assertThat(resp.denialReason()).isNull();
                        assertThat(resp.entitlementId()).isEqualTo(ENTITLEMENT_ID);
                    })
                    .verifyComplete();

            verify(publisher).publishAllowed(eq(TENANT_ID), eq(USER_ID), eq(FEATURE_KEY),
                    anyString(), anyString(), any(), anyLong(), anyBoolean());
            verify(sessionClient, never()).getActiveSessionCount(any());
        }

        @Test
        @DisplayName("ALLOWED for concurrent license when under seat limit")
        void allowed_concurrent_underLimit() {
            when(cacheService.get(TENANT_ID)).thenReturn(Mono.just(activeEntitlement("CONCURRENT", 5)));
            when(sessionClient.getActiveSessionCount(TENANT_ID)).thenReturn(Mono.just(3L));

            StepVerifier.create(service.checkAccess(TENANT_ID, request()))
                    .assertNext(resp -> {
                        assertThat(resp.allowed()).isTrue();
                        assertThat(resp.decision()).isEqualTo(EventTypes.Enforcement.ALLOWED);
                    })
                    .verifyComplete();

            verify(sessionClient).getActiveSessionCount(TENANT_ID);
            verify(publisher).publishAllowed(eq(TENANT_ID), anyString(), anyString(),
                    anyString(), anyString(), any(), anyLong(), anyBoolean());
        }

        @Test
        @DisplayName("DENIED SESSION_LIMIT_EXCEEDED for concurrent license at seat limit")
        void denied_concurrent_atSeatLimit() {
            when(cacheService.get(TENANT_ID)).thenReturn(Mono.just(activeEntitlement("CONCURRENT", 5)));
            when(sessionClient.getActiveSessionCount(TENANT_ID)).thenReturn(Mono.just(5L));

            StepVerifier.create(service.checkAccess(TENANT_ID, request()))
                    .assertNext(resp -> {
                        assertThat(resp.allowed()).isFalse();
                        assertThat(resp.decision()).isEqualTo(EventTypes.Enforcement.DENIED);
                        assertThat(resp.denialReason()).isEqualTo("SESSION_LIMIT_EXCEEDED");
                    })
                    .verifyComplete();

            verify(publisher).publishDenied(eq(TENANT_ID), anyString(), eq(FEATURE_KEY),
                    anyString(), eq("SESSION_LIMIT_EXCEEDED"), any(), anyLong(), anyBoolean());
        }

        @Test
        @DisplayName("DENIED ENTITLEMENT_NOT_FOUND when cache miss")
        void denied_noEntitlement() {
            when(cacheService.get(TENANT_ID)).thenReturn(Mono.empty());

            StepVerifier.create(service.checkAccess(TENANT_ID, request()))
                    .assertNext(resp -> {
                        assertThat(resp.allowed()).isFalse();
                        assertThat(resp.denialReason()).isEqualTo("ENTITLEMENT_NOT_FOUND");
                    })
                    .verifyComplete();

            verify(sessionClient, never()).getActiveSessionCount(any());
        }

        @Test
        @DisplayName("DENIED ENTITLEMENT_NOT_ACTIVE when status is EXPIRED")
        void denied_notActive() {
            CachedEntitlement expired = new CachedEntitlement(
                    ENTITLEMENT_ID, TENANT_ID, "NAMED_USER", "EXPIRED",
                    null, List.of(FEATURE_KEY), "PROFESSIONAL", Instant.now());
            when(cacheService.get(TENANT_ID)).thenReturn(Mono.just(expired));

            StepVerifier.create(service.checkAccess(TENANT_ID, request()))
                    .assertNext(resp -> {
                        assertThat(resp.allowed()).isFalse();
                        assertThat(resp.denialReason()).isEqualTo("ENTITLEMENT_NOT_ACTIVE");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("DENIED FEATURE_DISABLED when feature key not granted by entitlement")
        void denied_featureDisabled() {
            CachedEntitlement noExport = new CachedEntitlement(
                    ENTITLEMENT_ID, TENANT_ID, "NAMED_USER", "ACTIVE",
                    null, List.of("feature.read"), "STARTER", Instant.now());
            when(cacheService.get(TENANT_ID)).thenReturn(Mono.just(noExport));

            StepVerifier.create(service.checkAccess(TENANT_ID, request()))
                    .assertNext(resp -> {
                        assertThat(resp.allowed()).isFalse();
                        assertThat(resp.denialReason()).isEqualTo("FEATURE_DISABLED");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("DENIED ENTITLEMENT_NOT_FOUND on unexpected error (fail-safe)")
        void denied_onUnexpectedError() {
            when(cacheService.get(TENANT_ID))
                    .thenReturn(Mono.error(new RuntimeException("Redis timeout")));

            StepVerifier.create(service.checkAccess(TENANT_ID, request()))
                    .assertNext(resp -> {
                        assertThat(resp.allowed()).isFalse();
                        assertThat(resp.denialReason()).isEqualTo("ENTITLEMENT_NOT_FOUND");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("skips session check for concurrent license when seatLimit is null")
        void allowed_concurrent_nullSeatLimit() {
            // CONCURRENT with no seat limit behaves like NAMED_USER
            when(cacheService.get(TENANT_ID)).thenReturn(Mono.just(activeEntitlement("CONCURRENT", null)));

            StepVerifier.create(service.checkAccess(TENANT_ID, request()))
                    .assertNext(resp -> assertThat(resp.allowed()).isTrue())
                    .verifyComplete();

            verify(sessionClient, never()).getActiveSessionCount(any());
        }
    }
}
