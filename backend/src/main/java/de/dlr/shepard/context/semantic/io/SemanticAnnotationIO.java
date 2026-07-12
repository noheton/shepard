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

  @Schema(readOnly = true)
  private String appId;

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

  /**
   * UUID v7 of the vocabulary entry that defines the predicate.
   * Replaces the numeric {@link #propertyRepositoryId}; null when no vocabulary is linked.
   */
  @Schema(nullable = true, description = "appId of the vocabulary entry for the predicate. Replaces propertyRepositoryId.")
  private String propertyVocabularyEntryAppId;

  /**
   * UUID v7 of the vocabulary entry that defines the object value.
   * Replaces the numeric {@link #valueRepositoryId}; null when no vocabulary is linked.
   */
  @Schema(nullable = true, description = "appId of the vocabulary entry for the value. Replaces valueRepositoryId.")
  private String valueVocabularyEntryAppId;

  @Deprecated
  @NotNull
  @Schema(deprecated = true, description = "Deprecated — use propertyVocabularyEntryAppId instead.")
  private long propertyRepositoryId;

  @Deprecated
  @NotNull
  @Schema(deprecated = true, description = "Deprecated — use valueVocabularyEntryAppId instead.")
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
    this.appId = ref.getAppId();
    this.name = ref.getName();
    this.propertyIRI = ref.getPropertyIRI();
    this.valueIRI = ref.getValueIRI();
    this.propertyRepositoryId = ref.getPropertyRepository() != null ? ref.getPropertyRepository().getId() : -1;
    this.valueRepositoryId = ref.getValueRepository() != null ? ref.getValueRepository().getId() : -1;
    this.propertyVocabularyEntryAppId = ref.getPropertyRepository() != null ? ref.getPropertyRepository().getAppId() : null;
    this.valueVocabularyEntryAppId = ref.getValueRepository() != null ? ref.getValueRepository().getAppId() : null;
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
