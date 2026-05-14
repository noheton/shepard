package de.dlr.shepard.storage;

/**
 * Operator-readable failure raised by a {@link FileStorage}
 * implementation when a put / get / delete cannot complete (network
 * blip, upstream rejection, credential expiry, quota exhausted, …).
 *
 * <p>FS1a defines the superclass + three subclasses
 * ({@link StorageNotFoundException},
 * {@link StorageProviderUnavailableException},
 * {@link StorageQuotaExceededException}). The
 * {@code GridFsFileStorage} default in core throws only the
 * not-found and the catch-all shapes; FS1b's S3 adapter adds the
 * provider-unavailable + quota subclasses where they apply.
 *
 * <p>The REST layer maps {@code StorageException} to an RFC 7807
 * problem response (type {@code storage.failed}) — the
 * {@link #getMessage()} is suitable for the {@code detail} field.
 * The subclasses get distinct {@code type} URIs so operators can
 * triage:
 *
 * <ul>
 *   <li>{@link StorageNotFoundException} → 404
 *       {@code storage.payload.not-found}</li>
 *   <li>{@link StorageProviderUnavailableException} → 503
 *       {@code storage.provider.unavailable}</li>
 *   <li>{@link StorageQuotaExceededException} → 507
 *       {@code storage.quota.exceeded}</li>
 *   <li>plain {@link StorageException} → 500
 *       {@code storage.failed}</li>
 * </ul>
 *
 * <p>Checked rather than runtime: this is the contract every
 * adapter compiles against, and the {@code FileContainerService}
 * boundary translates {@code StorageException} into the JAX-RS
 * shape the upstream-compatible REST surface expects. Forcing the
 * checked-vs-unchecked decision at the SPI keeps the translation
 * boundary explicit.
 */
public class StorageException extends Exception {

  private static final long serialVersionUID = 1L;

  public StorageException(String message) {
    super(message);
  }

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
