package de.dlr.shepard.v2.krl.daos;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.krl.entities.KrlInterpreterConfigEntity;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import org.neo4j.ogm.session.Session;

/**
 * KRL-CONFIG-1 — DAO for the singleton {@link KrlInterpreterConfigEntity} node.
 *
 * <p>{@code @ApplicationScoped} rather than {@code @RequestScoped}
 * because the config is read on startup seed + admin REST + on every
 * sidecar call to resolve effective timeout and URL — all low-volume
 * and the OGM session is short-lived per call. The singleton invariant
 * (exactly one {@code :KrlInterpreterConfigEntity} node) is held by:
 *
 * <ol>
 *   <li>The startup hook in
 *       {@code KrlInterpreterConfigService.seedIfNeeded()} creating the
 *       node when none exists.</li>
 *   <li>The {@code V96} migration adding the
 *       {@code REQUIRE n.appId IS UNIQUE} constraint so accidental
 *       duplicates fail at the database boundary.</li>
 * </ol>
 *
 * <p>Mirrors the {@code JupyterConfigDAO} shape — per the J1e pattern
 * that all admin-config singletons follow.
 */
@ApplicationScoped
public class KrlInterpreterConfigDAO extends GenericDAO<KrlInterpreterConfigEntity> {

  @Override
  public Class<KrlInterpreterConfigEntity> getEntityType() {
    return KrlInterpreterConfigEntity.class;
  }

  /**
   * Load the single {@link KrlInterpreterConfigEntity} node, or
   * {@code null} if none has been seeded yet. Callers should treat
   * {@code null} as "config not yet initialised" and either seed the
   * default or fail-fast depending on their needs.
   *
   * <p>Refetches the OGM session on every call so that admin reads
   * succeed even when the bean was constructed before the
   * {@code SessionFactory} finished booting (CHOKE-03 pattern).
   */
  public KrlInterpreterConfigEntity findSingleton() {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      return null;
    }
    Collection<KrlInterpreterConfigEntity> all =
      live.loadAll(KrlInterpreterConfigEntity.class, 1);
    if (all == null || all.isEmpty()) {
      return null;
    }
    // V96 uniqueness constraint + service-layer seed guarantees at most
    // one row; multiple rows are a bug elsewhere — return the smallest
    // Neo4j id deterministically.
    return List.copyOf(all)
      .stream()
      .min((a, b) -> Long.compare(
        a.getId() == null ? 0L : a.getId(),
        b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }

  /**
   * Override {@code createOrUpdate} to use a fresh OGM session for the
   * same reason {@link #findSingleton} does — the cached
   * {@code this.session} inherited from {@code GenericDAO} can be null
   * when the bean was constructed before the {@code SessionFactory}
   * finished booting.
   *
   * <p>Mints an appId via {@link AppIdGenerator} if absent, then saves.
   */
  @Override
  public KrlInterpreterConfigEntity createOrUpdate(KrlInterpreterConfigEntity entity) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException(
        "Neo4j session unavailable — cannot persist :KrlInterpreterConfigEntity");
    }
    if (entity.getAppId() == null) {
      entity.setAppId(AppIdGenerator.next());
    }
    live.save(entity, 1);
    return entity;
  }
}
