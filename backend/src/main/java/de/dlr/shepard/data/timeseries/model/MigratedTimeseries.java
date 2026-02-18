package de.dlr.shepard.data.timeseries.model;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class MigratedTimeseries implements HasId {

  @Id
  @GeneratedValue
  private Long id;

  @NotBlank
  @NonNull
  private String measurement;

  @NotBlank
  @NonNull
  private String device;

  @NotBlank
  @NonNull
  private String location;

  @NotBlank
  @NonNull
  private String symbolicName;

  @NotBlank
  @NonNull
  private String field;

  @NotBlank
  @NonNull
  private DataPointValueType valueType;

  @NotBlank
  @NonNull
  private Long timeseriesId;

  @Relationship(type = Constants.IS_IN_CONTAINER)
  @NotBlank
  @NonNull
  private TimeseriesContainer container;

  @Override
  public String getUniqueId() {
    return String.valueOf(timeseriesId);
  }
}
