package com.modus.license.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "temporal")
public record TemporalProperties(
        String serviceAddress,
        String namespace,
        String taskQueue,
        Worker worker
) {

    public record Worker(int maxConcurrentActivities, int maxConcurrentWorkflows) {}
}
