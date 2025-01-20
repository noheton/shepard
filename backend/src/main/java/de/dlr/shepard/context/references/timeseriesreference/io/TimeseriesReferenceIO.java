package de.dlr.shepard.context.references.timeseriesreference.io;

import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.ReferencedTimeseriesNodeEntity;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!super.equals(o)) return false;
    if (this.getClass() != o.getClass()) return false;
    TimeseriesReferenceIO other = (TimeseriesReferenceIO) o;
    return (
      start == other.start &&
      end == other.end &&
      timeseriesContainerId == other.timeseriesContainerId &&
      HasId.areEqualSetsByUniqueId(referencedTimeseriesList, other.referencedTimeseriesList)
    );
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash((Long) start, (Long) end, (Long) timeseriesContainerId);
    result = prime * result + HasId.hashcodeHelper(referencedTimeseriesList);
    return result;
  }
}
