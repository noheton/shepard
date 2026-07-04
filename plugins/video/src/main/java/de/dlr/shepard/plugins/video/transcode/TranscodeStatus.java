package de.dlr.shepard.plugins.video.transcode;

/**
 * VID-FFMPEG-TRANSCODE-2026-06-29 — lifecycle states stamped onto
 * {@code VideoStreamReference.proxyStatus}.
 *
 * <p>Persisted as a string property on the Neo4j node (rather than a typed
 * enum mapping) so the schema stays additive-nullable per the
 * {@code CLAUDE.md} schema rule — pre-feature rows simply carry
 * {@code null} for {@code proxyStatus} and {@code proxyStorageLocator} and
 * the read path treats them as {@link #NONE}.
 */
public enum TranscodeStatus {
  /** Field is null on the entity — no proxy was requested. */
  NONE,
  /** Job submitted to the transcode executor; proxy locator is still null. */
  PENDING,
  /** Proxy bytes are in storage; {@code proxyAvailable=true}. */
  READY,
  /** Transcode errored; the UI falls back to the original bytes. */
  FAILED;

  /**
   * @param raw the raw string property from the Neo4j node, or null
   * @return the matching status, or {@link #NONE} when raw is null /
   *         blank / unknown (defence against forward-compatible writes
   *         from a newer node landing in an older reader)
   */
  public static TranscodeStatus fromString(String raw) {
    if (raw == null || raw.isBlank()) return NONE;
    try {
      return TranscodeStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return NONE;
    }
  }
}
