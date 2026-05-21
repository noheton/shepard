package de.dlr.shepard.v2.timeseriescontainer.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response envelope for the live-window endpoint.
 *
 * <p>Timestamps are epoch milliseconds (UTC). The {@code points} list is
 * ordered by ascending timestamp. Boundary points at the window edges are
 * linearly interpolated and carry {@code interpolated=true}.
 */
public record LiveWindowResponseIO(
  @Schema(description = "Window start in epoch milliseconds (= now − windowSeconds × 1000).")
  long windowStart,

  @Schema(description = "Window end in epoch milliseconds (≈ now at the time of the request).")
  long windowEnd,

  @Schema(description = "Data points in ascending timestamp order. May include interpolated boundary points.")
  List<LiveWindowPointIO> points
) {}
