package de.dlr.shepard.plugins.storage.s3.cli.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FS1b — CLI-side DTO mirroring the server's
 * {@code S3StorageConfigIO} for human-readable + JSON output.
 *
 * <p>Lives in the CLI sub-package so the CLI module doesn't import
 * the backend-side IO record (which carries Quarkus-only types in
 * other plugins). Jackson unmarshals the same JSON shape into this
 * POJO at the wire boundary.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class S3Config {

  private boolean enabled;
  private String endpointUrl;
  private String region;
  private String bucket;
  private String bucketPrefix;
  private boolean forcePathStyle;
  private String accessKeyId;
  private boolean secretKeySet;
  private String secretKeyFingerprint;
  private String sseAlgorithm;
  private long multipartThresholdBytes;
  private int connectionTimeoutSeconds;
  private int requestTimeoutSeconds;
  private Date updatedAt;
  private String updatedBy;
}
