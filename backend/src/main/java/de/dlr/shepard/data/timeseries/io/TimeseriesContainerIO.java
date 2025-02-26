package de.dlr.shepard.data.timeseries.io;

import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(name = "TimeseriesContainer")
public class TimeseriesContainerIO extends BasicContainerIO {

  public TimeseriesContainerIO(TimeseriesContainer container) {
    super(container);
  }
}
