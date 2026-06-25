package com.modus.license.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scheduler")
public record SchedulerProperties(
        String renewalSweepCron,
        String expirySweepCron,
        int gracePeriodDays
) {}
