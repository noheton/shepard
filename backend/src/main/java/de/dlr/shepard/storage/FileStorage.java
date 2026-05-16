package de.dlr.shepard.storage;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * FS1a SPI seam — a {@code FileStorage} adapter persists file
 * payload bytes for shepard's file payload kind. Mirrors the
 * {@link de.dlr.shepard.publish.minter.Minter} SPI shape: an
 * in-tree interface every plugin compiles against, with the
 * interface itself living in core and every adapter implementing
 * it from a sibling Maven module (or, for the default GridFS
 * adapter, from core itself).
 *
 * <p>Designed in {@code aidocs/45 §3.2} ("pluggable storage
 * backend"). Plugin-first heuristic #3 in {@code CLAUDE.md}: SPI
 * stays in core, adapters live wherever fits — the default
 * {@link de.dlr.shepard.storage.gridfs.GridFsFileStorage} ships in
 * core (zero-extra-deps install), the S3 adapter
 * ({@code shepard-plugin-file-s3}) lands in FS1b under
 * {@code plugins/storage-s3/}, future Seaweed / Wasabi / Garage
 * direct-binding adapters follow.
 *
 * <p>Lifecycle: discovered by CDI under {@code @ApplicationScoped} +
 * {@code @Default}; resolved by {@link FileStorageRegistry} via the
 * deploy-time {@code shepard.storage.provider} key. Switching the
 * active provider is a re-bootstrap decision (storage backend
 * change re-points the bytes pipeline; not safely runtime-flippable —
 * the {@code CLAUDE.md} "cluster identity / topology" exception
 * applies), so FS1a keeps this as a deploy-time-only knob.
 * Per-adapter admin config (e.g. S3 endpoint / bucket) lands with
 * the relevant plugin in FS1b and uses the runtime {@code :*Config}
 * pattern then.
 *
 * <p><strong>Optional posture.</strong> {@link FileStorageRegistry}
 * does not fail-fast when no provider matches the configured id —
 * the install boots with no active storage and any attempt to
 * upload or download a file payload returns RFC 7807
 * {@code storage.provider.not-installed} (503) until an operator
 * sets {@code shepard.storage.provider=<id>} + the matching adapter
 * is present. This matches the post-KIP1h
 * {@link de.dlr.shepard.publish.minter.MinterRegistry} relaxation.
 */
public interface FileStorage {
  /**
   * Stable identifier for this storage adapter — e.g.
   * {@code "gridfs"} (the in-core default), {@code "s3"} (FS1b
   * plugin). Must match the value an operator would put in
   * {@code shepard.storage.provider=…} to activate this adapter.
   *
   * <p>If two beans return the same {@code id()} the registry logs
   * a WARN and picks the first one CDI hands it (defensive, not
   * fail-fast — same shape as {@code MinterRegistry}).
   */
  String id();

  /**
   * Whether this storage adapter is currently usable (credentials
   * present, upstream reachable, feature toggle on). The default
   * {@code GridFsFileStorage} returns {@code true} when the MongoDB
   * connection is up; FS1b's {@code S3FileStorage} returns
   * {@code false} when its bucket / credentials aren't configured.
   *
   * <p>The registry refuses to activate a {@code !isEnabled()}
   * adapter — operators see a clean RFC 7807 error rather than a
   * cryptic upstream 5xx mid-upload.
   */
  boolean isEnabled();

  /**
   * Store bytes; return an opaque locator the caller persists
   * alongside the payload metadata (filename, md5, size, …). The
   * locator format is provider-specific — GridFS uses
   * {@code "<containerMongoId>:<fileOid>"}, S3 uses
   * {@code "<containerMongoId>/<uuid>"} — and is treated as opaque
   * by the rest of shepard.
   *
   * @throws StorageException with operator-readable message when
   *                          the storage tier rejects the upload
   *                          (network failure, quota exceeded,
   *                          credential expiry).
   * @throws StorageQuotaExceededException specifically when an
   *                                       operator-side quota has
   *                                       been exhausted.
   */
  StorageLocator put(StoragePutRequest request) throws StorageException;

  /**
   * Fetch by locator. Caller closes the stream.
   *
   * @throws StorageNotFoundException when the locator points at no
   *                                  known blob (404 surface).
   * @throws StorageProviderUnavailableException when the storage
   *                                             tier is reachable
   *                                             but unhealthy (503).
   * @throws StorageException for any other storage-tier failure.
   */
  StorageGetResponse get(StorageLocator locator) throws StorageException;

  /**
   * Delete by locator. Idempotent — missing keys must not throw.
   * Implementations should swallow {@code StorageNotFoundException}
   * at this layer so that double-deletes after a partial failure
   * are safe to retry.
   *
   * @throws StorageProviderUnavailableException when the storage
   *                                             tier is reachable
   *                                             but unhealthy.
   * @throws StorageException for any other storage-tier failure.
   */
  void delete(StorageLocator locator) throws StorageException;

  /**
   * FS1c — optionally returns a presigned PUT URL so clients can
   * upload directly to the storage backend without routing bytes
   * through the shepard application tier.
   *
   * <p>Adapters that don't support presigned upload (e.g. GridFS)
   * return {@code Optional.empty()}. The REST layer falls back to
   * the direct-upload path in that case.
   *
   * @param containerMongoId the MongoDB ObjectId of the container
   * @param fileName         original filename (stored as Content-Disposition)
   * @param ttl              how long the URL should be valid
   * @return a {@link PresignedPut} carrying the upload URL, the
   *         assigned object id (UUID), and the expiry instant; or
   *         {@link Optional#empty()} if not supported
   */
  default Optional<PresignedPut> presignedUploadUrl(String containerMongoId, String fileName, Duration ttl)
      throws StorageException {
    return Optional.empty();
  }

  /**
   * FS1c — optionally returns a presigned GET URL so clients can
   * download directly from the storage backend.
   *
   * <p>Adapters that don't support presigned download return
   * {@code Optional.empty()}.
   *
   * @param locator  the storage locator returned by {@link #put}
   * @param fileName original filename for Content-Disposition override
   * @param ttl      how long the URL should be valid
   * @return the presigned download URI, or {@link Optional#empty()}
   */
  default Optional<URI> presignedDownloadUrl(StorageLocator locator, String fileName, Duration ttl)
      throws StorageException {
    return Optional.empty();
  }

  /**
   * FS1g — upload a transient export artifact (e.g. an RO-Crate ZIP)
   * to the storage backend and return a presigned GET URL so the client
   * can download it directly without routing bytes through the JVM.
   *
   * <p>The object is keyed as {@code exports/<key>} in the same bucket
   * as regular file payloads. Adapters that don't support presigned
   * export (e.g. GridFS) return {@code Optional.empty()}; the REST
   * layer converts that to 503.
   *
   * <p>Cleanup: export objects accumulate unless the operator
   * configures a bucket lifecycle rule on the {@code exports/} prefix
   * (recommended: 24 h expiration). See
   * {@code docs/reference/file-storage.md §Export URL lifecycle}.
   *
   * @param key      unique key for this export (e.g. a UUID); the
   *                 adapter prefixes it with {@code "exports/"}
   * @param zipBytes the full ZIP payload to upload
   * @param fileName suggested download filename
   * @param ttl      how long the presigned GET URL should be valid
   * @return a presigned GET URI, or {@link Optional#empty()} if not
   *         supported by this adapter
   */
  default Optional<URI> presignedExportUrl(String key, byte[] zipBytes, String fileName, Duration ttl)
      throws StorageException {
    return Optional.empty();
  }

  /**
   * FS1c — payload returned by {@link #presignedUploadUrl}.
   *
   * @param uploadUrl   presigned PUT URL the client sends bytes to
   * @param assignedOid UUID assigned to this object (the oid that
   *                    will be stored once the client commits)
   * @param expiresAt   when the presigned URL expires
   */
  record PresignedPut(URI uploadUrl, String assignedOid, Instant expiresAt) {}
}
