package de.dlr.shepard.v2.admin.krl.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.admin.krl.entities.KrlInterpreterConfigSingleton;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import org.neo4j.ogm.session.Session;

/**
 * KRL-CONFIG-1 — DAO for the singleton {@link KrlInterpreterConfigSingleton} node.
 *
 * <p>{@code @ApplicationScoped} rather than {@code @RequestScoped}
 * because the config is read on startup seed + admin REST + on every
 * interpret request to gate the sidecar call — all low-volume and the
 * OGM session is short-lived per call. The singleton invariant
 * (exactly one {@code :KrlInterpreterConfigSingleton} node) is held by:
 *
 * <ol>
 *   <li>The startup hook in
 *       {@code KrlInterpreterConfigService.seedIfNeeded()} creating the
 *       node when none exists.</li>
 *   <li>The {@code V99} migration adding the
 *       {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 *       duplicates fail at the database boundary.</li>
 * </ol>
 *
 * <p>Mirrors the {@code JupyterConfigDAO} / {@code SqlTimeseriesConfigDAO}
 * shape — per the pattern that all admin-config singletons follow.
 */
@ApplicationScoped
public class KrlInterpreterConfigDAO extends GenericDAO<KrlInterpreterConfigSingleton> {

  @Override
  public Class<KrlInterpreterConfigSingleton> getEntityType() {
    return KrlInterpreterConfigSingleton.class;
  }

  /**
   * Load the single {@link KrlInterpreterConfigSingleton} node, or
   * {@code null} if none has been seeded yet. Callers should treat
   * {@code null} as "config not yet initialised" and either seed the
   * default or fail-fast depending on their needs.
   *
   * <p>Refetches the OGM session on every call so that admin reads
   * succeed even when the bean was constructed before the
   * {@code SessionFactory} finished booting (a known startup-ordering
   * race when the bean's {@code GenericDAO} constructor cached a
   * {@code null} session). The runtime cost is one
   * {@code openSession()} per call — negligible for an admin endpoint
   * that fires at most a handful of times per interpret request.
   */
  public KrlInterpreterConfigSingleton findSingleton() {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      return null;
    }
    Collection<KrlInterpreterConfigSingleton> all =
        live.loadAll(KrlInterpreterConfigSingleton.class, 1);
    if (all == null || all.isEmpty()) {
      return null;
    }
    // V99 uniqueness constraint + service-layer seed guarantees at most
    // one row; multiple rows are a bug elsewhere — return the smallest
    // Neo4j id deterministically.
    return List.copyOf(all)
        .stream()
        .min(
            (a, b) ->
                Long.compare(
                    a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId()))
        .orElse(null);
  }

  /**
   * Override {@code createOrUpdate} to use a fresh OGM session for the
   * same reason {@link #findSingleton} does — the cached {@code this.session}
   * inherited from {@code GenericDAO} can be null when the bean was
   * constructed before the {@code SessionFactory} finished booting.
   *
   * <p>Mints an appId via the parent's path if absent, then saves.
   */
  @Override
  public KrlInterpreterConfigSingleton createOrUpdate(KrlInterpreterConfigSingleton entity) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException(
          "Neo4j session unavailable — cannot persist :KrlInterpreterConfigSingleton");
    }
    if (entity.getAppId() == null) {
      entity.setAppId(AppIdGenerator.next());
    }
    live.save(entity, 1);
    return entity;
  }
}
