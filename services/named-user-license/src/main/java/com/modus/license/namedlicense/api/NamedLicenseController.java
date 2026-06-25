package com.modus.license.namedlicense.api;

import com.modus.license.core.web.ApiResponse;
import com.modus.license.core.web.PageResponse;
import com.modus.license.namedlicense.api.dto.AssignSeatRequest;
import com.modus.license.namedlicense.api.dto.CreateLicensePoolRequest;
import com.modus.license.namedlicense.api.dto.LicensePoolResponse;
import com.modus.license.namedlicense.api.dto.SeatAssignmentResponse;
import com.modus.license.namedlicense.api.dto.TransferSeatRequest;
import com.modus.license.namedlicense.service.NamedLicenseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Named-user license pool and seat assignment endpoints.
 *
 * All routes are tenant-scoped — tenantId is resolved from the JWT via TenantContextFilter.
 */
@RestController
@RequestMapping("/api/v1/named-licenses")
public class NamedLicenseController {

    private final NamedLicenseService service;

    public NamedLicenseController(NamedLicenseService service) {
        this.service = service;
    }

    // ── Pools ──────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<LicensePoolResponse>> createPool(
            @Valid @RequestBody CreateLicensePoolRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(service.createPool(request)));
    }

    @GetMapping("/{licenseId}")
    public ResponseEntity<ApiResponse<LicensePoolResponse>> getPool(
            @PathVariable UUID licenseId) {
        return ResponseEntity.ok(ApiResponse.of(service.getPool(licenseId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<LicensePoolResponse>>> listPools(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<LicensePoolResponse> page = service.listPools(pageable);
        return ResponseEntity.ok(ApiResponse.of(
                PageResponse.of(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements())));
    }

    // ── Seats ──────────────────────────────────────────────────────────────

    @PostMapping("/{licenseId}/seats")
    public ResponseEntity<ApiResponse<SeatAssignmentResponse>> assignSeat(
            @PathVariable UUID licenseId,
            @Valid @RequestBody AssignSeatRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(service.assignSeat(licenseId, request)));
    }

    @DeleteMapping("/{licenseId}/seats/{userId}")
    public ResponseEntity<Void> revokeSeat(
            @PathVariable UUID licenseId,
            @PathVariable UUID userId) {
        service.revokeSeat(licenseId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{licenseId}/seats/transfer")
    public ResponseEntity<ApiResponse<SeatAssignmentResponse>> transferSeat(
            @PathVariable UUID licenseId,
            @Valid @RequestBody TransferSeatRequest request) {
        return ResponseEntity.ok(ApiResponse.of(service.transferSeat(licenseId, request)));
    }

    @GetMapping("/{licenseId}/seats")
    public ResponseEntity<ApiResponse<PageResponse<SeatAssignmentResponse>>> listAssignments(
            @PathVariable UUID licenseId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<SeatAssignmentResponse> page = service.listAssignments(licenseId, pageable);
        return ResponseEntity.ok(ApiResponse.of(
                PageResponse.of(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements())));
    }
}
