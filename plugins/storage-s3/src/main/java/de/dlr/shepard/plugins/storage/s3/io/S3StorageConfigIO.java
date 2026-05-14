package de.dlr.shepard.plugins.storage.s3.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.plugins.storage.s3.entities.S3StorageConfig;
import de.dlr.shepard.plugins.storage.s3.services.S3StorageConfigService;
import java.util.Date;

/**
 * FS1b — JSON shape returned by
 * {@code GET /v2/admin/storage/s3/config}.
 *
 * <p>Notably the {@code secretAccessKeyCipher} and
 * {@code secretAccessKeyHash} fields on the singleton are
 * <b>never</b> serialised through this IO — instead the masked
 * fingerprint (first 8 hex chars of the SHA-256) + {@code secretKeySet}
 * boolean are surfaced. An operator can confirm "yes that's the
 * credential I just set" without exposing material that could help
 * an attacker.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record S3StorageConfigIO(
  boolean enabled,
  String endpointUrl,
  String region,
  String bucket,
  String bucketPrefix,
  boolean forcePathStyle,
  String accessKeyId,
  boolean secretKeySet,
  String secretKeyFingerprint,
  String sseAlgorithm,
  long multipartThresholdBytes,
  int connectionTimeoutSeconds,
  int requestTimeoutSeconds,
  Date updatedAt,
  String updatedBy
) {
  /**
   * Project a {@link S3StorageConfig} entity onto the IO,
   * replacing the credential material with the fingerprint shape.
   */
  public static S3StorageConfigIO from(S3StorageConfig cfg) {
    Long updatedAtMillis = cfg.getUpdatedAt();
    return new S3StorageConfigIO(
      cfg.isEnabled(),
      cfg.getEndpointUrl(),
      cfg.getRegion(),
      cfg.getBucket(),
      cfg.getBucketPrefix(),
      cfg.isForcePathStyle(),
      cfg.getAccessKeyId(),
      cfg.getSecretAccessKeyHash() != null && !cfg.getSecretAccessKeyHash().isBlank(),
      S3StorageConfigService.fingerprint(cfg.getSecretAccessKeyHash()),
      cfg.getSseAlgorithm(),
      cfg.getMultipartThresholdBytes(),
      cfg.getConnectionTimeoutSeconds(),
      cfg.getRequestTimeoutSeconds(),
      updatedAtMillis == null ? null : new Date(updatedAtMillis),
      cfg.getUpdatedBy()
    );
  }
}
