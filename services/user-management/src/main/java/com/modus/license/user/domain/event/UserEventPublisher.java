package com.modus.license.user.domain.event;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.common.EventMetadata;
import com.modus.license.events.user.UserEvent;
import com.modus.license.user.domain.entity.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Publishes {@link UserEvent} Avro records to {@code modus.user.events}.
 *
 * Partition key: userId — all events for a user land on the same partition.
 */
@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UserEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishCreated(UserEntity user) {
        publish(buildEvent(user, EventTypes.User.CREATED, null, user.getRoles(), List.of()));
    }

    public void publishUpdated(UserEntity user) {
        publish(buildEvent(user, EventTypes.User.UPDATED, null, user.getRoles(), List.of()));
    }

    public void publishDeactivated(UserEntity user, String previousStatus) {
        publish(buildEvent(user, EventTypes.User.DEACTIVATED, previousStatus, user.getRoles(), List.of()));
    }

    public void publishReactivated(UserEntity user, String previousStatus) {
        publish(buildEvent(user, EventTypes.User.REACTIVATED, previousStatus, user.getRoles(), List.of()));
    }

    public void publishRoleChanged(UserEntity user, Set<String> previousRoles) {
        publish(buildEvent(user, EventTypes.User.ROLE_CHANGED, null,
                user.getRoles(), previousRoles));
    }

    public void publishDeleted(UserEntity user) {
        publish(buildEvent(user, EventTypes.User.DELETED, null, user.getRoles(), List.of()));
    }

    private UserEvent buildEvent(UserEntity user, String eventType, String previousStatus,
                                  Set<String> roles, java.util.Collection<String> previousRoles) {
        Instant now = Instant.now();

        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEventType(eventType)
                .setTenantId(user.getTenantId().toString())
                .setActorId(null)
                .setTimestamp(now)
                .setCorrelationId(null)
                .setSchemaVersion(1)
                .build();

        return UserEvent.newBuilder()
                .setMetadata(metadata)
                .setUserId(user.getId().toString())
                .setTenantId(user.getTenantId().toString())
                .setEmail(user.getEmail())
                .setFirstName(user.getFirstName())
                .setLastName(user.getLastName())
                .setStatus(user.getStatus().name())
                .setPreviousStatus(previousStatus)
                .setRoles(List.copyOf(roles))
                .setPreviousRoles(List.copyOf(previousRoles))
                .setEffectiveAt(now)
                .build();
    }

    private void publish(UserEvent event) {
        try {
            kafkaTemplate.send(TopicConstants.USER_EVENTS, event.getUserId(), event);
            log.debug("Published user event: type={} userId={}", event.getMetadata().getEventType(), event.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish user event: type={} userId={}. Error: {}",
                    event.getMetadata().getEventType(), event.getUserId(), e.getMessage());
        }
    }
}
