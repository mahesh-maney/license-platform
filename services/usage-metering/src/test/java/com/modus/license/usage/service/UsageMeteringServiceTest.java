package com.modus.license.usage.service;

import com.modus.license.test.context.TenantContextTestHelper;
import com.modus.license.usage.api.dto.RecordUsageRequest;
import com.modus.license.usage.api.dto.UsageResponse;
import com.modus.license.usage.domain.event.UsageEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsageMeteringService")
class UsageMeteringServiceTest {

    @Mock  UsageEventPublisher publisher;
    @InjectMocks UsageMeteringService service;

    static final String TENANT_ID = TenantContextTestHelper.DEFAULT_TENANT_ID.value().toString();

    @BeforeEach
    void setUp() {
        TenantContextTestHelper.setDefault();
    }

    @AfterEach
    void tearDown() {
        TenantContextTestHelper.clear();
    }

    @Test
    @DisplayName("recordUsage with userId publishes event and returns response")
    void recordUsage_withUserId() {
        UUID userId = UUID.randomUUID();
        RecordUsageRequest req = new RecordUsageRequest(
                userId, "EXPORT_PDF", "API_CALLS", 1.0, "CALLS");

        UsageResponse result = service.recordUsage(req);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.userId()).isEqualTo(userId.toString());
        assertThat(result.featureKey()).isEqualTo("EXPORT_PDF");
        assertThat(result.metricName()).isEqualTo("API_CALLS");
        assertThat(result.quantity()).isEqualTo(1.0);
        assertThat(result.unit()).isEqualTo("CALLS");
        assertThat(result.usageId()).isNotBlank();
        assertThat(result.recordedAt()).isNotNull();

        verify(publisher).publishRecorded(
                eq(TENANT_ID),
                eq(userId.toString()),
                eq("EXPORT_PDF"),
                eq("API_CALLS"),
                eq(1.0),
                eq("CALLS")
        );
    }

    @Test
    @DisplayName("recordUsage with null userId publishes event with null userId")
    void recordUsage_withoutUserId() {
        RecordUsageRequest req = new RecordUsageRequest(
                null, "BATCH_REPORT", "REPORTS_GENERATED", 5.0, "REPORTS");

        UsageResponse result = service.recordUsage(req);

        assertThat(result.userId()).isNull();
        assertThat(result.featureKey()).isEqualTo("BATCH_REPORT");

        verify(publisher).publishRecorded(
                eq(TENANT_ID),
                isNull(),
                eq("BATCH_REPORT"),
                eq("REPORTS_GENERATED"),
                eq(5.0),
                eq("REPORTS")
        );
    }
}
