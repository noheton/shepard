package de.dlr.shepard.context.references.videostreamreference.daos;

import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * VID1a — DAO for {@link VideoStreamReference} Neo4j nodes.
 */
@RequestScoped
public class VideoStreamReferenceDAO extends VersionableEntityDAO<VideoStreamReference> {

  @Override
  public Class<VideoStreamReference> getEntityType() {
    return VideoStreamReference.class;
  }

  /**
   * List all non-deleted {@link VideoStreamReference} nodes whose parent
   * {@code :DataObject} carries the given OGM id.
   *
   * <p>Uses the L2c appId-based read path (same pattern as
   * {@link de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO#findByDataObjectNeo4jId}).
   *
   * @param dataObjectId the Neo4j internal Long id of the parent DataObject
   * @return all non-deleted references attached to that DataObject
   */
  public List<VideoStreamReference> findByDataObjectNeo4jId(long dataObjectId) {
    String query =
      "MATCH (d:DataObject)-[hr:has_reference]->%s WHERE d.appId=$dataObjectAppId ".formatted(
          CypherQueryHelper.getObjectPart("r", "VideoStreamReference", false)
        ) +
      CypherQueryHelper.getReturnPart("r");

    var queryResult = findByQuery(query, Map.of("dataObjectAppId", resolveAppIdOrEmpty(dataObjectId)));

    return StreamSupport.stream(queryResult.spliterator(), false)
      .filter(r -> r.getDataObject() != null)
      .filter(r -> r.getDataObject().getId().equals(dataObjectId))
      .collect(Collectors.toList());
  }

  /**
   * Find a single {@link VideoStreamReference} by its {@code appId}.
   *
   * @param appId the UUID v7 application-level identifier
   * @return the reference, or {@code null} if not found
   */
  public VideoStreamReference findByAppId(String appId) {
    String query =
      "MATCH " +
      CypherQueryHelper.getObjectPart("r", "VideoStreamReference", false) +
      " WHERE r.appId = $appId " +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /**
   * VIDEO-HEVC-TRANSCODE-BACKFILL — list non-deleted video references whose
   * proxy is missing or failed, optionally narrowed by codec.
   *
   * <p>Backs the {@code POST /v2/admin/video/transcode-backfill} endpoint. The
   * codec filter accepts a single codec string (e.g. {@code "hevc"},
   * {@code "h265"}). When {@code limit > 0}, the result is truncated.
   *
   * @param codec optional case-insensitive codec filter; null or blank → all
   * @param limit max rows to return; non-positive → no cap
   */
  /**
   * CRIT-QUARKUS-CLASSTRANSFORM-VIDEOPAYLOAD — stamp proxy fields via Cypher
   * directly, without loading the entity into a Java local variable.  Loading
   * a {@link VideoStreamReference} inside a CDI bean's method triggers
   * {@code ClassTransformingBuildStep.getCommonSuperClass(VideoStreamReference,…)}
   * which tries to load {@code BasicReference} from the narrowed transformation
   * classloader and throws {@code NoClassDefFoundError}.
   *
   * @param appId              the reference's appId
   * @param proxyStatus        new proxyStatus value (e.g. "READY", "FAILED", "PENDING")
   * @param proxyStorageLocator new locator string, or {@code null} to leave unchanged
   */
  public void stampProxy(String appId, String proxyStatus, String proxyStorageLocator) {
    if (proxyStorageLocator != null) {
      session.query(
        "MATCH (r:VideoStreamReference {appId: $appId}) " +
        "SET r.proxyStatus = $status, r.proxyStorageLocator = $loc",
        Map.of("appId", appId, "status", proxyStatus, "loc", proxyStorageLocator)
      );
    } else {
      session.query(
        "MATCH (r:VideoStreamReference {appId: $appId}) SET r.proxyStatus = $status",
        Map.of("appId", appId, "status", proxyStatus)
      );
    }
  }

  public List<VideoStreamReference> findBackfillCandidates(String codec, int limit) {
    Map<String, Object> params = new java.util.HashMap<>();
    if (codec != null && !codec.isBlank()) {
      params.put("codec", codec.toLowerCase());
    }
    var queryResult = findByQuery(buildBackfillCypher(codec, limit), params);
    return StreamSupport.stream(queryResult.spliterator(), false).collect(Collectors.toList());
  }

  /**
   * VIDEO-HEVC-TRANSCODE-BACKFILL-2026-07-01 — build the candidate query as a
   * pure String helper so the WHERE-clause invariants can be unit-tested
   * without Neo4j.
   *
   * <p>Historic bug shape: the inline property-match {@code {deleted: FALSE}}
   * from {@link CypherQueryHelper#getObjectPart} silently excludes rows whose
   * {@code deleted} property is <em>unset</em>. Current data has {@code deleted}
   * explicit on every {@code :VideoStreamReference}, but the additive-schema
   * rule ({@code CLAUDE.md} — new columns nullable, read paths null-coalesce)
   * says the DAO must be robust to unset. We therefore drop the property-match
   * form here and use an explicit {@code (r.deleted IS NULL OR r.deleted = false)}
   * WHERE clause. Equivalent on today's data, correct on tomorrow's.
   *
   * <p>Package-private for {@link VideoStreamReferenceDAOTest}.
   */
  static String buildBackfillCypher(String codec, int limit) {
    StringBuilder cypher = new StringBuilder(
      "MATCH (r:VideoStreamReference) " +
      "WHERE (r.deleted IS NULL OR r.deleted = false) " +
      "AND (r.proxyStatus IS NULL OR r.proxyStatus = 'FAILED') " +
      "AND r.storageLocator IS NOT NULL AND r.storageLocator <> '' "
    );
    if (codec != null && !codec.isBlank()) {
      cypher.append("AND toLower(r.videoCodec) = $codec ");
    }
    cypher.append(CypherQueryHelper.getReturnPart("r"));
    if (limit > 0) {
      cypher.append(" LIMIT ").append(limit);
    }
    return cypher.toString();
  }
}
