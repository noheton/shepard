package de.dlr.shepard.plugins.storage.s3.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * FS1b — runtime-mutable S3-storage config singleton.
 *
 * <p>Single-instance Neo4j node mirroring the A3b / N1c2 / UH1a /
 * KIP1d pattern (CLAUDE.md "Always: surface operator knobs in the
 * admin config"). One {@code :S3StorageConfig} node is seeded on
 * first startup from the {@code shepard.storage.s3.*} install-time
 * defaults; subsequent runtime PATCHes against
 * {@code /v2/admin/storage/s3/config} mutate this node in place.
 *
 * <p>Field set is the runtime-mutable subset of the feature's knobs:
 *
 * <ul>
 *   <li>{@link #enabled} — master toggle. Plugin reports
 *       {@code isEnabled()=false} (so {@code FileStorageRegistry}
 *       refuses to activate it) until the operator explicitly opts
 *       in. Defaults to {@code false} so a fresh install with the
 *       plugin JAR on the classpath never accidentally tries to
 *       write to S3.</li>
 *   <li>{@link #endpointUrl} — endpoint URL of the S3-compatible
 *       service (Garage, MinIO, AWS S3, Cloudflare R2, …). Empty
 *       defaults to AWS S3's regional endpoint resolution; non-AWS
 *       endpoints supply their own
 *       {@code https://garage.example.org} / {@code https://s3.example.com:9000}.</li>
 *   <li>{@link #region} — AWS region (or {@code "us-east-1"} as a
 *       dummy value for most non-AWS S3 endpoints, which require a
 *       region in the signed-request header but ignore its
 *       content).</li>
 *   <li>{@link #bucket} — bucket name. Required when
 *       {@link #enabled} is true.</li>
 *   <li>{@link #bucketPrefix} — optional key prefix prepended to
 *       every uploaded object (lets one bucket host multiple
 *       shepard instances; e.g.
 *       {@code "shepard-prod/"} → keys land at
 *       {@code shepard-prod/files/…}).</li>
 *   <li>{@link #forcePathStyle} — Garage / MinIO / older Ceph need
 *       path-style addressing
 *       ({@code https://host/bucket/key}); AWS S3 + Cloudflare R2
 *       support virtual-host-style ({@code https://bucket.host/key}).
 *       Defaults to {@code true} for the broadest non-AWS
 *       compatibility.</li>
 *   <li>{@link #accessKeyId} — IAM Access Key Id (identifier; stored
 *       in plaintext, surfaced through admin GET).</li>
 *   <li>{@link #secretAccessKeyCipher} — encrypted secret access
 *       key. AES-GCM keyed off the {@code shepard.instance.id} —
 *       same "operator-managed secret" posture as KIP1d's DataCite
 *       password. NEVER returned through the admin REST.</li>
 *   <li>{@link #secretAccessKeyHash} — SHA-256 hex of the plaintext
 *       secret. Surfaced only via its first-8-hex fingerprint on the
 *       admin GET.</li>
 *   <li>{@link #sseAlgorithm} — optional server-side-encryption
 *       algorithm (e.g. {@code "AES256"}). Empty disables SSE.</li>
 *   <li>{@link #multipartThresholdBytes} — file size at/above which
 *       the adapter uses S3 multipart upload. Default 16 MB.</li>
 *   <li>{@link #connectionTimeoutSeconds} — connect timeout for the
 *       AWS SDK HTTP client. Default 10s.</li>
 *   <li>{@link #requestTimeoutSeconds} — per-request timeout. Default
 *       30s.</li>
 *   <li>{@link #updatedAt}, {@link #updatedBy} — audit shape.</li>
 * </ul>
 *
 * <p><b>Precedence.</b> Runtime field values win; deploy-time
 * {@code shepard.storage.s3.*} properties are install defaults that
 * seed the singleton on first start. The secret access key is NEVER
 * a deploy-time key — must always be set via the
 * {@code POST /v2/admin/storage/s3/credential} endpoint (security
 * posture; gitleaks would otherwise flag an operator's
 * application.properties as a credential leak).
 */
@NodeEntity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class S3StorageConfig implements HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO.createOrUpdate}.
   */
  @Property("appId")
  private String appId;

  /**
   * Master runtime toggle. When {@code false},
   * {@code S3FileStorage.isEnabled()} returns {@code false} and the
   * registry refuses to activate the adapter. Default {@code false}:
   * an operator must explicitly opt in after configuring credentials
   * + bucket + endpoint.
   */
  @Property("enabled")
  private boolean enabled = false;

  /**
   * Endpoint URL of the S3-compatible service. Empty falls back to
   * AWS's regional default. Non-AWS endpoints must supply the full
   * scheme+host (e.g. {@code https://garage.example.org}).
   */
  @Property("endpointUrl")
  private String endpointUrl = "";

  /**
   * Region identifier. Defaults to {@code "us-east-1"} (the safe
   * dummy region most non-AWS endpoints accept).
   */
  @Property("region")
  private String region = "us-east-1";

  /**
   * Bucket name. Required for puts / gets to succeed; the plugin
   * reports {@code isEnabled()=false} until this is populated.
   */
  @Property("bucket")
  private String bucket = "";

  /**
   * Optional key prefix prepended to every uploaded object key.
   * Empty means no prefix (keys live at the bucket root).
   */
  @Property("bucketPrefix")
  private String bucketPrefix = "";

  /**
   * Path-style addressing flag. {@code true} → keys live at
   * {@code https://host/bucket/key} (Garage, MinIO, older Ceph);
   * {@code false} → virtual-host-style
   * {@code https://bucket.host/key} (AWS, R2).
   */
  @Property("forcePathStyle")
  private boolean forcePathStyle = true;

  /**
   * IAM Access Key Id. Stored in plaintext (it's an identifier, not
   * a secret) and surfaced through {@code GET .../config}.
   */
  @Property("accessKeyId")
  private String accessKeyId = "";

  /**
   * Encrypted IAM Secret Access Key. AES-GCM keyed off the instance
   * id (same posture as KIP1d's DataCite password). NEVER returned
   * through admin REST; only the {@link #secretAccessKeyHash}
   * fingerprint is surfaced.
   */
  @Property("secretAccessKeyCipher")
  private String secretAccessKeyCipher;

  /**
   * SHA-256 hex of the plaintext secret access key. Surfaced only
   * via its first-8-hex fingerprint on {@code GET .../config}.
   * Recomputed on each credential-set call; cleared when the
   * credential is deleted.
   */
  @Property("secretAccessKeyHash")
  private String secretAccessKeyHash;

  /**
   * Optional server-side-encryption algorithm to request on PUT
   * (e.g. {@code "AES256"}). Empty / blank disables SSE.
   */
  @Property("sseAlgorithm")
  private String sseAlgorithm = "";

  /**
   * Multipart upload threshold in bytes. Files smaller than this
   * use a single PUT; larger ones use S3 multipart upload via
   * {@code S3TransferManager} / manual {@code CreateMultipartUpload}.
   * Default 16 MiB.
   */
  @Property("multipartThresholdBytes")
  private long multipartThresholdBytes = 16L * 1024 * 1024;

  /** AWS SDK HTTP connect timeout (seconds). Default 10. */
  @Property("connectionTimeoutSeconds")
  private int connectionTimeoutSeconds = 10;

  /** AWS SDK per-request timeout (seconds). Default 30. */
  @Property("requestTimeoutSeconds")
  private int requestTimeoutSeconds = 30;

  /** Epoch millis of the most recent config mutation. */
  @Property("updatedAt")
  private Long updatedAt;

  /**
   * Username of the operator who last patched / set credentials.
   * Surfaced on the admin GET. Not used for authz — just an audit
   * convenience read.
   */
  @Property("updatedBy")
  private String updatedBy;

  /** For testing purposes only. */
  public S3StorageConfig(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof S3StorageConfig other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
