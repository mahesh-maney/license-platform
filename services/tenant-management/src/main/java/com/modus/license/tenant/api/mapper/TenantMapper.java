package com.modus.license.tenant.api.mapper;

import com.modus.license.core.domain.enums.TenantStatus;
import com.modus.license.tenant.api.dto.CreateTenantRequest;
import com.modus.license.tenant.api.dto.TenantResponse;
import com.modus.license.tenant.domain.entity.TenantEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TenantMapper {

    /**
     * Maps a {@link TenantEntity} to a {@link TenantResponse}.
     * createdAt and updatedAt come from JpaBaseEntity via Lombok @Getter.
     */
    TenantResponse toResponse(TenantEntity entity);

    /**
     * Maps a {@link CreateTenantRequest} to a new {@link TenantEntity}.
     * status is not set here — the service sets it to PENDING_SETUP or TRIAL.
     * id, createdAt, updatedAt, version are managed by JPA.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "status", constant = "PENDING_SETUP")
    @Mapping(target = "trialEndsAt", ignore = true)
    TenantEntity toEntity(CreateTenantRequest request);
}
