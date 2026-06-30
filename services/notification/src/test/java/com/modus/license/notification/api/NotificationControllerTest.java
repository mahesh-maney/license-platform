package com.modus.license.notification.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.modus.license.notification.api.dto.NotificationResponse;
import com.modus.license.notification.service.NotificationService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController")
class NotificationControllerTest {

    @Mock NotificationService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    static final UUID NOTIFICATION_ID = UUID.randomUUID();
    static final UUID TENANT_ID       = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private NotificationResponse resp() {
        return new NotificationResponse(NOTIFICATION_ID, TENANT_ID,
                "SUBSCRIPTION_CREATED", "EMAIL", "alice@example.com",
                "Your subscription is active", "SENT", 0, null,
                Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("GET /api/v1/notifications → 200 returns page")
    void search() throws Exception {
        when(service.search(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp())));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{notificationId} → 200 when found")
    void getById() throws Exception {
        when(service.getById(NOTIFICATION_ID)).thenReturn(Optional.of(resp()));

        mockMvc.perform(get("/api/v1/notifications/{id}", NOTIFICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(NOTIFICATION_ID.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{notificationId} → 404 when not found")
    void getByIdNotFound() throws Exception {
        when(service.getById(NOTIFICATION_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/notifications/{id}", NOTIFICATION_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("500 on unexpected exception")
    void unexpectedException() throws Exception {
        when(service.search(any(Pageable.class))).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isInternalServerError());
    }
}
