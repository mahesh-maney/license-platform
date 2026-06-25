package com.modus.license.notification.api.mapper;

import com.modus.license.notification.api.dto.NotificationResponse;
import com.modus.license.notification.domain.entity.NotificationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(NotificationEntity entity);
}
