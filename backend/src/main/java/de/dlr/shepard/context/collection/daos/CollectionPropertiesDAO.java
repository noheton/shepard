package de.dlr.shepard.context.collection.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.collection.entities.CollectionProperties;
import jakarta.enterprise.context.RequestScoped;
import java.util.Map;
import java.util.Optional;

/**
 * DAO for the {@link CollectionProperties} side-node. Designed in
 * {@code aidocs/58 §5} (CP1).
 *
 * <p>Lookup is keyed by the parent Collection's {@code appId} — that
 * is the stable handle post-L2 (per {@code aidocs/25}). The relationship
 * direction is {@code (:Collection)-[:HAS_PROPERTIES]->(:CollectionProperties)}.
 */
@RequestScoped
public class CollectionPropertiesDAO extends GenericDAO<CollectionProperties> {

  /**
   * Find the properties node attached to the Collection with the given
   * {@code appId}. Returns {@link Optional#empty()} on legacy Collections
   * that haven't been backfilled yet (the V16 migration handles that on
   * startup; this is the read-through fallback).
   */
  public Optional<CollectionProperties> findByCollectionAppId(String collectionAppId) {
    String cypher =
      "MATCH (:Collection {appId: $cAppId})-[:HAS_PROPERTIES]->(p:CollectionProperties) RETURN p LIMIT 1";
    var iter = findByQuery(cypher, Map.of("cAppId", collectionAppId));
    var it = iter.iterator();
    return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
  }

  /**
   * Idempotent: returns the existing {@link CollectionProperties} for
   * the given Collection or creates one with default fields.
   *
   * <p>The Cypher mints a UUID v4 server-side via Neo4j's
   * {@code randomUUID()} for greenfield rows; future writes that come
   * through Java will continue to use the application-side UUID v7
   * minted by {@code GenericDAO#createOrUpdate}.
   */
  public CollectionProperties ensureFor(String collectionAppId) {
    String cypher =
      "MATCH (c:Collection {appId: $cAppId}) " +
      "MERGE (c)-[:HAS_PROPERTIES]->(p:CollectionProperties) " +
      "ON CREATE SET p.appId = randomUUID(), p.webdavVisible = true " +
      "RETURN p";
    var iter = findByQuery(cypher, Map.of("cAppId", collectionAppId));
    var it = iter.iterator();
    if (it.hasNext()) return it.next();
    // Collection doesn't exist — the caller's bug, not ours.
    throw new IllegalArgumentException("No Collection found with appId=" + collectionAppId);
  }

  /**
   * Update the {@code webdavVisible} flag for the Collection's
   * properties node. Lazily ensures the node exists.
   */
  public CollectionProperties setWebdavVisible(String collectionAppId, boolean visible) {
    var p = ensureFor(collectionAppId);
    p.setWebdavVisible(visible);
    return createOrUpdate(p);
  }

  /**
   * Resolve a Collection's {@code appId} to its OGM-side numeric id —
   * the handle the legacy {@code PermissionsService} expects. Returns
   * {@link Optional#empty()} when no Collection matches.
   */
  public Optional<Long> findCollectionIdByAppId(String collectionAppId) {
    String cypher = "MATCH (c:Collection {appId: $cAppId}) RETURN id(c) AS id LIMIT 1";
    var result = session.query(cypher, Map.of("cAppId", collectionAppId));
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return Optional.empty();
    Object id = it.next().get("id");
    return id instanceof Number n ? Optional.of(n.longValue()) : Optional.empty();
  }

  @Override
  public Class<CollectionProperties> getEntityType() {
    return CollectionProperties.class;
  }
}
