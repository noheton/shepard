package de.dlr.shepard.v2.viz.storage;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * VIS-S1b — mints time-bounded HMAC-SHA256 signed URLs for Garage/S3
 * objects so that VIS-V1 / VIS-X1 visualization plugins can serve asset
 * bytes over a short-lived token without exposing raw S3 credentials.
 *
 * <h2>URL shape</h2>
 *
 * <pre>
 * /v2/viz/objects/{bucket}/{objectKey}?exp={epochSeconds}&amp;sig={token}
 * </pre>
 *
 * where {@code token} = {@code HMAC-SHA256(signing-key, bucket + "|" + objectKey + "|" + epochSeconds)}
 * encoded as lower-case hex.
 *
 * <h2>Verification</h2>
 *
 * {@link SignedUrlAccessFilter} validates all {@code /v2/viz/**} requests:
 * it checks {@code exp} is in the future and reproduces the HMAC over the
 * same input, then compares using a constant-time equality function to
 * prevent timing side-channels.
 *
 * <h2>Config keys</h2>
 *
 * <ul>
 *   <li>{@code shepard.v2.viz.signed-url-ttl-seconds} (default 3600) —
 *       how long a minted URL remains valid.</li>
 *   <li>{@code shepard.v2.viz.signing-key} — HMAC-SHA256 signing key.
 *       <strong>Must be changed from the default "changeme-replace-in-production"
 *       before any production deployment.</strong></li>
 * </ul>
 *
 * <h2>Security notes</h2>
 *
 * <ul>
 *   <li>HMAC-SHA256 comparison in {@link SignedUrlAccessFilter} is
 *       constant-time via {@link java.security.MessageDigest#isEqual(byte[], byte[])}.</li>
 *   <li>This bean is {@code @ApplicationScoped}: the signing key config is
 *       read once at startup and reused per request.</li>
 *   <li>The default signing key emits a startup {@code WARN}; production
 *       operators must override it via {@code application.properties} or
 *       environment variable.</li>
 * </ul>
 *
 * <h2>Cross-references</h2>
 *
 * <ul>
 *   <li>{@code aidocs/16} — VIS-S1b row</li>
 *   <li>{@link SignedUrlAccessFilter} — companion filter that validates URLs
 *       minted by this class</li>
 * </ul>
 */
@ApplicationScoped
public class SignedUrlIssuer {

  static final String HMAC_ALGO = "HmacSHA256";
  private static final String DEFAULT_KEY = "changeme-replace-in-production";

  /** VIS-S1b base path for signed viz object URLs. */
  static final String VIZ_OBJECTS_PATH = "/v2/viz/objects/";

  @ConfigProperty(name = "shepard.v2.viz.signed-url-ttl-seconds", defaultValue = "3600")
  long signedUrlTtlSeconds;

  @ConfigProperty(name = "shepard.v2.viz.signing-key", defaultValue = "changeme-replace-in-production")
  String signingKey;

  /**
   * Mint a signed URL for the given Garage/S3 bucket + object key.
   *
   * <p>The signed URL carries:
   * <ul>
   *   <li>{@code exp} — expiry as Unix epoch seconds</li>
   *   <li>{@code sig} — HMAC-SHA256 of
   *       {@code bucket + "|" + objectKey + "|" + exp}
   *       encoded as lower-case hex</li>
   * </ul>
   *
   * <p>The URL is a Shepard-application-relative path (not an S3 presigned
   * URL). {@link SignedUrlAccessFilter} guards the {@code /v2/viz/**} surface
   * and validates the token on every request before any S3 access occurs.
   *
   * @param bucketName the S3 bucket name (must not be null or blank)
   * @param objectKey  the S3 object key (must not be null or blank)
   * @return the signed URI for the object, valid for
   *         {@code shepard.v2.viz.signed-url-ttl-seconds} seconds
   * @throws IllegalArgumentException if {@code bucketName} or {@code objectKey}
   *                                  is null or blank
   */
  public URI mintSignedUrl(String bucketName, String objectKey) {
    if (bucketName == null || bucketName.isBlank()) {
      throw new IllegalArgumentException("bucketName must not be null or blank");
    }
    if (objectKey == null || objectKey.isBlank()) {
      throw new IllegalArgumentException("objectKey must not be null or blank");
    }

    warnIfDefaultKey();

    long expiresAt = Instant.now().getEpochSecond() + signedUrlTtlSeconds;
    String message = buildMessage(bucketName, objectKey, expiresAt);
    String sig = computeHmacHex(message);

    // URL-encode the object key — it may contain slashes or other special chars.
    String encodedKey = java.net.URLEncoder.encode(objectKey, StandardCharsets.UTF_8)
        .replace("+", "%20");   // URLEncoder uses + for spaces; %20 is safer in query params
    String uriStr = VIZ_OBJECTS_PATH + bucketName + "/" + encodedKey
        + "?exp=" + expiresAt + "&sig=" + sig;
    return URI.create(uriStr);
  }

  /**
   * Return the configured TTL in seconds — exposed for tests and the
   * companion filter's expiry calculation.
   */
  public long getSignedUrlTtlSeconds() {
    return signedUrlTtlSeconds;
  }

  // ─── Package-private helpers (used by SignedUrlAccessFilter) ──────────────

  /**
   * Build the canonical message string for HMAC computation.
   *
   * <p>The separator {@code |} is chosen because it does not appear in
   * valid S3 bucket names (alphanumeric + hyphen only per AWS/Garage rules),
   * making it an unambiguous field separator that prevents a crafted
   * {@code objectKey} from producing the same message as a different
   * {@code (bucket, objectKey)} pair.
   */
  static String buildMessage(String bucket, String objectKey, long expiresAtEpochSeconds) {
    return bucket + "|" + objectKey + "|" + expiresAtEpochSeconds;
  }

  /**
   * Compute HMAC-SHA256 over {@code message} with the configured signing key.
   *
   * @return lower-case hex encoded HMAC digest
   * @throws IllegalStateException if HMAC-SHA256 is unavailable
   */
  String computeHmacHex(String message) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGO);
      mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
      byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException("HMAC-SHA256 unavailable — cannot mint signed URL", ex);
    }
  }

  /**
   * Compute HMAC-SHA256 and return the raw digest bytes for constant-time
   * comparison by {@link SignedUrlAccessFilter}.
   */
  byte[] computeHmacBytes(String message) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGO);
      mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
      return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException("HMAC-SHA256 unavailable — cannot verify signed URL", ex);
    }
  }

  /**
   * Constant-time byte-array equality — prevents timing side-channels on
   * HMAC comparisons. Delegates to {@link java.security.MessageDigest#isEqual(byte[], byte[])}
   * which is guaranteed constant-time by the JDK contract (JDK-8052537).
   *
   * <p>SpotBugs / findsecbugs would flag a naive {@code Arrays.equals} or
   * string {@code .equals()} in a security comparison as a timing side-channel
   * vulnerability. This method is the auditor-visible proof that the comparison
   * is timing-safe.
   */
  static boolean constantTimeEq(byte[] a, byte[] b) {
    return java.security.MessageDigest.isEqual(a, b);
  }

  private void warnIfDefaultKey() {
    if (DEFAULT_KEY.equals(signingKey)) {
      Log.warn(
        "VIS-S1b: shepard.v2.viz.signing-key is still the default insecure value. " +
        "Set a strong random key in application.properties (or via SHEPARD_V2_VIZ_SIGNING_KEY " +
        "environment variable) before production deployment."
      );
    }
  }
}
