package de.dlr.shepard.context.collection.daos;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;

@RequestScoped
public class DataObjectDAO extends VersionableEntityDAO<DataObject> {

  @Override
  public Class<DataObject> getEntityType() {
    return DataObject.class;
  }

  /**
   * Searches the database for DataObjects.
   *
   * @param collectionId identifies the Collection
   * @param params       encapsulates possible parameters
   * @return a List of DataObjects
   */
  public List<DataObject> findByCollectionByNeo4jIds(long collectionId, QueryParamHelper params) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // OGM Long ids (collection / parent / predecessor / successor) are translated
    // to their appIds via the request-scoped EntityIdResolver; the public method
    // signature stays long for caller-compat until L2d flips the public surface.
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", params.getName());
    paramsMap.put("collectionAppId", resolveAppIdOrEmpty(collectionId));
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
    }
    String match =
      "MATCH (c:Collection)-[hdo:has_dataobject]->" +
      CypherQueryHelper.getObjectPart("d", "DataObject", params.hasName());
    String where = " WHERE c.appId=$collectionAppId";

    if (params.hasParentId()) {
      if (params.getParentId() == -1) {
        where += " AND NOT EXISTS((d)<-[:has_child]-(:DataObject {deleted: FALSE}))";
      } else {
        match += "<-[:has_child]-(parent:DataObject {deleted: FALSE})";
        where += " AND parent.appId=$parentAppId";
        paramsMap.put("parentAppId", resolveAppIdOrEmpty(params.getParentId()));
      }
    }

    if (params.hasPredecessorId()) {
      if (params.getPredecessorId() == -1) {
        where += " AND NOT EXISTS((d)<-[:has_successor]-(:DataObject {deleted: FALSE}))";
      } else {
        match += "<-[:has_successor]-(predecessor:DataObject {deleted: FALSE})";
        where += " AND predecessor.appId=$predecessorAppId";
        paramsMap.put("predecessorAppId", resolveAppIdOrEmpty(params.getPredecessorId()));
      }
    }
    if (params.hasSuccessorId()) {
      if (params.getSuccessorId() == -1) {
        where += " AND NOT EXISTS((d)-[:has_successor]->(:DataObject {deleted: FALSE}))";
      } else {
        match += "-[:has_successor]->(successor:DataObject {deleted: FALSE})";
        where += " AND successor.appId=$successorAppId";
        paramsMap.put("successorAppId", resolveAppIdOrEmpty(params.getSuccessorId()));
      }
    }

    String query = match + where + " WITH d";
    if (params.hasOrderByAttribute()) {
      query += " " + CypherQueryHelper.getOrderByPart("d", params.getOrderByAttribute(), params.getOrderDesc());
    }
    if (params.hasPagination()) {
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("d");
    var result = new ArrayList<DataObject>();
    for (var obj : findByQuery(query, paramsMap)) {
      List<DataObject> parentList = obj.getParent() != null ? List.of(obj.getParent()) : Collections.emptyList();
      if (
        matchCollection(obj, collectionId) &&
        matchName(obj, params.getName()) &&
        matchRelated(parentList, params.getParentId()) &&
        matchRelated(obj.getSuccessors(), params.getSuccessorId()) &&
        matchRelated(obj.getPredecessors(), params.getPredecessorId())
      ) {
        result.add(obj);
      }
    }

    return result;
  }

  public List<DataObject> findByCollectionByShepardIds(
    long collectionShepardId,
    QueryParamHelper paramsWithShepardIds
  ) {
    return findByCollectionByShepardIds(collectionShepardId, paramsWithShepardIds, null);
  }

  /**
   * Returns all non-deleted, top-level DataObjects for the Collection identified by
   * {@code collectionAppId}. Top-level means the DataObject has no parent DataObject
   * (i.e. is a direct child of the Collection, not nested inside another DataObject).
   *
   * <p>Used by the AAS1b submodels endpoint ({@code GET /v2/aas/shells/{aasId}/submodels})
   * to project top-level DataObjects as IDTA AAS v3 Submodel references.
   *
   * @param collectionAppId the {@code appId} of the parent Collection
   * @return list of top-level DataObjects; empty when the Collection has none
   */
  public List<DataObject> findTopLevelByCollectionAppId(String collectionAppId) {
    List<DataObject> result = new ArrayList<>();
    findByQuery(
      "MATCH (c:Collection {appId: $collectionAppId, deleted: FALSE})" +
      "-[:has_dataobject]->(d:DataObject {deleted: FALSE})" +
      " WHERE NOT EXISTS((d)<-[:has_child]-(:DataObject {deleted: FALSE}))" +
      " WITH d " +
      CypherQueryHelper.getReturnPart("d"),
      Map.of("collectionAppId", collectionAppId)
    ).forEach(result::add);
    return result;
  }

  /**
   * Deletes the has_successor relation between the predecessor and the successor dataobjects in neo4j
   */
  public void deleteHasSuccessorRelation(long predecessorShepardId, long successorShepardId) {
    deleteRelation(
      predecessorShepardId,
      successorShepardId,
      getEntityType().getSimpleName(),
      getEntityType().getSimpleName(),
      Constants.HAS_SUCCESSOR
    );
  }

  /**
   * Deletes the has_child relation between the parent and the child in neo4j
   */
  public void deleteHasChildRelation(long parentShepardId, long childShepardId) {
    deleteRelation(
      parentShepardId,
      childShepardId,
      getEntityType().getSimpleName(),
      getEntityType().getSimpleName(),
      Constants.HAS_CHILD
    );
  }

  /**
   * Deletes all attributes of a DataObject in neo4j
   * @param dataObject  identifies the DataObject
   */
  public void deleteAllAttributes(DataObject dataObject) {
    if (dataObject.getAttributes() == null || dataObject.getAttributes().isEmpty()) return;
    Node d = Cypher.node("DataObject");
    String st = Cypher.match(d)
      .where(d.internalId().isEqualTo(Cypher.literalOf(dataObject.getId())))
      .remove(dataObject.getAttributes().keySet().stream().map(key -> d.property("attributes||" + key)).toList())
      .build()
      .getCypher();
    session.query(st, new HashMap<String, String>());
  }

  /**
   * Searches the database for DataObjects.
   *
   * @param collectionShepardId  identifies the Collection
   * @param paramsWithShepardIds encapsulates possible parameters
   * @return a List of DataObjects
   */
  public List<DataObject> findByCollectionByShepardIds(
    long collectionShepardId,
    QueryParamHelper paramsWithShepardIds,
    UUID versionUID
  ) {
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", paramsWithShepardIds.getName());
    if (paramsWithShepardIds.hasPagination()) {
      paramsMap.put("offset", paramsWithShepardIds.getPagination().getOffset());
      paramsMap.put("size", paramsWithShepardIds.getPagination().getSize());
    }
    String match =
      "MATCH (v:Version)<-[:has_version]-(c:Collection)-[hdo:has_dataobject]->" +
      CypherQueryHelper.getObjectPart("d", "DataObject", paramsWithShepardIds.hasName());
    String where = " WHERE c." + Constants.SHEPARD_ID + "=" + collectionShepardId + " AND ";
    //search in HEAD version
    if (versionUID == null) where = where + CypherQueryHelper.getVersionHeadPart("v");
    //search in version given by versionUID
    else where = where + CypherQueryHelper.getVersionPart("v", versionUID);
    if (paramsWithShepardIds.hasParentId()) {
      if (paramsWithShepardIds.getParentId() == -1) {
        where += " AND NOT EXISTS((d)<-[:has_child]-(:DataObject {deleted: FALSE}))";
      } else {
        match +=
          "<-[:has_child]-(parent:DataObject {deleted: FALSE, " +
          Constants.SHEPARD_ID +
          ": " +
          paramsWithShepardIds.getParentId() +
          "})";
      }
    }

    if (paramsWithShepardIds.hasPredecessorId()) {
      if (paramsWithShepardIds.getPredecessorId() == -1) {
        where += " AND NOT EXISTS((d)<-[:has_successor]-(:DataObject {deleted: FALSE}))";
      } else {
        match +=
          "<-[:has_successor]-(predecessor:DataObject {deleted: FALSE, " +
          Constants.SHEPARD_ID +
          ": " +
          paramsWithShepardIds.getPredecessorId() +
          "})";
      }
    }
    if (paramsWithShepardIds.hasSuccessorId()) {
      if (paramsWithShepardIds.getSuccessorId() == -1) {
        where += " AND NOT EXISTS((d)-[:has_successor]->(:DataObject {deleted: FALSE}))";
      } else {
        match +=
          "-[:has_successor]->(successor:DataObject {deleted: FALSE, " +
          Constants.SHEPARD_ID +
          ": " +
          paramsWithShepardIds.getSuccessorId() +
          "})";
      }
    }
    if (paramsWithShepardIds.hasStatus()) {
      paramsMap.put("status", paramsWithShepardIds.getStatus());
      where += " AND d.status = $status";
    }

    String query = match + where + " WITH d";
    if (paramsWithShepardIds.hasOrderByAttribute()) {
      query +=
        " " +
        CypherQueryHelper.getOrderByPart(
          "d",
          paramsWithShepardIds.getOrderByAttribute(),
          paramsWithShepardIds.getOrderDesc()
        );
    }
    if (paramsWithShepardIds.hasPagination()) {
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("d");
    var result = new ArrayList<DataObject>();
    for (var obj : findByQuery(query, paramsMap)) {
      List<DataObject> parentList = obj.getParent() != null ? List.of(obj.getParent()) : Collections.emptyList();
      if (
        matchCollectionByShepardId(obj, collectionShepardId) &&
        matchName(obj, paramsWithShepardIds.getName()) &&
        matchRelatedByShepardId(parentList, paramsWithShepardIds.getParentId()) &&
        matchRelatedByShepardId(obj.getSuccessors(), paramsWithShepardIds.getSuccessorId()) &&
        matchRelatedByShepardId(obj.getPredecessors(), paramsWithShepardIds.getPredecessorId())
      ) {
        result.add(obj);
      }
    }

    return result;
  }

  /**
   * Delete dataObject and all related references
   *
   * @param id        identifies the dataObject
   * @param updatedBy current date
   * @param updatedAt current user
   * @return whether the deletion was successful or not
   */
  public boolean deleteDataObjectByNeo4jId(long id, User updatedBy, Date updatedAt) {
    var dataObject = findByNeo4jId(id);
    dataObject.setUpdatedBy(updatedBy);
    dataObject.setUpdatedAt(updatedAt);
    dataObject.setDeleted(true);
    createOrUpdate(dataObject);
    // L2c read-path swap: use appId rather than the deprecated id() function.
    // dataObject was just persisted so its appId is guaranteed populated.
    String appId = dataObject.getAppId();
    if (appId == null) appId = entityIdResolver.resolveAppId(id);
    String query =
      "MATCH (d:DataObject {appId: $appId}) OPTIONAL MATCH (d)-[:has_reference]->(r:BasicReference) " +
      "FOREACH (n in [d,r] | SET n.deleted = true)";
    var result = runQuery(query, Map.of("appId", appId));
    return result;
  }

  /**
   * Load a DataObject deeper than the default {@code DEPTH_ENTITY=1} so that
   * the references' typed container relationships (e.g.
   * {@code TimeseriesReference -> TimeseriesContainer}) are populated. The
   * default load only fetches the references themselves, leaving each
   * reference's {@code container} pointer null — which is why the v2 detail
   * IO's {@code containers.timeseries[]} list comes back empty. MCP callers
   * (e.g. {@code get_data_object}) need that pointer to discover container
   * appIds for the next agent hop, so they call this variant explicitly.
   *
   * @param shepardId the DataObject's OGM long id
   * @param depth     OGM load depth (passing 2 picks up the container hop;
   *                  do not raise indiscriminately — Neo4j-OGM expands every
   *                  relationship at the given depth and the cost compounds)
   * @return the DataObject loaded to {@code depth}, or null if not found
   */
  public DataObject findByShepardIdAtDepth(long shepardId, int depth) {
    String appId = resolveAppIdOrEmpty(shepardId);
    String cypher = "MATCH (d:DataObject {appId: $appId}) RETURN d";
    var hits = session.query(DataObject.class, cypher, Map.of("appId", appId));
    DataObject dataObject = StreamSupport.stream(hits.spliterator(), false).findFirst().orElse(null);
    if (dataObject == null) return null;
    // Re-load at the requested depth so the relationships chain through.
    return session.load(DataObject.class, dataObject.getId(), depth);
  }

  /**
   * PROV1k — look up a DataObject by its {@code appId} (UUID v7).
   *
   * <p>Used by {@link de.dlr.shepard.context.collection.services.DataObjectService}
   * to resolve {@code TypedPredecessorIO.predecessorAppId} entries to DataObject
   * instances for graph-edge wiring.
   *
   * @param appId the DataObject's UUID v7 appId
   * @return the DataObject, or {@code null} if not found
   */
  public DataObject findByAppId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    String cypher = "MATCH (d:DataObject {appId: $appId}) RETURN d";
    var hits = session.query(DataObject.class, cypher, Map.of("appId", appId));
    return StreamSupport.stream(hits.spliterator(), false).findFirst().orElse(null);
  }

  /**
   * Delete dataObject and all related references
   *
   * @param shepardId identifies the dataObject
   * @param updatedBy current date
   * @param updatedAt current user
   * @return whether the deletion was successful or not
   */
  public boolean deleteDataObjectByShepardId(long shepardId, User updatedBy, Date updatedAt) {
    DataObject dataObject = findByShepardId(shepardId);
    dataObject.setUpdatedBy(updatedBy);
    dataObject.setUpdatedAt(updatedAt);
    dataObject.setDeleted(true);
    createOrUpdate(dataObject);
    // L2c read-path swap: use appId rather than the deprecated id() function.
    // dataObject was just persisted so its appId is guaranteed populated.
    String appId = dataObject.getAppId();
    if (appId == null) appId = entityIdResolver.resolveAppId(dataObject.getId());
    String query =
      "MATCH (d:DataObject {appId: $appId}) OPTIONAL MATCH (d)-[:has_reference]->(r:BasicReference) " +
      "FOREACH (n in [d,r] | SET n.deleted = true)";
    var result = runQuery(query, Map.of("appId", appId));
    return result;
  }

  private boolean matchName(DataObject obj, String name) {
    return name == null || name.equalsIgnoreCase(obj.getName());
  }

  private boolean matchRelated(List<DataObject> related, Long id) {
    if (id == null) {
      return true;
    } else if (id == -1) {
      // return true if there is no related object or all objects are deleted
      return related.stream().allMatch(DataObject::isDeleted);
    } else {
      // return true if at least one related object that is not deleted matches the ID
      return related.stream().anyMatch(d -> !d.isDeleted() && d.getId().equals(id));
    }
  }

  private boolean matchRelatedByShepardId(List<DataObject> related, Long shepardId) {
    if (shepardId == null) {
      return true;
    } else if (shepardId == -1) {
      // return true if there is no related object or all objects are deleted
      return related.stream().allMatch(DataObject::isDeleted);
    } else {
      // return true if at least one related object that is not deleted matches the ID
      return related.stream().anyMatch(d -> !d.isDeleted() && d.getShepardId().equals(shepardId));
    }
  }

  private boolean matchCollection(DataObject obj, long collectionId) {
    return obj.getCollection() != null && obj.getCollection().getId().equals(collectionId);
  }

  private boolean matchCollectionByShepardId(DataObject obj, long collectionShepardId) {
    return obj.getCollection() != null && obj.getCollection().getShepardId().equals(collectionShepardId);
  }

  public List<DataObject> getDataObjectsByQuery(String query) {
    var queryResult = findByQuery(query, Collections.emptyMap());
    List<DataObject> ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  /**
   * Batch-computes per-kind reference counts for a list of DataObject
   * {@code appId} values in a single Cypher round-trip (no N+1 queries).
   *
   * <p>Reference kinds covered:
   * <ul>
   *   <li><b>timeseries</b> — {@code :TimeseriesReference} nodes</li>
   *   <li><b>file</b> — {@code :FileReference} (bundles, FR1a) and
   *       {@code :SingletonFileReference} (singletons, FR1b) — both labels
   *       must be included; querying only one would produce incorrect counts</li>
   *   <li><b>structured-data</b> — {@code :StructuredDataReference} nodes</li>
   * </ul>
   *
   * <p>Only non-deleted references are counted ({@code WHERE NOT
   * coalesce(r.deleted, false)}), matching the post-load
   * {@code cutDeleted()} filter applied by
   * {@link de.dlr.shepard.context.collection.services.DataObjectService}.
   *
   * <p>DataObjects whose {@code appId} is not found in the graph (stale
   * OGM cache, race-delete) are silently omitted from the result map —
   * callers should default to zero for missing keys.
   *
   * @param appIds list of DataObject {@code appId} values (may be empty)
   * @return map keyed by {@code appId}; value is {@code long[3]} where
   *         {@code [0]=tsCount}, {@code [1]=fileCount}, {@code [2]=sdCount}
   */
  /**
   * ANC-1 — traverses the {@code has_predecessor} / {@code has_successor}
   * chain up to {@code depth} hops (clamped server-side to [1, 50]) starting
   * from the DataObject identified by {@code appId}.
   *
   * <p>The Cypher variable-length bounds ({@code *1..N}) cannot be supplied
   * as query parameters in Neo4j OGM — the driver always treats path-length
   * bounds as literals. {@code depth} is therefore string-interpolated into
   * the query after the server-side clamp; {@code $appId} still binds normally.
   *
   * @param appId  UUID v7 of the start DataObject
   * @param depth  maximum hop count; clamped to [1, 50]
   * @return ordered list of non-deleted DataObjects in the predecessor chain
   *         (excluding the start node itself); empty when no predecessors exist
   */
  public List<DataObject> findPredecessorChain(String appId, int depth) {
    int safeDepth = Math.max(1, Math.min(depth, 50));
    String cypher =
      "MATCH (start:DataObject {appId: $appId, deleted: FALSE})" +
      "<-[:has_successor*1.." + safeDepth + "]-(pred:DataObject {deleted: FALSE})" +
      " RETURN DISTINCT pred ORDER BY pred.shepardId";
    return StreamSupport.stream(
      session.query(DataObject.class, cypher, Map.of("appId", appId)).spliterator(),
      false
    ).toList();
  }

  /**
   * ANC-1 — traverses the {@code has_successor} chain up to {@code depth} hops
   * (clamped server-side to [1, 50]) starting from the DataObject identified by
   * {@code appId}.
   *
   * @param appId  UUID v7 of the start DataObject
   * @param depth  maximum hop count; clamped to [1, 50]
   * @return ordered list of non-deleted DataObjects in the successor chain
   *         (excluding the start node itself); empty when no successors exist
   */
  public List<DataObject> findSuccessorChain(String appId, int depth) {
    int safeDepth = Math.max(1, Math.min(depth, 50));
    String cypher =
      "MATCH (start:DataObject {appId: $appId, deleted: FALSE})" +
      "-[:has_successor*1.." + safeDepth + "]->(succ:DataObject {deleted: FALSE})" +
      " RETURN DISTINCT succ ORDER BY succ.shepardId";
    return StreamSupport.stream(
      session.query(DataObject.class, cypher, Map.of("appId", appId)).spliterator(),
      false
    ).toList();
  }

  /**
   * REF-1 fix — fetches typed container refs for a DataObject in one Cypher
   * round-trip. Avoids the OGM polymorphism issue where depth-1 loading returns
   * {@code BasicReference} instead of the concrete subtype.
   *
   * <p>NEO-AUDIT-008: Uses {@code CALL {}} subqueries instead of sibling
   * {@code OPTIONAL MATCH} legs to eliminate the Cartesian product. The old
   * shape walked all {@code has_reference} edges in each leg, causing N×M×P
   * dbHits when a DataObject had many refs. Each {@code CALL {}} scope
   * executes independently, so cardinalities do not multiply.
   *
   * <p>Returns a single result row with three list columns:
   * {@code tsRefs}, {@code fileRefs}, {@code sdRefs}. Each list element is a
   * map with keys: {@code refShepardId}, {@code refAppId},
   * {@code containerId}, {@code containerAppId}, {@code containerName}.
   */
  public Map<String, Object> findContainersByDataObjectAppId(String appId) {
    String cypher =
      "MATCH (do:DataObject {appId: $appId}) " +
      "CALL { " +
      "  WITH do " +
      "  OPTIONAL MATCH (do)-[:has_reference]->(tr:TimeseriesReference)-[:is_in_container]->(tc:TimeseriesContainer) " +
      "    WHERE NOT coalesce(tr.deleted, false) " +
      "  RETURN collect({refShepardId: tr.shepardId, refAppId: tr.appId, containerId: id(tc), containerAppId: tc.appId, containerName: tc.name}) AS tsRefs " +
      "} " +
      "CALL { " +
      "  WITH do " +
      "  OPTIONAL MATCH (do)-[:has_reference]->(fr:FileBundleReference)-[:is_in_container]->(fc:FileContainer) " +
      "    WHERE NOT coalesce(fr.deleted, false) " +
      "  RETURN collect({refShepardId: fr.shepardId, refAppId: fr.appId, containerId: id(fc), containerAppId: fc.appId, containerName: fc.name}) AS fileRefs " +
      "} " +
      "CALL { " +
      "  WITH do " +
      "  OPTIONAL MATCH (do)-[:has_reference]->(sdr:StructuredDataReference)-[:is_in_container]->(sc:StructuredDataContainer) " +
      "    WHERE NOT coalesce(sdr.deleted, false) " +
      "  RETURN collect({refShepardId: sdr.shepardId, refAppId: sdr.appId, containerId: id(sc), containerAppId: sc.appId, containerName: sc.name}) AS sdRefs " +
      "} " +
      "RETURN tsRefs, fileRefs, sdRefs";
    var results = session.query(cypher, Map.of("appId", appId)).queryResults();
    var iter = results.iterator();
    return iter.hasNext() ? iter.next() : Map.of("tsRefs", List.of(), "fileRefs", List.of(), "sdRefs", List.of());
  }

  public Map<String, long[]> findRefCountsByAppIds(List<String> appIds) {
    if (appIds == null || appIds.isEmpty()) return Collections.emptyMap();

    // One UNWIND pass — Neo4j evaluates each OPTIONAL MATCH independently
    // per row produced by UNWIND, so a DataObject with no timeseries refs
    // still appears in the result with tsCount=0.
    String cypher =
      "UNWIND $dataObjectAppIds AS doAppId " +
      "MATCH (d:DataObject {appId: doAppId}) " +
      "OPTIONAL MATCH (d)-[:has_reference]->(tr:TimeseriesReference) WHERE NOT coalesce(tr.deleted, false) " +
      "WITH d, count(DISTINCT tr) AS tsCount " +
      "OPTIONAL MATCH (d)-[:has_reference]->(fr) WHERE (fr:FileReference OR fr:SingletonFileReference) AND NOT coalesce(fr.deleted, false) " +
      "WITH d, tsCount, count(DISTINCT fr) AS fileCount " +
      "OPTIONAL MATCH (d)-[:has_reference]->(sdr:StructuredDataReference) WHERE NOT coalesce(sdr.deleted, false) " +
      "RETURN d.appId AS appId, tsCount, fileCount, count(DISTINCT sdr) AS sdCount";

    var result = session.query(cypher, Map.of("dataObjectAppIds", appIds));
    Map<String, long[]> out = new LinkedHashMap<>();
    for (Map<String, Object> row : result.queryResults()) {
      String appId = (String) row.get("appId");
      if (appId == null) continue;
      long tsCount = row.get("tsCount") instanceof Number n ? n.longValue() : 0L;
      long fileCount = row.get("fileCount") instanceof Number n ? n.longValue() : 0L;
      long sdCount = row.get("sdCount") instanceof Number n ? n.longValue() : 0L;
      out.put(appId, new long[] { tsCount, fileCount, sdCount });
    }
    return out;
  }

  /**
   * For each DataObject appId in the input list, returns the Neo4j long IDs of
   * all non-deleted TimeseriesContainers reachable via its TimeseriesReferences.
   *
   * <p>Used by the {@code ?include=time-bounds} feature on the DataObject list
   * endpoint to fan out to TimescaleDB for per-DataObject MIN/MAX time in a
   * single SQL pass.
   *
   * @param appIds DataObject appIds (one page worth)
   * @return map of DataObject appId → list of TimeseriesContainer Neo4j long ids
   */
  public Map<String, List<Long>> findTsContainerIdsByDataObjectAppIds(List<String> appIds) {
    if (appIds == null || appIds.isEmpty()) return Collections.emptyMap();

    String cypher =
      "UNWIND $dataObjectAppIds AS doAppId " +
      "MATCH (d:DataObject {appId: doAppId}) " +
      "OPTIONAL MATCH (d)-[:has_reference]->(tr:TimeseriesReference)-[:is_in_container]->(tc:TimeseriesContainer) " +
      "  WHERE NOT coalesce(tr.deleted, false) " +
      "RETURN d.appId AS appId, collect(DISTINCT id(tc)) AS containerNeo4jIds";

    var qResult = session.query(cypher, Map.of("dataObjectAppIds", appIds));
    Map<String, List<Long>> out = new LinkedHashMap<>();
    for (Map<String, Object> row : qResult.queryResults()) {
      String appId = (String) row.get("appId");
      if (appId == null) continue;
      List<Long> ids = new ArrayList<>();
      Object raw = row.get("containerNeo4jIds");
      if (raw instanceof Iterable<?> iter) {
        for (Object item : iter) {
          if (item instanceof Number n) ids.add(n.longValue());
        }
      }
      out.put(appId, ids);
    }
    return out;
  }

  /**
   * NEO-AUDIT-004 — time-bucketed Agent index for the `:User` supernode.
   *
   * <p>Writes a {@code (:User)-[:created_in_month {ym: "YYYYMM"}]->(:DataObject)}
   * relationship immediately after a DataObject is persisted. This lets queries
   * like "list DataObjects created by user X in month Y" avoid walking all
   * 33k+ {@code created_by} edges on the operator `:User` supernode — they
   * target the bounded {@code [:created_in_month]} rel set instead, typically
   * reducing dbHits by 100-1000x (Neo4j supernode KB:
   * https://neo4j.com/developer/kb/understanding-the-design-of-supernodes/).
   *
   * <p>Uses MERGE so the call is idempotent — re-runs (e.g. on retry) produce
   * no duplicates. The {@code ym} property is derived from
   * {@link DataObject#getCreatedAt()} and formatted as a 6-char
   * {@code "YYYYMM"} string (e.g. {@code "202605"} for May 2026).
   *
   * <p>This method is best-effort — it logs a warning and continues if the
   * DataObject or User node cannot be resolved, so a write failure here never
   * blocks DataObject creation.
   *
   * @param dataObject the newly-created DataObject (must have a non-null
   *                   {@code appId} and {@code createdAt}; {@code createdBy}
   *                   must be a non-null User with a non-null {@code username})
   */
  public void writeCreatedInMonth(DataObject dataObject) {
    if (dataObject == null) return;
    User createdBy = dataObject.getCreatedBy();
    String dataObjectAppId = dataObject.getAppId();
    Date createdAt = dataObject.getCreatedAt();
    if (createdBy == null || createdBy.getUsername() == null) {
      Log.warnf(
        "NEO-AUDIT-004: skipping created_in_month write — DataObject %s has no createdBy user",
        dataObjectAppId
      );
      return;
    }
    if (dataObjectAppId == null) {
      Log.warnf("NEO-AUDIT-004: skipping created_in_month write — DataObject has null appId");
      return;
    }
    if (createdAt == null) {
      Log.warnf(
        "NEO-AUDIT-004: skipping created_in_month write — DataObject %s has null createdAt",
        dataObjectAppId
      );
      return;
    }
    // Format as "YYYYMM" using UTC explicitly so the result aligns with the
    // Cypher backfill migration which uses datetime({epochMillis: x}).year/month (UTC).
    // Using JVM-default TZ would cause divergence for DataObjects created near midnight
    // in timezones east of UTC (e.g. CEST = UTC+2: 2026-06-01T00:30 CEST is
    // 2026-05-31T22:30 UTC — Java TZ-naive would produce "202606", Cypher "202605").
    var utc = createdAt.toInstant().atZone(java.time.ZoneOffset.UTC);
    String ym = String.format("%04d%02d", utc.getYear(), utc.getMonthValue());
    String cypher =
      "MATCH (u:User {username: $username}) " +
      "MATCH (d:DataObject {appId: $dataObjectAppId}) " +
      "MERGE (u)-[:created_in_month {ym: $ym}]->(d)";
    try {
      session.query(cypher, Map.of("username", createdBy.getUsername(), "dataObjectAppId", dataObjectAppId, "ym", ym));
    } catch (Exception e) {
      Log.warnf(
        "NEO-AUDIT-004: failed to write created_in_month rel for DataObject %s (user=%s, ym=%s): %s",
        dataObjectAppId,
        createdBy.getUsername(),
        ym,
        e.getMessage()
      );
    }
  }

}
