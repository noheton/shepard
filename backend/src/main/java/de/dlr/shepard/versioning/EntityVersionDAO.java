package de.dlr.shepard.versioning;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ENT1a DAO for {@link EntityVersion} rows per {@code aidocs/16} ENT1a.
 *
 * <p>Reads:
 * <ul>
 *   <li>{@link #findByParentAndLabel(String, String)} — single lookup
 *       by {@code (parentAppId, label)}; used by GET / PATCH / DELETE.</li>
 *   <li>{@link #findAllByParent(String)} — every version of an entity,
 *       newest-first by {@code versionOrdinal DESC}. Used by GET list.</li>
 *   <li>{@link #findLatestByParent(String)} — head of the parent's
 *       version list (highest ordinal).</li>
 *   <li>{@link #findMaxOrdinalByParent(String)} — used by
 *       {@code EntityVersionService} to compute the next ordinal
 *       before minting a new row.</li>
 *   <li>{@link #existsLabelForParent(String, String)} — collision
 *       check on a user-supplied label so the REST layer can surface
 *       RFC 7807 {@code versions.label.duplicate} 409 before the V35
 *       multi-property constraint trips at the DB layer.</li>
 * </ul>
 *
 * <p>Writes:
 * <ul>
 *   <li>{@link #save(EntityVersion)} — persist a fresh or existing row
 *       via {@code GenericDAO#createOrUpdate} (mints {@code appId} on
 *       first save).</li>
 *   <li>{@link #attachToParent(EntityVersion, String)} — attach the
 *       {@code (parent)-[:HAS_ENTITY_VERSION]->(version)} edge after
 *       the row is saved. Idempotent via MERGE.</li>
 *   <li>{@link #delete(EntityVersion)} — delete the version + its
 *       :Permissions node + clear the HAS_ENTITY_VERSION edge in one
 *       round-trip.</li>
 * </ul>
 *
 * <p>The relationship name {@code HAS_ENTITY_VERSION} is deliberately
 * distinct from the legacy {@code has_version} edge (used by the
 * pre-ENT1 {@code VersionableEntity} / {@code :Version} pair) so
 * ENT1a coexists with the legacy shape without touching it.
 */
@RequestScoped
public class EntityVersionDAO extends GenericDAO<EntityVersion> {

  /** Edge type connecting a versioned parent to its {@link EntityVersion} rows. */
  public static final String HAS_ENTITY_VERSION = "HAS_ENTITY_VERSION";

  @Override
  public Class<EntityVersion> getEntityType() {
    return EntityVersion.class;
  }

  /**
   * Find an EntityVersion by its {@code (parentEntityAppId, versionLabel)}
   * pair. Cheap path — the V35 multi-property constraint underwrites
   * an index on this pair.
   */
  public Optional<EntityVersion> findByParentAndLabel(String parentAppId, String label) {
    if (parentAppId == null || parentAppId.isBlank()) return Optional.empty();
    if (label == null || label.isBlank()) return Optional.empty();
    String query =
      "MATCH (v:EntityVersion {parentEntityAppId: $parentAppId, versionLabel: $label}) " + "RETURN v LIMIT 1";
    Iterable<EntityVersion> result = findByQuery(query, Map.of("parentAppId", parentAppId, "label", label));
    var iter = result.iterator();
    if (!iter.hasNext()) return Optional.empty();
    return Optional.of(iter.next());
  }

  /**
   * Find every EntityVersion attached to a given parent, ordered by
   * {@code versionOrdinal DESC} (newest-first — the "head" of the
   * parent's version list per the ENT1a convention).
   */
  public List<EntityVersion> findAllByParent(String parentAppId) {
    if (parentAppId == null || parentAppId.isBlank()) return List.of();
    String query =
      "MATCH (v:EntityVersion {parentEntityAppId: $parentAppId}) " + "RETURN v ORDER BY v.versionOrdinal DESC";
    Iterable<EntityVersion> result = findByQuery(query, Map.of("parentAppId", parentAppId));
    List<EntityVersion> out = new ArrayList<>();
    result.forEach(out::add);
    return out;
  }

  /**
   * Find the most-recent EntityVersion (highest ordinal) attached to a
   * parent. Empty when the parent has no versions yet (only happens
   * pre-V35 backfill / for entities created without a version, which
   * shouldn't occur post-ENT1a).
   */
  public Optional<EntityVersion> findLatestByParent(String parentAppId) {
    if (parentAppId == null || parentAppId.isBlank()) return Optional.empty();
    String query =
      "MATCH (v:EntityVersion {parentEntityAppId: $parentAppId}) " + "RETURN v ORDER BY v.versionOrdinal DESC LIMIT 1";
    Iterable<EntityVersion> result = findByQuery(query, Map.of("parentAppId", parentAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) return Optional.empty();
    return Optional.of(iter.next());
  }

  /**
   * Find the maximum {@code versionOrdinal} attached to a parent, or
   * {@code 0} when the parent has no versions yet. Used by
   * {@code EntityVersionService} to compute the next ordinal before
   * minting a new row.
   */
  public int findMaxOrdinalByParent(String parentAppId) {
    if (parentAppId == null || parentAppId.isBlank()) return 0;
    if (session == null) return 0;
    String query =
      "MATCH (v:EntityVersion {parentEntityAppId: $parentAppId}) " +
      "RETURN coalesce(max(v.versionOrdinal), 0) AS maxOrdinal";
    var result = session.query(query, Map.of("parentAppId", parentAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) return 0;
    Object raw = iter.next().get("maxOrdinal");
    if (raw instanceof Number n) return n.intValue();
    return 0;
  }

  /**
   * Collision check for user-supplied labels — returns {@code true}
   * when an {@code :EntityVersion} with the given label already exists
   * on the parent. The REST layer uses this to surface RFC 7807
   * {@code versions.label.duplicate} 409 before the V35 constraint
   * trips at the DB layer.
   */
  public boolean existsLabelForParent(String parentAppId, String label) {
    if (parentAppId == null || parentAppId.isBlank()) return false;
    if (label == null || label.isBlank()) return false;
    if (session == null) return false;
    String query =
      "MATCH (v:EntityVersion {parentEntityAppId: $parentAppId, versionLabel: $label}) " + "RETURN count(v) AS c";
    var result = session.query(query, Map.of("parentAppId", parentAppId, "label", label));
    var iter = result.iterator();
    if (!iter.hasNext()) return false;
    Object raw = iter.next().get("c");
    if (raw instanceof Number n) return n.longValue() > 0L;
    return false;
  }

  /**
   * Persist an {@link EntityVersion} via {@code GenericDAO#createOrUpdate}.
   * Mints {@code appId} on first save (per the L2a seam); the
   * per-version {@code :Permissions} child is saved transitively at
   * OGM depth 1 (the entity carries a relationship to it).
   */
  public EntityVersion save(EntityVersion v) {
    if (v == null) throw new IllegalArgumentException("EntityVersion must not be null");
    return createOrUpdate(v);
  }

  /**
   * Attach the {@code (parent)-[:HAS_ENTITY_VERSION]->(version)} edge.
   * Idempotent via MERGE — re-saving the same version-parent pair
   * won't duplicate the edge.
   *
   * <p>The parent is matched by appId regardless of label (Collection
   * / DataObject) because {@link EntityVersion#getParentEntityKind()}
   * is already stamped on the row and the V35 constraint enforces
   * uniqueness across all labels.
   *
   * @param version      freshly-saved EntityVersion (must have an
   *                     appId)
   * @param parentAppId  the parent entity's appId
   */
  public void attachToParent(EntityVersion version, String parentAppId) {
    if (version == null) throw new IllegalArgumentException("version must not be null");
    if (version.getAppId() == null) {
      throw new IllegalArgumentException("version must be saved (appId is null)");
    }
    if (parentAppId == null || parentAppId.isBlank()) {
      throw new IllegalArgumentException("parentAppId must not be null/blank");
    }
    String query =
      "MATCH (parent {appId: $parentAppId}), (v:EntityVersion {appId: $versionAppId}) " +
      "MERGE (parent)-[:" +
      HAS_ENTITY_VERSION +
      "]->(v)";
    runQuery(query, Map.of("parentAppId", parentAppId, "versionAppId", version.getAppId()));
  }

  /**
   * Delete an EntityVersion + its {@code :Permissions} node + every
   * inbound {@code HAS_ENTITY_VERSION} edge in a single Cypher
   * round-trip. The version's permission edges (owner / reader /
   * writer / manager / groups) are removed first via {@code DETACH
   * DELETE} on the {@code :Permissions} node.
   *
   * <p>"Cannot-delete-only" semantics are enforced in
   * {@link EntityVersionService} — this DAO method is unconditional.
   */
  public void delete(EntityVersion v) {
    if (v == null || v.getAppId() == null) return;
    String query =
      "MATCH (v:EntityVersion {appId: $versionAppId}) " +
      "OPTIONAL MATCH (v)-[:has_permissions]->(vp:Permissions) " +
      "DETACH DELETE vp, v";
    runQuery(query, Map.of("versionAppId", v.getAppId()));
  }

  /**
   * Convenience helper — wire a freshly-created {@link Permissions}
   * row as the version's ACL. Used by
   * {@link EntityVersionService#createVersion} when cloning a previous
   * version's permissions.
   */
  public void setPermissions(EntityVersion v, Permissions permissions) {
    if (v == null || v.getAppId() == null) {
      throw new IllegalArgumentException("version must be saved");
    }
    if (permissions == null || permissions.getAppId() == null) {
      throw new IllegalArgumentException("permissions must be saved");
    }
    String query =
      "MATCH (v:EntityVersion {appId: $versionAppId}), (p:Permissions {appId: $permsAppId}) " +
      "MERGE (v)-[:has_permissions]->(p)";
    runQuery(query, Map.of("versionAppId", v.getAppId(), "permsAppId", permissions.getAppId()));
  }
}
