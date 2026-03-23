package de.dlr.shepard.data.timeseries.io;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesFiveTuple;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * Representation of a timeseries payload data object, containing timeseries metadata and
 * the list of data points
 */
@Data
public class TimeseriesWithDataPoints {

  @NotNull
  private final TimeseriesFiveTuple timeseries;

  @NotEmpty
  private final List<TimeseriesDataPoint> points;
}
