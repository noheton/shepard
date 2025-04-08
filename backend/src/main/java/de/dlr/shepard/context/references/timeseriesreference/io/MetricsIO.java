package de.dlr.shepard.context.references.timeseriesreference.io;

import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MetricsIO {

  AggregateFunction function;
  Object value;
}
