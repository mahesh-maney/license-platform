package com.modus.license.reporting.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.modus.license.reporting.api.dto.EntitlementSnapshotResponse;
import com.modus.license.reporting.api.dto.ExportRequest;
import com.modus.license.reporting.api.dto.SubscriptionSnapshotResponse;
import com.modus.license.reporting.api.dto.UsageMetricResponse;
import com.modus.license.reporting.service.ReportingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportingController")
class ReportingControllerTest {

    @Mock ReportingService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReportingController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private UsageMetricResponse usageResp() {
        return new UsageMetricResponse(UUID.randomUUID(), TENANT_ID,
                "EXPORT_PDF", "api_calls", "count",
                Instant.now(), Instant.now(), 42.0, 10L, 0);
    }

    private EntitlementSnapshotResponse entitlementResp() {
        return new EntitlementSnapshotResponse(UUID.randomUUID(), TENANT_ID,
                UUID.randomUUID(), UUID.randomUUID(),
                "PROFESSIONAL", "NAMED_USER", 50,
                List.of("FEATURE_A"), "ACTIVE",
                Instant.now(), null, Instant.now());
    }

    private SubscriptionSnapshotResponse subscriptionResp() {
        return new SubscriptionSnapshotResponse(UUID.randomUUID(), TENANT_ID,
                UUID.randomUUID(), "PROFESSIONAL", "NAMED_USER",
                "MONTHLY", 50, "ACTIVE",
                Instant.now(), null, Instant.now());
    }

    @Test
    @DisplayName("GET /api/v1/reports/usage → 200 returns page")
    void getUsageMetrics() throws Exception {
        when(service.searchUsageMetrics(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(usageResp())));

        mockMvc.perform(get("/api/v1/reports/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/reports/usage?featureKey=EXPORT_PDF → passes filter")
    void getUsageMetricsWithFilter() throws Exception {
        when(service.searchUsageMetrics(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(usageResp())));

        mockMvc.perform(get("/api/v1/reports/usage").param("featureKey", "EXPORT_PDF"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/reports/entitlements → 200 returns page")
    void getEntitlements() throws Exception {
        when(service.getEntitlementSnapshots(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entitlementResp())));

        mockMvc.perform(get("/api/v1/reports/entitlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/reports/subscriptions → 200 returns page")
    void getSubscriptions() throws Exception {
        when(service.getSubscriptionSnapshots(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(subscriptionResp())));

        mockMvc.perform(get("/api/v1/reports/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/reports/export → 202 when export configured")
    void exportConfigured() throws Exception {
        when(service.exportReport(any(), any(), any()))
                .thenReturn(Optional.of("reports/2024/usage.csv"));

        mockMvc.perform(post("/api/v1/reports/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ExportRequest("USAGE", null, null))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.blobPath").value("reports/2024/usage.csv"));
    }

    @Test
    @DisplayName("POST /api/v1/reports/export → 503 when export not configured")
    void exportNotConfigured() throws Exception {
        when(service.exportReport(any(), any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/reports/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ExportRequest("USAGE", null, null))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("POST /api/v1/reports/export → 400 when reportType is blank")
    void exportMissingReportType() throws Exception {
        mockMvc.perform(post("/api/v1/reports/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("500 on unexpected exception")
    void unexpectedException() throws Exception {
        when(service.getEntitlementSnapshots(any(Pageable.class)))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/v1/reports/entitlements"))
                .andExpect(status().isInternalServerError());
    }
}
