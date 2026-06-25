package com.modus.license.audit.service;

import com.modus.license.audit.api.dto.AuditLogResponse;
import com.modus.license.audit.api.mapper.AuditLogMapper;
import com.modus.license.audit.domain.repository.AuditLogRepository;
import com.modus.license.core.context.TenantContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;
    private final AuditLogMapper     mapper;

    public AuditLogService(AuditLogRepository repository, AuditLogMapper mapper) {
        this.repository = repository;
        this.mapper     = mapper;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    public Page<AuditLogResponse> search(String actorId, String resourceType,
                                          String resourceId, String outcome,
                                          Instant from, Instant to,
                                          Pageable pageable) {
        UUID tenantId = TenantContextHolder.require().tenantId().value();
        return repository.search(tenantId, actorId, resourceType, resourceId,
                        outcome, from, to, pageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    public Optional<AuditLogResponse> getById(UUID auditId) {
        UUID tenantId = TenantContextHolder.require().tenantId().value();
        return repository.findById(auditId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .map(mapper::toResponse);
    }
}
