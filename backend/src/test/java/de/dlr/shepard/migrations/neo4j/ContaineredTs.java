package de.dlr.shepard.migrations.neo4j;

import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Helper class to represent a timeseries including its corresponding container id.
 */
@EqualsAndHashCode(callSuper = true)
@Data
class ContaineredTs extends TimeseriesWithDataPoints {

  @NotNull
  private long containerId;

  public ContaineredTs(long containerId, Timeseries timeseries, List<TimeseriesDataPoint> points) {
    super(timeseries, points);
    this.containerId = containerId;
  }
}
