package com.modus.license.feature.api.mapper;

import com.modus.license.feature.api.dto.CreateFeatureRequest;
import com.modus.license.feature.api.dto.FeatureResponse;
import com.modus.license.feature.domain.entity.FeatureDefinitionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FeatureMapper {

    FeatureResponse toResponse(FeatureDefinitionEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    FeatureDefinitionEntity toEntity(CreateFeatureRequest request);
}
