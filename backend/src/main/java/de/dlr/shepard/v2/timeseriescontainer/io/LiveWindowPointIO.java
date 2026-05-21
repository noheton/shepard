package de.dlr.shepard.v2.timeseriescontainer.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * One data point in a live-window response.
 *
 * <p>Boundary points inserted by the server carry {@code interpolated=true}.
 * Raw data points have {@code interpolated=false}.
 */
public record LiveWindowPointIO(
  @Schema(description = "Epoch milliseconds (UTC).")
  long timestamp,

  @Schema(description = "The channel value at this timestamp.")
  Object value,

  @Schema(description = "true when this point was linearly interpolated at the window boundary; false for raw data.")
  boolean interpolated
) {}
