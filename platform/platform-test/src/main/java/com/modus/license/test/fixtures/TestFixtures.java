package com.modus.license.test.fixtures;

import com.modus.license.core.domain.id.EntitlementId;
import com.modus.license.core.domain.id.FeatureId;
import com.modus.license.core.domain.id.LicenseId;
import com.modus.license.core.domain.id.PlanId;
import com.modus.license.core.domain.id.SessionId;
import com.modus.license.core.domain.id.TenantId;
import com.modus.license.core.domain.id.UserId;
import com.modus.license.test.context.TenantContextTestHelper;

import java.util.UUID;

/**
 * Static test data factories for common domain IDs and values.
 *
 * Using fixed UUIDs (rather than random) makes test failures reproducible.
 * Each entity type has:
 *   - A stable default ID (e.g. {@link #TENANT_1})
 *   - A second stable ID for multi-tenant or relationship tests
 *   - A {@code random()} factory for unique IDs within a test
 *
 * Usage:
 * <pre>
 *   TenantId tenantId = TestFixtures.TENANT_1;
 *   UserId   userId   = TestFixtures.USER_1;
 *   PlanId   planId   = TestFixtures.randomPlanId();
 * </pre>
 */
public final class TestFixtures {

    private TestFixtures() {}

    // ── Tenant IDs ────────────────────────────────────────────────────────────

    public static final TenantId TENANT_1 = TenantContextTestHelper.DEFAULT_TENANT_ID;
    public static final TenantId TENANT_2 = TenantContextTestHelper.SECOND_TENANT_ID;

    public static TenantId randomTenantId() { return TenantId.generate(); }

    // ── User IDs ──────────────────────────────────────────────────────────────

    public static final UserId USER_1 = TenantContextTestHelper.DEFAULT_USER_ID;
    public static final UserId USER_2 = UserId.of("dddddddd-dddd-dddd-dddd-dddddddddddd");

    public static UserId randomUserId() { return UserId.generate(); }

    // ── Plan IDs ──────────────────────────────────────────────────────────────

    public static final PlanId PLAN_STARTER      = PlanId.of("10000000-0000-0000-0000-000000000001");
    public static final PlanId PLAN_PROFESSIONAL = PlanId.of("10000000-0000-0000-0000-000000000002");
    public static final PlanId PLAN_ENTERPRISE   = PlanId.of("10000000-0000-0000-0000-000000000003");

    public static PlanId randomPlanId() { return PlanId.generate(); }

    // ── Entitlement IDs ───────────────────────────────────────────────────────

    public static final EntitlementId ENTITLEMENT_1 = EntitlementId.of("20000000-0000-0000-0000-000000000001");

    public static EntitlementId randomEntitlementId() { return EntitlementId.generate(); }

    // ── License IDs ───────────────────────────────────────────────────────────

    public static final LicenseId LICENSE_1 = LicenseId.of("30000000-0000-0000-0000-000000000001");

    public static LicenseId randomLicenseId() { return LicenseId.generate(); }

    // ── Session IDs ───────────────────────────────────────────────────────────

    public static final SessionId SESSION_1 = SessionId.of("40000000-0000-0000-0000-000000000001");

    public static SessionId randomSessionId() { return SessionId.generate(); }

    // ── Feature IDs ───────────────────────────────────────────────────────────

    public static final FeatureId FEATURE_1 = FeatureId.of("50000000-0000-0000-0000-000000000001");

    public static FeatureId randomFeatureId() { return FeatureId.generate(); }

    // ── Common feature keys ───────────────────────────────────────────────────

    public static final String FEATURE_KEY_REPORTING   = "ADVANCED_REPORTING";
    public static final String FEATURE_KEY_API_ACCESS  = "API_ACCESS";
    public static final String FEATURE_KEY_SSO         = "SSO_INTEGRATION";
    public static final String FEATURE_KEY_EXPORT      = "DATA_EXPORT";

    // ── String UUID helpers ───────────────────────────────────────────────────

    public static String randomUuid() { return UUID.randomUUID().toString(); }
}
