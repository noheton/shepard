package de.dlr.shepard.context.references.file.daos;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.references.file.daos.SingletonFileReferenceDAO.NotebookProjection;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * DAO for {@link FileBundleReference} (FR1a, formerly known as
 * {@code FileReferenceDAO} per {@code aidocs/53 §1.7}).
 *
 * <p>The DAO continues to query by the legacy {@code :FileReference}
 * label so the upstream-frozen REST surface and the existing
 * V11/V12 unique-constraint family keep working unchanged
 * (CLAUDE.md API-version policy). Newly persisted bundles carry both
 * labels via {@link FileBundleReference}'s {@code @Labels} field; the
 * V21 migration backfills the same shape for pre-FR1a rows.
 */
@RequestScoped
public class FileBundleReferenceDAO extends VersionableEntityDAO<FileBundleReference> {

  public List<FileBundleReference> findByDataObjectNeo4jId(long dataObjectId) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // Public method signature stays long for caller-compat until L2d.
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d.appId=$dataObjectAppId ".formatted(
          CypherQueryHelper.getObjectPart("r", "FileReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");

    var queryResult = findByQuery(query, Map.of("dataObjectAppId", resolveAppIdOrEmpty(dataObjectId)));

    List<FileBundleReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getId().equals(dataObjectId))
      .toList();

    return result;
  }

  public List<FileBundleReference> findByDataObjectShepardId(long dataObjectShepardId) {
    String query =
      String.format(
        "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d." + Constants.SHEPARD_ID + "=%d ",
        CypherQueryHelper.getObjectPart("r", "FileReference", false),
        dataObjectShepardId
      ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Collections.emptyMap());

    List<FileBundleReference> result = StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getShepardId().equals(dataObjectShepardId))
      .toList();

    return result;
  }

  /**
   * Look up a bundle by its appId. The query matches against the
   * legacy {@code :FileReference} label (which every bundle still
   * carries — see class-level Javadoc).
   *
   * @param appId the bundle's appId.
   * @return the row, or {@code null} when no match.
   */
  public FileBundleReference findByAppId(String appId) {
    String query =
      "MATCH %s WHERE r.appId = $appId ".formatted(CypherQueryHelper.getObjectPart("r", "FileReference", false)) +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of("appId", appId));
    var it = iter.iterator();
    return it.hasNext() ? it.next() : null;
  }

  public List<FileBundleReference> findByDataObjectAppId(String dataObjectAppId) {
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d.appId=$dataObjectAppId ".formatted(
          CypherQueryHelper.getObjectPart("r", "FileReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");
    var queryResult = findByQuery(query, Map.of("dataObjectAppId", dataObjectAppId));
    return StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .toList();
  }

  // ─── notebook projection ─────────────────────────────────────────────────

  /**
   * Count of non-deleted {@code .ipynb} files inside bundles attached to
   * the given DataObject.
   */
  public long countNotebooks(String dataObjectAppId) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->(r:FileReference)" +
      "-[:has_payload]->(f:ShepardFile) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "AND toLower(f.filename) ENDS WITH '.ipynb' " +
      "RETURN count(f) AS cnt";
    for (var row : session.query(query, Map.of("aid", dataObjectAppId))) {
      Object cnt = row.get("cnt");
      return cnt instanceof Number n ? n.longValue() : 0L;
    }
    return 0L;
  }

  /**
   * Paginated list of non-deleted {@code .ipynb} files inside bundles
   * attached to the given DataObject, ordered by bundle {@code createdAt ASC}
   * then filename ASC.
   */
  public List<NotebookProjection> listNotebooks(String dataObjectAppId, long skip, int limit) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->(r:FileReference)" +
      "-[:has_payload]->(f:ShepardFile) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "AND toLower(f.filename) ENDS WITH '.ipynb' " +
      "OPTIONAL MATCH (r)-[:created_by]->(u:User) " +
      "RETURN r.appId AS appId, f.filename AS filename, f.fileSize AS fileSize, " +
      "r.createdAt AS createdAt, " +
      "u.username AS username, u.displayName AS displayName, " +
      "u.firstName AS firstName, u.lastName AS lastName " +
      "ORDER BY r.createdAt ASC, toLower(f.filename) ASC " +
      "SKIP $skip LIMIT $limit";
    var rows = session.query(
      query,
      Map.of("aid", dataObjectAppId, "skip", skip, "limit", (long) limit)
    );
    return SingletonFileReferenceDAO.mapNotebookProjections(rows);
  }

  /**
   * APISIMP-REFS-HANDLER-PAGING-TAIL — count of non-deleted FileBundleReferences under a
   * DataObject (resolved by appId). Uses the {@code :FileBundleReference} label so singletons
   * (which only carry {@code :FileReference}) are naturally excluded.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @return total count of matching, non-deleted FileBundleReferences.
   */
  public int countByDataObjectAppId(String dataObjectAppId) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[:has_reference]->(r:FileBundleReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN count(r) AS cnt";
    for (var row : session.query(query, Map.of("aid", dataObjectAppId))) {
      Object cnt = row.get("cnt");
      return cnt instanceof Number n ? n.intValue() : 0;
    }
    return 0;
  }

  /**
   * APISIMP-REFS-HANDLER-PAGING-TAIL — paginated list of non-deleted FileBundleReferences under a
   * DataObject (resolved by appId). Pushes SKIP/LIMIT to Neo4j.
   *
   * @param dataObjectAppId the parent DataObject's appId.
   * @param skip 0-based offset.
   * @param limit maximum rows (must be &gt; 0).
   * @return the FileBundleReferences for the requested page; never null.
   */
  public List<FileBundleReference> findByDataObjectAppId(String dataObjectAppId, int skip, int limit) {
    String query =
      "MATCH (d:DataObject {appId: $aid})-[hr:has_reference]->(r:FileBundleReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "RETURN r, d, hr " +
      "ORDER BY r.createdAt ASC " +
      "SKIP $skip LIMIT $limit";
    var queryResult = findByQuery(
      query,
      Map.of("aid", dataObjectAppId, "skip", (long) skip, "limit", (long) limit)
    );
    return StreamSupport.stream(queryResult.spliterator(), false).toList();
  }

  @Override
  public Class<FileBundleReference> getEntityType() {
    return FileBundleReference.class;
  }
}
