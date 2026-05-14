package de.dlr.shepard.plugins.storage.s3.services;

import de.dlr.shepard.plugins.storage.s3.daos.S3StorageConfigDAO;
import de.dlr.shepard.plugins.storage.s3.entities.S3StorageConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * FS1b — service layer for the {@code :S3StorageConfig} singleton.
 * Mirrors the KIP1d {@code DataciteMinterConfigService} shape:
 *
 * <ol>
 *   <li><b>Seed on first start.</b> {@link #onStart(StartupEvent)}
 *       observes Quarkus's {@code StartupEvent}; if no
 *       {@code :S3StorageConfig} node exists, one is minted from
 *       the {@code shepard.storage.s3.*} install-time defaults.</li>
 *   <li><b>Get-or-seed.</b> {@link #current()} returns the singleton
 *       (seeding if absent).</li>
 *   <li><b>Merge-patch.</b> {@link #patch} applies the runtime-
 *       mutable subset; credential fields are read-only here
 *       (admins use the credential endpoint).</li>
 *   <li><b>Set / clear credential.</b>
 *       {@link #setCredential(String, String, String)} encrypts the
 *       secret via {@link CredentialCipher}, stores
 *       accessKeyId+cipher+hash, bumps the audit shape.
 *       {@link #clearCredential(String)} wipes all three.</li>
 *   <li><b>Resolve plaintext.</b> {@link #resolvePlaintextSecret()}
 *       decrypts the stored cipher for the {@code S3FileStorage}
 *       SigV4 signing path; never logged, never serialised through
 *       admin REST.</li>
 * </ol>
 *
 * <p>Plaintext-handling discipline: the S3 secret access key is
 * logged exactly never; we log only the SHA-256 fingerprint (first
 * 8 hex chars). The {@code ProvenanceCaptureFilter} captures the
 * {@code POST .../credential} request shape + status but not the
 * body, so the audit trail records "who set credentials when"
 * without the secret leaking into {@code :Activity} rows.
 */
@ApplicationScoped
public class S3StorageConfigService {

  /** First 8 hex chars of the secret-key hash — for masked fingerprint display. */
  static final int FINGERPRINT_LENGTH = 8;

  @Inject
  S3StorageConfigDAO dao;

  @Inject
  RequestContextController requestContextController;

  @ConfigProperty(name = "shepard.storage.s3.enabled", defaultValue = "false")
  boolean installDefaultEnabled;

  @ConfigProperty(name = "shepard.storage.s3.endpoint-url", defaultValue = "")
  String installDefaultEndpointUrl;

  @ConfigProperty(name = "shepard.storage.s3.region", defaultValue = "us-east-1")
  String installDefaultRegion;

  @ConfigProperty(name = "shepard.storage.s3.bucket", defaultValue = "")
  String installDefaultBucket;

  @ConfigProperty(name = "shepard.storage.s3.bucket-prefix", defaultValue = "")
  String installDefaultBucketPrefix;

  @ConfigProperty(name = "shepard.storage.s3.force-path-style", defaultValue = "true")
  boolean installDefaultForcePathStyle;

  @ConfigProperty(name = "shepard.storage.s3.sse-algorithm", defaultValue = "")
  String installDefaultSseAlgorithm;

  @ConfigProperty(name = "shepard.storage.s3.multipart-threshold-bytes", defaultValue = "16777216")
  long installDefaultMultipartThresholdBytes;

  @ConfigProperty(name = "shepard.storage.s3.connection-timeout-seconds", defaultValue = "10")
  int installDefaultConnectionTimeoutSeconds;

  @ConfigProperty(name = "shepard.storage.s3.request-timeout-seconds", defaultValue = "30")
  int installDefaultRequestTimeoutSeconds;

  /**
   * Per-instance id used to derive the credential-encryption key.
   * Defaults to the literal {@code "shepard"} so a fresh install
   * with no {@code shepard.instance.id} pinned still functions for
   * dev, but operators should pin a stable id in production.
   */
  @ConfigProperty(name = "shepard.instance.id", defaultValue = "shepard")
  String instanceId;

  /** Cipher — lazily built post-injection. */
  private volatile CredentialCipher cipher;

  /**
   * Seed the singleton on first startup. Idempotent. Fail-soft on
   * runtime errors (operator sees a WARN; the service's other
   * entry points retry on first read).
   */
  void onStart(@Observes StartupEvent event) {
    boolean activated = requestContextController.activate();
    try {
      seedIfNeeded();
    } catch (RuntimeException e) {
      Log.warnf(
        e,
        "FS1b: could not seed :S3StorageConfig on startup; admin actions will retry on first read"
      );
    } finally {
      if (activated) {
        requestContextController.deactivate();
      }
    }
  }

  /** Seed the singleton if it doesn't exist yet. Public for tests. */
  public synchronized S3StorageConfig seedIfNeeded() {
    S3StorageConfig existing = dao.findSingleton();
    if (existing != null) {
      Log.debugf(
        "FS1b: :S3StorageConfig already present (appId=%s, enabled=%s, bucket=%s)",
        existing.getAppId(),
        existing.isEnabled(),
        existing.getBucket()
      );
      return existing;
    }
    S3StorageConfig seed = new S3StorageConfig();
    seed.setEnabled(installDefaultEnabled);
    seed.setEndpointUrl(emptyToBlank(installDefaultEndpointUrl));
    seed.setRegion(emptyToDefault(installDefaultRegion, "us-east-1"));
    seed.setBucket(emptyToBlank(installDefaultBucket));
    seed.setBucketPrefix(emptyToBlank(installDefaultBucketPrefix));
    seed.setForcePathStyle(installDefaultForcePathStyle);
    seed.setSseAlgorithm(emptyToBlank(installDefaultSseAlgorithm));
    seed.setMultipartThresholdBytes(installDefaultMultipartThresholdBytes <= 0L ? 16L * 1024 * 1024 : installDefaultMultipartThresholdBytes);
    seed.setConnectionTimeoutSeconds(installDefaultConnectionTimeoutSeconds <= 0 ? 10 : installDefaultConnectionTimeoutSeconds);
    seed.setRequestTimeoutSeconds(installDefaultRequestTimeoutSeconds <= 0 ? 30 : installDefaultRequestTimeoutSeconds);
    seed.setUpdatedAt(System.currentTimeMillis());
    seed.setUpdatedBy("system:seed");
    // accessKeyId + secretAccessKeyCipher + secretAccessKeyHash
    // left null/blank — operator sets via
    // POST /v2/admin/storage/s3/credential.
    S3StorageConfig saved = dao.createOrUpdate(seed);
    Log.infof(
      "FS1b: seeded :S3StorageConfig singleton (appId=%s, enabled=%s, region=%s, bucket=%s)",
      saved.getAppId(),
      saved.isEnabled(),
      saved.getRegion(),
      blankToDash(saved.getBucket())
    );
    return saved;
  }

  /** Return the current singleton, seeding from install defaults if absent. */
  public S3StorageConfig current() {
    S3StorageConfig existing = dao.findSingleton();
    if (existing != null) {
      return existing;
    }
    return seedIfNeeded();
  }

  /**
   * @return the active config when {@link S3StorageConfig#isEnabled()}
   *         is true AND the required fields ({@code bucket},
   *         {@code accessKeyId}, {@code secretAccessKeyCipher}) are
   *         populated; otherwise {@code null}. Callers (the
   *         {@code S3FileStorage} adapter) treat {@code null} as
   *         "the registry must refuse to activate me".
   */
  public S3StorageConfig getActive() {
    S3StorageConfig cfg = current();
    if (!cfg.isEnabled()) return null;
    if (cfg.getBucket() == null || cfg.getBucket().isBlank()) return null;
    if (cfg.getAccessKeyId() == null || cfg.getAccessKeyId().isBlank()) return null;
    if (cfg.getSecretAccessKeyCipher() == null || cfg.getSecretAccessKeyCipher().isBlank()) return null;
    return cfg;
  }

  /**
   * Apply a runtime merge-patch. Only the non-credential fields are
   * patchable here; credential set goes through
   * {@link #setCredential(String, String, String)}.
   *
   * @throws ReadOnlyFieldException when the caller tried to patch
   *     a credential field directly.
   * @throws IllegalArgumentException when {@code endpointUrl} is
   *     non-blank but unparseable, or numeric fields are
   *     non-positive.
   */
  public synchronized S3StorageConfig patch(S3Patch patch, String updatedBy) {
    if (patch.accessKeyIdTouched || patch.secretAccessKeyCipherTouched || patch.secretAccessKeyHashTouched) {
      String which = patch.accessKeyIdTouched
        ? "accessKeyId"
        : (patch.secretAccessKeyCipherTouched ? "secretAccessKeyCipher" : "secretAccessKeyHash");
      throw new ReadOnlyFieldException(which);
    }
    S3StorageConfig cfg = current();
    if (patch.enabled != null) cfg.setEnabled(patch.enabled);
    if (patch.endpointUrlTouched) {
      String url = emptyToBlank(patch.endpointUrl);
      if (!url.isEmpty()) {
        validateEndpointUrl(url);
      }
      cfg.setEndpointUrl(url);
    }
    if (patch.regionTouched) cfg.setRegion(emptyToDefault(patch.region, "us-east-1"));
    if (patch.bucketTouched) cfg.setBucket(emptyToBlank(patch.bucket));
    if (patch.bucketPrefixTouched) cfg.setBucketPrefix(emptyToBlank(patch.bucketPrefix));
    if (patch.forcePathStyle != null) cfg.setForcePathStyle(patch.forcePathStyle);
    if (patch.sseAlgorithmTouched) cfg.setSseAlgorithm(emptyToBlank(patch.sseAlgorithm));
    if (patch.multipartThresholdBytes != null) {
      if (patch.multipartThresholdBytes <= 0L) {
        throw new IllegalArgumentException("multipartThresholdBytes must be > 0");
      }
      cfg.setMultipartThresholdBytes(patch.multipartThresholdBytes);
    }
    if (patch.connectionTimeoutSeconds != null) {
      if (patch.connectionTimeoutSeconds <= 0) {
        throw new IllegalArgumentException("connectionTimeoutSeconds must be > 0");
      }
      cfg.setConnectionTimeoutSeconds(patch.connectionTimeoutSeconds);
    }
    if (patch.requestTimeoutSeconds != null) {
      if (patch.requestTimeoutSeconds <= 0) {
        throw new IllegalArgumentException("requestTimeoutSeconds must be > 0");
      }
      cfg.setRequestTimeoutSeconds(patch.requestTimeoutSeconds);
    }
    cfg.setUpdatedAt(System.currentTimeMillis());
    cfg.setUpdatedBy(emptyToDefault(updatedBy, "unknown"));
    S3StorageConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "FS1b: :S3StorageConfig patched (enabled=%s, endpoint=%s, region=%s, bucket=%s, by=%s)",
      saved.isEnabled(),
      blankToDash(saved.getEndpointUrl()),
      saved.getRegion(),
      blankToDash(saved.getBucket()),
      saved.getUpdatedBy()
    );
    return saved;
  }

  /**
   * Store the S3 credential pair. The accessKeyId is stored in
   * plaintext (it's an identifier, not a secret); the
   * secretAccessKey is encrypted via {@link CredentialCipher} and
   * stored on {@code secretAccessKeyCipher}; its SHA-256 hex is
   * stored on {@code secretAccessKeyHash} for fingerprint display.
   * Plaintext secret is never logged.
   */
  public synchronized S3StorageConfig setCredential(String accessKeyId, String plaintextSecret, String updatedBy) {
    if (accessKeyId == null || accessKeyId.isBlank()) {
      throw new IllegalArgumentException("accessKeyId must not be null/blank");
    }
    if (plaintextSecret == null || plaintextSecret.isBlank()) {
      throw new IllegalArgumentException("secretAccessKey plaintext must not be null/blank");
    }
    S3StorageConfig cfg = current();
    String cipherText = cipher().encrypt(plaintextSecret);
    String hash = sha256Hex(plaintextSecret);
    cfg.setAccessKeyId(accessKeyId.trim());
    cfg.setSecretAccessKeyCipher(cipherText);
    cfg.setSecretAccessKeyHash(hash);
    cfg.setUpdatedAt(System.currentTimeMillis());
    cfg.setUpdatedBy(emptyToDefault(updatedBy, "unknown"));
    S3StorageConfig saved = dao.createOrUpdate(cfg);
    Log.infof(
      "FS1b: S3 credential set (accessKeyId=%s, fingerprint=%s, by=%s)",
      saved.getAccessKeyId(),
      fingerprint(hash),
      saved.getUpdatedBy()
    );
    return saved;
  }

  /** Clear the stored credential. */
  public synchronized S3StorageConfig clearCredential(String updatedBy) {
    S3StorageConfig cfg = current();
    cfg.setAccessKeyId("");
    cfg.setSecretAccessKeyCipher(null);
    cfg.setSecretAccessKeyHash(null);
    cfg.setUpdatedAt(System.currentTimeMillis());
    cfg.setUpdatedBy(emptyToDefault(updatedBy, "unknown"));
    S3StorageConfig saved = dao.createOrUpdate(cfg);
    Log.infof("FS1b: S3 credential cleared (by=%s)", saved.getUpdatedBy());
    return saved;
  }

  /**
   * Decrypt the stored secret access key for SigV4-signing use.
   * Returns {@code null} when no credential is set; throws
   * {@link IllegalStateException} when decryption fails (tampered /
   * key-mismatch).
   */
  public String resolvePlaintextSecret() {
    S3StorageConfig cfg = current();
    String c = cfg.getSecretAccessKeyCipher();
    if (c == null || c.isBlank()) return null;
    return cipher().decrypt(c);
  }

  /** Public fingerprint of the secret-key hash. {@code null} when no credential. */
  public String secretFingerprint() {
    return fingerprint(current().getSecretAccessKeyHash());
  }

  /** Static fingerprint helper (first 8 hex chars). */
  public static String fingerprint(String hashHex) {
    if (hashHex == null || hashHex.length() < FINGERPRINT_LENGTH) {
      return null;
    }
    return hashHex.substring(0, FINGERPRINT_LENGTH);
  }

  /** Re-derive the cipher lazily. Visible for tests. */
  CredentialCipher cipher() {
    CredentialCipher local = cipher;
    if (local == null) {
      synchronized (this) {
        local = cipher;
        if (local == null) {
          local = new CredentialCipher(emptyToDefault(instanceId, "shepard"));
          cipher = local;
        }
      }
    }
    return local;
  }

  /** SHA-256 hex digest of a UTF-8 string. */
  static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  static void validateEndpointUrl(String url) {
    if (url == null || url.isBlank()) return;
    try {
      URI u = new URI(url);
      if (u.getScheme() == null || u.getHost() == null) {
        throw new IllegalArgumentException("endpointUrl must include a scheme + host (got '" + url + "')");
      }
      String scheme = u.getScheme().toLowerCase();
      if (!"http".equals(scheme) && !"https".equals(scheme)) {
        throw new IllegalArgumentException("endpointUrl scheme must be http or https (got '" + scheme + "')");
      }
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("endpointUrl is not a valid URI: " + e.getMessage(), e);
    }
  }

  private static String emptyToBlank(String s) {
    if (s == null) return "";
    return s.trim();
  }

  private static String emptyToDefault(String s, String defaultValue) {
    if (s == null) return defaultValue;
    String trimmed = s.trim();
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }

  private static String blankToDash(String s) {
    return (s == null || s.isBlank()) ? "-" : s;
  }

  // ─── Inner types ────────────────────────────────────────────────

  /**
   * Patch DTO for {@link #patch(S3Patch, String)}. Carries "touched"
   * flags so the service can distinguish "field absent" (leave alone)
   * from "explicit null" (clear).
   */
  public static final class S3Patch {

    public Boolean enabled;
    public boolean endpointUrlTouched;
    public String endpointUrl;
    public boolean regionTouched;
    public String region;
    public boolean bucketTouched;
    public String bucket;
    public boolean bucketPrefixTouched;
    public String bucketPrefix;
    public Boolean forcePathStyle;
    public boolean sseAlgorithmTouched;
    public String sseAlgorithm;
    public Long multipartThresholdBytes;
    public Integer connectionTimeoutSeconds;
    public Integer requestTimeoutSeconds;
    /** Sentinel — when {@code true}, the call is rejected. */
    public boolean accessKeyIdTouched;
    /** Sentinel — when {@code true}, the call is rejected. */
    public boolean secretAccessKeyCipherTouched;
    /** Sentinel — when {@code true}, the call is rejected. */
    public boolean secretAccessKeyHashTouched;
  }

  /** Raised when a caller tries to PATCH a read-only field. */
  public static final class ReadOnlyFieldException extends RuntimeException {

    private final String field;

    public ReadOnlyFieldException(String field) {
      super("Field '" + field + "' is read-only via PATCH; use the dedicated credential endpoint.");
      this.field = field;
    }

    public String field() {
      return field;
    }
  }
}
