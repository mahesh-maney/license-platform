package com.modus.license.namedlicense.service;

import com.modus.license.audit.annotation.AuditAction;
import com.modus.license.audit.annotation.Auditable;
import com.modus.license.core.context.TenantContextHolder;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.LicenseEnforcementException;
import com.modus.license.core.exception.ModusException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.core.exception.ErrorCode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NamedLicenseService {

    private static final Logger log = LoggerFactory.getLogger(NamedLicenseService.class);

    private final NamedLicenseRepository licenseRepository;
    private final SeatAssignmentRepository assignmentRepository;
    private final NamedLicenseMapper mapper;
    private final NamedLicenseEventPublisher eventPublisher;

    public NamedLicenseService(NamedLicenseRepository licenseRepository,
                                SeatAssignmentRepository assignmentRepository,
                                NamedLicenseMapper mapper,
                                NamedLicenseEventPublisher eventPublisher) {
        this.licenseRepository = licenseRepository;
        this.assignmentRepository = assignmentRepository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    // ── Pool management ──────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.CREATE, resourceType = "NAMED_LICENSE",
               resourceIdExpression = "#result.id().toString()")
    public LicensePoolResponse createPool(CreateLicensePoolRequest request) {
        UUID tenantId = currentTenantId();

        if (licenseRepository.existsByTenantIdAndEntitlementId(tenantId, request.entitlementId())) {
            throw new ConflictException(ErrorCode.CONFLICT,
                    "License pool already exists for entitlement: " + request.entitlementId());
        }

        NamedLicenseEntity license = new NamedLicenseEntity();
        license.setTenantId(tenantId);
        license.setPlanId(request.planId());
        license.setEntitlementId(request.entitlementId());
        license.setTotalSeats(request.totalSeats());

        return mapper.toResponse(licenseRepository.save(license));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    public LicensePoolResponse getPool(UUID licenseId) {
        return mapper.toResponse(requirePool(currentTenantId(), licenseId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    public Page<LicensePoolResponse> listPools(Pageable pageable) {
        return licenseRepository.findByTenantId(currentTenantId(), pageable)
                .map(mapper::toResponse);
    }

    // ── Seat management ───────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.CREATE, resourceType = "SEAT_ASSIGNMENT",
               resourceIdExpression = "#result.id().toString()")
    public SeatAssignmentResponse assignSeat(UUID licenseId, AssignSeatRequest request) {
        UUID tenantId = currentTenantId();
        NamedLicenseEntity license = requirePool(tenantId, licenseId);

        if (!license.hasCapacity()) {
            throw LicenseEnforcementException.seatLimitExceeded(tenantId.toString());
        }
        if (assignmentRepository.existsByLicenseIdAndUserId(licenseId, request.userId())) {
            throw ConflictException.licenseAlreadyAssigned(request.userId().toString());
        }

        SeatAssignmentEntity assignment = new SeatAssignmentEntity();
        assignment.setLicense(license);
        assignment.setUserId(request.userId());
        assignment.setEmail(request.email());
        assignmentRepository.save(assignment);

        license.setUsedSeats(license.getUsedSeats() + 1);
        licenseRepository.save(license);

        eventPublisher.publishAssigned(license, request.userId());
        return mapper.toAssignmentResponse(assignment);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.DELETE, resourceType = "SEAT_ASSIGNMENT",
               resourceIdExpression = "#args[1].toString()")
    public void revokeSeat(UUID licenseId, UUID userId) {
        UUID tenantId = currentTenantId();
        NamedLicenseEntity license = requirePool(tenantId, licenseId);

        SeatAssignmentEntity assignment = assignmentRepository
                .findByLicenseIdAndUserId(licenseId, userId)
                .orElseThrow(() -> new ModusException(ErrorCode.LICENSE_NOT_ASSIGNED,
                        "User " + userId + " does not hold a seat in license " + licenseId));

        assignmentRepository.delete(assignment);
        license.setUsedSeats(license.getUsedSeats() - 1);
        licenseRepository.save(license);

        eventPublisher.publishRevoked(license, userId);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "SEAT_ASSIGNMENT",
               resourceIdExpression = "#result.id().toString()")
    public SeatAssignmentResponse transferSeat(UUID licenseId, TransferSeatRequest request) {
        UUID tenantId = currentTenantId();
        NamedLicenseEntity license = requirePool(tenantId, licenseId);

        SeatAssignmentEntity fromAssignment = assignmentRepository
                .findByLicenseIdAndUserId(licenseId, request.fromUserId())
                .orElseThrow(() -> new ModusException(ErrorCode.LICENSE_NOT_ASSIGNED,
                        "User " + request.fromUserId() + " does not hold a seat in license " + licenseId));

        if (assignmentRepository.existsByLicenseIdAndUserId(licenseId, request.toUserId())) {
            throw ConflictException.licenseAlreadyAssigned(request.toUserId().toString());
        }

        assignmentRepository.delete(fromAssignment);

        SeatAssignmentEntity toAssignment = new SeatAssignmentEntity();
        toAssignment.setLicense(license);
        toAssignment.setUserId(request.toUserId());
        toAssignment.setEmail(request.toUserEmail());
        assignmentRepository.save(toAssignment);

        // usedSeats unchanged — one out, one in
        eventPublisher.publishTransferred(license, request.fromUserId(), request.toUserId());
        return mapper.toAssignmentResponse(toAssignment);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    public Page<SeatAssignmentResponse> listAssignments(UUID licenseId, Pageable pageable) {
        requirePool(currentTenantId(), licenseId);
        return assignmentRepository.findByLicenseId(licenseId, pageable)
                .map(mapper::toAssignmentResponse);
    }

    // ── Internal (called by Kafka consumers — no @PreAuthorize) ─────────────

    @Transactional
    public void processEntitlementGranted(UUID tenantId, UUID planId,
                                           UUID entitlementId, int seatLimit) {
        if (licenseRepository.existsByTenantIdAndEntitlementId(tenantId, entitlementId)) {
            log.info("Pool already exists for entitlement={}, skipping creation", entitlementId);
            return;
        }
        NamedLicenseEntity license = new NamedLicenseEntity();
        license.setTenantId(tenantId);
        license.setPlanId(planId);
        license.setEntitlementId(entitlementId);
        license.setTotalSeats(seatLimit);
        licenseRepository.save(license);
        log.info("Created named-license pool for tenant={} entitlement={} seats={}",
                tenantId, entitlementId, seatLimit);
    }

    @Transactional
    public void processEntitlementSeatLimitChanged(UUID tenantId, UUID entitlementId, int newSeatLimit) {
        licenseRepository.findByTenantIdAndEntitlementId(tenantId, entitlementId).ifPresent(license -> {
            int previous = license.getTotalSeats();
            license.setTotalSeats(newSeatLimit);
            licenseRepository.save(license);
            eventPublisher.publishSeatLimitChanged(license, previous);
            log.info("Updated seat limit for pool={} from {} to {}",
                    license.getId(), previous, newSeatLimit);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NamedLicenseEntity requirePool(UUID tenantId, UUID licenseId) {
        return licenseRepository.findByTenantIdAndId(tenantId, licenseId)
                .orElseThrow(() -> ResourceNotFoundException.license(licenseId.toString()));
    }

    private UUID currentTenantId() {
        return TenantContextHolder.require().tenantId().value();
    }
}
