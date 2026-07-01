package com.modus.license.feature.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ErrorCode;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.feature.api.dto.CreateFeatureRequest;
import com.modus.license.feature.api.dto.FeatureResponse;
import com.modus.license.feature.api.dto.UpdateFeatureRequest;
import com.modus.license.feature.service.FeatureDefinitionService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureController")
class FeatureControllerTest {

    @Mock FeatureDefinitionService service;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    static final UUID FEATURE_ID = UUID.randomUUID();
    static final String FEATURE_KEY = "EXPORT_PDF";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeatureController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    private FeatureResponse resp(FeatureStatus status) {
        return new FeatureResponse(FEATURE_ID, FEATURE_KEY, "Export PDF", "Export to PDF",
                PlanTier.PROFESSIONAL, status, null, Instant.now(), Instant.now());
    }

    private CreateFeatureRequest createReq() {
        return new CreateFeatureRequest(FEATURE_KEY, "Export PDF", "Desc",
                PlanTier.PROFESSIONAL, FeatureStatus.ENABLED, null);
    }

    @Test
    @DisplayName("POST /api/v1/features → 201 on success")
    void create() throws Exception {
        when(service.createFeature(any())).thenReturn(resp(FeatureStatus.ENABLED));

        mockMvc.perform(post("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.featureKey").value(FEATURE_KEY));
    }

    @Test
    @DisplayName("POST /api/v1/features → 400 when featureKey is blank")
    void createInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Export\",\"status\":\"ENABLED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/features → 409 when key already exists")
    void createConflict() throws Exception {
        when(service.createFeature(any()))
                .thenThrow(new ConflictException(ErrorCode.CONFLICT, "Feature key taken"));

        mockMvc.perform(post("/api/v1/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/v1/features/{featureKey} → 200 when found")
    void getByKey() throws Exception {
        when(service.getFeature(FEATURE_KEY)).thenReturn(resp(FeatureStatus.ENABLED));

        mockMvc.perform(get("/api/v1/features/{key}", FEATURE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featureKey").value(FEATURE_KEY));
    }

    @Test
    @DisplayName("GET /api/v1/features/{featureKey} → 404 when not found")
    void getByKeyNotFound() throws Exception {
        when(service.getFeature(FEATURE_KEY))
                .thenThrow(ResourceNotFoundException.feature(FEATURE_KEY));

        mockMvc.perform(get("/api/v1/features/{key}", FEATURE_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/features → 200 returns page")
    void list() throws Exception {
        when(service.listFeatures(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp(FeatureStatus.ENABLED))));

        mockMvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/features?status=ENABLED → passes status filter")
    void listByStatus() throws Exception {
        when(service.listFeatures(eq(FeatureStatus.ENABLED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp(FeatureStatus.ENABLED))));

        mockMvc.perform(get("/api/v1/features").param("status", "ENABLED"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /api/v1/features/{featureKey} → 200 on update")
    void update() throws Exception {
        when(service.updateFeature(eq(FEATURE_KEY), any())).thenReturn(resp(FeatureStatus.ENABLED));

        mockMvc.perform(patch("/api/v1/features/{key}", FEATURE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateFeatureRequest("New Name", "New desc", PlanTier.ENTERPRISE, null))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/features/{featureKey}/enable → 200")
    void enable() throws Exception {
        when(service.enableFeature(FEATURE_KEY)).thenReturn(resp(FeatureStatus.ENABLED));

        mockMvc.perform(post("/api/v1/features/{key}/enable", FEATURE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENABLED"));
    }

    @Test
    @DisplayName("POST /api/v1/features/{featureKey}/disable → 200")
    void disable() throws Exception {
        when(service.disableFeature(FEATURE_KEY)).thenReturn(resp(FeatureStatus.DISABLED));

        mockMvc.perform(post("/api/v1/features/{key}/disable", FEATURE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    @DisplayName("POST /api/v1/features/{featureKey}/deprecate → 200")
    void deprecate() throws Exception {
        when(service.deprecateFeature(FEATURE_KEY)).thenReturn(resp(FeatureStatus.DEPRECATED));

        mockMvc.perform(post("/api/v1/features/{key}/deprecate", FEATURE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEPRECATED"));
    }

    @Test
    @DisplayName("500 on unexpected exception")
    void unexpectedException() throws Exception {
        when(service.getFeature(any())).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/v1/features/{key}", FEATURE_KEY))
                .andExpect(status().isInternalServerError());
    }
}
