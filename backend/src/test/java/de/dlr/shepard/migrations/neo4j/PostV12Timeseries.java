package de.dlr.shepard.migrations.neo4j;

import de.dlr.shepard.common.neo4j.entities.Annotatable;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@EqualsAndHashCode
@NodeEntity
@Data
@RequiredArgsConstructor
class PostV12Timeseries implements Annotatable, HasId {

  @Id
  @GeneratedValue
  private Long id;

  @NotBlank
  private final String measurement;

  @NotBlank
  private final String device;

  @NotBlank
  private final String location;

  @NotBlank
  private final String symbolicName;

  @NotBlank
  private final String field;

  @NotBlank
  private final DataPointValueType valueType;

  @NotBlank
  private final Long timeseriesId;

  @Relationship(type = Constants.IS_IN_CONTAINER)
  @NotBlank
  private final TimeseriesContainer container;

  @Relationship(type = Constants.HAS_ANNOTATION)
  @NotBlank
  private final List<SemanticAnnotation> annotations = new ArrayList<>();

  @Override
  public String getUniqueId() {
    return String.valueOf(id);
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
