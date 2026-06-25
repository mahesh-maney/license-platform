package com.modus.license.namedlicense.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A tenant's named-user license pool, derived from a NAMED_USER entitlement.
 *
 * {@code usedSeats} is maintained transactionally (incremented on assign,
 * decremented on revoke). The {@code @Version} field in JpaBaseEntity
 * provides optimistic locking to prevent overselling under concurrency.
 */
@Entity
@Table(name = "named_licenses")
@Getter
@Setter
@NoArgsConstructor
public class NamedLicenseEntity extends JpaBaseEntity {

    /** Owning tenant — plain UUID, no cross-DB FK. */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** The subscription plan this pool is derived from. */
    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    /** The entitlement that authorises this pool. Cross-DB reference. */
    @Column(name = "entitlement_id", nullable = false)
    private UUID entitlementId;

    /** Maximum seats allowed. Updated via SEAT_LIMIT_CHANGED events. */
    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    /**
     * Seats currently in use. Maintained transactionally alongside
     * seat_assignments to avoid a COUNT(*) query on every check.
     */
    @Column(name = "used_seats", nullable = false)
    private int usedSeats = 0;

    public int availableSeats() {
        return totalSeats - usedSeats;
    }

    public boolean hasCapacity() {
        return usedSeats < totalSeats;
    }
}
