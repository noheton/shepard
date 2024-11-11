package de.dlr.shepard.timeseries.io;

import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPoint;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * Representation of a timeseries payload data object, containing timeseries metadata and
 * the list of data points
 */
@Data
public class ExperimentalTimeseriesWithDataPoints {

  @NotNull
  private final ExperimentalTimeseries timeseries;

  @NotEmpty
  private final List<ExperimentalTimeseriesDataPoint> points;
}
