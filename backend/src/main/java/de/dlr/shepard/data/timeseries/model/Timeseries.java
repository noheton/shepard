package de.dlr.shepard.data.timeseries.model;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Relationship.Direction;

@NodeEntity
@Data
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
public class Timeseries implements HasId {

  @Id
  @GeneratedValue
  @EqualsAndHashCode.Exclude
  private Long id;

  @NotBlank
  @Relationship(type = Constants.IS_IN_CONTAINER, direction = Direction.OUTGOING)
  private final TimeseriesContainer container;

  @NotBlank
  @Relationship(type = Constants.HAS_TIMESERIES_TUPLE, direction = Direction.OUTGOING)
  private final TimeseriesTuple timeseriesTuple;

  @NotBlank
  @Enumerated(EnumType.STRING)
  private final DataPointValueType valueType;

  @NotBlank
  private final Long timeseriesId;

  @Override
  public String getUniqueId() {
    return "timeseries-" + getTimeseriesId();
  }
}
