package de.dlr.shepard.v2.semantic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMA-V6-PRED-UI — read projection for the per-predicate usage statistics
 * surface, returned by
 * {@code GET /v2/semantic/predicates/{predicateIriBase64}/stats}.
 *
 * <p>Three sub-projections are bundled into one round-trip because the
 * frontend predicate detail page renders them together:
 * <ul>
 *   <li>{@code annotationCount} — total {@code :SemanticAnnotation} rows whose
 *       {@code propertyIRI} equals the requested IRI (exact match, no prefix
 *       walk).</li>
 *   <li>{@code topValues} — most-used object values (by frequency, descending),
 *       grouped on {@code coalesce(valueIRI, valueName)} so literal-valued
 *       annotations still aggregate sensibly.</li>
 *   <li>{@code sampleEntities} — a small selection of entities that carry the
 *       predicate, so the UI can hand the user a "see who uses this" jump-off.
 *       Capped server-side at {@code sampleLimit} (default 10).</li>
 * </ul>
 *
 * <p>Design: {@code aidocs/semantics/100} §5 (Predicate detail surface).
 * Backlog: {@code SEMA-V6-PRED-UI}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PredicateStats", description = "Per-predicate usage statistics across all :SemanticAnnotation rows.")
public record PredicateStatsIO(
  @Schema(required = true, description = "The predicate IRI these stats describe (echoes the request).") String predicate,
  @Schema(required = true, description = "Total :SemanticAnnotation rows with propertyIRI=predicate (exact match).") long annotationCount,
  @Schema(required = true, description = "Most-used object values for this predicate, ordered by count DESC.") List<TopValue> topValues,
  @Schema(required = true, description = "Sample of entities annotated with this predicate (capped by sampleLimit).") List<SampleEntity> sampleEntities
) {

  /**
   * One row in {@link #topValues}. Either {@code objectIri} (for IRI-valued
   * annotations) or {@code objectLabel} (for literal-valued annotations) will
   * be populated — usually both, but {@code objectIri} may be null when the
   * annotation only carries a {@code valueName} literal.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "PredicateTopValue")
  public record TopValue(
    @Schema(description = "IRI of the object value, when present.") String objectIri,
    @Schema(description = "Human-readable label (valueName) of the object value, when present.") String objectLabel,
    @Schema(required = true, description = "Number of annotations carrying this object value.") long count
  ) {}

  /**
   * One row in {@link #sampleEntities}. {@code appId} is the entity's UUID v7;
   * {@code type} is its Neo4j label set joined (e.g. {@code "DataObject"},
   * {@code "FileReference"}); {@code name} falls back to the appId when the
   * entity has no human label.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "PredicateSampleEntity")
  public record SampleEntity(
    @Schema(required = true, description = "UUID v7 application-level identifier of the annotated entity.") String appId,
    @Schema(description = "Human-readable name of the entity, when one exists.") String name,
    @Schema(description = "Entity kind (Neo4j label), e.g. DataObject, FileReference, Collection.") String type
  ) {}
}
