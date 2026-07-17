package de.dlr.shepard.v2.semantic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-SEMIO-NUMERIC-ID — v2-clean response/request shape for semantic annotations.
 *
 * <p>Differences from the shared {@code SemanticAnnotationIO}:
 * <ul>
 *   <li>No {@code id} (numeric Neo4j node id).</li>
 *   <li>No {@code propertyRepositoryId} / {@code valueRepositoryId} (numeric Neo4j ids).
 *       Use {@code propertyVocabularyEntryAppId} / {@code valueVocabularyEntryAppId}
 *       (UUID v7) instead.</li>
 * </ul>
 *
 * <p>The shared {@link de.dlr.shepard.context.semantic.io.SemanticAnnotationIO}
 * remains on the frozen v1 {@code /shepard/api/...} surface unchanged.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "SemanticAnnotationV2")
public class SemanticAnnotationV2IO {

  @Schema(readOnly = true, nullable = true, example = "019506b4-dc55-7c92-b4e1-bf94db37e5b9")
  private String appId;

  @Schema(
    readOnly = true,
    nullable = true,
    deprecated = true,
    description = "DEPRECATED — pre-SEMA-V6 display name. Use propertyName instead."
  )
  private String name;

  @Schema(readOnly = true)
  private String propertyName;

  @Schema(required = true)
  private String propertyIRI;

  @Schema(readOnly = true)
  private String valueName;

  @Schema(required = true)
  private String valueIRI;

  @Schema(
    nullable = true,
    description = "UUID v7 appId of the vocabulary entry for the predicate. Replaces propertyRepositoryId."
  )
  private String propertyVocabularyEntryAppId;

  @Schema(
    nullable = true,
    description = "UUID v7 appId of the vocabulary entry for the value. Replaces valueRepositoryId."
  )
  private String valueVocabularyEntryAppId;

  @Schema(nullable = true)
  private Double numericValue;

  @Schema(nullable = true)
  private String unitIRI;

  public SemanticAnnotationV2IO() {}

  public SemanticAnnotationV2IO(SemanticAnnotation entry) {
    this.appId = entry.getAppId();
    this.name = entry.getName();
    this.propertyName = entry.getPropertyName();
    this.propertyIRI = entry.getPropertyIRI();
    this.valueName = entry.getValueName();
    this.valueIRI = entry.getValueIRI();
    this.propertyVocabularyEntryAppId =
        entry.getPropertyRepository() != null ? entry.getPropertyRepository().getAppId() : null;
    this.valueVocabularyEntryAppId =
        entry.getValueRepository() != null ? entry.getValueRepository().getAppId() : null;
    this.numericValue = entry.getNumericValue();
    this.unitIRI = entry.getUnitIRI();
  }
}
