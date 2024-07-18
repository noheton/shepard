package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.SemanticAnnotation;
import de.dlr.shepard.util.HasId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "SemanticAnnotation")
public class SemanticAnnotationIO implements HasId {

  @Schema(readOnly = true)
  private Long id;

  @Schema(readOnly = true)
  private String name;

  @NotBlank
  private String propertyIRI;

  @NotBlank
  private String valueIRI;

  @NotNull
  private long propertyRepositoryId;

  @NotNull
  private long valueRepositoryId;

  public SemanticAnnotationIO(SemanticAnnotation ref) {
    this.id = ref.getId();
    this.name = ref.getName();
    this.propertyIRI = ref.getPropertyIRI();
    this.valueIRI = ref.getValueIRI();
    this.propertyRepositoryId = ref.getPropertyRepository() != null ? ref.getPropertyRepository().getId() : -1;
    this.valueRepositoryId = ref.getValueRepository() != null ? ref.getValueRepository().getId() : -1;
  }

  @Override
  public String getUniqueId() {
    return id.toString();
  }
}
