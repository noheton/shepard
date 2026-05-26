package de.dlr.shepard.v2.shapes.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * One row from the {@code predicate_vocabulary} Postgres table,
 * returned by {@code GET /v2/shapes/predicates}.
 *
 * <p>Wire shape for the substrate-routing table that maps
 * {@code shepard:} predicate URIs to their authoritative storage
 * substrate. This is the PR-5 enabler: SHACL write paths use this
 * table to route each predicate to the correct store.
 *
 * <p>Substrate values: {@code neo4j} | {@code timescaledb} |
 * {@code postgres} | {@code garage}.
 *
 * <p>Cardinality values: {@code one} | {@code many}.
 *
 * @see <a href="../../../../../../aidocs/semantics/98-shapes-views-and-process-model.md">aidocs/98 §1.3</a>
 */
@Schema(description = "One predicate from the substrate-routing vocabulary table.")
public record PredicateVocabularyEntryIO(

  @Schema(
    required = true,
    description = "Absolute IRI of the predicate, e.g. http://semantics.dlr.de/shepard-upper#status."
  )
  String predicateUri,

  @Schema(
    required = true,
    description = "Authoritative storage substrate: neo4j | timescaledb | postgres | garage."
  )
  String substrate,

  @Schema(
    required = true,
    description = "Cardinality constraint: one (sh:maxCount 1) or many (unbounded)."
  )
  String cardinality,

  @Schema(
    required = true,
    description = "Whether application code may write this predicate (false = read-only / append-only)."
  )
  boolean writable,

  @Schema(
    nullable = true,
    description = "Human-readable description of what the predicate captures."
  )
  String description,

  @Schema(
    nullable = true,
    description = "Source shape or migration file that introduced this predicate (informational)."
  )
  String shapeFile,

  @Schema(
    nullable = true,
    description = "UTC timestamp when this row was inserted (ISO 8601)."
  )
  String addedAt
) {}
