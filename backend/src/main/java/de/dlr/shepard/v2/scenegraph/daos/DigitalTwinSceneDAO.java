package de.dlr.shepard.v2.scenegraph.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import jakarta.enterprise.context.ApplicationScoped;
import org.neo4j.ogm.session.Session;

/**
 * DT1-PHASE-0 — DAO for {@link DigitalTwinScene}.
 *
 * <p>Overrides {@code createOrUpdate} to acquire a fresh OGM session per
 * call via {@code NeoConnector.getInstance().getNeo4jSession()}, mirroring
 * the {@code JupyterConfigDAO} / {@code SqlTimeseriesConfigDAO} pattern
 * (CHOKE-03 fix, DT1-DAO-FRESH-SESSION). The {@code GenericDAO} constructor
 * caches the session at bean construction time, which can be {@code null} if
 * the bean is built before the {@code SessionFactory} finishes booting. The
 * scene-graph DAOs are called from the live {@code SCENEGRAPH-REST-1} REST
 * surface; using a fresh session per call prevents NPEs under load.
 *
 * <p>The {@code V95} migration adds the {@code REQUIRE n.appId IS UNIQUE}
 * constraint so accidental duplicates fail at the database boundary.
 */
@ApplicationScoped
public class DigitalTwinSceneDAO extends GenericDAO<DigitalTwinScene> {

  @Override
  public Class<DigitalTwinScene> getEntityType() {
    return DigitalTwinScene.class;
  }

  /**
   * Override {@code createOrUpdate} to use a fresh OGM session per call —
   * mirrors the {@code JupyterConfigDAO} fix for the CHOKE-03 startup-race
   * where the cached {@code this.session} can be {@code null} when the bean
   * is constructed before the {@code SessionFactory} finishes booting.
   *
   * <p>Null-guards the entity and mints an {@code appId} (UUID v7) via
   * {@link AppIdGenerator#next()} when absent before persisting.
   */
  @Override
  public DigitalTwinScene createOrUpdate(DigitalTwinScene entity) {
    if (entity == null) return null;
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException("Neo4j session unavailable — cannot persist :DigitalTwinScene");
    }
    if (entity.getAppId() == null) {
      entity.setAppId(AppIdGenerator.next());
    }
    live.save(entity, 1);
    return entity;
  }
}
