package de.dlr.shepard.timeseries.model;

import de.dlr.shepard.timeseries.io.ExperimentalTimeseriesPayloadDataPointIO;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ExperimentalTimeseriesData {

  @NotNull
  private ExperimentalTimeseries timeseries;

  @NotEmpty
  private List<ExperimentalTimeseriesPayloadDataPointIO> dataPoints = new ArrayList<>();
}
