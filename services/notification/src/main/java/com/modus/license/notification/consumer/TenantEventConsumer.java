package com.modus.license.notification.consumer;

import com.modus.license.events.EventTypes;
import com.modus.license.events.TopicConstants;
import com.modus.license.events.tenant.TenantEvent;
import com.modus.license.notification.domain.entity.TenantContactEntity;
import com.modus.license.notification.domain.repository.TenantContactRepository;
import com.modus.license.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes {@link TenantEvent} records from {@code modus.tenant.events}.
 *
 * <p>Two responsibilities:
 * <ol>
 *   <li>Upserts {@link TenantContactEntity} so the notification service always has
 *       an up-to-date admin email for each tenant.</li>
 *   <li>Sends lifecycle notifications for key tenant events
 *       (trial started/expired, suspended, activated).</li>
 * </ol>
 */
@Component
public class TenantEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TenantEventConsumer.class);

    private final TenantContactRepository contactRepository;
    private final NotificationService     notificationService;

    public TenantEventConsumer(TenantContactRepository contactRepository,
                                NotificationService notificationService) {
        this.contactRepository   = contactRepository;
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics         = TopicConstants.TENANT_EVENTS,
            groupId        = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTenantEvent(TenantEvent event) {
        try {
            UUID tenantId = UUID.fromString(event.getTenantId());
            upsertContact(tenantId, event);
            maybeNotify(tenantId, event);
        } catch (Exception e) {
            log.error("Failed to process TenantEvent type={} tenantId={}: {}",
                    event.getMetadata().getEventType(), event.getTenantId(), e.getMessage(), e);
            throw new RuntimeException("TenantEvent processing failed", e);
        }
    }

    private void upsertContact(UUID tenantId, TenantEvent event) {
        TenantContactEntity contact = contactRepository.findById(tenantId)
                .orElse(new TenantContactEntity());
        contact.setTenantId(tenantId);
        contact.setAdminEmail(event.getAdminEmail());
        contact.setTenantName(event.getName());
        contact.setStatus(event.getStatus());
        contact.setPlanTier(event.getPlanTier() != null ? event.getPlanTier().toString() : null);
        contact.setUpdatedAt(event.getEffectiveAt());
        contactRepository.save(contact);
        log.debug("Upserted TenantContact tenantId={} email={}", tenantId, event.getAdminEmail());
    }

    private void maybeNotify(UUID tenantId, TenantEvent event) {
        String type = event.getMetadata().getEventType();
        switch (type) {
            case EventTypes.Tenant.TRIAL_STARTED -> {
                String trialEnd = event.getTrialEndsAt() != null
                        ? ((Instant) event.getTrialEndsAt()).toString()
                        : "N/A";
                notificationService.notifyEmail(tenantId, "TRIAL_STARTED",
                        "Your Modus trial has started",
                        "Welcome to Modus! Your trial is active and expires on " + trialEnd + ".");
                log.info("Sent TRIAL_STARTED notification: tenantId={} trialEndsAt={}", tenantId, trialEnd);
            }
            case EventTypes.Tenant.TRIAL_EXPIRED -> {
                notificationService.notifyEmail(tenantId, "TRIAL_EXPIRED",
                        "Your Modus trial has expired",
                        "Your Modus trial has expired. Please upgrade your plan to continue using the platform.");
                log.info("Sent TRIAL_EXPIRED notification: tenantId={}", tenantId);
            }
            case EventTypes.Tenant.SUSPENDED -> {
                notificationService.notifyEmail(tenantId, "TENANT_SUSPENDED",
                        "Your Modus account has been suspended",
                        "Your Modus account has been suspended. Please contact support for assistance.");
                log.info("Sent TENANT_SUSPENDED notification: tenantId={}", tenantId);
            }
            case EventTypes.Tenant.ACTIVATED -> {
                notificationService.notifyEmail(tenantId, "TENANT_ACTIVATED",
                        "Your Modus account is now active",
                        "Welcome to Modus! Your account has been activated.");
                log.info("Sent TENANT_ACTIVATED notification: tenantId={}", tenantId);
            }
            default -> log.debug("No notification for TenantEvent type={}", type);
        }
    }
}
