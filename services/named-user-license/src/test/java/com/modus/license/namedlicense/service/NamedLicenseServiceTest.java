package com.modus.license.namedlicense.service;

import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.LicenseEnforcementException;
import com.modus.license.core.exception.ModusException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.namedlicense.api.dto.AssignSeatRequest;
import com.modus.license.namedlicense.api.dto.CreateLicensePoolRequest;
import com.modus.license.namedlicense.api.dto.LicensePoolResponse;
import com.modus.license.namedlicense.api.dto.SeatAssignmentResponse;
import com.modus.license.namedlicense.api.dto.TransferSeatRequest;
import com.modus.license.namedlicense.api.mapper.NamedLicenseMapper;
import com.modus.license.namedlicense.domain.entity.NamedLicenseEntity;
import com.modus.license.namedlicense.domain.entity.SeatAssignmentEntity;
import com.modus.license.namedlicense.domain.event.NamedLicenseEventPublisher;
import com.modus.license.namedlicense.domain.repository.NamedLicenseRepository;
import com.modus.license.namedlicense.domain.repository.SeatAssignmentRepository;
import com.modus.license.test.context.TenantContextTestHelper;
import org.junit.jupiter.api.AfterEach;
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
@DisplayName("NamedLicenseService")
class NamedLicenseServiceTest {

    @Mock NamedLicenseRepository    licenseRepository;
    @Mock SeatAssignmentRepository  assignmentRepository;
    @Mock NamedLicenseMapper        mapper;
    @Mock NamedLicenseEventPublisher eventPublisher;

    NamedLicenseService service;

    static final UUID TENANT_ID     = TenantContextTestHelper.DEFAULT_TENANT_ID.value();
    static final UUID PLAN_ID       = UUID.randomUUID();
    static final UUID ENTITLEMENT_ID = UUID.randomUUID();
    static final UUID USER_A        = UUID.randomUUID();
    static final UUID USER_B        = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NamedLicenseService(licenseRepository, assignmentRepository,
                mapper, eventPublisher);
        TenantContextTestHelper.setDefault();
    }

    @AfterEach
    void tearDown() {
        TenantContextTestHelper.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NamedLicenseEntity pool(UUID id, int total, int used) {
        NamedLicenseEntity e = new NamedLicenseEntity();
        ReflectionTestUtils.setField(e, "id", id);
        e.setTenantId(TENANT_ID);
        e.setPlanId(PLAN_ID);
        e.setEntitlementId(ENTITLEMENT_ID);
        e.setTotalSeats(total);
        e.setUsedSeats(used);
        return e;
    }

    private LicensePoolResponse poolResponse(UUID id, int total, int used) {
        return new LicensePoolResponse(id, TENANT_ID, PLAN_ID, ENTITLEMENT_ID,
                total, used, total - used, Instant.now(), Instant.now());
    }

    private SeatAssignmentEntity assignment(UUID id, NamedLicenseEntity license, UUID userId) {
        SeatAssignmentEntity a = new SeatAssignmentEntity();
        ReflectionTestUtils.setField(a, "id", id);
        a.setLicense(license);
        a.setUserId(userId);
        a.setEmail(userId + "@example.com");
        return a;
    }

    private SeatAssignmentResponse assignmentResponse(UUID id, UUID licenseId, UUID userId) {
        return new SeatAssignmentResponse(id, licenseId, userId,
                userId + "@example.com", Instant.now(), "system");
    }

    // ── createPool ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPool")
    class CreatePool {

        @Test
        @DisplayName("saves pool and returns response")
        void success() {
            UUID id = UUID.randomUUID();
            NamedLicenseEntity e = pool(id, 10, 0);
            CreateLicensePoolRequest req =
                    new CreateLicensePoolRequest(PLAN_ID, ENTITLEMENT_ID, 10);

            when(licenseRepository.existsByTenantIdAndEntitlementId(TENANT_ID, ENTITLEMENT_ID))
                    .thenReturn(false);
            when(licenseRepository.save(any())).thenAnswer(inv -> {
                NamedLicenseEntity saved = inv.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", id);
                return saved;
            });
            when(mapper.toResponse(any())).thenReturn(poolResponse(id, 10, 0));

            LicensePoolResponse result = service.createPool(req);

            assertThat(result.totalSeats()).isEqualTo(10);
            assertThat(result.tenantId()).isEqualTo(TENANT_ID);
            verify(licenseRepository).save(any(NamedLicenseEntity.class));
        }

        @Test
        @DisplayName("throws ConflictException when pool already exists for entitlement")
        void duplicate() {
            when(licenseRepository.existsByTenantIdAndEntitlementId(TENANT_ID, ENTITLEMENT_ID))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.createPool(
                    new CreateLicensePoolRequest(PLAN_ID, ENTITLEMENT_ID, 10)))
                    .isInstanceOf(ConflictException.class);

            verify(licenseRepository, never()).save(any());
        }
    }

    // ── getPool ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPool")
    class GetPool {

        @Test
        @DisplayName("returns response when found")
        void found() {
            UUID id = UUID.randomUUID();
            NamedLicenseEntity e = pool(id, 10, 2);
            when(licenseRepository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.of(e));
            when(mapper.toResponse(e)).thenReturn(poolResponse(id, 10, 2));

            assertThat(service.getPool(id).id()).isEqualTo(id);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void notFound() {
            UUID id = UUID.randomUUID();
            when(licenseRepository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPool(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── listPools ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listPools delegates to repo with current tenantId")
    void listPools() {
        UUID id = UUID.randomUUID();
        NamedLicenseEntity e = pool(id, 10, 0);
        when(licenseRepository.findByTenantId(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));
        when(mapper.toResponse(any())).thenReturn(poolResponse(id, 10, 0));

        assertThat(service.listPools(Pageable.unpaged()).getContent()).hasSize(1);
    }

    // ── assignSeat ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("assignSeat")
    class AssignSeat {

        @Test
        @DisplayName("assigns seat, increments usedSeats, publishes ASSIGNED")
        void success() {
            UUID licenseId = UUID.randomUUID();
            UUID assignId  = UUID.randomUUID();
            NamedLicenseEntity e = pool(licenseId, 5, 2);   // has capacity
            SeatAssignmentEntity sa = assignment(assignId, e, USER_A);
            AssignSeatRequest req = new AssignSeatRequest(USER_A, "user_a@example.com");

            when(licenseRepository.findByTenantIdAndId(TENANT_ID, licenseId)).thenReturn(Optional.of(e));
            when(assignmentRepository.existsByLicenseIdAndUserId(licenseId, USER_A)).thenReturn(false);
            when(assignmentRepository.save(any())).thenAnswer(inv -> {
                SeatAssignmentEntity s = inv.getArgument(0);
                ReflectionTestUtils.setField(s, "id", assignId);
                return s;
            });
            when(licenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toAssignmentResponse(any()))
                    .thenReturn(assignmentResponse(assignId, licenseId, USER_A));

            SeatAssignmentResponse result = service.assignSeat(licenseId, req);

            assertThat(result.userId()).isEqualTo(USER_A);
            assertThat(e.getUsedSeats()).isEqualTo(3);
            verify(eventPublisher).publishAssigned(e, USER_A);
        }

        @Test
        @DisplayName("throws LicenseEnforcementException when pool is at capacity")
        void noCapacity() {
            UUID licenseId = UUID.randomUUID();
            NamedLicenseEntity e = pool(licenseId, 5, 5);   // full
            when(licenseRepository.findByTenantIdAndId(TENANT_ID, licenseId)).thenReturn(Optional.of(e));

            assertThatThrownBy(() -> service.assignSeat(licenseId,
                    new AssignSeatRequest(USER_A, "a@example.com")))
                    .isInstanceOf(LicenseEnforcementException.class);

            verify(assignmentRepository, never()).save(any());
            verify(eventPublisher, never()).publishAssigned(any(), any());
        }

        @Test
        @DisplayName("throws ConflictException when user already holds a seat")
        void alreadyAssigned() {
            UUID licenseId = UUID.randomUUID();
            NamedLicenseEntity e = pool(licenseId, 5, 1);
            when(licenseRepository.findByTenantIdAndId(TENANT_ID, licenseId)).thenReturn(Optional.of(e));
            when(assignmentRepository.existsByLicenseIdAndUserId(licenseId, USER_A)).thenReturn(true);

            assertThatThrownBy(() -> service.assignSeat(licenseId,
                    new AssignSeatRequest(USER_A, "a@example.com")))
                    .isInstanceOf(ConflictException.class);

            verify(assignmentRepository, never()).save(any());
        }
    }

    // ── revokeSeat ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("revokeSeat")
    class RevokeSeat {

        @Test
        @DisplayName("deletes assignment, decrements usedSeats, publishes REVOKED")
        void success() {
            UUID licenseId = UUID.randomUUID();
            NamedLicenseEntity e = pool(licenseId, 5, 3);
            SeatAssignmentEntity sa = assignment(UUID.randomUUID(), e, USER_A);

            when(licenseRepository.findByTenantIdAndId(TENANT_ID, licenseId)).thenReturn(Optional.of(e));
            when(assignmentRepository.findByLicenseIdAndUserId(licenseId, USER_A))
                    .thenReturn(Optional.of(sa));
            when(licenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.revokeSeat(licenseId, USER_A);

            assertThat(e.getUsedSeats()).isEqualTo(2);
            verify(assignmentRepository).delete(sa);
            verify(eventPublisher).publishRevoked(e, USER_A);
        }

        @Test
        @DisplayName("throws ModusException when user does not hold a seat")
        void notAssigned() {
            UUID licenseId = UUID.randomUUID();
            NamedLicenseEntity e = pool(licenseId, 5, 1);
            when(licenseRepository.findByTenantIdAndId(TENANT_ID, licenseId)).thenReturn(Optional.of(e));
            when(assignmentRepository.findByLicenseIdAndUserId(licenseId, USER_A))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.revokeSeat(licenseId, USER_A))
                    .isInstanceOf(ModusException.class);

            verify(assignmentRepository, never()).delete(any());
        }
    }

    // ── transferSeat ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("transferSeat")
    class TransferSeat {

        @Test
        @DisplayName("deletes from-assignment, creates to-assignment, publishes TRANSFERRED")
        void success() {
            UUID licenseId  = UUID.randomUUID();
            UUID newAssignId = UUID.randomUUID();
            NamedLicenseEntity e = pool(licenseId, 5, 2);
            SeatAssignmentEntity fromSa = assignment(UUID.randomUUID(), e, USER_A);
            TransferSeatRequest req = new TransferSeatRequest(USER_A, USER_B, "user_b@example.com");

            when(licenseRepository.findByTenantIdAndId(TENANT_ID, licenseId)).thenReturn(Optional.of(e));
            when(assignmentRepository.findByLicenseIdAndUserId(licenseId, USER_A))
                    .thenReturn(Optional.of(fromSa));
            when(assignmentRepository.existsByLicenseIdAndUserId(licenseId, USER_B)).thenReturn(false);
            when(assignmentRepository.save(any())).thenAnswer(inv -> {
                SeatAssignmentEntity s = inv.getArgument(0);
                ReflectionTestUtils.setField(s, "id", newAssignId);
                return s;
            });
            when(mapper.toAssignmentResponse(any()))
                    .thenReturn(assignmentResponse(newAssignId, licenseId, USER_B));

            SeatAssignmentResponse result = service.transferSeat(licenseId, req);

            assertThat(result.userId()).isEqualTo(USER_B);
            assertThat(e.getUsedSeats()).isEqualTo(2); // unchanged
            verify(assignmentRepository).delete(fromSa);
            verify(eventPublisher).publishTransferred(e, USER_A, USER_B);
        }

        @Test
        @DisplayName("throws ModusException when fromUser does not hold a seat")
        void fromUserNotAssigned() {
            UUID licenseId = UUID.randomUUID();
            NamedLicenseEntity e = pool(licenseId, 5, 1);
            when(licenseRepository.findByTenantIdAndId(TENANT_ID, licenseId)).thenReturn(Optional.of(e));
            when(assignmentRepository.findByLicenseIdAndUserId(licenseId, USER_A))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.transferSeat(licenseId,
                    new TransferSeatRequest(USER_A, USER_B, "b@example.com")))
                    .isInstanceOf(ModusException.class);

            verify(assignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ConflictException when toUser already holds a seat")
        void toUserAlreadyAssigned() {
            UUID licenseId = UUID.randomUUID();
            NamedLicenseEntity e = pool(licenseId, 5, 2);
            SeatAssignmentEntity fromSa = assignment(UUID.randomUUID(), e, USER_A);

            when(licenseRepository.findByTenantIdAndId(TENANT_ID, licenseId)).thenReturn(Optional.of(e));
            when(assignmentRepository.findByLicenseIdAndUserId(licenseId, USER_A))
                    .thenReturn(Optional.of(fromSa));
            when(assignmentRepository.existsByLicenseIdAndUserId(licenseId, USER_B)).thenReturn(true);

            assertThatThrownBy(() -> service.transferSeat(licenseId,
                    new TransferSeatRequest(USER_A, USER_B, "b@example.com")))
                    .isInstanceOf(ConflictException.class);
        }
    }

    // ── processEntitlementGranted ─────────────────────────────────────────────

    @Nested
    @DisplayName("processEntitlementGranted")
    class ProcessEntitlementGranted {

        @Test
        @DisplayName("creates pool when none exists")
        void createsPool() {
            when(licenseRepository.existsByTenantIdAndEntitlementId(TENANT_ID, ENTITLEMENT_ID))
                    .thenReturn(false);
            when(licenseRepository.save(any())).thenAnswer(inv -> {
                NamedLicenseEntity saved = inv.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
                return saved;
            });

            service.processEntitlementGranted(TENANT_ID, PLAN_ID, ENTITLEMENT_ID, 25);

            verify(licenseRepository).save(any(NamedLicenseEntity.class));
        }

        @Test
        @DisplayName("skips creation when pool already exists (idempotent)")
        void skipsIfExists() {
            when(licenseRepository.existsByTenantIdAndEntitlementId(TENANT_ID, ENTITLEMENT_ID))
                    .thenReturn(true);

            service.processEntitlementGranted(TENANT_ID, PLAN_ID, ENTITLEMENT_ID, 25);

            verify(licenseRepository, never()).save(any());
        }
    }

    // ── processEntitlementSeatLimitChanged ────────────────────────────────────

    @Test
    @DisplayName("processEntitlementSeatLimitChanged updates totalSeats and publishes event")
    void processEntitlementSeatLimitChanged() {
        UUID licenseId = UUID.randomUUID();
        NamedLicenseEntity e = pool(licenseId, 10, 3);

        when(licenseRepository.findByTenantIdAndEntitlementId(TENANT_ID, ENTITLEMENT_ID))
                .thenReturn(Optional.of(e));
        when(licenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processEntitlementSeatLimitChanged(TENANT_ID, ENTITLEMENT_ID, 50);

        assertThat(e.getTotalSeats()).isEqualTo(50);
        verify(eventPublisher).publishSeatLimitChanged(e, 10);
    }
}
