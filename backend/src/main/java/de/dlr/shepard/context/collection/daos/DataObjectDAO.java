package de.dlr.shepard.context.collection.daos;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.version.daos.VersionableEntityDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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

}
