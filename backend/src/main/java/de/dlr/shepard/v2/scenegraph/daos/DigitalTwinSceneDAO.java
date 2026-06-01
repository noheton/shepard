package de.dlr.shepard.v2.scenegraph.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.Collections;
import org.neo4j.ogm.session.Session;

/**
 * DT1-PHASE-0 / DT1-DAO-FRESH-SESSION — DAO for {@link DigitalTwinScene}.
 *
 * <p>Overrides {@link #createOrUpdate} and {@link #findAll} to refetch
 * a live OGM session per call (CHOKE-03 / JupyterConfig pattern). The
 * {@code GenericDAO} constructor caches the OGM {@code Session} at bean
 * construction time; if the bean is built before the
 * {@code SessionFactory} finishes booting that cached reference is
 * {@code null} forever. Fetching via
 * {@link NeoConnector#getNeo4jSession()} on every call avoids the NPE
 * that would otherwise occur when {@code SCENEGRAPH-REST-1} (or any
 * plugin that boots before the factory) starts driving these DAOs.
 *
 * <p>Mirrors {@code JupyterConfigDAO}, {@code SqlTimeseriesConfigDAO},
 * and {@code InstanceRorConfigDAO}. See DT1-DAO-FRESH-SESSION in
 * {@code aidocs/16-dispatcher-backlog.md}.
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
   * Override {@code findAll} to use a fresh OGM session per call so
   * reads succeed even when the cached {@code this.session} inherited
   * from {@code GenericDAO} is null (startup-ordering race — CHOKE-03).
   * Returns an empty collection when the session factory is not yet
   * available (fail-soft per the registries rule).
   */
  @Override
  public Collection<DigitalTwinScene> findAll() {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      return Collections.emptyList();
    }
    return live.loadAll(DigitalTwinScene.class, DEPTH_ENTITY);
  }

  /**
   * Override {@code createOrUpdate} to use a fresh OGM session for the
   * same reason {@link #findAll} does — the cached {@code this.session}
   * inherited from {@code GenericDAO} can be null when the bean was
   * constructed before the {@code SessionFactory} finished booting.
   *
   * <p>Mints an appId via {@link AppIdGenerator#next()} if absent, then
   * saves at depth 1.
   *
   * @throws IllegalStateException if the Neo4j session factory is not
   *         yet available — a primary write cannot be deferred.
   */
  @Override
  public DigitalTwinScene createOrUpdate(DigitalTwinScene entity) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException(
        "Neo4j session unavailable — cannot persist :DigitalTwinScene"
      );
    }
    if (entity.getAppId() == null) {
      entity.setAppId(AppIdGenerator.next());
    }
    live.save(entity, 1);
    return entity;
  }
}
