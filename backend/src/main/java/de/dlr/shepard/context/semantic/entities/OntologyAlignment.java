package de.dlr.shepard.context.semantic.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * TPL3a-lite — Neo4j node that records how a Shepard concept maps onto an
 * upper ontology class (BFO 2020, IAO, PROV-O, IOF Core, EMMO).
 *
 * <p>These rows are seeded by
 * {@code V67__TPL3_upper_ontology_alignment.cypher} (idempotent MERGE) and are
 * read-only at runtime — there is no write endpoint.  The mapping authority is
 * {@code aidocs/semantics/96-upper-ontology-alignment.md}.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@link #shepardConcept} — simple class name (e.g. {@code "Collection"})</li>
 *   <li>{@link #upperOntologyUri} — canonical IRI of the upper-ontology class</li>
 *   <li>{@link #relationshipType} — {@code rdfs:subClassOf} or
 *       {@code owl:equivalentClass}</li>
 *   <li>{@link #confidence} — editorial confidence level: {@code HIGH},
 *       {@code MEDIUM}, or {@code LOW}</li>
 *   <li>{@link #source} — aidocs document that established the mapping</li>
 *   <li>{@link #createdAt} — epoch-millis timestamp set by the migration</li>
 * </ul>
 *
 * <p>The composite node-key constraint
 * ({@code shepardConcept}, {@code upperOntologyUri}) is enforced by
 * {@code V67} — each concept/URI pair is unique on disk.
 *
 * @see de.dlr.shepard.context.semantic.daos.OntologyAlignmentDAO
 * @see de.dlr.shepard.v2.semantic.resources.OntologyAlignmentRest
 */
@NodeEntity
@Data
@NoArgsConstructor
public class OntologyAlignment implements HasId, HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v4, set by the migration on first
   * creation; not minted via the L2a appId seam because these nodes are
   * migration-seeded, not service-created).
   */
  @Property("appId")
  private String appId;

  /**
   * Simple name of the Shepard Java / Neo4j type (e.g. {@code "Collection"},
   * {@code "DataObject"}).  Part of the composite node-key.
   */
  @Property("shepardConcept")
  private String shepardConcept;

  /**
   * Canonical IRI of the upper-ontology class this concept maps onto
   * (e.g. {@code "http://purl.obolibrary.org/obo/IAO_0000100"} for
   * {@code iao:DataSet}).  Part of the composite node-key.
   */
  @Property("upperOntologyUri")
  private String upperOntologyUri;

  /**
   * OWL relationship type: {@code rdfs:subClassOf} (Shepard concept is a
   * subclass) or {@code owl:equivalentClass} (alias / deprecated rename).
   */
  @Property("relationshipType")
  private String relationshipType;

  /**
   * Editorial confidence level for the mapping: {@code HIGH}, {@code MEDIUM},
   * or {@code LOW}.  Set by the migration based on the authoritative review in
   * {@code aidocs/semantics/96-upper-ontology-alignment.md}.
   */
  @Property("confidence")
  private String confidence;

  /**
   * aidocs path of the document that established this mapping
   * (e.g. {@code "aidocs/semantics/96-upper-ontology-alignment.md"}).
   */
  @Property("source")
  private String source;

  /**
   * Epoch-millis timestamp set by the migration when the node was first
   * created.  Null on rows created by Cypher migrations that predate this
   * field.
   */
  @Property("createdAt")
  private Long createdAt;

  @Override
  public String getUniqueId() {
    return id == null ? null : id.toString();
  }
}
