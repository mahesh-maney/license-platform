package com.modus.license.feature.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.feature.api.dto.SetTenantFeatureRequest;
import com.modus.license.feature.api.dto.TenantFeatureResponse;
import com.modus.license.feature.service.TenantFeatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantFeatureController")
class TenantFeatureControllerTest {

    @Mock TenantFeatureService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    static final UUID TENANT_ID  = UUID.randomUUID();
    static final String FEATURE_KEY = "EXPORT_PDF";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TenantFeatureController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private TenantFeatureResponse resp(FeatureStatus effectiveStatus) {
        return new TenantFeatureResponse(TENANT_ID, FEATURE_KEY, "Export PDF",
                PlanTier.PROFESSIONAL, FeatureStatus.ENABLED, null,
                effectiveStatus, null, true);
    }

    @Test
    @DisplayName("GET /api/v1/tenant-features → 200 returns list")
    void listOwn() throws Exception {
        when(service.listEffectiveFeatures()).thenReturn(List.of(resp(FeatureStatus.ENABLED)));

        mockMvc.perform(get("/api/v1/tenant-features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/tenant-features/{featureKey} → 200 when found")
    void getOwn() throws Exception {
        when(service.getEffectiveFeature(FEATURE_KEY)).thenReturn(resp(FeatureStatus.ENABLED));

        mockMvc.perform(get("/api/v1/tenant-features/{key}", FEATURE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featureKey").value(FEATURE_KEY));
    }

    @Test
    @DisplayName("GET /api/v1/tenant-features/{featureKey} → 404 when not found")
    void getOwnNotFound() throws Exception {
        when(service.getEffectiveFeature(FEATURE_KEY))
                .thenThrow(ResourceNotFoundException.feature(FEATURE_KEY));

        mockMvc.perform(get("/api/v1/tenant-features/{key}", FEATURE_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/tenant-features/{featureKey} → 200 on override")
    void setOverride() throws Exception {
        when(service.setOverride(eq(FEATURE_KEY), any())).thenReturn(resp(FeatureStatus.DISABLED));

        mockMvc.perform(put("/api/v1/tenant-features/{key}", FEATURE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SetTenantFeatureRequest(FeatureStatus.DISABLED, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveStatus").value("DISABLED"));
    }

    @Test
    @DisplayName("PUT /api/v1/tenant-features/{featureKey} → 400 when status is null")
    void setOverrideInvalid() throws Exception {
        mockMvc.perform(put("/api/v1/tenant-features/{key}", FEATURE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/tenant-features/{featureKey} → 204")
    void removeOverride() throws Exception {
        doNothing().when(service).removeOverride(FEATURE_KEY);

        mockMvc.perform(delete("/api/v1/tenant-features/{key}", FEATURE_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/tenant-features/{featureKey} → 404 when not found")
    void removeOverrideNotFound() throws Exception {
        doThrow(ResourceNotFoundException.feature(FEATURE_KEY))
                .when(service).removeOverride(FEATURE_KEY);

        mockMvc.perform(delete("/api/v1/tenant-features/{key}", FEATURE_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/tenant-features/admin/{tenantId} → 200")
    void listForTenant() throws Exception {
        when(service.listEffectiveFeaturesForTenant(TENANT_ID))
                .thenReturn(List.of(resp(FeatureStatus.ENABLED)));

        mockMvc.perform(get("/api/v1/tenant-features/admin/{tenantId}", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("PUT /api/v1/tenant-features/admin/{tenantId}/{featureKey} → 200")
    void setOverrideForTenant() throws Exception {
        when(service.setOverrideForTenant(eq(TENANT_ID), eq(FEATURE_KEY), any()))
                .thenReturn(resp(FeatureStatus.ENABLED));

        mockMvc.perform(put("/api/v1/tenant-features/admin/{tenantId}/{key}", TENANT_ID, FEATURE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SetTenantFeatureRequest(FeatureStatus.ENABLED, null))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/tenant-features/admin/{tenantId}/{featureKey} → 204")
    void removeOverrideForTenant() throws Exception {
        doNothing().when(service).removeOverrideForTenant(TENANT_ID, FEATURE_KEY);

        mockMvc.perform(delete("/api/v1/tenant-features/admin/{tenantId}/{key}", TENANT_ID, FEATURE_KEY))
                .andExpect(status().isNoContent());
    }
}
