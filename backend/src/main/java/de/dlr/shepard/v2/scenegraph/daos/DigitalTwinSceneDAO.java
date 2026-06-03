package de.dlr.shepard.v2.scenegraph.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import org.neo4j.ogm.session.Session;

/**
 * DT1-PHASE-0 / DT1-DAO-FRESH-SESSION — DAO for {@link DigitalTwinScene}.
 *
 * <p>Overrides {@code createOrUpdate} and {@code findAll} to use a live
 * OGM session fetched per call via
 * {@link NeoConnector#getNeo4jSession()} rather than the cached
 * {@code session} field inherited from {@code GenericDAO}. This
 * eliminates the CHOKE-03 null-session risk: the inherited field is
 * set at bean-construction time, which can precede the
 * {@code SessionFactory} finishing its boot, leaving the cached
 * reference {@code null} indefinitely.
 *
 * <p>Mirrors the canonical {@code JupyterConfigDAO} / {@code SqlTimeseriesConfigDAO}
 * / {@code InstanceRorConfigDAO} pattern (commits {@code 58b5b10fb} +
 * P10c regression) — fetch live, null-guard, mint, save.
 *
 * <p>The {@code V95} migration adds the
 * {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 * duplicates fail at the database boundary.
 */
@ApplicationScoped
public class DigitalTwinSceneDAO extends GenericDAO<DigitalTwinScene> {

  @Override
  public Class<DigitalTwinScene> getEntityType() {
    return DigitalTwinScene.class;
  }

  /**
   * Persist {@code entity} using a fresh OGM session fetched per call.
   *
   * <p>Mints an appId when absent (the L2a write seam), then saves at
   * depth 1. Throws {@link IllegalStateException} if the Neo4j session
   * factory has not yet booted (fail-fast rather than NPE).
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
    live.save(entity, 1);
    return entity;
  }

  /**
   * Load all {@link DigitalTwinScene} nodes using a live session.
   *
   * <p>Returns an empty collection when the session factory is not
   * yet available (fail-soft on reads).
   */
  @Override
  public Collection<DigitalTwinScene> findAll() {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      return java.util.Collections.emptyList();
    }
    return live.loadAll(DigitalTwinScene.class, DEPTH_ENTITY);
  }
}
