package de.dlr.shepard.v2.timeseriescontainer.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response envelope for the live-window endpoint.
 *
 * <p>Timestamps are ISO 8601 UTC strings (e.g. {@code "2024-06-01T08:00:00Z"}). The
 * {@code points} list is ordered by ascending timestamp. Boundary points at the window
 * edges are linearly interpolated and carry {@code interpolated=true}.
 */
public record LiveWindowResponseIO(
  @Schema(description = "Window start as ISO 8601 UTC (= now − windowSeconds).",
    example = "2024-06-01T07:55:00Z")
  String windowStart,

  @Schema(description = "Window end as ISO 8601 UTC (≈ now at the time of the request).",
    example = "2024-06-01T08:00:00Z")
  String windowEnd,

  @Schema(description = "Data points in ascending timestamp order. May include interpolated boundary points.")
  List<LiveWindowPointIO> points
) {}
