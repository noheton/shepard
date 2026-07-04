package de.dlr.shepard.spi.transcode;

import de.dlr.shepard.storage.StorageLocator;

/**
 * VID-FFMPEG-TRANSCODE-2026-06-29 — outcome of a
 * {@link TranscodingProvider#transcode} call.
 *
 * <p>Carries success ({@link #ok(StorageLocator)}) or recoverable failure
 * ({@link #failed(String)}). The transcode is a secondary effect of the
 * upload; recoverable failure must NOT propagate to the upload caller.
 *
 * @param locator      the proxy's {@link StorageLocator} on success; null
 *                     on failure
 * @param errorMessage operator-facing message on failure (ffmpeg stderr
 *                     tail, timeout reason, etc.); null on success
 */
public record TranscodeResult(StorageLocator locator, String errorMessage) {

  /** Did the transcode succeed? */
  public boolean isSuccess() {
    return locator != null;
  }

  /**
   * @param locator non-null proxy locator
   * @return success result
   */
  public static TranscodeResult ok(StorageLocator locator) {
    if (locator == null) {
      throw new IllegalArgumentException("locator must not be null on success");
    }
    return new TranscodeResult(locator, null);
  }

  /**
   * @param errorMessage non-null, non-blank human-readable failure detail
   * @return failure result
   */
  public static TranscodeResult failed(String errorMessage) {
    if (errorMessage == null || errorMessage.isBlank()) {
      throw new IllegalArgumentException("errorMessage must not be null/blank on failure");
    }
    return new TranscodeResult(null, errorMessage);
  }
}
