package com.modus.license.user.service;

import com.modus.license.core.exception.ConflictException;
import com.modus.license.core.exception.ResourceNotFoundException;
import com.modus.license.test.context.TenantContextTestHelper;
import com.modus.license.user.api.dto.AssignRolesRequest;
import com.modus.license.user.api.dto.CreateUserRequest;
import com.modus.license.user.api.dto.UpdateUserRequest;
import com.modus.license.user.api.dto.UserResponse;
import com.modus.license.user.api.mapper.UserMapper;
import com.modus.license.user.domain.entity.UserEntity;
import com.modus.license.user.domain.enums.UserStatus;
import com.modus.license.user.domain.event.UserEventPublisher;
import com.modus.license.user.domain.repository.UserRepository;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock UserRepository    repository;
    @Mock UserMapper        mapper;
    @Mock UserEventPublisher eventPublisher;

    UserService service;

    static final UUID   TENANT_ID = TenantContextTestHelper.DEFAULT_TENANT_ID.value();
    static final String EMAIL     = "alice@example.com";

    @BeforeEach
    void setUp() {
        service = new UserService(repository, mapper, eventPublisher);
        TenantContextTestHelper.setDefault();
    }

    @AfterEach
    void tearDown() {
        TenantContextTestHelper.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserEntity entity(UUID id, String email, UserStatus status) {
        UserEntity e = new UserEntity();
        ReflectionTestUtils.setField(e, "id", id);
        e.setTenantId(TENANT_ID);
        e.setEmail(email);
        e.setFirstName("Alice");
        e.setLastName("Smith");
        e.setStatus(status);
        e.setRoles(new java.util.HashSet<>(Set.of("TENANT_USER")));
        return e;
    }

    private UserResponse response(UUID id, String email, UserStatus status) {
        return new UserResponse(id, TENANT_ID, email, "Alice", "Smith",
                status, Set.of("TENANT_USER"), Instant.now(), Instant.now());
    }

    // ── createUser ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("saves entity, publishes CREATED, returns response")
        void success() {
            UUID id = UUID.randomUUID();
            UserEntity e = entity(id, EMAIL, UserStatus.ACTIVE);
            CreateUserRequest req = new CreateUserRequest(EMAIL, "Alice", "Smith", Set.of("TENANT_USER"));

            when(repository.existsByTenantIdAndEmail(TENANT_ID, EMAIL)).thenReturn(false);
            when(mapper.toEntity(req)).thenReturn(e);
            when(repository.save(e)).thenReturn(e);
            when(mapper.toResponse(e)).thenReturn(response(id, EMAIL, UserStatus.ACTIVE));

            UserResponse result = service.createUser(req);

            assertThat(result.email()).isEqualTo(EMAIL);
            assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
            verify(repository).save(e);
            verify(eventPublisher).publishCreated(e);
        }

        @Test
        @DisplayName("throws ConflictException when email already exists for tenant")
        void duplicateEmail() {
            when(repository.existsByTenantIdAndEmail(TENANT_ID, EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> service.createUser(
                    new CreateUserRequest(EMAIL, "Alice", "Smith", Set.of())))
                    .isInstanceOf(ConflictException.class);

            verify(repository, never()).save(any());
            verify(eventPublisher, never()).publishCreated(any());
        }
    }

    // ── getUser ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUser")
    class GetUser {

        @Test
        @DisplayName("returns response when found")
        void found() {
            UUID id = UUID.randomUUID();
            UserEntity e = entity(id, EMAIL, UserStatus.ACTIVE);
            when(repository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.of(e));
            when(mapper.toResponse(e)).thenReturn(response(id, EMAIL, UserStatus.ACTIVE));

            assertThat(service.getUser(id).id()).isEqualTo(id);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void notFound() {
            UUID id = UUID.randomUUID();
            when(repository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUser(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getUserByEmail ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserByEmail")
    class GetUserByEmail {

        @Test
        @DisplayName("returns response when found")
        void found() {
            UUID id = UUID.randomUUID();
            UserEntity e = entity(id, EMAIL, UserStatus.ACTIVE);
            when(repository.findByTenantIdAndEmail(TENANT_ID, EMAIL)).thenReturn(Optional.of(e));
            when(mapper.toResponse(e)).thenReturn(response(id, EMAIL, UserStatus.ACTIVE));

            assertThat(service.getUserByEmail(EMAIL).email()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when email not registered")
        void notFound() {
            when(repository.findByTenantIdAndEmail(TENANT_ID, EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUserByEmail(EMAIL))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── listUsers ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listUsers delegates to repo.findByTenantId")
    void listUsers() {
        UUID id = UUID.randomUUID();
        UserEntity e = entity(id, EMAIL, UserStatus.ACTIVE);
        when(repository.findByTenantId(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));
        when(mapper.toResponse(any())).thenReturn(response(id, EMAIL, UserStatus.ACTIVE));

        assertThat(service.listUsers(Pageable.unpaged()).getContent()).hasSize(1);
        verify(repository).findByTenantId(eq(TENANT_ID), any());
    }

    // ── listByStatus ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listByStatus delegates to repo.findByTenantIdAndStatus")
    void listByStatus() {
        UUID id = UUID.randomUUID();
        UserEntity e = entity(id, EMAIL, UserStatus.INACTIVE);
        when(repository.findByTenantIdAndStatus(eq(TENANT_ID), eq(UserStatus.INACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));
        when(mapper.toResponse(any())).thenReturn(response(id, EMAIL, UserStatus.INACTIVE));

        assertThat(service.listByStatus(UserStatus.INACTIVE, Pageable.unpaged()).getContent()).hasSize(1);
        verify(repository).findByTenantIdAndStatus(eq(TENANT_ID), eq(UserStatus.INACTIVE), any());
    }

    // ── updateUser ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("patches non-null fields, saves, publishes UPDATED")
        void success() {
            UUID id = UUID.randomUUID();
            UserEntity e = entity(id, EMAIL, UserStatus.ACTIVE);
            when(repository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(response(id, EMAIL, UserStatus.ACTIVE));

            service.updateUser(id, new UpdateUserRequest("Bob", "Jones"));

            assertThat(e.getFirstName()).isEqualTo("Bob");
            assertThat(e.getLastName()).isEqualTo("Jones");
            verify(eventPublisher).publishUpdated(e);
        }

        @Test
        @DisplayName("null fields leave existing values unchanged")
        void nullFieldsUnchanged() {
            UUID id = UUID.randomUUID();
            UserEntity e = entity(id, EMAIL, UserStatus.ACTIVE);
            e.setFirstName("Original");
            e.setLastName("Name");
            when(repository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.of(e));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toResponse(any())).thenReturn(response(id, EMAIL, UserStatus.ACTIVE));

            service.updateUser(id, new UpdateUserRequest(null, null));

            assertThat(e.getFirstName()).isEqualTo("Original");
            assertThat(e.getLastName()).isEqualTo("Name");
        }
    }

    // ── assignRoles ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("assignRoles replaces role set and publishes ROLE_CHANGED")
    void assignRoles() {
        UUID id = UUID.randomUUID();
        UserEntity e = entity(id, EMAIL, UserStatus.ACTIVE);
        when(repository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response(id, EMAIL, UserStatus.ACTIVE));

        service.assignRoles(id, new AssignRolesRequest(Set.of("TENANT_ADMIN", "READONLY")));

        assertThat(e.getRoles()).containsExactlyInAnyOrder("TENANT_ADMIN", "READONLY");
        verify(eventPublisher).publishRoleChanged(eq(e), any());
    }

    // ── deactivateUser ────────────────────────────────────────────────────────

    @Test
    @DisplayName("deactivateUser sets INACTIVE and publishes DEACTIVATED")
    void deactivateUser() {
        UUID id = UUID.randomUUID();
        UserEntity e = entity(id, EMAIL, UserStatus.ACTIVE);
        when(repository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response(id, EMAIL, UserStatus.INACTIVE));

        service.deactivateUser(id);

        assertThat(e.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(eventPublisher).publishDeactivated(e, "ACTIVE");
    }

    // ── reactivateUser ────────────────────────────────────────────────────────

    @Test
    @DisplayName("reactivateUser sets ACTIVE and publishes REACTIVATED")
    void reactivateUser() {
        UUID id = UUID.randomUUID();
        UserEntity e = entity(id, EMAIL, UserStatus.INACTIVE);
        when(repository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(response(id, EMAIL, UserStatus.ACTIVE));

        service.reactivateUser(id);

        assertThat(e.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(eventPublisher).publishReactivated(e, "INACTIVE");
    }

    // ── deleteUser ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteUser soft-deletes (INACTIVE) and publishes DELETED")
    void deleteUser() {
        UUID id = UUID.randomUUID();
        UserEntity e = entity(id, EMAIL, UserStatus.ACTIVE);
        when(repository.findByTenantIdAndId(TENANT_ID, id)).thenReturn(Optional.of(e));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deleteUser(id);

        assertThat(e.getStatus()).isEqualTo(UserStatus.INACTIVE);
        verify(eventPublisher).publishDeleted(e);
    }
}
