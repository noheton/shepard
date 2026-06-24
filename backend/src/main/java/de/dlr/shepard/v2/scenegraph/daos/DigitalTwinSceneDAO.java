package de.dlr.shepard.v2.scenegraph.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * DT1-PHASE-0 — DAO for {@link DigitalTwinScene}.
 *
 * <p>Plain {@code GenericDAO} subclass — no override of
 * {@code createOrUpdate}, so the inherited L2a appId-minting + Neo4j
 * save path applies unchanged. The {@code V95} migration adds the
 * {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 * duplicates fail at the database boundary.
 *
 * <p><b>Deferred (DT1-DAO-FRESH-SESSION).</b> {@code GenericDAO}'s
 * constructor caches the OGM {@code Session} at bean construction
 * time, which can be null if the bean is built before the
 * {@code SessionFactory} finishes booting (CHOKE-03 / JupyterConfig
 * pattern). This is acceptable for the scaffold because no startup
 * code path calls these DAOs; the first caller is the not-yet-shipped
 * {@code SCENEGRAPH-REST-1} REST resource, by which time the session
 * is live. If a later caller is added in a boot path, override
 * {@code createOrUpdate} / finder methods to refetch
 * {@code NeoConnector.getInstance().getNeo4jSession()} per call —
 * mirror {@code JupyterConfigDAO}.
 */
@ApplicationScoped
public class DigitalTwinSceneDAO extends GenericDAO<DigitalTwinScene> {

  @Override
  public Class<DigitalTwinScene> getEntityType() {
    return DigitalTwinScene.class;
  }
}
