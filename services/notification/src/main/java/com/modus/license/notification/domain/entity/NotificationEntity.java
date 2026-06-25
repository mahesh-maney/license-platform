package com.modus.license.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class NotificationEntity extends JpaBaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** SUBSCRIPTION_ACTIVATED | SUBSCRIPTION_CANCELLED | TRIAL_EXPIRING | etc. */
    @Column(name = "notification_type", nullable = false, length = 100)
    private String notificationType;

    /** EMAIL | WEBHOOK | IN_APP */
    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "webhook_url", columnDefinition = "TEXT")
    private String webhookUrl;

    /** PENDING | SENT | FAILED | RETRYING */
    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "sent_at")
    private Instant sentAt;
}
