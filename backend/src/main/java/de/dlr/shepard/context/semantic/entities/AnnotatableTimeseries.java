package de.dlr.shepard.context.semantic.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.neo4j.entities.Annotatable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@NoArgsConstructor
public class AnnotatableTimeseries implements HasId, HasAppId, Annotatable {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7) — additive in L2a.
   */
  @Property("appId")
  private String appId;

  private long containerId;

  private int timeseriesId;

  @Relationship(type = Constants.HAS_ANNOTATION)
  private List<SemanticAnnotation> annotations = new ArrayList<>();

  public AnnotatableTimeseries(long containerId, int timeseriesId, List<SemanticAnnotation> annotations) {
    this.containerId = containerId;
    this.timeseriesId = timeseriesId;
    this.annotations = annotations;
  }

  @Override
  public String getUniqueId() {
    return String.valueOf(id);
  }

  @Override
  public void addAnnotation(SemanticAnnotation annotation) {
    annotations.add(annotation);
  }
}
