package de.dlr.shepard.v2.scenegraph.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * DT1-PHASE-0 / DT1-DAO-FRESH-SESSION — DAO for {@link DigitalTwinScene}.
 *
 * <p>{@code @ApplicationScoped} because the scene graph is read on every
 * SCENEGRAPH-REST-1 call and the OGM session is short-lived per call.
 *
 * <p><b>Fresh-session pattern (CHOKE-03 / JupyterConfig).</b>
 * {@code GenericDAO}'s constructor caches the OGM {@code Session} at bean
 * construction time, which can be {@code null} if the bean is built before
 * the {@code SessionFactory} finishes booting. All overrides in this class
 * call {@link NeoConnector#getNeo4jSession()} per call — the same pattern
 * {@code JupyterConfigDAO} uses — so callers never see a stale {@code null}
 * session regardless of boot ordering.
 */
@ApplicationScoped
public class DigitalTwinSceneDAO extends GenericDAO<DigitalTwinScene> {

  @Override
  public Class<DigitalTwinScene> getEntityType() {
    return DigitalTwinScene.class;
  }

  /**
   * Find a scene by its {@code appId}. Returns {@code null} if not found or
   * if the Neo4j session is unavailable.
   *
   * <p>Refetches the OGM session per call (CHOKE-03 / JupyterConfig pattern).
   */
  public DigitalTwinScene findByAppId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return null;
    Collection<DigitalTwinScene> matches = live.loadAll(
      DigitalTwinScene.class,
      new Filter("appId", ComparisonOperator.EQUALS, appId),
      DEPTH_ENTITY
    );
    if (matches == null || matches.isEmpty()) return null;
    return List.copyOf(matches)
      .stream()
      .min((a, b) -> Long.compare(
        a.getId() == null ? 0L : a.getId(),
        b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }

  /**
   * Override {@code createOrUpdate} to use a fresh OGM session per call.
   * Mints an {@code appId} when absent, then saves at depth 1.
   */
  @Override
  public DigitalTwinScene createOrUpdate(DigitalTwinScene entity) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException("Neo4j session unavailable — cannot persist :DigitalTwinScene");
    }
    if (entity.getAppId() == null) {
      entity.setAppId(AppIdGenerator.next());
    }
    live.save(entity, DEPTH_ENTITY);
    return entity;
  }
}
