package com.modus.license.enforcement;

import com.modus.license.enforcement.config.EnforcementProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(EnforcementProperties.class)
public class EnforcementEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnforcementEngineApplication.class, args);
    }
}
