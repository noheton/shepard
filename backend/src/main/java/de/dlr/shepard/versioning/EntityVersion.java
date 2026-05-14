package de.dlr.shepard.versioning;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Relationship.Direction;

/**
 * ENT1a entity-versioning baseline per {@code aidocs/16} ENT1 + ENT1a.
 *
 * <p>The {@code :EntityVersion} node is attached to a versioned parent
 * (Collection / DataObject in ENT1a; future cardinality extensions
 * cover Bundle / File / Reference) via the {@code HAS_ENTITY_VERSION}
 * edge:
 *
 * <pre>
 *   (c:Collection {appId: "01HF…"})-[:HAS_ENTITY_VERSION]->(v:EntityVersion {versionLabel: "v1", …})
 *   (v)-[:has_permissions]->(p:Permissions)
 * </pre>
 *
 * <p>The relationship name is deliberately distinct from the legacy
 * {@code :Version}-backed {@code has_version} edge ({@code VersionableEntity}
 * / {@code Version} from the pre-ENT1 versioning model) so ENT1a can
 * coexist with the legacy shape without graph rewiring; ENT1c later
 * reconciles ENT1a's {@code :EntityVersion} with KIP1h's
 * {@code Publication.versionNumber} scalar.
 *
 * <p>Versions are <strong>append-only</strong> by convention: new
 * versions are created via
 * {@code POST /v2/{kind}/{appId}/versions}; the only delete path
 * refuses to remove the last remaining version (so an entity always
 * has at least one row).
 *
 * <p>Each {@code :EntityVersion} owns its own {@link Permissions} node
 * (the "one ACL per version" scope call from ENT1a §"Scope decisions").
 * A new version inherits the previous version's ACL on creation; the
 * operator can flip per-version ACL independently afterwards via
 * {@code PATCH /v2/{kind}/{appId}/versions/{label}/permissions}.
 *
 * <p>File-payload Copy-on-Write is <strong>not</strong> wired by
 * ENT1a — that's ENT1b's territory and is gated on FS1a's
 * {@code FileStorage} SPI landing. ENT1a just lays the graph + REST
 * baseline; file payloads still share blobs across versions today.
 *
 * @see EntityVersionDAO
 * @see EntityVersionService
 */
@NodeEntity(label = "EntityVersion")
@Data
@NoArgsConstructor
public class EntityVersion implements HasId, HasAppId {

  /** Neo4j-OGM internal id. */
  @Id
  @GeneratedValue
  private Long id;

  /**
   * Application-level identifier (UUID v7). Minted on save by
   * {@code GenericDAO#createOrUpdate} per L2a's seam. Useful for
   * cross-version references, citations, and future cross-instance
   * federation.
   */
  @Property("appId")
  private String appId;

  /**
   * User-facing label for this version. Defaults to {@code "v" + versionOrdinal}
   * when the user doesn't supply one (e.g. {@code "v1"} / {@code "v2"} / …);
   * users may override with arbitrary strings (e.g. {@code "1.0.0-rc.1"},
   * {@code "release-candidate-march"}) subject to the validation rules
   * in {@link EntityVersionService}.
   *
   * <p>Uniqueness within a parent is enforced by the V35 multi-property
   * constraint on {@code (parentEntityAppId, versionLabel)} plus a
   * service-layer pre-check that surfaces an RFC 7807
   * {@code versions.label.duplicate} 409 before the constraint trips.
   */
  @Property("versionLabel")
  private String versionLabel;

  /**
   * Monotonic-per-parent ordinal — the "true" sort order. The parser
   * computes it independently of the label, so an arbitrary user
   * label like {@code "1.0.0-rc.1"} still gets a clean ordinal (e.g.
   * {@code 5} if it's the 5th version of the parent) and the next
   * version after it gets ordinal {@code 6}.
   *
   * <p>{@code int} (not {@code Integer}) so it never serialises as
   * {@code null} on the wire — every {@code :EntityVersion} has an
   * ordinal at all times.
   */
  @Property("versionOrdinal")
  private int versionOrdinal;

  /**
   * Server-side wall-clock at the moment the version was created, in
   * epoch milliseconds. Backfilled rows (V35) carry the parent
   * entity's {@code createdAt} so the v1 timeline aligns with the
   * parent's existence on disk.
   */
  @Property("createdAt")
  private Long createdAt;

  /**
   * Username (subject identifier) of the version's creator — the
   * caller of {@code POST /v2/{kind}/{appId}/versions}. PROV1's
   * {@code :Activity} row captures the action; this denormalised
   * field is a fast-path for the version-list UI without a fan-out
   * activity join.
   *
   * <p>Backfilled rows carry the literal {@code "<backfill>"} when
   * the parent entity has no {@code createdBy} (legacy upstream
   * rows pre-dating the convention).
   */
  @Property("createdBy")
  private String createdBy;

  /**
   * Parent's entity-kind URL segment — one of {@code "collection"}
   * or {@code "data-object"} in ENT1a (singular; matches the
   * convention in {@code aidocs/16} ENT1a's scope decisions).
   * Stamped at create-time so the version list / get / patch / delete
   * endpoints can validate the kind without walking the inbound
   * {@code HAS_ENTITY_VERSION} edge.
   */
  @Property("parentEntityKind")
  private String parentEntityKind;

  /**
   * AppId of the parent entity. Same denormalisation rationale as
   * {@link #parentEntityKind} — keeps the parent lookup cheap on
   * the read side.
   */
  @Property("parentEntityAppId")
  private String parentEntityAppId;

  /**
   * Optional operator-supplied release note for this version
   * ({@code null} when not supplied at create-time). Free-form
   * string up to 2KB; bounded by REST-layer validation.
   */
  @Property("note")
  private String note;

  /**
   * Per-version ACL. New versions inherit the previous version's ACL
   * by deep-cloning the {@link Permissions} graph at create time;
   * mutations after creation are independent ("one ACL per version").
   */
  @ToString.Exclude
  @Relationship(type = "has_permissions", direction = Direction.OUTGOING)
  private Permissions permissions;

  /** For testing purposes only. */
  public EntityVersion(long id) {
    this.id = id;
  }

  @Override
  public String getUniqueId() {
    return appId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appId);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof EntityVersion other)) return false;
    return Objects.equals(appId, other.appId);
  }
}
