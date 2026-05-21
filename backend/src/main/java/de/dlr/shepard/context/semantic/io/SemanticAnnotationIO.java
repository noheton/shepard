package de.dlr.shepard.context.semantic.io;

import de.dlr.shepard.common.neo4j.entities.Named;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "SemanticAnnotation")
public class SemanticAnnotationIO implements HasId, Named {

  @Schema(readOnly = true, required = true)
  private Long id;

  @Deprecated
  @Schema(readOnly = true, required = true)
  private String name;

  @Schema(readOnly = true, required = true)
  private String propertyName;

  @NotBlank
  @Schema(required = true)
  private String propertyIRI;

  @Schema(readOnly = true, required = true)
  private String valueName;

  @NotBlank
  @Schema(required = true)
  private String valueIRI;

  @NotNull
  @Schema(required = true)
  private long propertyRepositoryId;

  @NotNull
  @Schema(required = true)
  private long valueRepositoryId;

  /**
   * QA-1 — optional numeric rendering of the annotation value. Nullable.
   */
  @Schema(nullable = true)
  private Double numericValue;

  /**
   * QA-1 — IRI of the unit of measurement (e.g. a QUDT unit IRI). Nullable.
   */
  @Schema(nullable = true)
  private String unitIRI;

  public SemanticAnnotationIO(SemanticAnnotation ref) {
    this.id = ref.getId();
    this.name = ref.getName();
    this.propertyIRI = ref.getPropertyIRI();
    this.valueIRI = ref.getValueIRI();
    this.propertyRepositoryId = ref.getPropertyRepository() != null ? ref.getPropertyRepository().getId() : -1;
    this.valueRepositoryId = ref.getValueRepository() != null ? ref.getValueRepository().getId() : -1;
    this.propertyName = ref.getPropertyName();
    this.valueName = ref.getValueName();
    this.numericValue = ref.getNumericValue();
    this.unitIRI = ref.getUnitIRI();
  }

  @Override
  public String getUniqueId() {
    return id.toString();
  }
}
