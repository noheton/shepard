package de.dlr.shepard.context.semantic.entities;

import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

/**
 * SEMA-V6-002 — a predicate (property / relationship type) within a
 * {@link Vocabulary}.
 *
 * <p>Each {@code :Predicate} node describes one annotation property that
 * can appear on the left-hand side of a {@link SemanticAnnotation} (i.e.
 * it maps to {@link SemanticAnnotation#getPropertyIRI()}). The
 * {@link #uri} is the canonical property IRI (e.g.
 * {@code "http://purl.org/dc/terms/creator"}) and serves as the stable
 * natural key (unique constraint V72).
 *
 * <p>Enums {@link ExpectedObjectType} and {@link Cardinality} are stored
 * as their {@code name()} strings so schema evolution is safe even if
 * Neo4j OGM's native enum mapping changes in a future version.
 *
 * @see Vocabulary
 * @see de.dlr.shepard.context.semantic.daos.PredicateDAO
 */
@NodeEntity
@Data
@NoArgsConstructor
public class Predicate implements HasId, HasAppId {

  /**
   * SEMA-V6-002 — the kind of object this predicate accepts.
   * Stored as {@code name()} string in Neo4j.
   */
  public enum ExpectedObjectType {
    /** A plain string literal (e.g. a title, a status value). */
    LITERAL,
    /** A URI / IRI (e.g. a class reference from a controlled vocabulary). */
    URI,
    /** An {@code appId} of a {@code :DataObject} node in this instance. */
    DATAOBJECT_APPID,
    /** An {@code appId} of a container node (file, timeseries, structured-data). */
    CONTAINER_APPID
  }

  /**
   * SEMA-V6-002 — how many times this predicate may appear on one entity.
   * Stored as {@code name()} string in Neo4j.
   */
  public enum Cardinality {
    /** At most one annotation of this predicate per entity. */
    ONE,
    /** Multiple annotations of this predicate are allowed on one entity. */
    MANY
  }

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on first save.
   */
  @Property("appId")
  private String appId;

  /**
   * Canonical property IRI (e.g. {@code "http://purl.org/dc/terms/creator"}).
   * Unique constraint V72.
   */
  @Property("uri")
  private String uri;

  /**
   * Human-readable label (e.g. {@code "Creator"}).
   */
  @Property("label")
  private String label;

  /**
   * {@code appId} of the parent {@link Vocabulary} node.
   * Denormalised for fast lookup — the graph edge {@code (:Predicate)-[:IN_VOCABULARY]->(:Vocabulary)}
   * is the authoritative relation (shipped in a later migration); this field
   * is the read-optimised cache copy.
   */
  @Property("vocabularyAppId")
  private String vocabularyAppId;

  /**
   * Kind of object this predicate expects. Stored as {@link ExpectedObjectType#name()}.
   */
  @Property("expectedObjectType")
  private String expectedObjectType;

  /**
   * How many times this predicate may appear on one entity.
   * Stored as {@link Cardinality#name()}.
   */
  @Property("cardinality")
  private String cardinality;

  /**
   * When {@code true}, every entity in the annotation's subject scope
   * <em>must</em> carry at least one annotation with this predicate.
   * Used by {@code DataQualityRequirement} evaluation (TPL10) as the semantic
   * backing for {@code ANNOTATION_REQUIRED} checks.
   */
  @Property("required")
  private boolean required;

  @Override
  public String getUniqueId() {
    return id == null ? null : id.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, uri, label, vocabularyAppId, expectedObjectType, cardinality, required, appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Predicate other)) return false;
    return Objects.equals(id, other.id) &&
      Objects.equals(uri, other.uri) &&
      Objects.equals(label, other.label) &&
      Objects.equals(vocabularyAppId, other.vocabularyAppId) &&
      Objects.equals(expectedObjectType, other.expectedObjectType) &&
      Objects.equals(cardinality, other.cardinality) &&
      required == other.required &&
      Objects.equals(appId, other.appId);
  }
}
