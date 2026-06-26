package com.modus.license.feature.service;

import com.modus.license.core.domain.enums.FeatureStatus;
import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.feature.api.dto.SetTenantFeatureRequest;
import com.modus.license.feature.api.dto.TenantFeatureResponse;
import com.modus.license.feature.domain.entity.FeatureDefinitionEntity;
import com.modus.license.feature.domain.entity.TenantFeatureOverrideEntity;
import com.modus.license.feature.domain.event.FeatureEventPublisher;
import com.modus.license.feature.domain.repository.FeatureDefinitionRepository;
import com.modus.license.feature.domain.repository.TenantFeatureOverrideRepository;
import com.modus.license.test.context.TenantContextTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantFeatureService")
class TenantFeatureServiceTest {

    @Mock FeatureDefinitionRepository   featureRepository;
    @Mock TenantFeatureOverrideRepository overrideRepository;
    @Mock FeatureDefinitionService      definitionService;
    @Mock FeatureEventPublisher         eventPublisher;

    TenantFeatureService service;

    static final UUID   TENANT_ID   = TenantContextTestHelper.DEFAULT_TENANT_ID.value();
    static final String FEATURE_KEY = "EXPORT_PDF";

    @BeforeEach
    void setUp() {
        service = new TenantFeatureService(featureRepository, overrideRepository,
                definitionService, eventPublisher);
        TenantContextTestHelper.setDefault();
    }

    @AfterEach
    void tearDown() {
        TenantContextTestHelper.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FeatureDefinitionEntity featureDef(String key, FeatureStatus status) {
        FeatureDefinitionEntity e = new FeatureDefinitionEntity();
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        e.setFeatureKey(key);
        e.setName("Export PDF");
        e.setStatus(status);
        return e;
    }

    private TenantFeatureOverrideEntity override(UUID tenantId, String key,
                                                  FeatureStatus status,
                                                  FeatureDefinitionEntity feature) {
        TenantFeatureOverrideEntity o = new TenantFeatureOverrideEntity();
        ReflectionTestUtils.setField(o, "id", UUID.randomUUID());
        o.setTenantId(tenantId);
        o.setFeatureKey(key);
        o.setStatus(status);
        o.setFeature(feature);
        return o;
    }

    // ── listEffectiveFeatures ─────────────────────────────────────────────────

    @Test
    @DisplayName("listEffectiveFeatures merges global + override for current tenant")
    void listEffectiveFeatures_mergesOverrides() {
        FeatureDefinitionEntity fDef = featureDef(FEATURE_KEY, FeatureStatus.DISABLED);
        TenantFeatureOverrideEntity ov = override(TENANT_ID, FEATURE_KEY, FeatureStatus.ENABLED, fDef);

        when(featureRepository.findAll()).thenReturn(List.of(fDef));
        when(overrideRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(ov));

        List<TenantFeatureResponse> result = service.listEffectiveFeatures();

        assertThat(result).hasSize(1);
        TenantFeatureResponse r = result.get(0);
        assertThat(r.globalStatus()).isEqualTo(FeatureStatus.DISABLED);
        assertThat(r.overriddenStatus()).isEqualTo(FeatureStatus.ENABLED);
        assertThat(r.effectiveStatus()).isEqualTo(FeatureStatus.ENABLED);
        assertThat(r.accessible()).isTrue();
    }

    @Test
    @DisplayName("listEffectiveFeatures uses global status when no override exists")
    void listEffectiveFeatures_noOverride() {
        FeatureDefinitionEntity fDef = featureDef(FEATURE_KEY, FeatureStatus.ENABLED);

        when(featureRepository.findAll()).thenReturn(List.of(fDef));
        when(overrideRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        List<TenantFeatureResponse> result = service.listEffectiveFeatures();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).effectiveStatus()).isEqualTo(FeatureStatus.ENABLED);
        assertThat(result.get(0).overriddenStatus()).isNull();
    }

    // ── getEffectiveFeature ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getEffectiveFeature")
    class GetEffectiveFeature {

        @Test
        @DisplayName("override status wins over global when override exists")
        void withOverride() {
            FeatureDefinitionEntity fDef = featureDef(FEATURE_KEY, FeatureStatus.DISABLED);
            TenantFeatureOverrideEntity ov = override(TENANT_ID, FEATURE_KEY, FeatureStatus.BETA, fDef);

            when(definitionService.findOrThrow(FEATURE_KEY)).thenReturn(fDef);
            when(overrideRepository.findByTenantIdAndFeatureKey(TENANT_ID, FEATURE_KEY))
                    .thenReturn(Optional.of(ov));

            TenantFeatureResponse result = service.getEffectiveFeature(FEATURE_KEY);

            assertThat(result.effectiveStatus()).isEqualTo(FeatureStatus.BETA);
            assertThat(result.overriddenStatus()).isEqualTo(FeatureStatus.BETA);
            assertThat(result.accessible()).isTrue();
        }

        @Test
        @DisplayName("global status used when no override exists")
        void withoutOverride() {
            FeatureDefinitionEntity fDef = featureDef(FEATURE_KEY, FeatureStatus.ENABLED);

            when(definitionService.findOrThrow(FEATURE_KEY)).thenReturn(fDef);
            when(overrideRepository.findByTenantIdAndFeatureKey(TENANT_ID, FEATURE_KEY))
                    .thenReturn(Optional.empty());

            TenantFeatureResponse result = service.getEffectiveFeature(FEATURE_KEY);

            assertThat(result.effectiveStatus()).isEqualTo(FeatureStatus.ENABLED);
            assertThat(result.overriddenStatus()).isNull();
        }
    }

    // ── setOverride ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setOverride")
    class SetOverride {

        @Test
        @DisplayName("creates new override when none exists and publishes ENABLED event")
        void createsNewOverride() {
            FeatureDefinitionEntity fDef = featureDef(FEATURE_KEY, FeatureStatus.DISABLED);
            SetTenantFeatureRequest req = new SetTenantFeatureRequest(FeatureStatus.ENABLED, null);

            when(definitionService.findOrThrow(FEATURE_KEY)).thenReturn(fDef);
            when(overrideRepository.findByTenantIdAndFeatureKey(TENANT_ID, FEATURE_KEY))
                    .thenReturn(Optional.empty());
            when(overrideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TenantFeatureResponse result = service.setOverride(FEATURE_KEY, req);

            assertThat(result.effectiveStatus()).isEqualTo(FeatureStatus.ENABLED);
            verify(overrideRepository).save(any(TenantFeatureOverrideEntity.class));
            verify(eventPublisher).publishTenantEnabled(fDef, TENANT_ID, null, null);
        }

        @Test
        @DisplayName("updates existing override and publishes DISABLED event")
        void updatesExistingOverride() {
            FeatureDefinitionEntity fDef = featureDef(FEATURE_KEY, FeatureStatus.ENABLED);
            TenantFeatureOverrideEntity existing = override(TENANT_ID, FEATURE_KEY,
                    FeatureStatus.ENABLED, fDef);
            SetTenantFeatureRequest req = new SetTenantFeatureRequest(FeatureStatus.DISABLED, null);

            when(definitionService.findOrThrow(FEATURE_KEY)).thenReturn(fDef);
            when(overrideRepository.findByTenantIdAndFeatureKey(TENANT_ID, FEATURE_KEY))
                    .thenReturn(Optional.of(existing));
            when(overrideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TenantFeatureResponse result = service.setOverride(FEATURE_KEY, req);

            assertThat(result.effectiveStatus()).isEqualTo(FeatureStatus.DISABLED);
            verify(eventPublisher).publishTenantDisabled(fDef, TENANT_ID, "ENABLED");
        }
    }

    // ── removeOverride ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeOverride")
    class RemoveOverride {

        @Test
        @DisplayName("deletes override and publishes tenantDisabled when override exists")
        void removesExistingOverride() {
            FeatureDefinitionEntity fDef = featureDef(FEATURE_KEY, FeatureStatus.ENABLED);
            TenantFeatureOverrideEntity existing = override(TENANT_ID, FEATURE_KEY,
                    FeatureStatus.DISABLED, fDef);

            when(definitionService.findOrThrow(FEATURE_KEY)).thenReturn(fDef);
            when(overrideRepository.findByTenantIdAndFeatureKey(TENANT_ID, FEATURE_KEY))
                    .thenReturn(Optional.of(existing));

            service.removeOverride(FEATURE_KEY);

            verify(overrideRepository).deleteByTenantIdAndFeatureKey(TENANT_ID, FEATURE_KEY);
            verify(eventPublisher).publishTenantDisabled(fDef, TENANT_ID, "DISABLED");
        }

        @Test
        @DisplayName("no-op when no override exists")
        void noopWhenNoOverride() {
            FeatureDefinitionEntity fDef = featureDef(FEATURE_KEY, FeatureStatus.ENABLED);

            when(definitionService.findOrThrow(FEATURE_KEY)).thenReturn(fDef);
            when(overrideRepository.findByTenantIdAndFeatureKey(TENANT_ID, FEATURE_KEY))
                    .thenReturn(Optional.empty());

            service.removeOverride(FEATURE_KEY);

            verify(overrideRepository, never()).deleteByTenantIdAndFeatureKey(any(), any());
            verify(eventPublisher, never()).publishTenantDisabled(any(), any(), any());
        }
    }
}
