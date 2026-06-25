package com.modus.license.subscription.api.mapper;

import com.modus.license.subscription.api.dto.SubscriptionResponse;
import com.modus.license.subscription.domain.entity.SubscriptionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {SubscriptionPlanMapper.class})
public interface SubscriptionMapper {

    @Mapping(source = "plan", target = "plan")
    @Mapping(expression = "java(entity.effectiveSeatLimit())", target = "effectiveSeatLimit")
    SubscriptionResponse toResponse(SubscriptionEntity entity);
}
