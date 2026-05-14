package de.dlr.shepard.storage;

/**
 * Opaque container identifying a stored payload. FS1a baseline
 * shape per {@code aidocs/45 §3.2}.
 *
 * <p>The {@code providerId} carries the {@link FileStorage#id()} of
 * the adapter that produced the row — stamped onto {@code :ShepardFile}
 * (the file-payload entity) so a re-fetch can route to the right
 * adapter across mixed-provider history (operator started on GridFS,
 * moved to S3 via FS1e migration; old rows still resolve via the
 * GridFS adapter).
 *
 * <p>The {@code locator} field is provider-specific and treated as
 * opaque by everything outside the producing adapter:
 *
 * <ul>
 *   <li>{@code "gridfs"} → {@code "<containerMongoId>:<fileOid>"}
 *       (the FileService bookkeeping pair).</li>
 *   <li>{@code "s3"} (FS1b) → {@code "<bucket>/<key>"}.</li>
 *   <li>future adapters → whatever they need.</li>
 * </ul>
 *
 * <p>Same role as {@code MintResult.pid} on the publish side: the
 * SPI's caller knows what kind of identifier to expect (PID for the
 * minter, opaque locator for storage), but the format itself is the
 * adapter's business.
 *
 * @param providerId the {@link FileStorage#id()} of the adapter
 *                   that owns this locator. Never null, never
 *                   blank.
 * @param locator    the provider-specific opaque key. Never null,
 *                   never blank.
 */
public record StorageLocator(String providerId, String locator) {
  public StorageLocator {
    if (providerId == null || providerId.isBlank()) {
      throw new IllegalArgumentException("providerId must not be null/blank");
    }
    if (locator == null || locator.isBlank()) {
      throw new IllegalArgumentException("locator must not be null/blank");
    }
  }
}
