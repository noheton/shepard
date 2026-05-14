package de.dlr.shepard.storage;

/**
 * FS1a — signals that the {@link FileStorage} provider has refused
 * a {@link FileStorage#put(StoragePutRequest)} because an
 * operator-side quota (bucket size cap, per-tenant budget,
 * provider-imposed object-count limit) has been exhausted.
 *
 * <p>Mapped by the REST layer to RFC 7807
 * {@code storage.quota.exceeded} with HTTP 507 (Insufficient
 * Storage) — operators see an actionable error pointing at the
 * quota knob.
 *
 * <p>The default {@code GridFsFileStorage} never throws this — Mongo
 * doesn't expose a per-collection size cap that GridFS surfaces
 * through its Java driver. FS1b's S3 adapter raises this when the
 * S3 endpoint replies with {@code 507 Insufficient Storage} or
 * {@code 403 QuotaExceeded} (per-endpoint convention).
 */
public class StorageQuotaExceededException extends StorageException {

  private static final long serialVersionUID = 1L;

  public StorageQuotaExceededException(String message) {
    super(message);
  }

  public StorageQuotaExceededException(String message, Throwable cause) {
    super(message, cause);
  }
}
