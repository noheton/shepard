package de.dlr.shepard.v2.admin.thermography.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.admin.thermography.entities.ThermographyConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.neo4j.ogm.session.Session;

/**
 * MFFD-NDT-ADMIN-CONFIG-1 — DAO for the singleton {@link ThermographyConfig} node.
 *
 * <p>{@code @ApplicationScoped} rather than {@code @RequestScoped}
 * because the config is read on startup seed + admin REST + on every
 * thermography analysis invocation to resolve effective defaults —
 * all low-volume and the OGM session is short-lived per call.
 * The singleton invariant (exactly one {@code :ThermographyConfig} node)
 * is held by:
 *
 * <ol>
 *   <li>The startup hook in
 *       {@code ThermographyConfigService.seedIfNeeded()} creating the
 *       node when none exists.</li>
 *   <li>The {@code V111} migration adding the
 *       {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 *       duplicates fail at the database boundary.</li>
 * </ol>
 *
 * <p>Mirrors the {@code JupyterConfigDAO} shape — per the J1e pattern
 * that all admin-config singletons follow.
 */
@ApplicationScoped
public class ThermographyConfigDAO extends GenericDAO<ThermographyConfig> {

  @Override
  public Class<ThermographyConfig> getEntityType() {
    return ThermographyConfig.class;
  }

  /**
   * Load the single {@link ThermographyConfig} node, returning
   * {@link Optional#empty()} when none has been seeded yet.
   *
   * <p>Refetches the OGM session on every call so that admin reads
   * succeed even when the bean was constructed before the
   * {@code SessionFactory} finished booting (a known startup-ordering
   * race when the bean's {@code GenericDAO} constructor cached a
   * {@code null} session). The runtime cost is one
   * {@code openSession()} per call — negligible for an admin endpoint
   * that fires at most a handful of times per analysis run.
   */
  public Optional<ThermographyConfig> findSingleton() {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      return Optional.empty();
    }
    Collection<ThermographyConfig> all = live.loadAll(ThermographyConfig.class, 1);
    if (all == null || all.isEmpty()) {
      return Optional.empty();
    }
    // V111 uniqueness constraint + service-layer seed guarantees at most
    // one row; multiple rows are a bug elsewhere — return the smallest
    // Neo4j id deterministically.
    return List.copyOf(all)
      .stream()
      .min((a, b) -> Long.compare(
        a.getId() == null ? 0L : a.getId(),
        b.getId() == null ? 0L : b.getId()));
  }

  /**
   * Override {@code createOrUpdate} to use a fresh OGM session for the
   * same reason {@link #findSingleton} does — the cached {@code this.session}
   * inherited from {@code GenericDAO} can be null when the bean was
   * constructed before the {@code SessionFactory} finished booting.
   *
   * <p>Mints an appId when absent, then saves.
   */
  @Override
  public ThermographyConfig createOrUpdate(ThermographyConfig entity) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException(
        "Neo4j session unavailable — cannot persist :ThermographyConfig");
    }
    if (entity.getAppId() == null) {
      entity.setAppId(AppIdGenerator.next());
    }
    live.save(entity, 1);
    return entity;
  }
}
