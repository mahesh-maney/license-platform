package com.modus.license.core.domain.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Pure domain base class — no persistence annotations.
 *
 * JPA services extend this and add @MappedSuperclass, @Id, @Version,
 * @CreatedDate, @LastModifiedDate, @CreatedBy, @LastModifiedBy.
 *
 * R2DBC services extend this and add @Table, @Id, @Version, etc.
 */
public abstract class BaseEntity {

    protected UUID id;
    protected Instant createdAt;
    protected Instant updatedAt;
    protected String createdBy;
    protected String lastModifiedBy;
    protected Long version;

    protected BaseEntity() {
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public Long getVersion() {
        return version;
    }

    public boolean isNew() {
        return id == null;
    }
}
