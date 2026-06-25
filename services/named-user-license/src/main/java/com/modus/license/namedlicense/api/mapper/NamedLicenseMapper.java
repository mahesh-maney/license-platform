package com.modus.license.namedlicense.api.mapper;

import com.modus.license.namedlicense.api.dto.LicensePoolResponse;
import com.modus.license.namedlicense.api.dto.SeatAssignmentResponse;
import com.modus.license.namedlicense.domain.entity.NamedLicenseEntity;
import com.modus.license.namedlicense.domain.entity.SeatAssignmentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NamedLicenseMapper {

    @Mapping(target = "availableSeats", expression = "java(license.availableSeats())")
    LicensePoolResponse toResponse(NamedLicenseEntity license);

    @Mapping(target = "licenseId", source = "license.id")
    @Mapping(target = "assignedAt", source = "createdAt")
    @Mapping(target = "assignedBy", source = "createdBy")
    SeatAssignmentResponse toAssignmentResponse(SeatAssignmentEntity assignment);
}
