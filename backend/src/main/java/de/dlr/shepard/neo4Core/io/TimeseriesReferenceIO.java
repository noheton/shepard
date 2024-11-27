package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.influxDB.Timeseries;
import de.dlr.shepard.neo4Core.entities.TimeseriesReference;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "TimeseriesReference")
public class TimeseriesReferenceIO extends BasicReferenceIO {

  @NotNull
  @Schema(required = true)
  private long start;

  @NotNull
  @Schema(required = true)
  private long end;

  @NotEmpty
  @Schema(required = true)
  private Timeseries[] timeseries;

  @NotNull
  @Schema(required = true)
  private long timeseriesContainerId;

  public TimeseriesReferenceIO(TimeseriesReference ref) {
    super(ref);
    this.start = ref.getStart();
    this.end = ref.getEnd();
    this.timeseries = ref.getTimeseries().toArray(Timeseries[]::new);
    this.timeseriesContainerId = ref.getTimeseriesContainer() != null ? ref.getTimeseriesContainer().getId() : -1;
  }
}
