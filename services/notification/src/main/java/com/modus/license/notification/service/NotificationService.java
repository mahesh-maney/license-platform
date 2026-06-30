package com.modus.license.notification.service;

import com.modus.license.core.context.TenantContextHolder;
import com.modus.license.notification.api.dto.NotificationResponse;
import com.modus.license.notification.api.mapper.NotificationMapper;
import com.modus.license.notification.domain.entity.NotificationEntity;
import com.modus.license.notification.domain.entity.TenantContactEntity;
import com.modus.license.notification.domain.event.NotificationEventPublisher;
import com.modus.license.notification.domain.repository.NotificationRepository;
import com.modus.license.notification.domain.repository.TenantContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository    repository;
    private final TenantContactRepository   contactRepository;
    private final NotificationEventPublisher publisher;
    private final NotificationMapper        mapper;
    private final EmailDispatchService      emailDispatchService;
    private final WebhookDispatchService    webhookDispatchService;

    public NotificationService(NotificationRepository repository,
                                TenantContactRepository contactRepository,
                                NotificationEventPublisher publisher,
                                NotificationMapper mapper,
                                EmailDispatchService emailDispatchService,
                                WebhookDispatchService webhookDispatchService) {
        this.repository            = repository;
        this.contactRepository     = contactRepository;
        this.publisher             = publisher;
        this.mapper                = mapper;
        this.emailDispatchService  = emailDispatchService;
        this.webhookDispatchService = webhookDispatchService;
    }

    /**
     * Sends an EMAIL notification to the tenant's admin address.
     * Called by Kafka consumers — no tenant context required.
     */
    @Transactional
    public void notifyEmail(UUID tenantId, String notificationType,
                             String subject, String body) {
        Optional<TenantContactEntity> contactOpt = contactRepository.findById(tenantId);
        if (contactOpt.isEmpty()) {
            log.warn("No tenant contact for tenantId={}; skipping notification type={}",
                    tenantId, notificationType);
            return;
        }
        TenantContactEntity contact = contactOpt.get();
        dispatch("EMAIL", tenantId, notificationType, contact.getAdminEmail(),
                subject, body, null);
    }

    /**
     * Sends a WEBHOOK notification.
     * Called by Kafka consumers — no tenant context required.
     */
    @Transactional
    public void notifyWebhook(UUID tenantId, String notificationType,
                               String payloadJson, String webhookUrl) {
        dispatch("WEBHOOK", tenantId, notificationType, null, null, payloadJson, webhookUrl);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    public Page<NotificationResponse> search(Pageable pageable) {
        UUID tenantId = TenantContextHolder.require().tenantId().value();
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'PLATFORM_ADMIN')")
    public Optional<NotificationResponse> getById(UUID notificationId) {
        UUID tenantId = TenantContextHolder.require().tenantId().value();
        return repository.findByIdAndTenantId(notificationId, tenantId)
                .map(mapper::toResponse);
    }

    private void dispatch(String channel, UUID tenantId, String notificationType,
                           String recipientEmail, String subject, String body, String webhookUrl) {
        String status = "PENDING";
        String failureReason = null;
        Instant sentAt = null;

        try {
            if ("EMAIL".equals(channel)) {
                emailDispatchService.send(recipientEmail, subject, body);
            } else if ("WEBHOOK".equals(channel)) {
                webhookDispatchService.send(webhookUrl, body);
            }
            status = "SENT";
            sentAt = Instant.now();
            log.info("Notification dispatched: tenantId={} type={} channel={}", tenantId, notificationType, channel);
        } catch (Exception e) {
            log.error("Notification dispatch failed for tenant={} type={} channel={}: {}",
                    tenantId, notificationType, channel, e.getMessage());
            status = "FAILED";
            failureReason = e.getMessage();
        }

        NotificationEntity entity = new NotificationEntity();
        entity.setTenantId(tenantId);
        entity.setNotificationType(notificationType);
        entity.setChannel(channel);
        entity.setRecipientEmail(recipientEmail);
        entity.setSubject(subject);
        entity.setPayloadJson(body);
        entity.setWebhookUrl(webhookUrl);
        entity.setStatus(status);
        entity.setSentAt(sentAt);
        entity.setFailureReason(failureReason);

        NotificationEntity saved = repository.save(entity);
        publisher.publish(saved);
    }
}
