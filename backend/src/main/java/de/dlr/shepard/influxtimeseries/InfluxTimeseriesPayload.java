package de.dlr.shepard.influxtimeseries;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InfluxTimeseriesPayload {

  @NotNull
  private InfluxTimeseries timeseries;

  @NotEmpty
  private List<InfluxPoint> points = new ArrayList<>();
}
