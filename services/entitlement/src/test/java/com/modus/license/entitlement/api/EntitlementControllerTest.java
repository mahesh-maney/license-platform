package com.modus.license.entitlement.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.modus.license.core.domain.enums.EntitlementStatus;
import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.exception.ModusException;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.entitlement.api.dto.CreateEntitlementRequest;
import com.modus.license.entitlement.api.dto.EntitlementResponse;
import com.modus.license.entitlement.api.dto.UpdateEntitlementRequest;
import com.modus.license.entitlement.service.EntitlementService;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntitlementController")
class EntitlementControllerTest {

    @Mock EntitlementService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    static final UUID ENT_ID    = UUID.randomUUID();
    static final UUID TENANT_ID = UUID.randomUUID();
    static final UUID SUB_ID    = UUID.randomUUID();
    static final UUID PLAN_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EntitlementController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private EntitlementResponse resp(EntitlementStatus status) {
        return new EntitlementResponse(ENT_ID, TENANT_ID, SUB_ID, PLAN_ID,
                PlanTier.PROFESSIONAL, LicenseType.NAMED_USER, 50,
                Set.of("FEATURE_A"), status, Instant.now(), null,
                Instant.now(), Instant.now());
    }

    private CreateEntitlementRequest createReq() {
        return new CreateEntitlementRequest(TENANT_ID, SUB_ID, PLAN_ID,
                PlanTier.PROFESSIONAL, LicenseType.NAMED_USER, 50,
                Set.of("FEATURE_A"), Instant.now(), null);
    }

    @Test
    @DisplayName("POST /api/v1/entitlements → 201 on success")
    void grant() throws Exception {
        when(service.grantEntitlement(any())).thenReturn(resp(EntitlementStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/entitlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/entitlements → 400 when required field missing")
    void grantMissingField() throws Exception {
        mockMvc.perform(post("/api/v1/entitlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planTier\":\"PROFESSIONAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /active → 200 on success")
    void getActive() throws Exception {
        when(service.getActiveEntitlement()).thenReturn(resp(EntitlementStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/entitlements/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /active → 404 when no active entitlement")
    void getActiveNotFound() throws Exception {
        when(service.getActiveEntitlement())
                .thenThrow(new ModusException(ErrorCode.ENTITLEMENT_NOT_FOUND, "No active entitlement"));

        mockMvc.perform(get("/api/v1/entitlements/active"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id} → 200 when found")
    void getById() throws Exception {
        when(service.getEntitlement(ENT_ID)).thenReturn(resp(EntitlementStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/entitlements/{id}", ENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ENT_ID.toString()));
    }

    @Test
    @DisplayName("GET /{id} → 404 when not found")
    void getByIdNotFound() throws Exception {
        when(service.getEntitlement(ENT_ID))
                .thenThrow(ResourceNotFoundException.entitlement(ENT_ID.toString()));

        mockMvc.perform(get("/api/v1/entitlements/{id}", ENT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET / → 200 returns page")
    void list() throws Exception {
        when(service.listEntitlements(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp(EntitlementStatus.ACTIVE))));

        mockMvc.perform(get("/api/v1/entitlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("PUT /{id} → 200 on update")
    void update() throws Exception {
        when(service.updateEntitlement(eq(ENT_ID), any())).thenReturn(resp(EntitlementStatus.ACTIVE));

        mockMvc.perform(put("/api/v1/entitlements/{id}", ENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateEntitlementRequest(Set.of("FEATURE_B"), 100, null))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /{id}/revoke → 200")
    void revoke() throws Exception {
        when(service.revokeEntitlement(ENT_ID)).thenReturn(resp(EntitlementStatus.REVOKED));

        mockMvc.perform(post("/api/v1/entitlements/{id}/revoke", ENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    @DisplayName("POST /{id}/suspend → 200")
    void suspend() throws Exception {
        when(service.suspendEntitlement(ENT_ID)).thenReturn(resp(EntitlementStatus.SUSPENDED));

        mockMvc.perform(post("/api/v1/entitlements/{id}/suspend", ENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }
}
