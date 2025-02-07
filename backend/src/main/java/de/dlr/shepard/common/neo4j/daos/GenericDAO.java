package de.dlr.shepard.common.neo4j.daos;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.query.Pagination;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.common.util.PaginationHelper;
import de.dlr.shepard.common.util.TraversalRules;
import io.quarkus.logging.Log;

public abstract class GenericDAO<T> {

  protected static final int DEPTH_ENTITY = 1;

  protected Session session = null;

  protected GenericDAO() {
    session = NeoConnector.getInstance().getNeo4jSession();
  }

  /**
   * Find all instances of a certain entity T
   *
   * @return an Iterable over the found entities
   */
  public Collection<T> findAll() {
    Collection<T> iter = session.loadAll(getEntityType(), DEPTH_ENTITY);
    return iter;
  }

  /**
   * Find all instances of a certain entity T
   *
   * @param page which page should be fetched
   * @return an Iterable over the found entities
   */
  public Collection<T> findAll(PaginationHelper page) {
    Collection<T> iter = session.loadAll(getEntityType(), new Pagination(page.getPage(), page.getSize()), DEPTH_ENTITY);
    return iter;
  }

  /**
   * Find the entity with the given id
   *
   * @param id The given id
   * @return The entity with the given id or null
   */
  public T findByNeo4jId(long id) {
    return session.load(getEntityType(), id, DEPTH_ENTITY);
  }

  /**
   * Find the entity with the given id without any related entities
   *
   * @param id The given id
   * @return The entity with the given id or null
   */
  public T findLightByNeo4jId(long id) {
    return session.load(getEntityType(), id, 0);
  }

  /**
   * Find entities matching the given filter
   *
   * @param filter The given filter
   * @return An iterable with the found entities
   */
  public Collection<T> findMatching(Filter filter) {
    return session.loadAll(getEntityType(), filter, DEPTH_ENTITY);
  }

  /**
   * Delete an entity
   *
   * @param id The entity to be deleted
   * @return Whether the deletion was successful or not
   */
  public boolean deleteByNeo4jId(long id) {
    T entity = session.load(getEntityType(), id);
    if (entity != null) {
      session.delete(entity);
      return true;
    }
    return false;
  }

  /**
   * Save an entity and all related entities
   *
   * @param entity The entity to be saved
   * @return the saved entity
   */
  public T createOrUpdate(T entity) {
    session.save(entity, DEPTH_ENTITY);
    return entity;
  }

  /**
   * CAUTION: The query runs against the database and is not checked. You can do
   * anything you want.
   *
   * @param query     The query
   * @param paramsMap Map of parameters
   * @return Iterable The result
   */
  public Iterable<T> findByQuery(String query, Map<String, Object> paramsMap) {
    Log.debugf("Run query: %s", query);
    StringBuilder str = new StringBuilder();
    for (var entry : paramsMap.entrySet()) {
      str.append("(" + entry.getKey() + ", " + entry.getValue() + "), ");
    }
    Log.debugf("queryParams: %s", str.toString());
    Iterable<T> iter = session.query(getEntityType(), query, paramsMap);
    return iter;
  }

  public boolean runQuery(String query, Map<String, Object> paramsMap) {
    Log.debugf("Run query: %s", query);
    Result result = session.query(query, paramsMap);
    return result.queryStatistics().containsUpdates();
  }

  public String getSearchForReachableReferencesByShepardIdQuery(
    TraversalRules traversalRule,
    long collectionShepardId,
    long startShepardId,
    String userName
  ) {
    String ret = "MATCH path = (col:Collection)-[:has_dataobject]->";
    ret += getTraversalRulesPath(traversalRule);
    ret += "-[hr:has_reference]->(r:" + getEntityType().getSimpleName() + ")";
    ret += getWithPart("ns", "ret");
    ret +=
      " WHERE d." +
      Constants.SHEPARD_ID +
      " = " +
      startShepardId +
      " AND col." +
      Constants.SHEPARD_ID +
      " = " +
      collectionShepardId;
    ret += getReturnPart("ns", "ret", "col", userName);
    return ret;
  }

  public String getSearchForReachableReferencesByNeo4jIdQuery(
    TraversalRules traversalRule,
    long collectionShepardId,
    long startShepardId,
    String userName
  ) {
    String ret = "MATCH path = (col:Collection)-[:has_dataobject]->";
    ret += getTraversalRulesPath(traversalRule);
    ret += "-[hr:has_reference]->(r:" + getEntityType().getSimpleName() + ")";
    ret += getWithPart("ns", "ret");
    ret += " WHERE id(d) = " + startShepardId + " AND id(col) = " + collectionShepardId;
    ret += getReturnPart("ns", "ret", "col", userName);
    return ret;
  }

  private String getTraversalRulesPath(TraversalRules traversalRule) {
    if (traversalRule == null) return "(d:DataObject)";
    return switch (traversalRule) {
      case children -> "(d:DataObject)-[:has_child*0..]->(e:DataObject)";
      case parents -> "(d:DataObject)<-[:has_child*0..]-(e:DataObject)";
      case successors -> "(d:DataObject)-[:has_successor*0..]->(e:DataObject)";
      case predecessors -> "(d:DataObject)<-[:has_successor*0..]-(e:DataObject)";
      default -> "(d:DataObject)";
    };
  }

  public String getSearchForReachableReferencesQuery(long collectionId, String userName) {
    String ret = "MATCH path = (col:Collection)-[:has_dataobject]->(do:DataObject)";
    ret += "-[hr:has_reference]->(r:" + getEntityType().getSimpleName() + ")";
    ret += getWithPart("ns", "ret");
    ret += " WHERE id(col) = " + collectionId;
    ret += getReturnPart("ns", "ret", "col", userName);
    return ret;
  }

  public String getSearchForReachableReferencesByShepardIdQuery(long collectionShepardId, String userName) {
    String ret = "MATCH path = (col:Collection)-[:has_dataobject]->(do:DataObject)";
    ret += "-[hr:has_reference]->(r:" + getEntityType().getSimpleName() + ")";
    ret += getWithPart("ns", "ret");
    ret += " WHERE col." + Constants.SHEPARD_ID + " = " + collectionShepardId;
    ret += getReturnPart("ns", "ret", "col", userName);
    return ret;
  }

  public String getSearchForReachableReferencesQuery(long collectionId, long startId, String userName) {
    String ret = "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)";
    ret += "-[hr:has_reference]->(r:" + getEntityType().getSimpleName() + ")";
    ret += getWithPart("ns", "ret");
    ret += " WHERE id(d) = " + startId + " AND id(col) = " + collectionId;
    ret += getReturnPart("ns", "ret", "col", userName);
    return ret;
  }

  public String getSearchForReachableReferencesByShepardIdQuery(
    long collectionShepardId,
    long startShepardId,
    String userName
  ) {
    String ret = "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)";
    ret += "-[hr:has_reference]->(r:" + getEntityType().getSimpleName() + ")";
    ret += getWithPart("ns", "ret");
    ret +=
      " WHERE d." +
      Constants.SHEPARD_ID +
      " = " +
      startShepardId +
      " AND col." +
      Constants.SHEPARD_ID +
      " = " +
      collectionShepardId;
    ret += getReturnPart("ns", "ret", "col", userName);
    return ret;
  }

  private String getWithPart(String nodesVar, String retVar) {
    return " WITH nodes(path) as " + nodesVar + ", r as " + retVar;
  }

  private String getReturnPart(String nodesVar, String retVar, String collectionVar, String username) {
    String ret = "";
    ret += " AND NONE(node IN " + nodesVar + " WHERE (node.deleted = TRUE))";
    ret += " AND " + CypherQueryHelper.getReadableByQuery(collectionVar, username);
    ret += " " + CypherQueryHelper.getReturnPart(retVar, Neighborhood.EVERYTHING);
    return ret;
  }

  /**
   * Deletes the has_child relation between the parent and the child in neo4j
   */
  public void deleteHasChildRelation(long parentShepardId, long childShepardId) {
    deleteRelation(parentShepardId, childShepardId, Constants.HAS_CHILD);
  }

  /**
   * Deletes the has_successor relation betweend the predecessor and the successor in neo4j
   */
  public void deleteHasSuccessorRelation(long predecessorShepardId, long successorShepardId) {
    deleteRelation(predecessorShepardId, successorShepardId, Constants.HAS_SUCCESSOR);
  }

  private void deleteRelation(long fromId, long toId, String relationName) {
    String query = String.format(
      "MATCH (a:%s {shepardId: %s})-[r:%s]->(b:%s {shepardId: %s}) DELETE r;",
      getEntityType().getSimpleName(),
      fromId,
      relationName,
      getEntityType().getSimpleName(),
      toId
    );
    session.query(query, new HashMap<String, String>());
  }

  public abstract Class<T> getEntityType();
}
