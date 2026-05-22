package de.dlr.shepard.plugins.v1compat.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.plugins.v1compat.entities.LegacyV1Config;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

/**
 * V1COMPAT.0 — DAO for the singleton {@link LegacyV1Config} node.
 *
 * <p>{@code @ApplicationScoped} (rather than {@code @RequestScoped})
 * because the config is read on every {@code /shepard/api/...}
 * request through {@code LegacyV1GateFilter} and the OGM session is
 * short-lived per call. A small in-process cache lives in
 * {@code LegacyV1ConfigService} to keep the read path zero-DB on the
 * hot path; this DAO is the cache-miss / patch backend.
 *
 * <p>The singleton invariant (exactly one {@code :LegacyV1Config}
 * node) is held by:
 *
 * <ol>
 *   <li>The {@code V63__Bootstrap_legacy_v1_config.cypher} migration
 *       (idempotent {@code MERGE}) creating the node on first start.</li>
 *   <li>A future appId-uniqueness migration (mirrors V30 for
 *       {@code :UnhideConfig}); defence-in-depth at the DB boundary.</li>
 *   <li>The service-layer {@code seedIfNeeded()} double-check, so an
 *       admin who has deleted the migration row mid-flight still gets
 *       a working config on the next read.</li>
 * </ol>
 */
@ApplicationScoped
public class LegacyV1ConfigDAO extends GenericDAO<LegacyV1Config> {

  @Override
  public Class<LegacyV1Config> getEntityType() {
    return LegacyV1Config.class;
  }

  /**
   * Load the single {@link LegacyV1Config} node, or {@code null}
   * if none has been seeded yet. Callers should treat {@code null}
   * as "first-start, not yet seeded" and either seed the default or
   * treat the flag as enabled (the fail-open default for the v1
   * surface — operator hasn't opted out, so v1 stays on).
   */
  public LegacyV1Config findSingleton() {
    Collection<LegacyV1Config> all = findAll();
    if (all.isEmpty()) {
      return null;
    }
    // The Cypher migration + service-layer seed guarantees at most one
    // row; pick deterministically (smallest internal id) so two
    // accidental duplicates still produce the same observable answer.
    return all
      .stream()
      .min((a, b) -> Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }
}
