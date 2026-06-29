package de.dlr.shepard.spi.transcode;

import de.dlr.shepard.storage.StorageLocator;

/**
 * VID-FFMPEG-TRANSCODE-2026-06-29 — input record for
 * {@link TranscodingProvider#transcode}.
 *
 * @param sourceLocator      where the original bytes live in the active
 *                           {@link de.dlr.shepard.storage.FileStorage}
 *                           adapter. Never null.
 * @param targetContainer    storage container key for the proxy output
 *                           (typically the same container the source
 *                           lives in — e.g.
 *                           {@code VideoStreamReferenceService.VIDEO_CONTAINER}).
 *                           Never null/blank.
 * @param targetFileName     human-readable filename for the proxy
 *                           ({@code Content-Disposition} on download —
 *                           e.g. {@code "MyClip_proxy.mp4"}). Never
 *                           null/blank.
 * @param videoBitrateKbps   target video bitrate in kbit/s. The 2026-06-29
 *                           backlog row suggests 2-5 Mbps for 4K hevc
 *                           sources; default 3000 (~3 Mbps) gives a
 *                           middle-of-the-road quality/size tradeoff.
 * @param maxHeight          cap on the proxy's pixel height — letterbox-safe
 *                           downscale (ffmpeg's {@code scale=-2:HEIGHT}).
 *                           Set to 0 to leave the source resolution alone.
 *                           1080 by default — 4K → 1080p for desktop
 *                           playback.
 * @param timeoutSeconds     hard upper bound on the transcode runtime.
 *                           Must be &gt; 0. The provider kills the process
 *                           and returns {@link TranscodeResult#failed} on
 *                           timeout.
 */
public record TranscodeRequest(
  StorageLocator sourceLocator,
  String targetContainer,
  String targetFileName,
  int videoBitrateKbps,
  int maxHeight,
  long timeoutSeconds
) {

  /** ~3 Mbps — middle-of-the-road browser-playable quality. */
  public static final int DEFAULT_BITRATE_KBPS = 3000;

  /** 1080p — desktop-playback ceiling; downscales 4K, leaves 720p alone. */
  public static final int DEFAULT_MAX_HEIGHT = 1080;

  /** 1 hour — enough for a 4K hevc source on commodity CPU. */
  public static final long DEFAULT_TIMEOUT_SECONDS = 3600L;

  public TranscodeRequest {
    if (sourceLocator == null) {
      throw new IllegalArgumentException("sourceLocator must not be null");
    }
    if (targetContainer == null || targetContainer.isBlank()) {
      throw new IllegalArgumentException("targetContainer must not be null/blank");
    }
    if (targetFileName == null || targetFileName.isBlank()) {
      throw new IllegalArgumentException("targetFileName must not be null/blank");
    }
    if (videoBitrateKbps < 0) {
      throw new IllegalArgumentException("videoBitrateKbps must be >= 0 (got " + videoBitrateKbps + ")");
    }
    if (maxHeight < 0) {
      throw new IllegalArgumentException("maxHeight must be >= 0 (got " + maxHeight + ")");
    }
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("timeoutSeconds must be > 0 (got " + timeoutSeconds + ")");
    }
  }

  /**
   * Convenience factory using the defaults
   * ({@link #DEFAULT_BITRATE_KBPS}, {@link #DEFAULT_MAX_HEIGHT},
   * {@link #DEFAULT_TIMEOUT_SECONDS}).
   */
  public static TranscodeRequest defaults(
    StorageLocator sourceLocator,
    String targetContainer,
    String targetFileName
  ) {
    return new TranscodeRequest(
      sourceLocator,
      targetContainer,
      targetFileName,
      DEFAULT_BITRATE_KBPS,
      DEFAULT_MAX_HEIGHT,
      DEFAULT_TIMEOUT_SECONDS
    );
  }
}
