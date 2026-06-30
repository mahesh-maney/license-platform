package com.modus.license.tenant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.domain.enums.TenantStatus;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.tenant.api.dto.CreateTenantRequest;
import com.modus.license.tenant.api.dto.TenantResponse;
import com.modus.license.tenant.api.dto.UpdateTenantRequest;
import com.modus.license.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantController")
class TenantControllerTest {

    @Mock TenantService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TenantController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private TenantResponse resp(TenantStatus status) {
        return new TenantResponse(TENANT_ID, "acme-corp", "Acme Corp", "ACME",
                "admin@acme.com", status, PlanTier.PROFESSIONAL, "us-east-1",
                null, Instant.now(), Instant.now());
    }

    private CreateTenantRequest createReq() {
        return new CreateTenantRequest("acme-corp", "Acme Corp", "ACME",
                "admin@acme.com", PlanTier.PROFESSIONAL, "us-east-1");
    }

    // ── POST /api/v1/tenants ──────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/tenants")
    class Create {

        @Test
        @DisplayName("201 on success")
        void success() throws Exception {
            when(service.createTenant(any())).thenReturn(resp(TenantStatus.ACTIVE));

            mockMvc.perform(post("/api/v1/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(TENANT_ID.toString()));
        }

        @Test
        @DisplayName("400 when slug is blank")
        void missingSlug() throws Exception {
            mockMvc.perform(post("/api/v1/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Acme\",\"adminEmail\":\"a@b.com\",\"planTier\":\"STARTER\",\"region\":\"us\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when adminEmail is invalid")
        void invalidEmail() throws Exception {
            mockMvc.perform(post("/api/v1/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"slug\":\"acme-corp\",\"name\":\"Acme\",\"adminEmail\":\"not-an-email\",\"planTier\":\"STARTER\",\"region\":\"us\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("409 when slug already exists")
        void conflict() throws Exception {
            when(service.createTenant(any()))
                    .thenThrow(new ConflictException(ErrorCode.TENANT_ALREADY_EXISTS, "Slug taken"));

            mockMvc.perform(post("/api/v1/tenants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq())))
                    .andExpect(status().isConflict());
        }
    }

    // ── GET /api/v1/tenants/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /{id} → 200 when found")
    void getById() throws Exception {
        when(service.getTenant(TENANT_ID)).thenReturn(resp(TenantStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/tenants/{id}", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(TENANT_ID.toString()));
    }

    @Test
    @DisplayName("GET /{id} → 404 when not found")
    void getByIdNotFound() throws Exception {
        when(service.getTenant(TENANT_ID))
                .thenThrow(ResourceNotFoundException.tenant(TENANT_ID.toString()));

        mockMvc.perform(get("/api/v1/tenants/{id}", TENANT_ID))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/tenants/by-slug/{slug} ───────────────────────────────────

    @Test
    @DisplayName("GET /by-slug → 200 when found")
    void getBySlug() throws Exception {
        when(service.getTenantBySlug("acme-corp")).thenReturn(resp(TenantStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/tenants/by-slug/{slug}", "acme-corp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("acme-corp"));
    }

    // ── GET /api/v1/tenants ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET / → 200 returns page")
    void list() throws Exception {
        when(service.listTenants(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp(TenantStatus.ACTIVE))));

        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /?status=SUSPENDED → delegates to listByStatus")
    void listByStatus() throws Exception {
        when(service.listByStatus(eq(TenantStatus.SUSPENDED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp(TenantStatus.SUSPENDED))));

        mockMvc.perform(get("/api/v1/tenants").param("status", "SUSPENDED"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /?region=us-east-1 → delegates to listByRegion")
    void listByRegion() throws Exception {
        when(service.listByRegion(eq("us-east-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp(TenantStatus.ACTIVE))));

        mockMvc.perform(get("/api/v1/tenants").param("region", "us-east-1"))
                .andExpect(status().isOk());
    }

    // ── PUT /api/v1/tenants/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("PUT /{id} → 200 on update")
    void update() throws Exception {
        when(service.updateTenant(eq(TENANT_ID), any())).thenReturn(resp(TenantStatus.ACTIVE));

        mockMvc.perform(put("/api/v1/tenants/{id}", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateTenantRequest("New Name", "New Display", PlanTier.ENTERPRISE, "eu-west-1"))))
                .andExpect(status().isOk());
    }

    // ── POST /api/v1/tenants/{id}/suspend ────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/suspend → 200")
    void suspend() throws Exception {
        when(service.suspendTenant(TENANT_ID)).thenReturn(resp(TenantStatus.SUSPENDED));

        mockMvc.perform(post("/api/v1/tenants/{id}/suspend", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }

    // ── POST /api/v1/tenants/{id}/activate ───────────────────────────────────

    @Test
    @DisplayName("POST /{id}/activate → 200")
    void activate() throws Exception {
        when(service.activateTenant(TENANT_ID)).thenReturn(resp(TenantStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/tenants/{id}/activate", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    // ── DELETE /api/v1/tenants/{id} ───────────────────────────────────────────

    @Test
    @DisplayName("DELETE /{id} → 204")
    void delete_() throws Exception {
        doNothing().when(service).deleteTenant(TENANT_ID);

        mockMvc.perform(delete("/api/v1/tenants/{id}", TENANT_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /{id} → 404 when not found")
    void deleteNotFound() throws Exception {
        doThrow(ResourceNotFoundException.tenant(TENANT_ID.toString()))
                .when(service).deleteTenant(TENANT_ID);

        mockMvc.perform(delete("/api/v1/tenants/{id}", TENANT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("500 on unexpected exception")
    void unexpectedException() throws Exception {
        when(service.getTenant(any())).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/v1/tenants/{id}", TENANT_ID))
                .andExpect(status().isInternalServerError());
    }
}
