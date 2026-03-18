package de.dlr.shepard.migrations.neo4j;

import de.dlr.shepard.common.neo4j.entities.Annotatable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@EqualsAndHashCode(callSuper = true)
@NodeEntity
@Data
class PostV12Timeseries extends PreV12Timeseries implements Annotatable {

  public PostV12Timeseries(
    String measurement,
    String device,
    String location,
    String symbolicName,
    String field,
    DataPointValueType valueType,
    long timeseriesId,
    TimeseriesContainer container
  ) {
    super(measurement, device, location, symbolicName, field);
    this.valueType = valueType;
    this.timeseriesId = timeseriesId;
    this.container = container;
  }

  @NotBlank
  private DataPointValueType valueType;

  @NotBlank
  private Long timeseriesId;

  @Relationship(type = Constants.IS_IN_CONTAINER)
  @NotBlank
  private TimeseriesContainer container;

  @Relationship(type = Constants.HAS_ANNOTATION)
  @NotBlank
  private final List<SemanticAnnotation> annotations = new ArrayList<>();

  @Override
  public String getUniqueId() {
    return String.valueOf(timeseriesId);
  }

  @Override
  public List<SemanticAnnotation> getAnnotations() {
    return annotations;
  }

  @Override
  public void addAnnotation(SemanticAnnotation annotation) {
    annotations.add(annotation);
  }
}
