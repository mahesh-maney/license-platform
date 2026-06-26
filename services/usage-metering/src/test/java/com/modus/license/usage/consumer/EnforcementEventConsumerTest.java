package com.modus.license.usage.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.enforcement.EnforcementDecision;
import com.modus.license.usage.domain.event.UsageEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnforcementEventConsumer")
class EnforcementEventConsumerTest {

    @Mock  UsageEventPublisher publisher;
    @InjectMocks EnforcementEventConsumer consumer;

    static final String TENANT_ID   = UUID.randomUUID().toString();
    static final String USER_ID     = UUID.randomUUID().toString();
    static final String FEATURE_KEY = "ADVANCED_REPORTING";

    private EnforcementDecision decision(String eventType) {
        EventMetadata meta = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(TENANT_ID)
                .setTimestamp(Instant.now())
                .build();

        return EnforcementDecision.newBuilder()
                .setMetadata(meta)
                .setDecisionId(UUID.randomUUID().toString())
                .setTenantId(TENANT_ID)
                .setUserId(USER_ID)
                .setFeatureKey(FEATURE_KEY)
                .setLicenseType("CONCURRENT")
                .setDecision(eventType.equals(EventTypes.Enforcement.ALLOWED) ? "ALLOWED" : "DENIED")
                .setResponseTimeMs(5L)
                .build();
    }

    @Test
    @DisplayName("ACCESS_ALLOWED → publishes USAGE_RECORDED event")
    void allowed_publishesUsage() {
        consumer.onEnforcementDecision(decision(EventTypes.Enforcement.ALLOWED));

        verify(publisher).publishRecorded(
                TENANT_ID, USER_ID, FEATURE_KEY, "API_CALLS", 1.0, "CALLS");
    }

    @Test
    @DisplayName("ACCESS_DENIED → no usage recorded")
    void denied_noUsage() {
        consumer.onEnforcementDecision(decision(EventTypes.Enforcement.DENIED));

        verify(publisher, never()).publishRecorded(
                anyString(), anyString(), anyString(), anyString(), anyDouble(), anyString());
    }
}
