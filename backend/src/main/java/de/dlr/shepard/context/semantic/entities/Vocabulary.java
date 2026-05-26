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
 * SEMA-V6-002 — a controlled vocabulary (namespace) for semantic predicates.
 *
 * <p>Each {@code :Vocabulary} node describes one namespace that groups
 * {@link Predicate} nodes (e.g. Dublin Core, PROV-O, schema.org). The
 * {@link #uri} is the canonical IRI prefix / namespace URI and serves as
 * the stable natural key (unique constraint V72).
 *
 * <p>The {@link #enabled} flag allows an operator to runtime-disable a
 * vocabulary (removes it from autocomplete + validation) without deleting
 * its data. Default: {@code true}.
 *
 * <p>Bootstrap seed: ten vocabularies are seeded in
 * {@code V72__Add_Vocabulary_and_Predicate.cypher}.
 *
 * @see Predicate
 * @see de.dlr.shepard.context.semantic.daos.VocabularyDAO
 */
@NodeEntity
@Data
@NoArgsConstructor
public class Vocabulary implements HasId, HasAppId {

  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on first save by
   * {@code GenericDAO#createOrUpdate}; seeded via {@code randomUUID()} in V72.
   */
  @Property("appId")
  private String appId;

  /**
   * Canonical namespace URI / IRI prefix (e.g.
   * {@code "http://purl.org/dc/terms/"}).  Unique constraint V72.
   */
  @Property("uri")
  private String uri;

  /**
   * Human-readable label (e.g. {@code "Dublin Core Terms"}).
   */
  @Property("label")
  private String label;

  /**
   * Short namespace prefix for display (e.g. {@code "dcterms"}).
   * Not guaranteed unique — operators may alias with a personal prefix.
   */
  @Property("prefix")
  private String prefix;

  /**
   * Optional free-text description of the vocabulary's scope.
   */
  @Property("description")
  private String description;

  /**
   * When {@code false}, this vocabulary is hidden from autocomplete and
   * predicate lookup. Predicates remain in the graph (data integrity
   * preserved). Default: {@code true}.
   */
  @Property("enabled")
  private boolean enabled = true;

  // ─── SEMA-V6-014 fields ──────────────────────────────────────────────────

  /**
   * SEMA-V6-014 — vocabulary kind.
   * <ul>
   *   <li>{@code null} / absent — system-seeded or operator-uploaded vocabulary (the pre-existing shape).</li>
   *   <li>{@code "PERSONAL"} — user-minted personal vocabulary
   *       ({@code urn:shepard:personal:<userAppId>:<name>}).</li>
   * </ul>
   * Stored as a plain string so future kinds can be added without migration.
   */
  @Property("type")
  private String type;

  /**
   * SEMA-V6-014 — {@code appId} of the {@link de.dlr.shepard.auth.users.entities.User}
   * who owns this vocabulary. Only set when {@link #type} is {@code "PERSONAL"};
   * {@code null} for system and operator vocabularies. Enables
   * owner-scoped listing without a graph relationship.
   */
  @Property("ownedByUserAppId")
  private String ownedByUserAppId;

  /**
   * Epoch-millis when this row was first created. Set once on V72 seed;
   * for operator-added vocabularies, set on first save.
   */
  @Property("createdAt")
  private Long createdAt;

  @Override
  public String getUniqueId() {
    return id == null ? null : id.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, uri, label, prefix, description, enabled, createdAt, appId, type, ownedByUserAppId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Vocabulary other)) return false;
    return Objects.equals(id, other.id) &&
      Objects.equals(uri, other.uri) &&
      Objects.equals(label, other.label) &&
      Objects.equals(prefix, other.prefix) &&
      Objects.equals(description, other.description) &&
      enabled == other.enabled &&
      Objects.equals(createdAt, other.createdAt) &&
      Objects.equals(appId, other.appId) &&
      Objects.equals(type, other.type) &&
      Objects.equals(ownedByUserAppId, other.ownedByUserAppId);
  }
}
