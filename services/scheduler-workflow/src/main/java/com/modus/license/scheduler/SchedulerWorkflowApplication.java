package com.modus.license.scheduler;

import com.modus.license.scheduler.config.SchedulerProperties;
import com.modus.license.scheduler.config.ServicesProperties;
import com.modus.license.scheduler.config.TemporalProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({SchedulerProperties.class, TemporalProperties.class, ServicesProperties.class})
public class SchedulerWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerWorkflowApplication.class, args);
    }
}
