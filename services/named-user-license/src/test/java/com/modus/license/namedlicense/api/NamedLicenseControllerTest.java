package com.modus.license.namedlicense.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.namedlicense.api.dto.AssignSeatRequest;
import com.modus.license.namedlicense.api.dto.CreateLicensePoolRequest;
import com.modus.license.namedlicense.api.dto.LicensePoolResponse;
import com.modus.license.namedlicense.api.dto.SeatAssignmentResponse;
import com.modus.license.namedlicense.api.dto.TransferSeatRequest;
import com.modus.license.namedlicense.service.NamedLicenseService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("NamedLicenseController")
class NamedLicenseControllerTest {

    @Mock NamedLicenseService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    static final UUID LICENSE_ID     = UUID.randomUUID();
    static final UUID TENANT_ID      = UUID.randomUUID();
    static final UUID PLAN_ID        = UUID.randomUUID();
    static final UUID ENTITLEMENT_ID = UUID.randomUUID();
    static final UUID USER_ID        = UUID.randomUUID();
    static final UUID USER_ID_2      = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NamedLicenseController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private LicensePoolResponse poolResp() {
        return new LicensePoolResponse(LICENSE_ID, TENANT_ID, PLAN_ID, ENTITLEMENT_ID,
                10, 3, 7, Instant.now(), Instant.now());
    }

    private SeatAssignmentResponse seatResp() {
        return new SeatAssignmentResponse(UUID.randomUUID(), LICENSE_ID, USER_ID,
                "alice@example.com", Instant.now(), "system");
    }

    // ── Pools ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/named-licenses → 201 on success")
    void createPool() throws Exception {
        when(service.createPool(any())).thenReturn(poolResp());

        mockMvc.perform(post("/api/v1/named-licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLicensePoolRequest(PLAN_ID, ENTITLEMENT_ID, 10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(LICENSE_ID.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/named-licenses → 400 when planId is missing")
    void createPoolMissingPlanId() throws Exception {
        mockMvc.perform(post("/api/v1/named-licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalSeats\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/named-licenses → 409 when entitlement already has a pool")
    void createPoolConflict() throws Exception {
        when(service.createPool(any()))
                .thenThrow(new ConflictException(ErrorCode.CONFLICT, "Pool already exists"));

        mockMvc.perform(post("/api/v1/named-licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateLicensePoolRequest(PLAN_ID, ENTITLEMENT_ID, 10))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/v1/named-licenses/{licenseId} → 200 when found")
    void getPool() throws Exception {
        when(service.getPool(LICENSE_ID)).thenReturn(poolResp());

        mockMvc.perform(get("/api/v1/named-licenses/{id}", LICENSE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(LICENSE_ID.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/named-licenses/{licenseId} → 404 when not found")
    void getPoolNotFound() throws Exception {
        when(service.getPool(LICENSE_ID))
                .thenThrow(ResourceNotFoundException.license(LICENSE_ID.toString()));

        mockMvc.perform(get("/api/v1/named-licenses/{id}", LICENSE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/named-licenses → 200 returns page")
    void listPools() throws Exception {
        when(service.listPools(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(poolResp())));

        mockMvc.perform(get("/api/v1/named-licenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ── Seats ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/named-licenses/{licenseId}/seats → 201 on success")
    void assignSeat() throws Exception {
        when(service.assignSeat(eq(LICENSE_ID), any())).thenReturn(seatResp());

        mockMvc.perform(post("/api/v1/named-licenses/{id}/seats", LICENSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssignSeatRequest(USER_ID, "alice@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/named-licenses/{licenseId}/seats → 400 when userId missing")
    void assignSeatMissingUserId() throws Exception {
        mockMvc.perform(post("/api/v1/named-licenses/{id}/seats", LICENSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/named-licenses/{licenseId}/seats → 409 when no seats available")
    void assignSeatNoCapacity() throws Exception {
        when(service.assignSeat(eq(LICENSE_ID), any()))
                .thenThrow(new ConflictException(ErrorCode.CONFLICT, "No seats available"));

        mockMvc.perform(post("/api/v1/named-licenses/{id}/seats", LICENSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssignSeatRequest(USER_ID, "alice@example.com"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /api/v1/named-licenses/{licenseId}/seats/{userId} → 204")
    void revokeSeat() throws Exception {
        doNothing().when(service).revokeSeat(LICENSE_ID, USER_ID);

        mockMvc.perform(delete("/api/v1/named-licenses/{id}/seats/{userId}", LICENSE_ID, USER_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/named-licenses/{licenseId}/seats/{userId} → 404 when not found")
    void revokeSeatNotFound() throws Exception {
        doThrow(ResourceNotFoundException.license(LICENSE_ID.toString()))
                .when(service).revokeSeat(LICENSE_ID, USER_ID);

        mockMvc.perform(delete("/api/v1/named-licenses/{id}/seats/{userId}", LICENSE_ID, USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/named-licenses/{licenseId}/seats/transfer → 200")
    void transferSeat() throws Exception {
        when(service.transferSeat(eq(LICENSE_ID), any())).thenReturn(seatResp());

        mockMvc.perform(post("/api/v1/named-licenses/{id}/seats/transfer", LICENSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferSeatRequest(USER_ID, USER_ID_2, "bob@example.com"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/named-licenses/{licenseId}/seats → 200 returns page")
    void listAssignments() throws Exception {
        when(service.listAssignments(eq(LICENSE_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(seatResp())));

        mockMvc.perform(get("/api/v1/named-licenses/{id}/seats", LICENSE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("500 on unexpected exception")
    void unexpectedException() throws Exception {
        when(service.getPool(any())).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/v1/named-licenses/{id}", LICENSE_ID))
                .andExpect(status().isInternalServerError());
    }
}
