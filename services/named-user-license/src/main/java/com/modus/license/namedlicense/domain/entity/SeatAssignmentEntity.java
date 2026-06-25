package com.modus.license.namedlicense.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A single named-user seat assignment within a license pool.
 *
 * A user can hold at most one seat per pool (unique constraint on license_id + user_id).
 * {@code createdAt} from JpaBaseEntity records when the seat was assigned.
 * {@code createdBy} records the admin UUID who performed the assignment.
 */
@Entity
@Table(
    name = "seat_assignments",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_seat_assignment_user", columnNames = {"license_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class SeatAssignmentEntity extends JpaBaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "license_id", nullable = false, updatable = false)
    private NamedLicenseEntity license;

    /** The user holding this seat. Cross-DB reference — no FK to user DB. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Denormalised email for display without a cross-service call. */
    @Column(nullable = false, length = 255, updatable = false)
    private String email;
}
