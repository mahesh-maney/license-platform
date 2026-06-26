package com.modus.license.feature.service;

import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.feature.api.dto.CreateFeatureRequest;
import com.modus.license.feature.api.dto.FeatureResponse;
import com.modus.license.feature.api.dto.UpdateFeatureRequest;
import com.modus.license.feature.api.mapper.FeatureMapper;
import com.modus.license.feature.domain.entity.FeatureDefinitionEntity;
import com.modus.license.feature.domain.event.FeatureEventPublisher;
import com.modus.license.feature.domain.repository.FeatureDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureDefinitionService")
class FeatureDefinitionServiceTest {

    @Mock FeatureDefinitionRepository repository;
    @Mock FeatureMapper                mapper;
    @Mock FeatureEventPublisher        eventPublisher;

    FeatureDefinitionService service;

    static final String FEATURE_KEY = "ADVANCED_REPORTING";

    @BeforeEach
    void setUp() {
        service = new FeatureDefinitionService(repository, mapper, eventPublisher);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FeatureDefinitionEntity entity(String key, FeatureStatus status) {
        FeatureDefinitionEntity e = new FeatureDefinitionEntity();
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        e.setFeatureKey(key);
        e.setName("Advanced Reporting");
        e.setStatus(status);
        e.setMinimumPlanTier(PlanTier.PROFESSIONAL);
        return e;
    }

    private FeatureResponse response(String key, FeatureStatus status) {
        return new FeatureResponse(UUID.randomUUID(), key, "Advanced Reporting", null,
                PlanTier.PROFESSIONAL, status, null, Instant.now(), Instant.now());
    }

    private CreateFeatureRequest createRequest(String key) {
        return new CreateFeatureRequest(key, "Advanced Reporting", "Desc",
                PlanTier.PROFESSIONAL, FeatureStatus.ENABLED, null);
    }

    // ── createFeature ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createFeature")
    class CreateFeature {

        @Test
        @DisplayName("saves entity and publishes globalUpdated")
        void success() {
            FeatureDefinitionEntity e = entity(FEATURE_KEY, FeatureStatus.ENABLED);
            FeatureResponse resp = response(FEATURE_KEY, FeatureStatus.ENABLED);

            when(repository.existsByFeatureKey(FEATURE_KEY)).thenReturn(false);
            when(mapper.toEntity(any())).thenReturn(e);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(resp);

            FeatureResponse result = service.createFeature(createRequest(FEATURE_KEY));

            assertThat(result.featureKey()).isEqualTo(FEATURE_KEY);
            verify(repository).save(e);
            verify(eventPublisher).publishGlobalUpdated(e);
        }

        @Test
        @DisplayName("throws ConflictException when featureKey already exists")
        void duplicateKey() {
            when(repository.existsByFeatureKey(FEATURE_KEY)).thenReturn(true);

            assertThatThrownBy(() -> service.createFeature(createRequest(FEATURE_KEY)))
                    .isInstanceOf(ConflictException.class);

            verify(repository, never()).save(any());
            verify(eventPublisher, never()).publishGlobalUpdated(any());
        }
    }

    // ── getFeature ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFeature")
    class GetFeature {

        @Test
        @DisplayName("returns response when found")
        void found() {
            FeatureDefinitionEntity e = entity(FEATURE_KEY, FeatureStatus.ENABLED);
            when(repository.findByFeatureKey(FEATURE_KEY)).thenReturn(Optional.of(e));
            when(mapper.toResponse(e)).thenReturn(response(FEATURE_KEY, FeatureStatus.ENABLED));

            assertThat(service.getFeature(FEATURE_KEY).featureKey()).isEqualTo(FEATURE_KEY);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void notFound() {
            when(repository.findByFeatureKey(FEATURE_KEY)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getFeature(FEATURE_KEY))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── listFeatures ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listFeatures")
    class ListFeatures {

        @Test
        @DisplayName("status filter → delegates to findByStatus")
        void withStatusFilter() {
            FeatureDefinitionEntity e = entity(FEATURE_KEY, FeatureStatus.ENABLED);
            when(repository.findByStatus(eq(FeatureStatus.ENABLED), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(e)));
            when(mapper.toResponse(any())).thenReturn(response(FEATURE_KEY, FeatureStatus.ENABLED));

            assertThat(service.listFeatures(FeatureStatus.ENABLED, Pageable.unpaged()).getContent())
                    .hasSize(1);
            verify(repository).findByStatus(eq(FeatureStatus.ENABLED), any());
            verify(repository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("null status → delegates to findAll")
        void withoutFilter() {
            FeatureDefinitionEntity e = entity(FEATURE_KEY, FeatureStatus.DISABLED);
            when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(e)));
            when(mapper.toResponse(any())).thenReturn(response(FEATURE_KEY, FeatureStatus.DISABLED));

            assertThat(service.listFeatures(null, Pageable.unpaged()).getContent()).hasSize(1);
            verify(repository).findAll(any(Pageable.class));
        }
    }

    // ── updateFeature ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateFeature")
    class UpdateFeature {

        @Test
        @DisplayName("patches non-null fields and saves")
        void success() {
            FeatureDefinitionEntity e = entity(FEATURE_KEY, FeatureStatus.ENABLED);
            UpdateFeatureRequest req = new UpdateFeatureRequest("New Name", "New Desc",
                    PlanTier.ENTERPRISE, "{\"type\":\"object\"}");

            when(repository.findByFeatureKey(FEATURE_KEY)).thenReturn(Optional.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(response(FEATURE_KEY, FeatureStatus.ENABLED));

            service.updateFeature(FEATURE_KEY, req);

            assertThat(e.getName()).isEqualTo("New Name");
            assertThat(e.getDescription()).isEqualTo("New Desc");
            assertThat(e.getMinimumPlanTier()).isEqualTo(PlanTier.ENTERPRISE);
            assertThat(e.getConfigSchemaJson()).isEqualTo("{\"type\":\"object\"}");
            verify(eventPublisher).publishGlobalUpdated(e);
        }

        @Test
        @DisplayName("null fields are left unchanged")
        void nullFieldsUnchanged() {
            FeatureDefinitionEntity e = entity(FEATURE_KEY, FeatureStatus.ENABLED);
            e.setName("Original");
            e.setMinimumPlanTier(PlanTier.STARTER);

            when(repository.findByFeatureKey(FEATURE_KEY)).thenReturn(Optional.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(response(FEATURE_KEY, FeatureStatus.ENABLED));

            service.updateFeature(FEATURE_KEY, new UpdateFeatureRequest(null, null, null, null));

            assertThat(e.getName()).isEqualTo("Original");
            assertThat(e.getMinimumPlanTier()).isEqualTo(PlanTier.STARTER);
        }
    }

    // ── enableFeature ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("enableFeature sets ENABLED and publishes globalEnabled")
    void enableFeature() {
        FeatureDefinitionEntity e = entity(FEATURE_KEY, FeatureStatus.DISABLED);
        when(repository.findByFeatureKey(FEATURE_KEY)).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response(FEATURE_KEY, FeatureStatus.ENABLED));

        service.enableFeature(FEATURE_KEY);

        assertThat(e.getStatus()).isEqualTo(FeatureStatus.ENABLED);
        verify(eventPublisher).publishGlobalEnabled(e, "DISABLED");
    }

    // ── disableFeature ────────────────────────────────────────────────────────

    @Test
    @DisplayName("disableFeature sets DISABLED and publishes globalDisabled")
    void disableFeature() {
        FeatureDefinitionEntity e = entity(FEATURE_KEY, FeatureStatus.ENABLED);
        when(repository.findByFeatureKey(FEATURE_KEY)).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response(FEATURE_KEY, FeatureStatus.DISABLED));

        service.disableFeature(FEATURE_KEY);

        assertThat(e.getStatus()).isEqualTo(FeatureStatus.DISABLED);
        verify(eventPublisher).publishGlobalDisabled(e, "ENABLED");
    }

    // ── deprecateFeature ──────────────────────────────────────────────────────

    @Test
    @DisplayName("deprecateFeature sets DEPRECATED and publishes globalDeprecated")
    void deprecateFeature() {
        FeatureDefinitionEntity e = entity(FEATURE_KEY, FeatureStatus.ENABLED);
        when(repository.findByFeatureKey(FEATURE_KEY)).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response(FEATURE_KEY, FeatureStatus.DEPRECATED));

        service.deprecateFeature(FEATURE_KEY);

        assertThat(e.getStatus()).isEqualTo(FeatureStatus.DEPRECATED);
        verify(eventPublisher).publishGlobalDeprecated(e, "ENABLED");
    }
}
