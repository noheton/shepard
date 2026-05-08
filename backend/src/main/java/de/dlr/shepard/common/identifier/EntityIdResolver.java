package de.dlr.shepard.common.identifier;

import de.dlr.shepard.common.neo4j.NeoConnector;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.NotFoundException;
import java.util.HashMap;
import java.util.Map;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * Bidirectional translator between Neo4j-OGM's Long primary key and the
 * application-level {@code appId} (UUID v7) introduced in L2a.
 *
 * <p>Phase 3 of the Neo4j-ID migration (L2c) flips internal Cypher reads from
 * {@code WHERE ID(e) = $entityId} to {@code WHERE e.appId = $appId}. Internal
 * call seams still hold the OGM Long during this slice — the public API
 * surface keeps its long-id shape until L2d. This resolver bridges the two:
 * DAO methods that accept a long for caller-compat translate it to its
 * {@code appId} via {@link #resolveAppId(long)} and bind that string to
 * Cypher.
 *
 * <p>Lookups are <em>request-scoped</em> via the CDI {@code @RequestScoped}
 * lifecycle and additionally memoised within the bean instance so multiple
 * resolutions of the same id within one HTTP request reuse the result. The
 * V11 unique constraint on {@code appId} guarantees the at-most-one shape;
 * pinning to {@code LIMIT 1} is belt-and-braces. No fan-out — the resolver
 * runs at most twice per id per request (one per direction).
 *
 * <p>Per the design doc {@code aidocs/25 §3.3}, this resolver does <strong>not
 * </strong> change the permissions cache key shape; the cache stays keyed by
 * {@code long} during the L2 deprecation window. L2e flips the cache key.
 *
 * <p><strong>Note on the long → appId direction.</strong> The Cypher in
 * {@link #resolveAppId(long)} still calls {@code id(e)}. That is the function
 * we are migrating away from, but it is not yet removed by Neo4j (it is only
 * <em>deprecated</em>). Using it once at the edge to bootstrap the appId
 * lookup is acceptable; this is the last seam where we read the OGM Long out
 * of Neo4j directly. L2e drops the OGM Long in lockstep with retiring
 * Neo4j-OGM (issue #274), which removes this last call site.
 */
@RequestScoped
public class EntityIdResolver {

  /** Request-scoped memo for {@code long → appId}. */
  private final Map<Long, String> appIdByOgmId = new HashMap<>();

  /** Request-scoped memo for {@code appId → long}. */
  private final Map<String, Long> ogmIdByAppId = new HashMap<>();

  /**
   * Resolve the application-level identifier for an entity addressed by its
   * OGM Long id. Memoised request-scoped.
   *
   * @param ogmId the OGM-managed Long id (i.e. Neo4j's internal id)
   * @return the entity's {@code appId} (UUID v7 canonical 36-character form)
   * @throws NotFoundException if no entity with that id exists, or if the
   *   entity exists but does not yet have an {@code appId} (a pre-L2a row
   *   that the V12 backfill hasn't reached — should not happen post-L2b but
   *   we still fail closed rather than silently dropping the lookup)
   */
  public String resolveAppId(long ogmId) {
    String memo = appIdByOgmId.get(ogmId);
    if (memo != null) return memo;

    Session session = session();
    if (session == null) {
      throw new NotFoundException("Neo4j session not available; cannot resolve appId for id=" + ogmId);
    }
    String query = "MATCH (e) WHERE id(e) = $ogmId RETURN e.appId AS appId LIMIT 1";
    Result result = session.query(query, Map.of("ogmId", ogmId));
    var iter = result.iterator();
    if (!iter.hasNext()) {
      Log.debugf("EntityIdResolver: no node with id=%d", ogmId);
      throw new NotFoundException("No entity with Neo4j id " + ogmId);
    }
    Object appIdObj = iter.next().get("appId");
    if (appIdObj == null) {
      // Pre-L2a row that escaped the V12 backfill — shouldn't happen but
      // surface as a hard failure rather than silently mis-resolving.
      throw new NotFoundException("Entity with Neo4j id " + ogmId + " has no appId yet");
    }
    String appId = appIdObj.toString();
    appIdByOgmId.put(ogmId, appId);
    ogmIdByAppId.put(appId, ogmId);
    return appId;
  }

  /**
   * Resolve the OGM Long id for an entity addressed by its application-level
   * identifier. Memoised request-scoped.
   *
   * @param appId the entity's {@code appId} (UUID v7)
   * @return the OGM-managed Long id (Neo4j's internal id)
   * @throws NotFoundException if no entity with that appId exists
   */
  public long resolveLong(String appId) {
    if (appId == null) {
      throw new NotFoundException("appId must not be null");
    }
    Long memo = ogmIdByAppId.get(appId);
    if (memo != null) return memo;

    Session session = session();
    if (session == null) {
      throw new NotFoundException("Neo4j session not available; cannot resolve id for appId=" + appId);
    }
    // Single indexed lookup: V11 unique constraint guarantees at-most-one
    // node per appId across all in-scope labels. LIMIT 1 is belt-and-braces.
    String query = "MATCH (e {appId: $appId}) RETURN id(e) AS ogmId LIMIT 1";
    Result result = session.query(query, Map.of("appId", appId));
    var iter = result.iterator();
    if (!iter.hasNext()) {
      Log.debugf("EntityIdResolver: no node with appId=%s", appId);
      throw new NotFoundException("No entity with appId " + appId);
    }
    Object ogmIdObj = iter.next().get("ogmId");
    if (ogmIdObj == null) {
      throw new NotFoundException("Entity with appId " + appId + " has null id");
    }
    long ogmId = ((Number) ogmIdObj).longValue();
    appIdByOgmId.put(ogmId, appId);
    ogmIdByAppId.put(appId, ogmId);
    return ogmId;
  }

  /**
   * Test seam: in unit tests, an explicitly-constructed resolver can have its
   * memo populated directly without going through the Session. Production
   * callers always go through {@link #resolveAppId(long)} /
   * {@link #resolveLong(String)}.
   */
  public void primeForTesting(long ogmId, String appId) {
    appIdByOgmId.put(ogmId, appId);
    ogmIdByAppId.put(appId, ogmId);
  }

  /**
   * Indirection to keep the static {@link NeoConnector} singleton at one
   * point of entry. Subclassed in tests if direct mocking of the underlying
   * session becomes useful; today the {@link #primeForTesting(long, String)}
   * seam is sufficient.
   */
  protected Session session() {
    return NeoConnector.getInstance().getNeo4jSession();
  }
}
