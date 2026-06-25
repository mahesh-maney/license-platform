package com.modus.license.user.service;

import com.modus.license.audit.annotation.AuditAction;
import com.modus.license.audit.annotation.Auditable;
import com.modus.license.core.context.TenantContext;
import com.modus.license.core.context.TenantContextHolder;
import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.user.api.dto.AssignRolesRequest;
import com.modus.license.user.api.dto.CreateUserRequest;
import com.modus.license.user.api.dto.UpdateUserRequest;
import com.modus.license.user.api.dto.UserResponse;
import com.modus.license.user.api.mapper.UserMapper;
import com.modus.license.user.domain.entity.UserEntity;
import com.modus.license.user.domain.enums.UserStatus;
import com.modus.license.user.domain.event.UserEventPublisher;
import com.modus.license.user.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;
    private final UserMapper mapper;
    private final UserEventPublisher eventPublisher;

    public UserService(UserRepository repository,
                       UserMapper mapper,
                       UserEventPublisher eventPublisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN')")
    @Auditable(action = AuditAction.CREATE, resourceType = "USER",
               resourceIdExpression = "#result.id().toString()")
    public UserResponse createUser(CreateUserRequest request) {
        UUID tenantId = currentTenantId();

        if (repository.existsByTenantIdAndEmail(tenantId, request.email())) {
            throw ConflictException.userAlreadyExists(request.email());
        }

        UserEntity entity = mapper.toEntity(request);
        entity.setTenantId(tenantId);
        entity.setStatus(UserStatus.ACTIVE);
        if (request.roles() != null) {
            entity.setRoles(new HashSet<>(request.roles()));
        }

        entity = repository.save(entity);
        log.info("Created user: id={} tenantId={} email={}", entity.getId(), tenantId, entity.getEmail());
        eventPublisher.publishCreated(entity);

        return mapper.toResponse(entity);
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public UserResponse getUser(UUID id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public UserResponse getUserByEmail(String email) {
        UUID tenantId = currentTenantId();
        return repository.findByTenantIdAndEmail(tenantId, email)
                .map(mapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.user(email));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public Page<UserResponse> listUsers(Pageable pageable) {
        UUID tenantId = currentTenantId();
        return repository.findByTenantId(tenantId, pageable).map(mapper::toResponse);
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN', 'READONLY')")
    public Page<UserResponse> listByStatus(UserStatus status, Pageable pageable) {
        UUID tenantId = currentTenantId();
        return repository.findByTenantIdAndStatus(tenantId, status, pageable).map(mapper::toResponse);
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN')")
    @Auditable(action = AuditAction.UPDATE, resourceType = "USER",
               resourceIdExpression = "#id.toString()")
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        UserEntity entity = findOrThrow(id);

        if (request.firstName() != null) entity.setFirstName(request.firstName());
        if (request.lastName() != null)  entity.setLastName(request.lastName());

        entity = repository.save(entity);
        log.info("Updated user: id={}", entity.getId());
        eventPublisher.publishUpdated(entity);

        return mapper.toResponse(entity);
    }

    // -------------------------------------------------------------------------
    // Role management
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN')")
    @Auditable(action = AuditAction.ASSIGN, resourceType = "USER",
               resourceIdExpression = "#id.toString()")
    public UserResponse assignRoles(UUID id, AssignRolesRequest request) {
        UserEntity entity = findOrThrow(id);
        Set<String> previousRoles = new HashSet<>(entity.getRoles());

        entity.getRoles().clear();
        entity.getRoles().addAll(request.roles());
        entity = repository.save(entity);

        log.info("Roles updated for user: id={} roles={}", entity.getId(), entity.getRoles());
        eventPublisher.publishRoleChanged(entity, previousRoles);

        return mapper.toResponse(entity);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN')")
    @Auditable(action = AuditAction.DEACTIVATE, resourceType = "USER",
               resourceIdExpression = "#id.toString()")
    public UserResponse deactivateUser(UUID id) {
        UserEntity entity = findOrThrow(id);
        String previousStatus = entity.getStatus().name();

        entity.setStatus(UserStatus.INACTIVE);
        entity = repository.save(entity);

        log.info("Deactivated user: id={}", entity.getId());
        eventPublisher.publishDeactivated(entity, previousStatus);

        return mapper.toResponse(entity);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'TENANT_ADMIN')")
    @Auditable(action = AuditAction.ACTIVATE, resourceType = "USER",
               resourceIdExpression = "#id.toString()")
    public UserResponse reactivateUser(UUID id) {
        UserEntity entity = findOrThrow(id);
        String previousStatus = entity.getStatus().name();

        entity.setStatus(UserStatus.ACTIVE);
        entity = repository.save(entity);

        log.info("Reactivated user: id={}", entity.getId());
        eventPublisher.publishReactivated(entity, previousStatus);

        return mapper.toResponse(entity);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Auditable(action = AuditAction.DELETE, resourceType = "USER",
               resourceIdExpression = "#id.toString()")
    public void deleteUser(UUID id) {
        UserEntity entity = findOrThrow(id);
        entity.setStatus(UserStatus.INACTIVE);
        repository.save(entity);

        log.info("Soft-deleted user: id={}", entity.getId());
        eventPublisher.publishDeleted(entity);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private UserEntity findOrThrow(UUID id) {
        UUID tenantId = currentTenantId();
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> ResourceNotFoundException.user(id.toString()));
    }

    private UUID currentTenantId() {
        TenantContext ctx = TenantContextHolder.require();
        return ctx.tenantId().value();
    }
}
