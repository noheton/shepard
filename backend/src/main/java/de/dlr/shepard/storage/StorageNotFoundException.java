package de.dlr.shepard.storage;

/**
 * FS1a — signals that a {@link FileStorage#get(StorageLocator)} or
 * a non-idempotent caller (e.g. an admin probe) found no blob at
 * the supplied locator. The {@link FileStorage#delete(StorageLocator)}
 * contract is idempotent; adapters swallow this internally on
 * delete.
 *
 * <p>Mapped by the REST layer to RFC 7807
 * {@code storage.payload.not-found} with HTTP 404 — clients see no
 * change from the pre-FS1a {@code NotFoundException} that the
 * legacy upstream surface threw.
 *
 * <p>Distinct from {@link StorageProviderUnavailableException}:
 * "we tried but the bytes aren't there" vs "we couldn't reach the
 * provider to ask".
 */
public class StorageNotFoundException extends StorageException {

  private static final long serialVersionUID = 1L;

  public StorageNotFoundException(String message) {
    super(message);
  }

  public StorageNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
