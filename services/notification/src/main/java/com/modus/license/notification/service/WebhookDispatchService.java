package com.modus.license.notification.service;

import com.modus.license.notification.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Dispatches webhook notifications via HTTP POST with configurable retries.
 */
@Component
public class WebhookDispatchService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatchService.class);

    private final RestTemplate restTemplate;
    private final NotificationProperties props;

    public WebhookDispatchService(RestTemplateBuilder builder, NotificationProperties props) {
        this.props = props;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(props.webhook().timeoutMs()))
                .setReadTimeout(Duration.ofMillis(props.webhook().timeoutMs()))
                .build();
    }

    /**
     * Posts {@code payloadJson} to {@code url} with up to {@code retryMax} attempts.
     *
     * @throws RuntimeException if all attempts fail
     */
    public void send(String url, String payloadJson) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);

        int attempt = 0;
        Exception lastException = null;
        while (attempt < props.webhook().retryMax()) {
            try {
                restTemplate.postForEntity(url, request, String.class);
                log.debug("Webhook delivered to={} attempt={}", url, attempt + 1);
                return;
            } catch (RestClientException e) {
                lastException = e;
                attempt++;
                log.warn("Webhook attempt {} failed for url={}: {}", attempt, url, e.getMessage());
                if (attempt < props.webhook().retryMax()) {
                    try {
                        Thread.sleep(props.webhook().retryDelayMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Webhook dispatch interrupted", ie);
                    }
                }
            }
        }
        throw new RuntimeException(
                "Webhook dispatch failed after " + attempt + " attempts to " + url, lastException);
    }
}
