package de.dlr.shepard.context.references.videostreamreference.services;

/**
 * VID1a — immutable result of a {@link VideoProbeService} probe run.
 *
 * <p>All fields are nullable: when ffprobe is absent, the file is
 * unreadable, or a particular stream/tag is missing, the corresponding
 * field is {@code null} — the upload still succeeds.
 */
public record VideoProbeResult(
  Double durationSeconds,
  Long fileSizeBytes,
  Integer width,
  Integer height,
  Double frameRate,
  String videoCodec,
  String audioCodec,
  Long wallClockTimestamp
) {
  /** Convenience factory that represents a fully-failed probe (all nulls). */
  public static VideoProbeResult empty() {
    return new VideoProbeResult(null, null, null, null, null, null, null, null);
  }
}
