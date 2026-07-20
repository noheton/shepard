package de.dlr.shepard.context.collection.daos;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequestScoped
public class CollectionDAO extends VersionableEntityDAO<Collection> {

  @Override
  public Class<Collection> getEntityType() {
    return Collection.class;
  }

  /**
   * Searches the database for collections.
   *
   * @param params   encapsulates possible parameters
   * @param username the name of the user
   * @return a list of collections
   */
  public List<Collection> findAllCollectionsByNeo4jId(QueryParamHelper params, String username) {
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", params.getName());
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
    }
    var query =
      "MATCH %s WHERE %s WITH c".formatted(
          CypherQueryHelper.getObjectPart("c", "Collection", params.hasName()),
          CypherQueryHelper.getReadableByQuery("c", username)
        );
    if (params.hasOrderByAttribute()) {
      query += " " + CypherQueryHelper.getOrderByPart("c", params.getOrderByAttribute(), params.getOrderDesc());
    }
    if (params.hasPagination()) {
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("c");
    var result = new ArrayList<Collection>();
    for (var col : findByQuery(query, paramsMap)) {
      if (matchName(col, params.getName())) {
        result.add(col);
      }
    }
    return result;
  }

  /**
   * Searches the database for collections.
   *
   * @param params   encapsulates possible parameters
   * @param username the name of the user
   * @return a list of collections
   */
  public List<Collection> findAllCollectionsByShepardId(QueryParamHelper params, String username) {
    return findAllCollectionsByShepardId(params, username, false);
  }

  /**
   * SUPERNODE-F2-COLLECTION-DETAIL — light variant of
   * {@link #findAllCollectionsByShepardId(QueryParamHelper, String)} that excludes
   * the {@code has_dataobject} fan-out per Collection, so listing Collections never
   * drags each one's members (up to <b>8,483</b> on the live
   * {@code mffd-afp-tapelaying} Collection) into OGM.
   *
   * <p>Used only by members-ignoring callers: the v2 list
   * ({@code CollectionV2IO} {@code @JsonIgnore}s {@code dataObjectIds}) and the MCP
   * {@code list_collections} tool (reads only {@code appId}/{@code name}/
   * {@code description}/{@code status}). The v1 {@code /shepard/api/collections}
   * list keeps the members-hydrating {@link #findAllCollectionsByShepardId} because
   * {@code CollectionIO.dataObjectIds} is {@code required=true}.
   *
   * @param params   encapsulates possible parameters
   * @param username the name of the user
   * @return a list of Collections without their {@code dataObjects} members
   */
  public List<Collection> findAllCollectionsByShepardIdLight(QueryParamHelper params, String username) {
    return findAllCollectionsByShepardId(params, username, true);
  }

  private List<Collection> findAllCollectionsByShepardId(QueryParamHelper params, String username, boolean light) {
    String versionVariable = "v";
    String collectionVariable = "c";
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", params.getName());
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
    }
    String query =
      "MATCH %s WHERE %s AND %s WITH %s".formatted(
          CypherQueryHelper.getObjectPartWithVersion(
            collectionVariable,
            "Collection",
            params.hasName(),
            versionVariable
          ),
          CypherQueryHelper.getReadableByQuery(collectionVariable, username),
          CypherQueryHelper.getVersionHeadPart(versionVariable),
          collectionVariable
        );
    if (params.hasOrderByAttribute()) {
      query +=
        " " + CypherQueryHelper.getOrderByPart(collectionVariable, params.getOrderByAttribute(), params.getOrderDesc());
    }
    if (params.hasPagination()) {
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query +=
      " " +
      (light
          ? CypherQueryHelper.getReturnPartForCollectionDetail(collectionVariable)
          : CypherQueryHelper.getReturnPart(collectionVariable));
    ArrayList<Collection> result = new ArrayList<Collection>();
    for (Collection col : findByQuery(query, paramsMap)) {
      if (matchName(col, params.getName())) {
        result.add(col);
      }
    }
    return result;
  }

  /**
   * Delete collection and all related dataObjects and references
   *
   * @param shepardId identifies the collection
   * @param updatedBy current date
   * @param updatedAt current user
   * @return whether the deletion was successful or not
   */
  public boolean deleteCollectionByShepardId(long shepardId, User updatedBy, Date updatedAt) {
    Collection collection = findByShepardId(shepardId);
    collection.setUpdatedBy(updatedBy);
    collection.setUpdatedAt(updatedAt);
    collection.setDeleted(true);
    createOrUpdate(collection);
    // NEO-AUDIT-008: the old chained OPTIONAL MATCH created c × d × r row triples
    // (1 collection × many DOs × many refs each), causing row explosion on large
    // collections. Replaced with three CALL{} subqueries that each execute
    // independently, so cardinalities never multiply across the three targets.
    String query =
      """
      MATCH (c:Collection {shepardId:%d})
      SET c.deleted = true
      WITH c
      CALL {
        WITH c
        MATCH (c)-[:has_dataobject]->(d:DataObject)
        SET d.deleted = true
        RETURN count(d) AS doCount
      }
      CALL {
        WITH c
        MATCH (c)-[:has_dataobject]->(d:DataObject)-[:has_reference]->(r:BasicReference)
        SET r.deleted = true
        RETURN count(r) AS refCount
      }
      RETURN doCount, refCount""".formatted(shepardId);
    boolean result = runQuery(query, Collections.emptyMap());
    return result;
  }

  /**
   * Returns the Collection with the given {@code appId} that is readable by {@code username},
   * or {@code null} when no such Collection exists or the caller lacks read permission
   * (404-on-no-read discipline: DAO returns null for both cases).
   */
  public Collection findByAppId(String appId, String username) {
    String query =
      "MATCH (c:Collection {deleted: FALSE}) WHERE c.appId = $appId AND " +
      CypherQueryHelper.getReadableByQuery("c", username) +
      " WITH c " +
      CypherQueryHelper.getReturnPart("c");
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /**
   * SUPERNODE-F2-COLLECTION-DETAIL — collection-detail load that hydrates the
   * bounded structural neighborhood (permissions, version, default file container,
   * created/updated-by, incoming CollectionReferences) but <strong>excludes the
   * {@code has_dataobject} fan-out edge</strong>, so opening the detail page of a
   * large Collection (the live {@code mffd-afp-tapelaying} holds <b>8,483</b>
   * DataObjects) stays O(1) instead of dragging every member into OGM.
   *
   * <p>The contained DataObjects are served separately by the paged
   * {@code GET /v2/collections/{appId}/data-objects} endpoint. Used by the v2 detail
   * surface only ({@code CollectionV2IO} {@code @JsonIgnore}s {@code dataObjectIds},
   * so the response is wire-identical); the v1 detail path keeps the
   * members-hydrating {@link VersionableEntityDAO#findByShepardId(Long, boolean)}
   * because {@code CollectionIO.dataObjectIds} is {@code required=true}. Loaders that
   * genuinely iterate the members (RO-Crate / DMP export) stay on the heavy load.
   *
   * @param shepardId  identifies the Collection
   * @param versionUID the version to load, or {@code null} for HEAD
   * @return the Collection without its {@code dataObjects} members, or {@code null}
   * @see CypherQueryHelper#getReturnPartForCollectionDetail(String)
   */
  public Collection findByShepardIdForCollectionDetail(long shepardId, UUID versionUID) {
    String versionPart = (versionUID != null)
      ? CypherQueryHelper.getVersionPart("v", versionUID)
      : CypherQueryHelper.getVersionHeadPart("v");
    String query =
      "MATCH (o:%s {deleted: FALSE})-[:has_version]->(v:Version) WHERE %s AND %s WITH o ".formatted(
          getEntityType().getSimpleName(),
          CypherQueryHelper.getShepardIdPart("o", shepardId),
          versionPart
        );
    query += CypherQueryHelper.getReturnPartForCollectionDetail("o");
    var iter = findByQuery(query, Map.of()).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /** Returns the non-deleted Collection with {@code appId}, or {@code null}. Permission-agnostic. */
  public Collection findByAppId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    String query = "MATCH (c:Collection {appId: $appId, deleted: false}) RETURN c";
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /** Returns the number of Collections readable by {@code username} (version-head only). */
  public long countAllCollectionsByShepardId(String username) {
    String query = "MATCH %s WHERE %s AND %s RETURN COUNT(c)".formatted(
        CypherQueryHelper.getObjectPartWithVersion("c", "Collection", false, "v"),
        CypherQueryHelper.getReadableByQuery("c", username),
        CypherQueryHelper.getVersionHeadPart("v")
    );
    var it = session.query(Long.class, query, Map.of()).iterator();
    return it.hasNext() ? it.next() : 0L;
  }

  /** Returns the number of Collections readable by {@code username}, optionally filtered by name (version-head only). */
  public long countAllCollectionsByShepardId(QueryParamHelper params, String username) {
    Map<String, Object> paramsMap = new HashMap<>();
    if (params.hasName()) {
      paramsMap.put("name", params.getName());
    }
    String query = "MATCH %s WHERE %s AND %s RETURN COUNT(c)".formatted(
        CypherQueryHelper.getObjectPartWithVersion("c", "Collection", params.hasName(), "v"),
        CypherQueryHelper.getReadableByQuery("c", username),
        CypherQueryHelper.getVersionHeadPart("v")
    );
    var it = session.query(Long.class, query, paramsMap).iterator();
    return it.hasNext() ? it.next() : 0L;
  }

  /** Returns the total number of non-deleted Collections (permission-agnostic). */
  public long countAll() {
    var result = session.query(
      Long.class,
      "MATCH (c:Collection) WHERE (c.deleted IS NULL OR c.deleted = false) RETURN COUNT(c)",
      Map.of()
    );
    var iter = result.iterator();
    return iter.hasNext() ? iter.next() : 0L;
  }

  /**
   * Returns Collections eligible for the Helmholtz Unhide feed — non-deleted and not
   * opted-out via {@code publishToHelmholtzKG=false} — ordered by {@code createdAt ASC,
   * appId ASC} with Cypher-level SKIP/LIMIT pagination. The filter+sort+pagination are
   * executed entirely in Neo4j; no Java-side filtering or subList() is needed.
   */
  public List<Collection> findForUnhideFeed(int skip, int limit) {
    String query =
      "MATCH (c:Collection) " +
      "WHERE c.deleted = false OR c.deleted IS NULL " +
      "OPTIONAL MATCH (c)-[:HAS_PROPERTIES]->(p:CollectionProperties) " +
      "WITH c, p " +
      "WHERE p IS NULL OR p.publishToHelmholtzKG <> false " +
      "ORDER BY c.createdAt ASC, c.appId ASC " +
      "SKIP $skip LIMIT $limit " +
      "RETURN c";
    var result = new ArrayList<Collection>();
    for (var col : findByQuery(query, Map.of("skip", skip, "limit", limit))) {
      result.add(col);
    }
    return result;
  }

  /** Returns the count of Collections eligible for the Helmholtz Unhide feed. */
  public long countForUnhideFeed() {
    String query =
      "MATCH (c:Collection) " +
      "WHERE c.deleted = false OR c.deleted IS NULL " +
      "OPTIONAL MATCH (c)-[:HAS_PROPERTIES]->(p:CollectionProperties) " +
      "WITH c, p " +
      "WHERE p IS NULL OR p.publishToHelmholtzKG <> false " +
      "RETURN count(c)";
    var it = session.query(Long.class, query, Map.of()).iterator();
    return it.hasNext() ? it.next() : 0L;
  }

  private boolean matchName(Collection col, String name) {
    return name == null || col.getName().equalsIgnoreCase(name);
  }
}
