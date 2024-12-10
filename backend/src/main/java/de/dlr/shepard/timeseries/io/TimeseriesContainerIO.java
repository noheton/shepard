package de.dlr.shepard.timeseries.io;

import de.dlr.shepard.neo4Core.io.BasicContainerIO;
import de.dlr.shepard.timeseries.model.TimeseriesContainer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "TimeseriesContainer")
public class TimeseriesContainerIO extends BasicContainerIO {

  @Schema(readOnly = true, required = true)
  private String database;

  public TimeseriesContainerIO(TimeseriesContainer container) {
    super(container);
    this.database = container.getDatabase();
  }
}
