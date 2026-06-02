package de.dlr.shepard.v2.collection.daos;

import de.dlr.shepard.common.neo4j.NeoConnector;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import org.neo4j.ogm.session.Session;

/**
 * COLL-SCENE-1 — single-purpose DAO for the
 * {@code :Collection.sceneGraphAppId} scalar link.
 *
 * <p>Three operations on the {@code /v2/collections/{appId}/scene-graph}
 * resource: read the linked scene appId, write/replace the link, drop the
 * link. The link is stored as a scalar property on the Collection node
 * (additive nullable per the V102 migration note); no OGM relationship
 * edge is involved.
 *
 * <p>Fresh-session-per-call pattern (CHOKE-03 / JupyterConfig) so this
 * DAO works regardless of when Quarkus instantiates the bean relative to
 * the OGM SessionFactory boot.
 *
 * <p>The {@link #findCollectionIdByAppId(String)} helper exists for the
 * REST resource's permission gate — translating the {@code appId} into
 * the OGM-side Long {@code PermissionsService} consumes. It mirrors the
 * helper of the same name on {@code CollectionPropertiesDAO}.
 */
@ApplicationScoped
public class CollectionSceneGraphLinkDAO {

  /**
   * Resolve the linked scene appId for the Collection identified by
   * {@code collectionAppId}.
   *
   * <p>Returns {@link Optional#empty()} in two distinguishable cases:
   * Collection not found (the caller should 404 before this) and
   * Collection present but unlinked. The REST resource separates
   * those by first checking the Collection exists via
   * {@link #findCollectionIdByAppId(String)}.
   */
  public Optional<String> findLinkedSceneAppId(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return Optional.empty();
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return Optional.empty();
    var result = live.query(
      "MATCH (c:Collection {appId: $cAppId}) RETURN c.sceneGraphAppId AS sg LIMIT 1",
      Map.of("cAppId", collectionAppId)
    );
    if (result == null) return Optional.empty();
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return Optional.empty();
    Object sg = it.next().get("sg");
    if (sg == null) return Optional.empty();
    String s = sg.toString();
    return s.isBlank() ? Optional.empty() : Optional.of(s);
  }

  /**
   * Resolve a Collection's {@code appId} to its OGM-side numeric id —
   * the handle the legacy {@code PermissionsService} expects. Returns
   * {@link Optional#empty()} when no Collection matches.
   *
   * <p>Mirrors the same-named helper on
   * {@code CollectionPropertiesDAO} — both ride on the
   * {@code :Collection {appId: ...}} index for O(1) lookup.
   */
  public Optional<Long> findCollectionIdByAppId(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return Optional.empty();
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return Optional.empty();
    var result = live.query(
      "MATCH (c:Collection {appId: $cAppId}) RETURN id(c) AS id LIMIT 1",
      Map.of("cAppId", collectionAppId)
    );
    if (result == null) return Optional.empty();
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return Optional.empty();
    Object id = it.next().get("id");
    return id instanceof Number n ? Optional.of(n.longValue()) : Optional.empty();
  }

  /**
   * Link / replace: set {@code :Collection.sceneGraphAppId} to
   * {@code sceneAppId}. Returns {@code true} when the Collection
   * existed and the write committed; {@code false} when no Collection
   * matched (in which case the REST resource has a logic bug — it
   * should have 404'd before this call).
   */
  public boolean link(String collectionAppId, String sceneAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return false;
    if (sceneAppId == null || sceneAppId.isBlank()) return false;
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return false;
    var result = live.query(
      "MATCH (c:Collection {appId: $cAppId}) SET c.sceneGraphAppId = $sgAppId RETURN c.appId AS appId",
      Map.of("cAppId", collectionAppId, "sgAppId", sceneAppId)
    );
    if (result == null) return false;
    return result.queryResults().iterator().hasNext();
  }

  /**
   * Unlink: clear the {@code :Collection.sceneGraphAppId} property.
   * Idempotent — re-running on an already-unlinked Collection succeeds
   * silently. Returns {@code true} when the Collection existed and the
   * write committed; {@code false} when no Collection matched.
   *
   * <p>This does NOT delete the {@code :DigitalTwinScene} entity —
   * it only drops the back-pointer on the Collection side. Scene
   * deletion goes through {@code SceneGraphService} on the
   * {@code /v2/scene-graphs/} surface (and isn't exposed there today).
   */
  public boolean unlink(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return false;
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return false;
    var result = live.query(
      "MATCH (c:Collection {appId: $cAppId}) REMOVE c.sceneGraphAppId RETURN c.appId AS appId",
      Map.of("cAppId", collectionAppId)
    );
    if (result == null) return false;
    return result.queryResults().iterator().hasNext();
  }
}
