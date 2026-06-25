package com.modus.license.reporting.api.mapper;

import com.modus.license.reporting.api.dto.EntitlementSnapshotResponse;
import com.modus.license.reporting.api.dto.SubscriptionSnapshotResponse;
import com.modus.license.reporting.api.dto.UsageMetricResponse;
import com.modus.license.reporting.domain.entity.EntitlementSnapshotEntity;
import com.modus.license.reporting.domain.entity.SubscriptionSnapshotEntity;
import com.modus.license.reporting.domain.entity.UsageMetricEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ReportingMapper {

    UsageMetricResponse toUsageResponse(UsageMetricEntity entity);

    @Mapping(target = "featureKeys",
             expression = "java(splitFeatureKeys(entity.getFeatureKeys()))")
    EntitlementSnapshotResponse toEntitlementResponse(EntitlementSnapshotEntity entity);

    SubscriptionSnapshotResponse toSubscriptionResponse(SubscriptionSnapshotEntity entity);

    default List<String> splitFeatureKeys(String featureKeys) {
        if (featureKeys == null || featureKeys.isBlank()) return Collections.emptyList();
        return Arrays.asList(featureKeys.split(","));
    }
}
