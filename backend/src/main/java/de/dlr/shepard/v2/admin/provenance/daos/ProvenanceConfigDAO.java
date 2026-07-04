package de.dlr.shepard.v2.admin.provenance.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.admin.provenance.entities.ProvenanceConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import org.neo4j.ogm.session.Session;

/**
 * FTOGGLE-PROV-1 — DAO for the singleton {@link ProvenanceConfig} node.
 *
 * <p>Fresh OGM session per call to avoid startup-ordering race where the
 * cached session from {@code GenericDAO} is null (same fix as
 * {@code SqlTimeseriesConfigDAO}).
 */
@ApplicationScoped
public class ProvenanceConfigDAO extends GenericDAO<ProvenanceConfig> {

  @Override
  public Class<ProvenanceConfig> getEntityType() {
    return ProvenanceConfig.class;
  }

  /**
   * Load the single {@link ProvenanceConfig} node, or {@code null} if none
   * has been seeded yet.
   */
  public ProvenanceConfig findSingleton() {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      return null;
    }
    Collection<ProvenanceConfig> all = live.loadAll(ProvenanceConfig.class, 1);
    if (all == null || all.isEmpty()) {
      return null;
    }
    return List.copyOf(all)
      .stream()
      .min((a, b) -> Long.compare(
        a.getId() == null ? 0L : a.getId(),
        b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }

  @Override
  public ProvenanceConfig createOrUpdate(ProvenanceConfig entity) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException("Neo4j session unavailable — cannot persist :ProvenanceConfig");
    }
    if (entity.getAppId() == null) {
      entity.setAppId(AppIdGenerator.next());
    }
    live.save(entity, 1);
    return entity;
  }
}
