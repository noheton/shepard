package de.dlr.shepard.v2.semantic.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * N1e — a single term suggestion returned by the semantic term-search endpoint.
 *
 * <p>Each suggestion comes from a {@code :Resource} node imported by n10s
 * (neosemantics). The {@code uri} field is the canonical IRI that callers
 * store in annotation objects; {@code label} is a human-readable display
 * string resolved from {@code rdfs:label} / {@code skos:prefLabel} (or
 * the URI itself when no label property exists); {@code description}
 * carries {@code rdfs:comment} when present.
 */
@Schema(name = "TermSuggestion", description = "A single ontology term matching the autocomplete query.")
public record TermSuggestionIO(
  @Schema(required = true, description = "Full IRI of the ontology term.") String uri,
  @Schema(required = true, description = "Human-readable label (rdfs:label / skos:prefLabel, or the URI when none exists).") String label,
  @Schema(description = "Optional human-readable description (rdfs:comment).") String description
) {}
