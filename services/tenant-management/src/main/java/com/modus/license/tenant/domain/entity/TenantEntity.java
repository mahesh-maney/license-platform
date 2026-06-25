package com.modus.license.tenant.domain.entity;

import com.modus.license.core.domain.enums.PlanTier;
import com.modus.license.core.domain.enums.TenantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class TenantEntity extends JpaBaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "admin_email", nullable = false, length = 255)
    private String adminEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tier", length = 20)
    private PlanTier planTier;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;
}
