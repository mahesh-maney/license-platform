package com.modus.license.subscription.api.mapper;

import com.modus.license.subscription.api.dto.CreatePlanRequest;
import com.modus.license.subscription.api.dto.PlanResponse;
import com.modus.license.subscription.domain.entity.SubscriptionPlanEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SubscriptionPlanMapper {

    @Mapping(source = "tier", target = "tier")
    PlanResponse toResponse(SubscriptionPlanEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    SubscriptionPlanEntity toEntity(CreatePlanRequest request);
}
