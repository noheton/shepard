package de.dlr.shepard.v2.admin.storage.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.admin.storage.entities.AutosweepConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import org.neo4j.ogm.session.Session;

/**
 * FTOGGLE-AUTOSWEEP-1 — DAO for the singleton {@link AutosweepConfig} node.
 *
 * <p>Fresh OGM session per call to avoid startup-ordering race where the
 * cached session from {@code GenericDAO} is null (same fix as
 * {@code SqlTimeseriesConfigDAO} and {@code ProvenanceConfigDAO}).
 */
@ApplicationScoped
public class AutosweepConfigDAO extends GenericDAO<AutosweepConfig> {

  @Override
  public Class<AutosweepConfig> getEntityType() {
    return AutosweepConfig.class;
  }

  /**
   * Load the single {@link AutosweepConfig} node, or {@code null} if none
   * has been seeded yet.
   */
  public AutosweepConfig findSingleton() {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      return null;
    }
    Collection<AutosweepConfig> all = live.loadAll(AutosweepConfig.class, 1);
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
  public AutosweepConfig createOrUpdate(AutosweepConfig entity) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException("Neo4j session unavailable — cannot persist :AutosweepConfig");
    }
    if (entity.getAppId() == null) {
      entity.setAppId(AppIdGenerator.next());
    }
    live.save(entity, 1);
    return entity;
  }
}
