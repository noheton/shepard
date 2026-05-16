package de.dlr.shepard.plugins.files3;

import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StorageNotFoundException;
import de.dlr.shepard.storage.StorageProviderUnavailableException;
import de.dlr.shepard.storage.StoragePutRequest;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.core.ResponseInputStream;

/**
 * FS1b — S3-compatible {@link FileStorage} adapter. Implements the
 * FS1a SPI using AWS SDK v2; any S3-compatible endpoint works (AWS
 * S3, Cloudflare R2, Backblaze B2, Wasabi, Garage, MinIO, …).
 *
 * <p>The default quick-start endpoint for the {@code files-s3}
 * compose profile is <b>Garage</b> per ADR-0024.
 *
 * <p><b>Locator format.</b> The opaque locator string returned by
 * {@link #put} is {@code "<bucket>/<containerOid>/<uuid>"} where
 * {@code uuid} is a fresh random key per upload. Storing the bucket
 * name in the locator keeps old rows self-describing even if the
 * operator later changes {@code shepard.files.s3.bucket}.
 *
 * <p><b>Deploy-time config</b> (cluster-identity exception per
 * {@code CLAUDE.md} — switching the storage endpoint mid-deploy
 * would orphan in-flight writes):
 * <ul>
 *   <li>{@code shepard.files.s3.endpoint} — full URL (e.g.
 *       {@code http://garage:3900}). Omit for real AWS S3.</li>
 *   <li>{@code shepard.files.s3.region} — AWS region; default
 *       {@code us-east-1} (also used by Garage for the bucket
 *       placement zone).</li>
 *   <li>{@code shepard.files.s3.access-key-id} — S3 access key ID.
 *       Falls back to AWS default credentials chain if blank.</li>
 *   <li>{@code shepard.files.s3.secret-access-key} — S3 secret.</li>
 *   <li>{@code shepard.files.s3.bucket} — bucket name. If blank the
 *       adapter is disabled ({@link #isEnabled()} → false).</li>
 *   <li>{@code shepard.files.s3.path-style-access} — {@code true}
 *       (default) forces path-style URLs
 *       ({@code http://<host>/<bucket>/<key>}) required by Garage,
 *       MinIO, LocalStack. Set {@code false} for real AWS S3.</li>
 * </ul>
 *
 * <p>See {@code aidocs/45 §9 FS1b} and the operator runbook at
 * {@code docs/reference/file-storage.md}.
 */
@ApplicationScoped
public class S3FileStorage implements FileStorage {

  public static final String ID = "s3";

  @ConfigProperty(name = "shepard.files.s3.endpoint", defaultValue = "")
  String endpoint;

  @ConfigProperty(name = "shepard.files.s3.region", defaultValue = "us-east-1")
  String region;

  @ConfigProperty(name = "shepard.files.s3.access-key-id", defaultValue = "")
  String accessKeyId;

  @ConfigProperty(name = "shepard.files.s3.secret-access-key", defaultValue = "")
  String secretAccessKey;

  @ConfigProperty(name = "shepard.files.s3.bucket", defaultValue = "")
  String bucket;

  @ConfigProperty(name = "shepard.files.s3.path-style-access", defaultValue = "true")
  boolean pathStyleAccess;

  private volatile S3Client s3;
  private volatile boolean enabled;

  @PostConstruct
  void init() {
    if (bucket == null || bucket.isBlank()) {
      enabled = false;
      Log.infof(
        "S3FileStorage: shepard.files.s3.bucket is unset — adapter disabled. " +
        "Set shepard.storage.provider=s3 only after configuring the bucket."
      );
      return;
    }

    S3ClientBuilder builder = S3Client.builder()
      .httpClientBuilder(UrlConnectionHttpClient.builder())
      .region(Region.of(region == null || region.isBlank() ? "us-east-1" : region.trim()))
      .serviceConfiguration(S3Configuration.builder()
        .pathStyleAccessEnabled(pathStyleAccess)
        .build());

    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint.trim()));
    }

    if (accessKeyId != null && !accessKeyId.isBlank() && secretAccessKey != null && !secretAccessKey.isBlank()) {
      builder.credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim())
        )
      );
    }

    this.s3 = builder.build();
    ensureBucketExists();
    this.enabled = true;

    Log.infof(
      "S3FileStorage: registered (id=%s, bucket=%s, endpoint=%s, pathStyle=%s)",
      ID,
      bucket,
      endpoint == null || endpoint.isBlank() ? "<aws>" : endpoint,
      pathStyleAccess
    );
  }

  private void ensureBucketExists() {
    try {
      s3.headBucket(r -> r.bucket(bucket));
    } catch (NoSuchBucketException | S3Exception e) {
      try {
        s3.createBucket(r -> r.bucket(bucket));
        Log.infof("S3FileStorage: created bucket '%s'", bucket);
      } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ok) {
        // race: another instance created it first — fine
      } catch (S3Exception createEx) {
        Log.warnf(
          "S3FileStorage: could not create bucket '%s' — %s (bucket must exist before uploads work)",
          bucket,
          createEx.awsErrorDetails().errorMessage()
        );
      }
    }
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public StorageLocator put(StoragePutRequest request) throws StorageException {
    requireEnabled();
    String key = request.container() + "/" + UUID.randomUUID();
    String locatorValue = bucket + "/" + key;

    PutObjectRequest.Builder reqBuilder = PutObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .contentDisposition("attachment; filename=\"" + request.fileName() + "\"");
    if (request.contentType() != null) {
      reqBuilder.contentType(request.contentType());
    }

    RequestBody body;
    try {
      if (request.sizeBytes() != null) {
        body = RequestBody.fromInputStream(request.bytes(), request.sizeBytes());
      } else {
        body = RequestBody.fromBytes(request.bytes().readAllBytes());
      }
    } catch (IOException e) {
      throw new StorageException("S3 put failed reading input stream: " + e.getMessage(), e);
    }

    try {
      s3.putObject(reqBuilder.build(), body);
    } catch (S3Exception e) {
      throw new StorageException("S3 put failed for key '" + key + "': " + e.awsErrorDetails().errorMessage(), e);
    }

    return new StorageLocator(ID, locatorValue);
  }

  @Override
  public StorageGetResponse get(StorageLocator locator) throws StorageException {
    requireEnabled();
    requireS3Locator(locator);
    LocatorParts parts = splitLocator(locator);

    GetObjectRequest request = GetObjectRequest.builder()
      .bucket(parts.bucket())
      .key(parts.key())
      .build();

    try {
      ResponseInputStream<GetObjectResponse> response = s3.getObject(request);
      GetObjectResponse meta = response.response();
      return new StorageGetResponse(
        ID,
        extractFileName(meta.contentDisposition(), parts.key()),
        meta.contentType(),
        meta.contentLength() > 0 ? meta.contentLength() : null,
        response
      );
    } catch (NoSuchKeyException e) {
      throw new StorageNotFoundException(
        "S3 object '" + locator.locator() + "' not found",
        e
      );
    } catch (S3Exception e) {
      throw new StorageProviderUnavailableException(
        "S3 get failed for '" + locator.locator() + "': " + e.awsErrorDetails().errorMessage(),
        e
      );
    }
  }

  @Override
  public void delete(StorageLocator locator) throws StorageException {
    requireEnabled();
    requireS3Locator(locator);
    LocatorParts parts = splitLocator(locator);

    try {
      s3.deleteObject(DeleteObjectRequest.builder()
        .bucket(parts.bucket())
        .key(parts.key())
        .build());
    } catch (NoSuchKeyException e) {
      // Idempotent contract: missing key is a no-op.
    } catch (S3Exception e) {
      throw new StorageProviderUnavailableException(
        "S3 delete failed for '" + locator.locator() + "': " + e.awsErrorDetails().errorMessage(),
        e
      );
    }
  }

  private void requireEnabled() throws StorageException {
    if (!enabled) {
      throw new StorageProviderUnavailableException(
        "S3FileStorage is not enabled — configure shepard.files.s3.bucket (and optionally endpoint, credentials)"
      );
    }
  }

  private static void requireS3Locator(StorageLocator locator) throws StorageException {
    if (!ID.equals(locator.providerId())) {
      throw new StorageException(
        "S3FileStorage received locator for provider '" + locator.providerId() +
        "' — refusing to dispatch. FileStorageRegistry should route via providerId."
      );
    }
  }

  static LocatorParts splitLocator(StorageLocator locator) throws StorageException {
    String raw = locator.locator();
    int firstSlash = raw.indexOf('/');
    if (firstSlash <= 0 || firstSlash >= raw.length() - 1) {
      throw new StorageException(
        "Malformed S3 locator '" + raw + "' — expected '<bucket>/<key>'"
      );
    }
    return new LocatorParts(raw.substring(0, firstSlash), raw.substring(firstSlash + 1));
  }

  private static String extractFileName(String contentDisposition, String fallbackKey) {
    if (contentDisposition != null) {
      int idx = contentDisposition.indexOf("filename=");
      if (idx >= 0) {
        String fn = contentDisposition.substring(idx + 9).trim();
        if (fn.startsWith("\"") && fn.length() > 2) {
          fn = fn.substring(1, fn.endsWith("\"") ? fn.length() - 1 : fn.length());
        }
        if (!fn.isBlank()) return fn;
      }
    }
    int lastSlash = fallbackKey.lastIndexOf('/');
    return lastSlash >= 0 && lastSlash < fallbackKey.length() - 1
      ? fallbackKey.substring(lastSlash + 1)
      : fallbackKey;
  }

  record LocatorParts(String bucket, String key) {}
}
