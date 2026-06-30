package com.modus.license.usage.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.modus.license.usage.api.dto.RecordUsageRequest;
import com.modus.license.usage.api.dto.UsageResponse;
import com.modus.license.usage.service.UsageMeteringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsageController")
class UsageControllerTest {

    @Mock UsageMeteringService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    static final UUID TENANT_ID = UUID.randomUUID();
    static final UUID USER_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UsageController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UsageResponse resp() {
        return new UsageResponse(UUID.randomUUID().toString(), TENANT_ID.toString(),
                USER_ID.toString(), "EXPORT_PDF", "api_calls", 1.0, "count", Instant.now());
    }

    @Test
    @DisplayName("POST /api/v1/usage/record → 202 on success")
    void recordUsage() throws Exception {
        when(service.recordUsage(any())).thenReturn(resp());

        mockMvc.perform(post("/api/v1/usage/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecordUsageRequest(USER_ID, "EXPORT_PDF", "api_calls", 1.0, "count"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.featureKey").value("EXPORT_PDF"));
    }

    @Test
    @DisplayName("POST /api/v1/usage/record → 400 when featureKey is blank")
    void recordUsageMissingFeatureKey() throws Exception {
        mockMvc.perform(post("/api/v1/usage/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metricName\":\"api_calls\",\"quantity\":1.0,\"unit\":\"count\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/usage/record → 400 when metricName is blank")
    void recordUsageMissingMetricName() throws Exception {
        mockMvc.perform(post("/api/v1/usage/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"featureKey\":\"EXPORT_PDF\",\"quantity\":1.0,\"unit\":\"count\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/usage/record → 400 when unit is blank")
    void recordUsageMissingUnit() throws Exception {
        mockMvc.perform(post("/api/v1/usage/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"featureKey\":\"EXPORT_PDF\",\"metricName\":\"api_calls\",\"quantity\":1.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/usage/record → 202 without optional userId")
    void recordUsageNoUserId() throws Exception {
        when(service.recordUsage(any())).thenReturn(resp());

        mockMvc.perform(post("/api/v1/usage/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecordUsageRequest(null, "EXPORT_PDF", "api_calls", 1.0, "count"))))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("500 on unexpected exception")
    void unexpectedException() throws Exception {
        when(service.recordUsage(any())).thenThrow(new RuntimeException("Kafka unavailable"));

        mockMvc.perform(post("/api/v1/usage/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecordUsageRequest(null, "EXPORT_PDF", "api_calls", 1.0, "count"))))
                .andExpect(status().isInternalServerError());
    }
}
