package de.dlr.shepard.storage;

/**
 * FS1a — signals that the {@link FileStorage} provider is reachable
 * but unhealthy (Mongo replica set in failover, S3 endpoint
 * returning 5xx, credential temporarily revoked).
 *
 * <p>Mapped by the REST layer to RFC 7807
 * {@code storage.provider.unavailable} with HTTP 503 —
 * operators see a clean envelope with the underlying provider's
 * message rather than the cryptic Hibernate / Mongo / S3-SDK stack
 * trace that pre-FS1a would have surfaced through the H4 fallback.
 *
 * <p>Distinct from {@link StorageNotInstalledException}: "the
 * provider is wired but not answering" vs "no provider is wired
 * at all".
 */
public class StorageProviderUnavailableException extends StorageException {

  private static final long serialVersionUID = 1L;

  public StorageProviderUnavailableException(String message) {
    super(message);
  }

  public StorageProviderUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
