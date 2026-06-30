package com.modus.license.subscription.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.modus.license.core.domain.enums.LicenseType;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ModusException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.subscription.api.dto.CreateSubscriptionRequest;
import com.modus.license.subscription.api.dto.PlanResponse;
import com.modus.license.subscription.api.dto.SubscriptionResponse;
import com.modus.license.subscription.domain.enums.BillingCycle;
import com.modus.license.subscription.domain.enums.SubscriptionStatus;
import com.modus.license.subscription.service.SubscriptionService;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionController")
class SubscriptionControllerTest {

    @Mock SubscriptionService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    static final UUID SUB_ID    = UUID.randomUUID();
    static final UUID PLAN_ID   = UUID.randomUUID();
    static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SubscriptionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PlanResponse planResp() {
        return new PlanResponse(PLAN_ID, "Pro", "desc", PlanTier.PROFESSIONAL,
                LicenseType.NAMED_USER, 50, BillingCycle.MONTHLY, 4999L, "USD",
                true, Set.of(), Instant.now(), Instant.now());
    }

    private SubscriptionResponse subResp(SubscriptionStatus status) {
        return new SubscriptionResponse(SUB_ID, TENANT_ID, planResp(), status,
                50, 50, BillingCycle.MONTHLY, Instant.now(), null, null, null,
                Instant.now(), Instant.now());
    }

    // ── POST /api/v1/subscriptions ────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/subscriptions")
    class Create {

        @Test
        @DisplayName("201 on success")
        void success() throws Exception {
            when(service.createSubscription(any())).thenReturn(subResp(SubscriptionStatus.ACTIVE));

            CreateSubscriptionRequest req = new CreateSubscriptionRequest(
                    PLAN_ID, BillingCycle.MONTHLY, null, Instant.now(), null);

            mockMvc.perform(post("/api/v1/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(SUB_ID.toString()));
        }

        @Test
        @DisplayName("400 when planId missing")
        void missingPlanId() throws Exception {
            mockMvc.perform(post("/api/v1/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"billingCycle\":\"MONTHLY\",\"startDate\":\"2024-01-01T00:00:00Z\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("409 when active subscription already exists")
        void conflict() throws Exception {
            when(service.createSubscription(any()))
                    .thenThrow(new ConflictException(ErrorCode.CONFLICT, "Already subscribed"));

            CreateSubscriptionRequest req = new CreateSubscriptionRequest(
                    PLAN_ID, BillingCycle.MONTHLY, null, Instant.now(), null);

            mockMvc.perform(post("/api/v1/subscriptions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isConflict());
        }
    }

    // ── GET /api/v1/subscriptions/active ─────────────────────────────────────

    @Test
    @DisplayName("GET /active → 200 when found")
    void getActive() throws Exception {
        when(service.getActiveSubscription()).thenReturn(subResp(SubscriptionStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/subscriptions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /active → 404 when no active subscription")
    void getActiveNotFound() throws Exception {
        when(service.getActiveSubscription())
                .thenThrow(new ModusException(ErrorCode.SUBSCRIPTION_NOT_FOUND, "No active subscription"));

        mockMvc.perform(get("/api/v1/subscriptions/active"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/subscriptions/{id} ───────────────────────────────────────

    @Test
    @DisplayName("GET /{id} → 200 when found")
    void getById() throws Exception {
        when(service.getSubscription(SUB_ID)).thenReturn(subResp(SubscriptionStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/subscriptions/{id}", SUB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(SUB_ID.toString()));
    }

    @Test
    @DisplayName("GET /{id} → 404 when not found")
    void getByIdNotFound() throws Exception {
        when(service.getSubscription(SUB_ID))
                .thenThrow(ResourceNotFoundException.subscription(SUB_ID.toString()));

        mockMvc.perform(get("/api/v1/subscriptions/{id}", SUB_ID))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/subscriptions ─────────────────────────────────────────────

    @Test
    @DisplayName("GET / → 200 returns page")
    void list() throws Exception {
        when(service.listSubscriptions(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(subResp(SubscriptionStatus.ACTIVE))));

        mockMvc.perform(get("/api/v1/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /?status=CANCELLED → delegates to listByStatus")
    void listByStatus() throws Exception {
        when(service.listByStatus(eq(SubscriptionStatus.CANCELLED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(subResp(SubscriptionStatus.CANCELLED))));

        mockMvc.perform(get("/api/v1/subscriptions").param("status", "CANCELLED"))
                .andExpect(status().isOk());
    }

    // ── POST /api/v1/subscriptions/{id}/change-plan ───────────────────────────

    @Test
    @DisplayName("POST /{id}/change-plan → 200")
    void changePlan() throws Exception {
        UUID newPlanId = UUID.randomUUID();
        when(service.changePlan(eq(SUB_ID), eq(newPlanId)))
                .thenReturn(subResp(SubscriptionStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/subscriptions/{id}/change-plan", SUB_ID)
                        .param("newPlanId", newPlanId.toString()))
                .andExpect(status().isOk());
    }

    // ── POST /api/v1/subscriptions/{id}/cancel ────────────────────────────────

    @Test
    @DisplayName("POST /{id}/cancel → 200")
    void cancel() throws Exception {
        when(service.cancelSubscription(eq(SUB_ID), any()))
                .thenReturn(subResp(SubscriptionStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/subscriptions/{id}/cancel", SUB_ID))
                .andExpect(status().isOk());
    }

    // ── POST /api/v1/subscriptions/{id}/suspend ───────────────────────────────

    @Test
    @DisplayName("POST /{id}/suspend → 200")
    void suspend() throws Exception {
        when(service.suspendSubscription(SUB_ID))
                .thenReturn(subResp(SubscriptionStatus.SUSPENDED));

        mockMvc.perform(post("/api/v1/subscriptions/{id}/suspend", SUB_ID))
                .andExpect(status().isOk());
    }

    // ── POST /api/v1/subscriptions/{id}/renew ────────────────────────────────

    @Test
    @DisplayName("POST /{id}/renew → 200")
    void renew() throws Exception {
        when(service.renewSubscription(eq(SUB_ID), isNull()))
                .thenReturn(subResp(SubscriptionStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/subscriptions/{id}/renew", SUB_ID))
                .andExpect(status().isOk());
    }
}
