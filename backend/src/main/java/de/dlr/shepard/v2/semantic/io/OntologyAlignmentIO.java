package de.dlr.shepard.v2.semantic.io;

import de.dlr.shepard.context.semantic.entities.OntologyAlignment;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TPL3a-lite — response shape for a single
 * {@link de.dlr.shepard.context.semantic.entities.OntologyAlignment} row.
 *
 * <p>Returned by {@code GET /v2/semantic/ontology/alignment}.  Each item
 * records how a core Shepard concept maps onto an upper-ontology class
 * (BFO 2020, IAO, PROV-O, IOF Core).  The mapping authority is
 * {@code aidocs/semantics/96-upper-ontology-alignment.md}.
 */
@Schema(
  name = "OntologyAlignment",
  description =
    "A single entry in the Shepard upper-ontology alignment registry. " +
    "Records the mapping from a Shepard concept to an upper-ontology class IRI, " +
    "the OWL relationship type, an editorial confidence level, and the aidocs " +
    "document that established the mapping."
)
public record OntologyAlignmentIO(

  @Schema(required = true, description = "Application-level UUID of this alignment row.")
  String appId,

  @Schema(
    required = true,
    description =
      "Simple name of the Shepard Java / Neo4j type, e.g. \"Collection\", \"DataObject\"."
  )
  String shepardConcept,

  @Schema(
    required = true,
    description =
      "Canonical IRI of the upper-ontology class, " +
      "e.g. \"http://purl.obolibrary.org/obo/IAO_0000100\" for iao:DataSet."
  )
  String upperOntologyUri,

  @Schema(
    required = true,
    description = "OWL relationship type: \"rdfs:subClassOf\" or \"owl:equivalentClass\"."
  )
  String relationshipType,

  @Schema(
    required = true,
    description = "Editorial confidence level for the mapping: HIGH, MEDIUM, or LOW."
  )
  String confidence,

  @Schema(
    required = true,
    description =
      "aidocs path of the document that established this mapping, " +
      "e.g. \"aidocs/semantics/96-upper-ontology-alignment.md\"."
  )
  String source,

  @Schema(description = "ISO 8601 UTC timestamp when the row was first created by the migration. Null for rows seeded before this field was added.")
  String createdAt

) {

  /**
   * Map an {@link OntologyAlignment} entity to its wire representation.
   */
  public static OntologyAlignmentIO from(OntologyAlignment entity) {
    return new OntologyAlignmentIO(
      entity.getAppId(),
      entity.getShepardConcept(),
      entity.getUpperOntologyUri(),
      entity.getRelationshipType(),
      entity.getConfidence(),
      entity.getSource(),
      toIso(entity.getCreatedAt())
    );
  }

  private static String toIso(Long ms) {
    if (ms == null) return null;
    return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms));
  }
}
