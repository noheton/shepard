package de.dlr.shepard.context.references.timeseriesreference.io;

import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
public class MetricsIO {

  @NotNull
  @Schema(required = true)
  AggregateFunction function;

  @NotNull
  @Schema(required = true)
  Object value;
}
