package de.dlr.shepard.plugins.storage.s3;

import de.dlr.shepard.plugins.storage.s3.entities.S3StorageConfig;
import de.dlr.shepard.plugins.storage.s3.services.S3StorageConfigService;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StorageNotFoundException;
import de.dlr.shepard.storage.StorageProviderUnavailableException;
import de.dlr.shepard.storage.StoragePutRequest;
import de.dlr.shepard.storage.StorageQuotaExceededException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.StorageClass;

/**
 * FS1b — {@link FileStorage} SPI implementation backed by any
 * S3-compatible object store (AWS S3, Garage, MinIO, Ceph RGW,
 * Cloudflare R2, Backblaze B2, Wasabi, SeaweedFS). Activated when
 * the operator sets {@code shepard.storage.provider=s3}.
 *
 * <p>Locator format: {@code "<bucket>/<key>"} where {@code key} is
 * {@code <dataObjectAppId>/<filename>}. The container field of
 * {@link StoragePutRequest} carries the bucket name (for the
 * GridFS adapter it was the Mongo collection; here we repurpose
 * it as the bucket override — if blank, the configured default
 * bucket is used).
 *
 * <p><b>Client lifecycle.</b> The {@code S3Client} is lazily
 * re-built on each call to ensure runtime config changes (endpoint,
 * region, credentials patched via the admin REST) are reflected
 * immediately without a restart. This is acceptable — the admin
 * config-change rate is extremely low and the builder is cheap
 * compared to the I/O latency.
 *
 * @see S3StorageConfigService
 * @see S3StorageAdminRest
 */
@ApplicationScoped
public class S3FileStorage implements FileStorage {

  /** Stable id; matches {@code shepard.storage.provider=s3}. */
  public static final String ID = "s3";

  /**
   * Separator between bucket and key in the opaque locator string.
   * Uses {@code ":"} (same as GridFS) to stay consistent with the
   * FS1a locator convention. The bucket name is a DNS label (no
   * colon allowed); the key may contain virtually any char including
   * slashes.
   */
  public static final char LOCATOR_SEPARATOR = ':';

  @Inject
  S3StorageConfigService configService;

  @Override
  public String id() {
    return ID;
  }

  @Override
  public boolean isEnabled() {
    return configService.getActive() != null;
  }

  /**
   * Store bytes via {@code PutObjectRequest}. Key format:
   * {@code <container>/<fileName>} where container is either the
   * request's container field (if non-blank) or the configured bucket,
   * and fileName is sanitised for S3 path safety.
   *
   * <p>SSE is applied when {@code sseAlgorithm} is configured on the
   * {@link S3StorageConfig}.
   */
  @Override
  public StorageLocator put(StoragePutRequest request) throws StorageException {
    S3StorageConfig cfg = requireActive();
    S3Client client = buildClient(cfg);

    String bucket = resolveBucket(cfg, request.container());
    String key = buildKey(request.container(), request.fileName());

    PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
      .bucket(bucket)
      .key(key);

    if (request.contentType() != null && !request.contentType().isBlank()) {
      putBuilder.contentType(request.contentType());
    }
    if (cfg.getSseAlgorithm() != null && !cfg.getSseAlgorithm().isBlank()) {
      putBuilder.serverSideEncryption(cfg.getSseAlgorithm());
    }

    PutObjectRequest putRequest = putBuilder.build();

    try {
      RequestBody body;
      if (request.sizeBytes() != null && request.sizeBytes() > 0) {
        body = RequestBody.fromInputStream(request.bytes(), request.sizeBytes());
      } else {
        // Unknown size — read into byte array. For large files operators
        // should configure presigned URLs (FS1c) or set sizeBytes upstream.
        byte[] data = request.bytes().readAllBytes();
        body = RequestBody.fromBytes(data);
      }

      client.putObject(putRequest, body);
      String locator = bucket + LOCATOR_SEPARATOR + key;
      Log.debugf("S3FileStorage.put: bucket=%s key=%s size=%s", bucket, key, request.sizeBytes());
      return new StorageLocator(ID, locator);
    } catch (S3Exception e) {
      if (isQuotaError(e)) {
        throw new StorageQuotaExceededException(
          "S3 storage quota exceeded (bucket=" + bucket + "): " + e.getMessage(),
          e
        );
      }
      throw new StorageProviderUnavailableException(
        "S3 put failed (bucket=" + bucket + ", key=" + key + "): " + e.getMessage(),
        e
      );
    } catch (Exception e) {
      throw new StorageException("S3 put failed unexpectedly: " + e.getMessage(), e);
    }
  }

  /**
   * Fetch bytes via {@code GetObjectRequest}. Streams the S3 response
   * body; caller is responsible for closing the returned stream.
   */
  @Override
  public StorageGetResponse get(StorageLocator locator) throws StorageException {
    validateLocator(locator);
    S3StorageConfig cfg = requireActive();
    S3Client client = buildClient(cfg);

    String[] parts = splitLocator(locator.locator());
    String bucket = parts[0];
    String key = parts[1];

    HeadObjectRequest headRequest = HeadObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .build();

    GetObjectRequest getRequest = GetObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .build();

    try {
      HeadObjectResponse head = client.headObject(headRequest);
      InputStream stream = client.getObject(getRequest);
      String fileName = extractFileName(key);
      Long sizeBytes = head.contentLength();
      String contentType = head.contentType();

      Log.debugf("S3FileStorage.get: bucket=%s key=%s size=%s", bucket, key, sizeBytes);
      return new StorageGetResponse(
        ID,
        fileName,
        (contentType == null || contentType.isBlank()) ? null : contentType,
        sizeBytes,
        stream
      );
    } catch (NoSuchKeyException e) {
      throw new StorageNotFoundException(
        "S3 object not found (bucket=" + bucket + ", key=" + key + ")",
        e
      );
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        throw new StorageNotFoundException(
          "S3 object not found (bucket=" + bucket + ", key=" + key + "): " + e.getMessage(),
          e
        );
      }
      throw new StorageProviderUnavailableException(
        "S3 get failed (bucket=" + bucket + ", key=" + key + "): " + e.getMessage(),
        e
      );
    } catch (StorageException e) {
      throw e;
    } catch (Exception e) {
      throw new StorageException("S3 get failed unexpectedly: " + e.getMessage(), e);
    }
  }

  /**
   * Delete object. Idempotent — swallows {@code NoSuchKeyException}
   * so double-deletes after a partial failure are safe.
   */
  @Override
  public void delete(StorageLocator locator) throws StorageException {
    validateLocator(locator);
    S3StorageConfig cfg = requireActive();
    S3Client client = buildClient(cfg);

    String[] parts = splitLocator(locator.locator());
    String bucket = parts[0];
    String key = parts[1];

    DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .build();

    try {
      client.deleteObject(deleteRequest);
      Log.debugf("S3FileStorage.delete: bucket=%s key=%s", bucket, key);
    } catch (NoSuchKeyException e) {
      // Idempotent — swallow, as specified by the SPI contract.
      Log.debugf("S3FileStorage.delete: already absent (bucket=%s key=%s) — idempotent OK", bucket, key);
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        // Some S3-compatible impls return 404 from DeleteObject for missing keys.
        Log.debugf("S3FileStorage.delete: 404 on absent key (bucket=%s key=%s) — idempotent OK", bucket, key);
        return;
      }
      throw new StorageProviderUnavailableException(
        "S3 delete failed (bucket=" + bucket + ", key=" + key + "): " + e.getMessage(),
        e
      );
    } catch (Exception e) {
      throw new StorageException("S3 delete failed unexpectedly: " + e.getMessage(), e);
    }
  }

  // ─── helpers ────────────────────────────────────────────────────

  /**
   * Build a fresh {@code S3Client} from the current config. Throws
   * {@link StorageProviderUnavailableException} if credential or
   * endpoint config is missing.
   */
  S3Client buildClient(S3StorageConfig cfg) throws StorageProviderUnavailableException {
    String accessKeyId = cfg.getAccessKeyId();
    String secretKey;
    try {
      secretKey = configService.resolvePlaintextSecret();
    } catch (IllegalStateException e) {
      throw new StorageProviderUnavailableException(
        "S3 credential decryption failed — check shepard.instance.id and re-set credentials: " + e.getMessage(),
        e
      );
    }

    if (accessKeyId == null || accessKeyId.isBlank() || secretKey == null || secretKey.isBlank()) {
      throw new StorageProviderUnavailableException(
        "S3 credentials not configured — use POST /v2/admin/storage/s3/credential to set them"
      );
    }

    software.amazon.awssdk.services.s3.S3ClientBuilder builder = S3Client.builder()
      .credentialsProvider(
        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretKey))
      )
      .region(Region.of(cfg.getRegion() != null && !cfg.getRegion().isBlank() ? cfg.getRegion() : "us-east-1"))
      .serviceConfiguration(
        S3Configuration.builder()
          .pathStyleAccessEnabled(cfg.isForcePathStyle())
          .build()
      )
      .httpClient(
        UrlConnectionHttpClient.builder()
          .connectionTimeout(Duration.ofSeconds(cfg.getConnectionTimeoutSeconds()))
          .socketTimeout(Duration.ofSeconds(cfg.getRequestTimeoutSeconds()))
          .build()
      );

    String endpointUrl = cfg.getEndpointUrl();
    if (endpointUrl != null && !endpointUrl.isBlank()) {
      builder.endpointOverride(URI.create(endpointUrl));
    }

    return builder.build();
  }

  private S3StorageConfig requireActive() throws StorageProviderUnavailableException {
    S3StorageConfig cfg = configService.getActive();
    if (cfg == null) {
      throw new StorageProviderUnavailableException(
        "S3 storage adapter is not enabled or not fully configured. " +
        "Set enabled=true, bucket, accessKeyId, and secretAccessKey via " +
        "POST /v2/admin/storage/s3/credential + PATCH /v2/admin/storage/s3/config."
      );
    }
    return cfg;
  }

  /**
   * Resolve the bucket: if the request's container is a non-blank
   * bucket name override, use it; otherwise fall back to the
   * configured default bucket.
   */
  static String resolveBucket(S3StorageConfig cfg, String container) {
    // The GridFS adapter used container as the Mongo collection name.
    // For S3 the bucket is configured globally; the container field
    // is re-interpreted as a per-request bucket override when it looks
    // like a plain bucket name (no path separator, no colon).
    // If blank, fall back to the singleton bucket.
    if (container != null && !container.isBlank()
      && !container.contains(":") && !container.contains("/")) {
      return container;
    }
    return cfg.getBucket();
  }

  /**
   * Build the S3 object key from container and filename.
   * Format: {@code <prefix><container>/<fileName>} where prefix is
   * the optional bucket prefix. The container is included so
   * multiple containers don't collide under the same key space.
   */
  static String buildKey(String container, String fileName) {
    // Sanitise: strip leading slashes, collapse internal double-slashes
    String safeContainer = sanitise(container);
    String safeFileName = sanitise(fileName);
    if (safeContainer.isEmpty()) {
      return safeFileName;
    }
    return safeContainer + "/" + safeFileName;
  }

  /** Strip leading / trailing slashes and collapse double-slashes. */
  private static String sanitise(String s) {
    if (s == null) return "";
    String t = s.trim();
    // Remove leading slashes
    while (t.startsWith("/")) {
      t = t.substring(1);
    }
    // Collapse double-slashes
    while (t.contains("//")) {
      t = t.replace("//", "/");
    }
    return t;
  }

  /**
   * Split a locator string of the form {@code "<bucket>:<key>"}
   * into a 2-element array {@code {bucket, key}}.
   */
  static String[] splitLocator(String locator) throws StorageNotFoundException {
    int sep = locator.indexOf(LOCATOR_SEPARATOR);
    if (sep < 0) {
      throw new StorageNotFoundException(
        "Malformed S3 locator (missing ':' separator): '" + locator + "'"
      );
    }
    return new String[] { locator.substring(0, sep), locator.substring(sep + 1) };
  }

  /** Extract the filename from a key path (last path segment). */
  static String extractFileName(String key) {
    if (key == null || key.isBlank()) return "file";
    int slash = key.lastIndexOf('/');
    String name = slash >= 0 ? key.substring(slash + 1) : key;
    return name.isBlank() ? "file" : name;
  }

  private void validateLocator(StorageLocator locator) throws StorageNotFoundException {
    if (!ID.equals(locator.providerId())) {
      throw new StorageNotFoundException(
        "S3FileStorage cannot resolve locator for provider '" + locator.providerId() + "' (expected 's3')"
      );
    }
  }

  private static boolean isQuotaError(S3Exception e) {
    // S3 itself doesn't have a dedicated quota error code in standard APIs;
    // some compatible impls (Backblaze B2) return 507 or specific error codes.
    int status = e.statusCode();
    return status == 507;
  }
}
