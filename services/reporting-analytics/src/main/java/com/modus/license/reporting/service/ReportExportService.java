package com.modus.license.reporting.service;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Exports report data to Azure Blob Storage.
 *
 * <p>Only active when the {@link BlobServiceClient} bean is available
 * (i.e., {@code azure.storage.account-name} is configured). The container
 * is set via {@code azure.storage.container-name}.
 *
 * <p>Blob naming convention: {@code {tenantId}/{reportType}/{timestamp}.json}
 */
@Service
@ConditionalOnBean(BlobServiceClient.class)
public class ReportExportService {

    private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);

    private final BlobContainerClient containerClient;

    public ReportExportService(
            BlobServiceClient blobServiceClient,
            @Value("${azure.storage.container-name:modus-reports}") String containerName) {
        this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
    }

    /**
     * Uploads {@code jsonData} to Azure Blob as a UTF-8 JSON file.
     *
     * @return blob path within the container (use for client download or reference)
     */
    public String export(UUID tenantId, String reportType, String jsonData) {
        String blobName = buildBlobName(tenantId, reportType);
        try {
            byte[] bytes = jsonData.getBytes(StandardCharsets.UTF_8);
            containerClient.getBlobClient(blobName)
                    .upload(new ByteArrayInputStream(bytes), bytes.length, true);
            log.info("Exported report: blobName={} bytes={}", blobName, bytes.length);
            return blobName;
        } catch (Exception e) {
            log.error("Failed to export report to blob {}: {}", blobName, e.getMessage(), e);
            throw new RuntimeException("Report export failed", e);
        }
    }

    private String buildBlobName(UUID tenantId, String reportType) {
        Instant now = Instant.now();
        int year  = now.atZone(ZoneOffset.UTC).getYear();
        int month = now.atZone(ZoneOffset.UTC).getMonthValue();
        return String.format("%s/%s/%d/%02d/%d.json",
                tenantId, reportType.toLowerCase(), year, month, now.toEpochMilli());
    }
}
