package de.dlr.shepard.v2.scenegraph.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import jakarta.enterprise.context.ApplicationScoped;
import org.neo4j.ogm.session.Session;

/**
 * DT1-DAO-FRESH-SESSION — DAO for {@link DigitalTwinScene}.
 *
 * <p>Overrides {@link #createOrUpdate} to refetch a live OGM session per
 * call rather than relying on the cached {@code this.session} field
 * inherited from {@code GenericDAO}. That field is set at bean construction
 * time (CHOKE-03 race): if the bean is constructed before the
 * {@code SessionFactory} finishes booting, the cached reference stays
 * {@code null} forever, and the first write from
 * {@code SCENEGRAPH-REST-1} (or any startup-path caller) would NPE.
 *
 * <p>Mirrors the {@code JupyterConfigDAO} fix (commit {@code 58b5b10fb})
 * and the {@code SqlTimeseriesConfigDAO} fix (P10c / TS-INGEST-222GB-CHOKE-03).
 *
 * <p>The {@code findAll} / {@code findByNeo4jId} paths inherited from
 * {@code GenericDAO} also use the cached session; they are not overridden
 * here because those finders are only called from the REST layer (which
 * runs well after startup) and the call-site cost of the CHOKE-03 race
 * is therefore low. Override them when a startup-path caller is added.
 */
@ApplicationScoped
public class DigitalTwinSceneDAO extends GenericDAO<DigitalTwinScene> {

  @Override
  public Class<DigitalTwinScene> getEntityType() {
    return DigitalTwinScene.class;
  }

  /**
   * Persist (create or update) a {@link DigitalTwinScene}, refetching a
   * live OGM session on every call to survive the CHOKE-03 startup race.
   *
   * @param entity the scene to persist; must not be {@code null}
   * @return the same entity instance (appId minted if it was null on entry)
   * @throws IllegalStateException if the Neo4j session is unavailable
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
}
