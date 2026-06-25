package com.modus.license.audit.api.mapper;

import com.modus.license.audit.api.dto.AuditLogResponse;
import com.modus.license.audit.domain.entity.AuditLogEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLogEntity entity);
}
