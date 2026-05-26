package de.dlr.shepard.plugins.video.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.plugins.video.entities.VideoConfig;

/**
 * VID1c — JSON shape returned by {@code GET /v2/admin/video/config}.
 *
 * <p>{@code @JsonInclude(NON_NULL)} so optional fields
 * ({@code maxFileSizeMb}) are dropped from the response when unset
 * (= no cap configured).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoConfigIO(
  boolean ffprobeEnabled,
  Long maxFileSizeMb
) {

  /**
   * Project a {@link VideoConfig} entity onto the response IO.
   */
  public static VideoConfigIO from(VideoConfig cfg) {
    return new VideoConfigIO(
      cfg.isFfprobeEnabled(),
      cfg.getMaxFileSizeMb()
    );
  }
}
