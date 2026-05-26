package de.dlr.shepard.plugins.video.cli.io;

/**
 * VID1c — CLI mirror of the backend's {@code VideoConfigIO} JSON shape —
 * deserialises the response from {@code GET /v2/admin/video/config}.
 *
 * <p>Read-side only; the CLI sends merge-patch requests as raw
 * {@code Map<String, Object>} bodies (the absent / null distinction
 * is hard to model on a Jackson POJO without {@code @JsonSetter}
 * tracking we don't need on the CLI side).
 */
public final class VideoConfigCli {

  private boolean ffprobeEnabled;
  private Long maxFileSizeMb;

  public boolean isFfprobeEnabled() {
    return ffprobeEnabled;
  }

  public void setFfprobeEnabled(boolean ffprobeEnabled) {
    this.ffprobeEnabled = ffprobeEnabled;
  }

  public Long getMaxFileSizeMb() {
    return maxFileSizeMb;
  }

  public void setMaxFileSizeMb(Long maxFileSizeMb) {
    this.maxFileSizeMb = maxFileSizeMb;
  }
}
