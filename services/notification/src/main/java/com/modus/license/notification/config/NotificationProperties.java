package com.modus.license.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        Email email,
        Webhook webhook,
        List<Integer> renewalWarnDays
) {

    public record Email(String from) {}

    public record Webhook(int timeoutMs, int retryMax, int retryDelayMs) {}
}
