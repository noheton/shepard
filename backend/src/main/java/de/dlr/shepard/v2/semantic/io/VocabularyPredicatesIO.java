package de.dlr.shepard.v2.semantic.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SEMA-V6-UI-FOLLOWUP — response body of
 * {@code GET /v2/semantic/vocabularies/{vocabId}/predicates}.
 *
 * <p>The {@code vocabularyAppId} field echoes the path parameter so a caller
 * that drops the response shape into a flat list can correlate without
 * keeping the request path around. {@code predicates} is a possibly-empty
 * list of {@link PredicateIO} rows ordered by {@code label} ASC.
 *
 * <p>Design: {@code aidocs/semantics/100} §4.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "VocabularyPredicates", description = "List of predicates declared by one vocabulary.")
public record VocabularyPredicatesIO(
  @Schema(required = true, description = "appId of the parent vocabulary.") String vocabularyAppId,
  @Schema(required = true, description = "Predicates declared by this vocabulary, ordered by label ASC.") List<PredicateIO> predicates
) {}
