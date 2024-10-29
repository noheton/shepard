package de.dlr.shepard.timeseries.model;

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
  private List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>();
}
