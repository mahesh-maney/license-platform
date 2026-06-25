package com.modus.license.audit.annotation;

/**
 * Verb describing what was done to a resource.
 * Maps to the {@code action} field in {@link com.modus.license.events.audit.AuditEvent}.
 */
public enum AuditAction {

    // CRUD
    CREATE,
    UPDATE,
    DELETE,
    READ,

    // Lifecycle
    ACTIVATE,
    SUSPEND,
    DEACTIVATE,
    EXPIRE,

    // Access / Auth
    LOGIN,
    LOGOUT,
    APPROVE,
    REJECT,

    // License operations
    ASSIGN,
    REVOKE,
    TRANSFER,
    UPGRADE,
    DOWNGRADE,
    RENEW,
    CANCEL,

    // Data operations
    EXPORT,
    IMPORT,
    PURGE,

    // Invitations / onboarding
    INVITE,
    ACCEPT,
}
