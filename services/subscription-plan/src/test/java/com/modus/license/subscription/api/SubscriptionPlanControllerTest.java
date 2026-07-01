package com.modus.license.subscription.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.subscription.api.dto.CreatePlanRequest;
import com.modus.license.subscription.api.dto.PlanResponse;
import com.modus.license.subscription.api.dto.UpdatePlanRequest;
import com.modus.license.subscription.domain.enums.BillingCycle;
import com.modus.license.subscription.service.SubscriptionPlanService;
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
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionPlanController")
class SubscriptionPlanControllerTest {

    @Mock SubscriptionPlanService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    static final UUID PLAN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SubscriptionPlanController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private PlanResponse planResp(boolean active) {
        return new PlanResponse(PLAN_ID, "Pro", "A pro plan", PlanTier.PROFESSIONAL,
                LicenseType.NAMED_USER, 50, BillingCycle.MONTHLY, 4999L, "USD",
                active, Set.of("feature.export"), Instant.now(), Instant.now());
    }

    private CreatePlanRequest createReq(String name) {
        return new CreatePlanRequest(name, "desc", PlanTier.PROFESSIONAL,
                LicenseType.NAMED_USER, 50, BillingCycle.MONTHLY, 4999L, "USD", Set.of());
    }

    // ── POST /api/v1/plans ────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/plans")
    class Create {

        @Test
        @DisplayName("201 on success")
        void success() throws Exception {
            when(service.createPlan(any())).thenReturn(planResp(true));

            mockMvc.perform(post("/api/v1/plans")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq("Pro Plan"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("Pro"));
        }

        @Test
        @DisplayName("400 when name is blank")
        void missingName() throws Exception {
            mockMvc.perform(post("/api/v1/plans")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"\",\"tier\":\"PROFESSIONAL\",\"licenseType\":\"NAMED_USER\","
                                    + "\"billingCycle\":\"MONTHLY\",\"priceInCents\":0}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("409 when plan name already exists")
        void conflict() throws Exception {
            when(service.createPlan(any()))
                    .thenThrow(new ConflictException(ErrorCode.CONFLICT, "Plan name taken"));

            mockMvc.perform(post("/api/v1/plans")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createReq("Dup"))))
                    .andExpect(status().isConflict());
        }
    }

    // ── GET /api/v1/plans/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /{id} → 200 when found")
    void getById() throws Exception {
        when(service.getPlan(PLAN_ID)).thenReturn(planResp(true));

        mockMvc.perform(get("/api/v1/plans/{id}", PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(PLAN_ID.toString()));
    }

    @Test
    @DisplayName("GET /{id} → 404 when not found")
    void getByIdNotFound() throws Exception {
        when(service.getPlan(PLAN_ID))
                .thenThrow(ResourceNotFoundException.plan(PLAN_ID.toString()));

        mockMvc.perform(get("/api/v1/plans/{id}", PLAN_ID))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/plans ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET / → 200 returns page")
    void list() throws Exception {
        when(service.listPlans(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(planResp(true))));

        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /?activeOnly=true → passes activeOnly filter")
    void listActiveOnly() throws Exception {
        when(service.listPlans(eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(planResp(true))));

        mockMvc.perform(get("/api/v1/plans").param("activeOnly", "true"))
                .andExpect(status().isOk());
    }

    // ── GET /api/v1/plans/by-tier/{tier} ─────────────────────────────────────

    @Test
    @DisplayName("GET /by-tier/PROFESSIONAL → 200")
    void listByTier() throws Exception {
        when(service.listByTier(PlanTier.PROFESSIONAL)).thenReturn(List.of(planResp(true)));

        mockMvc.perform(get("/api/v1/plans/by-tier/{tier}", "PROFESSIONAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── PATCH /api/v1/plans/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /{id} → 200")
    void update() throws Exception {
        when(service.updatePlan(eq(PLAN_ID), any())).thenReturn(planResp(true));

        mockMvc.perform(patch("/api/v1/plans/{id}", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdatePlanRequest("new desc", 100, null, null, Set.of()))))
                .andExpect(status().isOk());
    }

    // ── POST /api/v1/plans/{id}/deactivate ───────────────────────────────────

    @Test
    @DisplayName("POST /{id}/deactivate → 200")
    void deactivate() throws Exception {
        when(service.deactivatePlan(PLAN_ID)).thenReturn(planResp(false));

        mockMvc.perform(post("/api/v1/plans/{id}/deactivate", PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }
}
