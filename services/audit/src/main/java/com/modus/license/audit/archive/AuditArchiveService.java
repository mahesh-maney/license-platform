package com.modus.license.audit.archive;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.modus.license.audit.config.AuditProperties;
import com.modus.license.audit.domain.entity.AuditLogEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Writes audit records to Azure Blob immutable (WORM) storage for compliance archival.
 * Activated only when {@code audit.worm-enabled=true}.
 */
@Service
@ConditionalOnProperty(name = "audit.worm-enabled", havingValue = "true")
public class AuditArchiveService {

    private static final Logger log = LoggerFactory.getLogger(AuditArchiveService.class);

    private final BlobContainerClient containerClient;

    public AuditArchiveService(BlobServiceClient blobServiceClient,
                                @Value("${azure.storage.container-name:audit-worm}") String containerName) {
        this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
    }

    /**
     * Uploads the audit record as a JSON blob named by its audit ID.
     * The blob key is: {@code {tenantId}/{year}/{month}/{auditId}.json}
     */
    public void archive(AuditLogEntity log, String json) {
        String blobName = buildBlobName(log);
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            containerClient.getBlobClient(blobName)
                    .upload(new ByteArrayInputStream(bytes), bytes.length, false);
            AuditArchiveService.log.debug("Archived audit record to WORM: {}", blobName);
        } catch (Exception e) {
            AuditArchiveService.log.error("Failed to archive audit record {} to WORM: {}",
                    log.getId(), e.getMessage());
            // Non-fatal: DB persistence is the primary store; WORM is best-effort
        }
    }

    private String buildBlobName(AuditLogEntity entry) {
        int year  = entry.getRecordedAt().atZone(java.time.ZoneOffset.UTC).getYear();
        int month = entry.getRecordedAt().atZone(java.time.ZoneOffset.UTC).getMonthValue();
        return String.format("%s/%d/%02d/%s.json",
                entry.getTenantId(), year, month, entry.getId());
    }
}
