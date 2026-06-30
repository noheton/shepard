package de.dlr.shepard.plugins.video.cli;

/**
 * VID1c — URL constants for the video plugin admin REST surface.
 */
public final class VideoAdminPaths {

  public static final String CONFIG = "/v2/admin/config/video";

  /** VIDEO-HEVC-TRANSCODE-BACKFILL-2026-06-30 — admin re-submit endpoint. */
  public static final String TRANSCODE_BACKFILL = "/v2/admin/video/transcode-backfill";

  private VideoAdminPaths() {}
}
