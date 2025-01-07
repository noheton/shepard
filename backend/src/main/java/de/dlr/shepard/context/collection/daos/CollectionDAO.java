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
    var query = String.format(
      "MATCH %s WHERE %s WITH c",
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
    String versionVariable = "v";
    String collectionVariable = "c";
    Map<String, Object> paramsMap = new HashMap<>();
    paramsMap.put("name", params.getName());
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
    }
    String query = String.format(
      "MATCH %s WHERE %s AND %s WITH %s",
      CypherQueryHelper.getObjectPartWithVersion(collectionVariable, "Collection", params.hasName(), versionVariable),
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
    query += " " + CypherQueryHelper.getReturnPart(collectionVariable);
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
    String query = String.format(
      """
      MATCH (c:Collection {shepardId:%d}) OPTIONAL MATCH (c)-[:has_dataobject]->(d:DataObject) \
      OPTIONAL MATCH (d)-[:has_reference]->(r:BasicReference) \
      FOREACH (n in [c,d,r] | SET n.deleted = true)""",
      shepardId
    );
    boolean result = runQuery(query, Collections.emptyMap());
    return result;
  }

  private boolean matchName(Collection col, String name) {
    return name == null || col.getName().equalsIgnoreCase(name);
  }
}
