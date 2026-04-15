package de.dlr.shepard.data.timeseries.model;

import de.dlr.shepard.common.neo4j.entities.Annotatable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Relationship.Direction;

@NodeEntity
@Data
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
public class Timeseries implements HasId, Annotatable {

  @Id
  @GeneratedValue
  @EqualsAndHashCode.Exclude
  private Long id;

  @NotBlank
  @NonNull
  @Relationship(type = Constants.IS_IN_CONTAINER, direction = Direction.OUTGOING)
  private TimeseriesContainer container;

  @NotBlank
  @NonNull
  @Relationship(type = Constants.HAS_TIMESERIES_TUPLE, direction = Direction.OUTGOING)
  private TimeseriesTuple timeseriesTuple;

  @NotBlank
  @Enumerated(EnumType.STRING)
  private final DataPointValueType valueType;

  @NotBlank
  private final Long timeseriesId;

  @NotBlank
  @Relationship(type = Constants.HAS_ANNOTATION, direction = Direction.OUTGOING)
  private final List<SemanticAnnotation> annotations = new ArrayList<>();

  @Override
  public String getUniqueId() {
    return "timeseries-" + getTimeseriesId();
  }

  @Override
  public void addAnnotation(SemanticAnnotation annotation) {
    annotations.add(annotation);
  }
}
