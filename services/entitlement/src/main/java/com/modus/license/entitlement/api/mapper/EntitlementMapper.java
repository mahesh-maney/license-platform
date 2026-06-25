package com.modus.license.entitlement.api.mapper;

import com.modus.license.entitlement.api.dto.CreateEntitlementRequest;
import com.modus.license.entitlement.api.dto.EntitlementResponse;
import com.modus.license.entitlement.domain.entity.EntitlementEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EntitlementMapper {

    EntitlementResponse toResponse(EntitlementEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    EntitlementEntity toEntity(CreateEntitlementRequest request);
}
