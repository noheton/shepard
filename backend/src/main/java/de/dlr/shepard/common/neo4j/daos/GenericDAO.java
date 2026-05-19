package de.dlr.shepard.common.neo4j.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.context.version.entities.VersionableEntity;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.search.query.Neo4jQuery;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.common.util.TraversalRules;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

public abstract class GenericDAO<T> {

  protected static final int DEPTH_ENTITY = 1;

  protected Session session = null;

  @Inject
  protected EntityIdResolver entityIdResolver;

  protected GenericDAO() {
    session = NeoConnector.getInstance().getNeo4jSession();
  }

  /**
   * Resolve an OGM Long id to its appId for use in a Cypher parameter under
   * the L2c read-path swap. If no such node exists, returns a
   * guaranteed-non-matching sentinel so the surrounding filter Cypher returns
   * no rows — preserving the pre-L2c {@code WHERE ID(e)=$id} behaviour where
   * an unknown id meant "no match" rather than an exception.
   */
  protected String resolveAppIdOrEmpty(long ogmId) {
    try {
      return entityIdResolver.resolveAppId(ogmId);
    } catch (NotFoundException e) {
      // Guaranteed-no-match: the V11 unique constraint says no real appId is
      // the empty string.
      return "";
    }
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
   * Caution: Regarding the official documentation ids will be reused by neo4j.
   * @see <a href='https://neo4j.com/docs/ogm-manual/current/reference/#reference:annotating-entities:entity-identifier'>See official documentation for further information on the id issue</a>
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
   * Save an entity and all related entities.
   *
   * <p>If the entity implements {@link HasAppId} and its {@code appId} is still
   * {@code null}, a fresh UUID v7 is minted via {@link AppIdGenerator#next()}
   * before persistence. This is the L2a write-side seam of the Neo4j-ID
   * migration: every newly-created node-entity ships with a stable
   * application-level identifier without round-tripping the database. Existing
   * rows pre-dating L2a keep {@code appId == null} until L2b's backfill
   * migration runs.
   *
   * @param entity The entity to be saved
   * @return the saved entity
   */
  public T createOrUpdate(T entity) {
    if (entity instanceof HasAppId hasAppId && hasAppId.getAppId() == null) {
      hasAppId.setAppId(AppIdGenerator.next());
    }
    // V2a: increment the revision counter on every update. The discriminator is
    // shepardId (non-null once the entity is fully initialised after its first
    // save + shepardId assignment); a brand-new entity has shepardId == null on
    // its first createOrUpdate call and keeps revision = 1. Subsequent saves
    // (including the second phase of the two-step creation pattern in the
    // service layer) increment from there.
    //
    // Note: the standard two-phase create pattern (save-1 with shepardId==null,
    // assign shepardId, save-2 with shepardId!=null) results in revision=2 after
    // the full creation flow, because save-2 is indistinguishable from a true
    // update from this method's perspective. This is an accepted implementation
    // artifact; the invariant is monotonic increase, not a fixed starting value.
    if (entity instanceof VersionableEntity) {
      VersionableEntity ve = (VersionableEntity) entity;
      if (ve.getShepardId() != null) {
        ve.setRevision(ve.getRevision() + 1);
      }
    }
    session.save(entity, DEPTH_ENTITY);
    // Auto-assign shepardId for compiled plugins (e.g. git plugin) that issue only a single
    // createOrUpdate rather than the service-layer two-step pattern. Without this, the Neo4j
    // node is left with shepardId = NULL, causing NPE in BasicEntityIO.extractShepardIds.
    if (entity instanceof VersionableEntity ve && ve.getId() != null && ve.getShepardId() == null) {
      ve.setShepardId(ve.getId());
      session.save(ve, DEPTH_ENTITY);
    }
    return entity;
  }

  public void clearSession() {
    session.clear();
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

  public Neo4jQuery getSearchForReachableReferencesByNeo4jIdQuery(
    TraversalRules traversalRule,
    long collectionShepardId,
    long startShepardId,
    String userName
  ) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // OGM Longs (kept on the call signature for compat) are resolved to their
    // appIds via EntityIdResolver; the public method signature stays long
    // until L2d flips the public surface.
    String ret = "MATCH path = (col:Collection)-[:has_dataobject]->";
    ret += getTraversalRulesPath(traversalRule);
    ret += "-[hr:has_reference]->(r:" + getEntityType().getSimpleName() + ")";
    ret += getWithPart("ns", "ret");
    ret += " WHERE d.appId = $startAppId AND col.appId = $collectionAppId";
    ret += getReturnPart("ns", "ret", "col", userName);
    return new Neo4jQuery(
      ret,
      Map.of("startAppId", resolveAppIdOrEmpty(startShepardId), "collectionAppId", resolveAppIdOrEmpty(collectionShepardId))
    );
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

  public Neo4jQuery getSearchForReachableReferencesQuery(long collectionId, String userName) {
    // L2c read-path swap: query by appId (see sibling method for rationale).
    String ret = "MATCH path = (col:Collection)-[:has_dataobject]->(do:DataObject)";
    ret += "-[hr:has_reference]->(r:" + getEntityType().getSimpleName() + ")";
    ret += getWithPart("ns", "ret");
    ret += " WHERE col.appId = $collectionAppId";
    ret += getReturnPart("ns", "ret", "col", userName);
    return new Neo4jQuery(ret, Map.of("collectionAppId", resolveAppIdOrEmpty(collectionId)));
  }

  public String getSearchForReachableReferencesByShepardIdQuery(long collectionShepardId, String userName) {
    String ret = "MATCH path = (col:Collection)-[:has_dataobject]->(do:DataObject)";
    ret += "-[hr:has_reference]->(r:" + getEntityType().getSimpleName() + ")";
    ret += getWithPart("ns", "ret");
    ret += " WHERE col." + Constants.SHEPARD_ID + " = " + collectionShepardId;
    ret += getReturnPart("ns", "ret", "col", userName);
    return ret;
  }

  public Neo4jQuery getSearchForReachableReferencesQuery(long collectionId, long startId, String userName) {
    // L2c read-path swap: query by appId (see sibling method for rationale).
    String ret = "MATCH path = (col:Collection)-[:has_dataobject]->(d:DataObject)";
    ret += "-[hr:has_reference]->(r:" + getEntityType().getSimpleName() + ")";
    ret += getWithPart("ns", "ret");
    ret += " WHERE d.appId = $startAppId AND col.appId = $collectionAppId";
    ret += getReturnPart("ns", "ret", "col", userName);
    return new Neo4jQuery(
      ret,
      Map.of("startAppId", resolveAppIdOrEmpty(startId), "collectionAppId", resolveAppIdOrEmpty(collectionId))
    );
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

  public void deleteRelation(long fromId, long toId, String fromType, String toType, String relationName) {
    String query =
      "MATCH (a:%s {shepardId: %s})-[r:%s]->(b:%s {shepardId: %s}) DELETE r;".formatted(
          fromType,
          fromId,
          relationName,
          toType,
          toId
        );
    session.query(query, new HashMap<String, String>());
  }

  public abstract Class<T> getEntityType();
}
