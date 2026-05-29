package de.dlr.shepard.v2.semantic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.context.semantic.entities.Predicate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMA-V6-UI-FOLLOWUP — read projection for a {@link Predicate} node, as
 * returned by {@code GET /v2/semantic/vocabularies/{vocabId}/predicates}.
 *
 * <p>Carries the user-visible subset of fields a predicate browser needs:
 * the stable {@code appId}, the canonical {@code uri} (used as the
 * {@code propertyIRI} on {@code :SemanticAnnotation}), the human-readable
 * {@code label}, and the typed metadata ({@code expectedObjectType},
 * {@code cardinality}, {@code required}).
 *
 * <p>{@code vocabularyAppId} is denormalised onto the row for convenience
 * — callers reaching here from a vocabulary-detail view already know it,
 * but autocomplete callers that drop the row into a flat list benefit
 * from the back-pointer.
 *
 * <p>Design: {@code aidocs/semantics/100} §4 (Vocabularies + predicate model).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Predicate", description = "One predicate (annotation property) declared inside a vocabulary.")
public record PredicateIO(
  @Schema(required = true, description = "UUID v7 application-level identifier.") String appId,
  @Schema(required = true, description = "Canonical property IRI (the value stored as propertyIRI on :SemanticAnnotation).") String uri,
  @Schema(description = "Human-readable label.") String label,
  @Schema(description = "appId of the parent :Vocabulary.") String vocabularyAppId,
  @Schema(description = "Expected object type for this predicate: LITERAL | URI | DATAOBJECT_APPID | CONTAINER_APPID.") String expectedObjectType,
  @Schema(description = "Cardinality: ONE | MANY.") String cardinality,
  @Schema(description = "When true, every entity in the predicate's scope must carry at least one annotation with this predicate.") boolean required
) {

  /** Map from entity. */
  public static PredicateIO from(Predicate p) {
    if (p == null) return null;
    return new PredicateIO(
      p.getAppId(),
      p.getUri(),
      p.getLabel(),
      p.getVocabularyAppId(),
      p.getExpectedObjectType(),
      p.getCardinality(),
      p.isRequired()
    );
  }
}
