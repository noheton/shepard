package de.dlr.shepard.context.references.file.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.references.file.entities.FileReference;
import jakarta.enterprise.context.RequestScoped;
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
    String query =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->(r:SingletonFileReference) " +
      "OPTIONAL MATCH (r)-[:has_payload]->(f:ShepardFile) " +
      "RETURN r, f, [(r)-[r_p:has_payload]->(f) | [r_p, f]] AS rels " +
      "ORDER BY r.createdAt ASC";
    var queryResult = findByQuery(query, Map.of("aid", dataObjectAppId));
    return StreamSupport.stream(queryResult.spliterator(), false).toList();
  }

  @Override
  public Class<FileReference> getEntityType() {
    return FileReference.class;
  }
}
