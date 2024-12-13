package de.dlr.shepard.timeseriesreference.io;

import de.dlr.shepard.neo4Core.io.BasicReferenceIO;
import de.dlr.shepard.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.timeseriesreference.model.TimeseriesReference;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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
  private List<ReferencedTimeseriesNodeEntity> referencedTimeseriesList;

  @NotNull
  @Schema(required = true)
  private long timeseriesContainerId;

  public TimeseriesReferenceIO(TimeseriesReference ref) {
    super(ref);
    this.start = ref.getStart();
    this.end = ref.getEnd();
    this.referencedTimeseriesList = ref.getReferencedTimeseriesList();
    this.timeseriesContainerId = ref.getTimeseriesContainer() != null ? ref.getTimeseriesContainer().getId() : -1;
  }
}
