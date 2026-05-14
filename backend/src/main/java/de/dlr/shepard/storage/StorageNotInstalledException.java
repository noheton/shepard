package de.dlr.shepard.storage;

/**
 * FS1a — signals that the file-payload flow cannot proceed because
 * no {@link FileStorage} adapter is currently active. Raised by
 * {@code FileContainerService} when
 * {@link FileStorageRegistry#activeStorage()} is empty (no provider
 * installed, configured id missing, or active bean reports
 * {@code isEnabled()=false}).
 *
 * <p>Mapped by {@link StorageNotInstalledExceptionMapper} to RFC 7807
 * {@code storage.provider.not-installed} with HTTP 503 — operators
 * see a clean, actionable error message ("set
 * {@code shepard.storage.provider=gridfs} (or install an FS1b
 * plugin and set it to that id)") rather than the cryptic
 * null-pointer that pre-FS1a would have surfaced.
 *
 * <p>Distinct from {@link StorageException}: that exception signals
 * a put / get / delete that started but failed (network blip,
 * upstream rejection); this one signals the prerequisite "we never
 * even tried because there's nothing wired".
 *
 * <p>Mirrors the
 * {@link de.dlr.shepard.publish.minter.MinterNotInstalledException}
 * on the publish side — same shape, same semantics.
 */
public class StorageNotInstalledException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public StorageNotInstalledException(String message) {
    super(message);
  }
}
