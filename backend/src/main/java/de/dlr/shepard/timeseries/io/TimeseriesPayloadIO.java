package de.dlr.shepard.timeseries.io;

import de.dlr.shepard.timeseries.entities.ExperimentalTimeseries;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Representation of a timeseries payload data object, containing timeseries metadata and
 * the list of payload points
 */
@Data
public class TimeseriesPayloadIO {

  @NotNull
  private ExperimentalTimeseries timeseries;

  @NotEmpty
  private List<TimeseriesPayloadDataPointIO> points = new ArrayList<>();
}
