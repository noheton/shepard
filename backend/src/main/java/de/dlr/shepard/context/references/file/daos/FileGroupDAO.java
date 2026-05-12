package de.dlr.shepard.context.references.file.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.references.file.entities.FileGroup;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * Lookups for {@link FileGroup} sub-nodes hanging off a
 * {@code FileBundleReference} (FR1a, see {@code aidocs/53 §1.4}).
 *
 * <p>Mirrors the {@code GitReferenceDAO} shape — appId-keyed lookups
 * + parent-bundle-keyed list. {@code GenericDAO} handles
 * {@code createOrUpdate} (and mints the appId via
 * {@code AppIdGenerator}) so we don't repeat that here.
 */
@RequestScoped
public class FileGroupDAO extends GenericDAO<FileGroup> {

  /**
   * @param bundleAppId the parent {@code FileBundleReference}'s appId.
   * @return all groups under that bundle, ordered by {@code index} asc.
   */
  public List<FileGroup> findByBundleAppId(String bundleAppId) {
    String query =
      "MATCH (b:FileBundleReference {appId: $aid})-[:HAS_GROUP]->(g:FileGroup) " +
      "OPTIONAL MATCH (g)-[:has_payload]->(f:ShepardFile) " +
      "WITH g, collect(DISTINCT f) AS gf " +
      "RETURN g, gf, [(g)-[r_g:has_payload]->(f) | [r_g, f]] AS rels " +
      "ORDER BY coalesce(g.index, 0) ASC";
    var queryResult = findByQuery(query, Map.of("aid", bundleAppId));
    return StreamSupport.stream(queryResult.spliterator(), false).toList();
  }

  /**
   * @param appId the FileGroup's own appId.
   * @return the row, or {@code null} when no match.
   */
  public FileGroup findByAppId(String appId) {
    String query =
      "MATCH (g:FileGroup {appId: $appId}) " +
      "OPTIONAL MATCH (g)-[:has_payload]->(f:ShepardFile) " +
      "WITH g, collect(DISTINCT f) AS gf " +
      "RETURN g, gf, [(g)-[r_g:has_payload]->(f) | [r_g, f]] AS rels";
    var iter = findByQuery(query, Map.of("appId", appId));
    var it = iter.iterator();
    return it.hasNext() ? it.next() : null;
  }

  /**
   * @param bundleAppId the parent {@code FileBundleReference}'s appId.
   * @return the highest {@code index} currently assigned to any group
   *   under the bundle, or {@code -1} when no groups exist yet
   *   (so callers can assign {@code max + 1 = 0} for the first group).
   */
  public int findMaxIndexUnderBundle(String bundleAppId) {
    String query =
      "MATCH (:FileBundleReference {appId: $aid})-[:HAS_GROUP]->(g:FileGroup) " +
      "RETURN coalesce(max(g.index), -1) AS maxIndex";
    var result = session.query(query, Map.of("aid", bundleAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) return -1;
    Object v = iter.next().get("maxIndex");
    return v instanceof Number ? ((Number) v).intValue() : -1;
  }

  /**
   * @param groupAppId a FileGroup's appId.
   * @return the parent FileBundleReference's appId, or {@code null}
   *   when the group has no parent bundle (a graph inconsistency).
   */
  public String findBundleAppIdForGroup(String groupAppId) {
    String query =
      "MATCH (b:FileBundleReference)-[:HAS_GROUP]->(g:FileGroup {appId: $aid}) " +
      "RETURN b.appId AS appId LIMIT 1";
    var result = session.query(query, Map.of("aid", groupAppId));
    var iter = result.iterator();
    if (!iter.hasNext()) return null;
    Object v = iter.next().get("appId");
    return v == null ? null : v.toString();
  }

  @Override
  public Class<FileGroup> getEntityType() {
    return FileGroup.class;
  }
}
