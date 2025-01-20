package de.dlr.shepard.data.timeseries.io;

import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "TimeseriesContainer")
public class TimeseriesContainerIO extends BasicContainerIO {

  @Schema(readOnly = true, required = true)
  private String database;

  public TimeseriesContainerIO(TimeseriesContainer container) {
    super(container);
    this.database = container.getDatabase();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;
    if (!(o instanceof TimeseriesContainerIO)) return false;
    TimeseriesContainerIO other = (TimeseriesContainerIO) o;
    return (Objects.equals(database, other.database));
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hashCode(database);
    return result;
  }
}
