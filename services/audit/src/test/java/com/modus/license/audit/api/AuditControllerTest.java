package com.modus.license.audit.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.modus.license.audit.api.dto.AuditLogResponse;
import com.modus.license.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditController")
class AuditControllerTest {

    @Mock AuditLogService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    static final UUID AUDIT_ID  = UUID.randomUUID();
    static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuditController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AuditLogResponse resp() {
        return new AuditLogResponse(AUDIT_ID, TENANT_ID,
                "user-1", "USER", "CREATE", "SUBSCRIPTION", "sub-1",
                "SUCCESS", "subscription-plan", "127.0.0.1", "Mozilla/5.0",
                "req-001", null, Instant.now(), "abc123");
    }

    @Test
    @DisplayName("GET /api/v1/audit/logs → 200 returns page")
    void search() throws Exception {
        when(service.search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp())));

        mockMvc.perform(get("/api/v1/audit/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/audit/logs?actorId=user-1 → passes filter")
    void searchWithFilters() throws Exception {
        when(service.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp())));

        mockMvc.perform(get("/api/v1/audit/logs")
                        .param("actorId", "user-1")
                        .param("outcome", "SUCCESS"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/audit/logs/{auditId} → 200 when found")
    void getById() throws Exception {
        when(service.getById(AUDIT_ID)).thenReturn(Optional.of(resp()));

        mockMvc.perform(get("/api/v1/audit/logs/{id}", AUDIT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(AUDIT_ID.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/audit/logs/{auditId} → 404 when not found")
    void getByIdNotFound() throws Exception {
        when(service.getById(AUDIT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/audit/logs/{id}", AUDIT_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("500 on unexpected exception")
    void unexpectedException() throws Exception {
        when(service.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/v1/audit/logs"))
                .andExpect(status().isInternalServerError());
    }
}
