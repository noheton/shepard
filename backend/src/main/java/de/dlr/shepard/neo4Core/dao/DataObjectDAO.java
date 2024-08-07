package de.dlr.shepard.neo4Core.dao;

import de.dlr.shepard.neo4Core.entities.DataObject;
import de.dlr.shepard.neo4Core.entities.User;
import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.CypherQueryHelper;
import de.dlr.shepard.util.QueryParamHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

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
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", params.getName());
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
    }
    String match =
      "MATCH (c:Collection)-[hdo:has_dataobject]->" +
      CypherQueryHelper.getObjectPart("d", "DataObject", params.hasName());
    String where = " WHERE ID(c)=" + collectionId;

    if (params.hasParentId()) {
      if (params.getParentId() == -1) {
        where += " AND NOT EXISTS((d)<-[:has_child]-(:DataObject {deleted: FALSE}))";
      } else {
        match += "<-[:has_child]-(parent:DataObject {deleted: FALSE})";
        where += " AND ID(parent)=" + params.getParentId();
      }
    }

    if (params.hasPredecessorId()) {
      if (params.getPredecessorId() == -1) {
        where += " AND NOT EXISTS((d)<-[:has_successor]-(:DataObject {deleted: FALSE}))";
      } else {
        match += "<-[:has_successor]-(predecessor:DataObject {deleted: FALSE})";
        where += " AND ID(predecessor)=" + params.getPredecessorId();
      }
    }
    if (params.hasSuccessorId()) {
      if (params.getSuccessorId() == -1) {
        where += " AND NOT EXISTS((d)-[:has_successor]->(:DataObject {deleted: FALSE}))";
      } else {
        match += "-[:has_successor]->(successor:DataObject {deleted: FALSE})";
        where += " AND ID(successor)=" + params.getSuccessorId();
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

  /**
   * Searches the database for DataObjects.
   *
   * @param collectionShepardId  identifies the Collection
   * @param paramsWithShepardIds encapsulates possible parameters
   * @return a List of DataObjects
   */
  public List<DataObject> findByCollectionByShepardIds(
    long collectionShepardId,
    QueryParamHelper paramsWithShepardIds
  ) {
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", paramsWithShepardIds.getName());
    if (paramsWithShepardIds.hasPagination()) {
      paramsMap.put("offset", paramsWithShepardIds.getPagination().getOffset());
      paramsMap.put("size", paramsWithShepardIds.getPagination().getSize());
    }
    String match =
      "MATCH (c:Collection)-[hdo:has_dataobject]->" +
      CypherQueryHelper.getObjectPart("d", "DataObject", paramsWithShepardIds.hasName());
    String where = " WHERE c." + Constants.SHEPARD_ID + "=" + collectionShepardId;

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
    String query = String.format(
      "MATCH (d:DataObject) WHERE ID(d) = %d OPTIONAL MATCH (d)-[:has_reference]->(r:BasicReference) " +
      "FOREACH (n in [d,r] | SET n.deleted = true)",
      id
    );
    var result = runQuery(query, Collections.emptyMap());
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
    String query = String.format(
      "MATCH (d:DataObject) WHERE ID(d) = %d OPTIONAL MATCH (d)-[:has_reference]->(r:BasicReference) " +
      "FOREACH (n in [d,r] | SET n.deleted = true)",
      dataObject.getId()
    );
    var result = runQuery(query, Collections.emptyMap());
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
