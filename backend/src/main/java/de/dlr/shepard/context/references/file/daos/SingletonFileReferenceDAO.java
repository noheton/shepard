package de.dlr.shepard.context.references.file.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.context.references.file.entities.FileReference;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * DAO for the FR1b singleton {@link FileReference} entity (see
 * {@code aidocs/53 §1.8}).
 *
 * <p>Queries match the {@code :SingletonFileReference} label
 * exclusively, so this DAO never accidentally picks up FR1a
 * {@link de.dlr.shepard.context.references.file.entities.FileBundleReference}
 * rows (which carry the legacy {@code :FileReference} label kept for
 * upstream-API byte-compatibility, per CLAUDE.md §API-version
 * policy). The class-name discriminator from the design
 * ({@code SingletonFileReferenceDAO} vs.
 * {@code FileBundleReferenceDAO}) plus the label discriminator on
 * the entity together keep the two shapes from leaking into each
 * other.
 *
 * <p>The DAO inherits {@link GenericDAO#createOrUpdate} (which mints
 * the appId via {@code AppIdGenerator}); no
 * {@code VersionableEntityDAO} subclass because singleton-versioning
 * is FR1c work — FR1b ships without snapshot hooks.
 */
@RequestScoped
public class SingletonFileReferenceDAO extends GenericDAO<FileReference> {

  /**
   * Look up a singleton FileReference by its appId.
   *
   * @param appId the singleton's appId (UUID v7).
   * @return the row with its attached {@link
   *   de.dlr.shepard.data.file.entities.ShepardFile}, or {@code null}
   *   when no row matches.
   */
  public FileReference findByAppId(String appId) {
    String query =
      "MATCH (r:SingletonFileReference {appId: $appId}) " +
      "OPTIONAL MATCH (r)-[:has_payload]->(f:ShepardFile) " +
      "OPTIONAL MATCH (d:DataObject)-[hr:has_reference]->(r) " +
      "RETURN r, f, d, hr, [(r)-[r_p:has_payload]->(f) | [r_p, f]] AS rels";
    var queryResult = findByQuery(query, Map.of("appId", appId));
    var it = queryResult.iterator();
    return it.hasNext() ? it.next() : null;
  }

  /**
   * List every singleton {@link FileReference} attached to a given
   * DataObject (resolved by its appId). Used by export / journal /
   * RO-Crate code paths that walk every Reference under a DataObject.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @return all singletons under that DataObject (may be empty); never
   *   {@code null}.
   */
  public java.util.List<FileReference> findByDataObjectAppId(String dataObjectAppId) {
    // REF-UNIFIED-TABLE-FR1B (2026-05-29): also return the parent DataObject
    // and the has_reference edge — the BasicReferenceIO ctor dereferences
    // `getDataObject()` and NPEs without it on the
    // `GET /v2/files/by-data-object/{appId}` path. Mirrors the
    // findByAppId shape, which already returns `d` + `hr`.
    String query =
      "MATCH (d:DataObject {appId: $aid})-[hr:has_reference]->(r:SingletonFileReference) " +
      "OPTIONAL MATCH (r)-[:has_payload]->(f:ShepardFile) " +
      "RETURN r, f, d, hr, [(r)-[r_p:has_payload]->(f) | [r_p, f]] AS rels " +
      "ORDER BY r.createdAt ASC";
    var queryResult = findByQuery(query, Map.of("aid", dataObjectAppId));
    return StreamSupport.stream(queryResult.spliterator(), false).toList();
  }

  /**
   * URDF-FILEREF-PICKER-SEARCHABLE — instance-wide projection of every
   * accessible-candidate URDF singleton {@link FileReference}, joined with its
   * parent DataObject and Collection so the caller can render a disambiguating
   * "&lt;name&gt; — &lt;collection&gt;" label across collections.
   *
   * <p>A row qualifies as a URDF candidate when it is a non-deleted
   * {@code :SingletonFileReference} whose name ends {@code .urdf}
   * (case-insensitive) <em>or</em> whose {@code fileKind == "urdf"} (set by the
   * {@code BuiltinFileKindDetector} on upload — the migrated showcase
   * {@code kr210-r2700-urdf} carries {@code fileKind=urdf} but no {@code .urdf}
   * suffix, so the {@code fileKind} arm is load-bearing).
   *
   * <p>No permission filtering happens here — this DAO is a pure read. The
   * caller ({@code AccessibleUrdfService}) narrows the result to the collections
   * the user may read via {@code PermissionsService.filterAllowedDataObjectAppIds}.
   * Orphaned candidates (no parent DataObject / Collection) are dropped, since a
   * reference the user can never reach through a collection is not selectable.
   *
   * @return the candidate projections (may be empty); never {@code null}.
   */
  public List<UrdfCandidate> findAllUrdfCandidates() {
    String query =
      "MATCH (c:Collection)-[:" + Constants.HAS_DATAOBJECT + "]->(d:DataObject)" +
      "-[:" + Constants.HAS_REFERENCE + "]->(r:SingletonFileReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "AND (d.deleted IS NULL OR d.deleted = false) " +
      "AND (c.deleted IS NULL OR c.deleted = false) " +
      "AND (toLower(r.name) ENDS WITH '.urdf' OR r.fileKind = 'urdf') " +
      "RETURN r.appId AS refAppId, r.name AS name, r.fileKind AS fileKind, " +
      "d.appId AS doAppId, c.appId AS collAppId, c.name AS collName " +
      "ORDER BY toLower(r.name) ASC";
    return mapRows(session.query(query, Map.of()));
  }

  /**
   * Pure row → {@link UrdfCandidate} projection. Static + no I/O so it is
   * unit-testable with synthetic maps (the {@code session.query} call above is
   * the only untestable line). Drops rows with no {@code refAppId} or no
   * reachable parent DataObject (not selectable), and de-duplicates by
   * {@code refAppId} — a {@code :SingletonFileReference} is a
   * {@code VersionableEntity}, so this guards against any historical version
   * reachable through {@code has_reference} inflating the list.
   *
   * @param rows the raw Cypher projection rows.
   * @return the de-duplicated candidate list, insertion-order preserved.
   */
  static List<UrdfCandidate> mapRows(Iterable<Map<String, Object>> rows) {
    java.util.LinkedHashMap<String, UrdfCandidate> byAppId = new java.util.LinkedHashMap<>();
    for (var row : rows) {
      Object refAppId = row.get("refAppId");
      Object doAppId = row.get("doAppId");
      // A candidate with no appId or no reachable parent DataObject is not selectable.
      if (!(refAppId instanceof String rid) || rid.isBlank()) continue;
      if (!(doAppId instanceof String did) || did.isBlank()) continue;
      byAppId.putIfAbsent(rid, new UrdfCandidate(
        rid,
        row.get("name") instanceof String n ? n : rid,
        row.get("fileKind") instanceof String fk ? fk : null,
        did,
        row.get("collAppId") instanceof String ca ? ca : null,
        row.get("collName") instanceof String cn ? cn : null
      ));
    }
    return new ArrayList<>(byAppId.values());
  }

  /**
   * Flat projection of a URDF singleton candidate — the reference's appId + name
   * + fileKind, plus the parent DataObject appId (for the permission gate) and
   * the parent Collection appId + name (for the disambiguating picker label).
   */
  public record UrdfCandidate(
    String refAppId,
    String name,
    String fileKind,
    String dataObjectAppId,
    String collectionAppId,
    String collectionName
  ) {}

  @Override
  public Class<FileReference> getEntityType() {
    return FileReference.class;
  }
}
