package de.dlr.shepard.timeseries.io;

import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * Representation of a timeseries payload data object, containing timeseries metadata and
 * the list of payload points
 */
@Data
public class ExperimentalTimeseriesPayloadIO {

  @NotNull
  private final ExperimentalTimeseries timeseries;

  @NotEmpty
  private final List<ExperimentalTimeseriesPayloadDataPointIO> points;
}
