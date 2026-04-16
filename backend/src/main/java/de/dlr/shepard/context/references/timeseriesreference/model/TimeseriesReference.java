package de.dlr.shepard.context.references.timeseriesreference.model;

import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.common.util.Neo4jLabels;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesTuple;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@NoArgsConstructor
public class TimeseriesReference extends BasicReference {

  private long start;

  private long end;

  @Relationship(type = Neo4jLabels.HAS_PAYLOAD)
  private List<TimeseriesTuple> referencedTimeseriesList = new ArrayList<>();

  @ToString.Exclude
  @Relationship(type = Neo4jLabels.IS_IN_CONTAINER)
  private TimeseriesContainer timeseriesContainer;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public TimeseriesReference(long id) {
    super(id);
  }

  public void addTimeseries(TimeseriesTuple timeseries) {
    this.referencedTimeseriesList.add(timeseries);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(end, start, referencedTimeseriesList);
    result = prime * result + HasId.hashcodeHelper(timeseriesContainer);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (!(obj instanceof TimeseriesReference)) return false;
    TimeseriesReference other = (TimeseriesReference) obj;
    return (
      end == other.end &&
      start == other.start &&
      Objects.equals(referencedTimeseriesList, other.referencedTimeseriesList) &&
      HasId.equalsHelper(timeseriesContainer, other.timeseriesContainer)
    );
  }
}
