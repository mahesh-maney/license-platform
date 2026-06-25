package com.modus.license.user.domain.entity;

import com.modus.license.user.domain.enums.UserStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(name = "uq_users_tenant_email", columnNames = {"tenant_id", "email"})
)
@Getter
@Setter
@NoArgsConstructor
public class UserEntity extends JpaBaseEntity {

    /**
     * Owning tenant. Stored as a plain UUID column — no FK constraint because
     * tenants live in a separate database (tenant-management service).
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    /**
     * Roles assigned to this user within the tenant.
     * Stored in a separate {@code user_roles} join table.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role", length = 100)
    private Set<String> roles = new HashSet<>();
}
