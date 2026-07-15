package de.dlr.shepard.v2.timeseriescontainer.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * One data point in a live-window response.
 *
 * <p>Boundary points inserted by the server carry {@code interpolated=true}.
 * Raw data points have {@code interpolated=false}.
 */
public record LiveWindowPointIO(
  @Schema(description = "ISO 8601 UTC timestamp of this data point.",
    example = "2024-06-01T07:58:32.001Z")
  String timestamp,

  @Schema(description = "The channel value at this timestamp.")
  Object value,

  @Schema(description = "true when this point was linearly interpolated at the window boundary; false for raw data.")
  boolean interpolated
) {}
