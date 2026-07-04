package de.dlr.shepard.v2.collection.daos;

import de.dlr.shepard.common.neo4j.NeoConnector;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import org.neo4j.ogm.session.Session;

/**
 * V2CONV-B4 — single-purpose DAO for the {@code :Collection.sceneGraphAppId}
 * scalar hero-view link.
 *
 * <p>Replaces {@code CollectionSceneGraphLinkDAO}. The bespoke scene-graph
 * subsystem dissolved into the generic MAPPING_RECIPE mechanism
 * (aidocs/platform/191 decision #2): a Collection's "hero scene-graph" is now a
 * pointer to a MAPPING_RECIPE {@code ShepardTemplate} (a "hero view") rather
 * than a {@code :DigitalTwinScene}. The stored scalar property name
 * ({@code sceneGraphAppId}) is kept unchanged so no Collection-property
 * migration is needed — only what the appId points <em>at</em> changes (a
 * template appId instead of a scene appId). Migration V111 clears the property
 * on Collections whose value pointed at a now-deleted scene.
 *
 * <p>Fresh-session-per-call pattern (CHOKE-03 / JupyterConfig) so this DAO works
 * regardless of when Quarkus instantiates the bean relative to the OGM
 * SessionFactory boot. The link is a scalar property — no OGM relationship edge.
 */
@ApplicationScoped
public class CollectionHeroViewLinkDAO {

  /** Resolve the linked hero-view (MAPPING_RECIPE template) appId for a Collection. */
  public Optional<String> findLinkedTemplateAppId(String collectionAppId) {
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

  /** Resolve a Collection {@code appId} to its OGM-side numeric id (for the permission gate). */
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

  /** Link / replace: set {@code :Collection.sceneGraphAppId} to {@code templateAppId}. */
  public boolean link(String collectionAppId, String templateAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return false;
    if (templateAppId == null || templateAppId.isBlank()) return false;
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return false;
    var result = live.query(
      "MATCH (c:Collection {appId: $cAppId}) SET c.sceneGraphAppId = $tAppId RETURN c.appId AS appId",
      Map.of("cAppId", collectionAppId, "tAppId", templateAppId)
    );
    if (result == null) return false;
    return result.queryResults().iterator().hasNext();
  }

  /** Unlink: clear the {@code :Collection.sceneGraphAppId} property. Idempotent. */
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
