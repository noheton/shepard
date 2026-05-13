package de.dlr.shepard.plugin;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PM1e — DAO for {@link PluginRuntimeOverride} rows.
 *
 * <p>Indexed by {@code pluginId} (V32 uniqueness constraint). The
 * registry seeds its in-memory cache from {@link #findAll()} at
 * startup and routes every PATCH through {@link #save(PluginRuntimeOverride)}
 * (when the override differs from the deploy-time default) or
 * {@link #deleteByPluginId(String)} (when the operator resets to
 * the default — keeps the table sparse).
 *
 * <p>The bean is {@code @ApplicationScoped} (not the more common
 * {@code @RequestScoped} for DAOs) because the
 * {@link PluginRegistry}'s startup-seed runs from an Arc lifecycle
 * event observer that doesn't establish a request scope. The
 * underlying OGM {@code Session} is per-bean rather than
 * per-request, but the registry only writes from the admin REST
 * (which IS request-scoped) and reads at startup once — no
 * concurrency hazard worth fighting CDI scoping for.
 */
@ApplicationScoped
public class PluginRuntimeOverrideDAO extends GenericDAO<PluginRuntimeOverride> {

  @Override
  public Class<PluginRuntimeOverride> getEntityType() {
    return PluginRuntimeOverride.class;
  }

  /**
   * Find the persisted override for the given plugin id, if any.
   * Returns {@link Optional#empty()} when no row exists (i.e. the
   * deploy-time default applies).
   *
   * <p>Implementation note: routes through
   * {@link GenericDAO#findByQuery(String, Map)} rather than the
   * OGM's {@code loadAll(class, filter, depth)} so the V32
   * uniqueness-constrained {@code pluginId} index is used directly
   * (cheap path; same posture as
   * {@code PublicationDAO#findByPid(String)} from KIP1a).
   */
  public Optional<PluginRuntimeOverride> findByPluginId(String pluginId) {
    if (pluginId == null || pluginId.isBlank()) {
      return Optional.empty();
    }
    String query =
      "MATCH (o:PluginRuntimeOverride {pluginId: $pluginId}) RETURN o LIMIT 1";
    Iterable<PluginRuntimeOverride> result = findByQuery(query, Map.of("pluginId", pluginId));
    var iter = result.iterator();
    if (!iter.hasNext()) {
      return Optional.empty();
    }
    return Optional.of(iter.next());
  }

  /**
   * Find every persisted override. Called once at startup by
   * {@link PluginRegistry} to seed its in-memory cache; never on a
   * hot path.
   */
  public List<PluginRuntimeOverride> findAllOverrides() {
    Collection<PluginRuntimeOverride> rows = findAll();
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    return new ArrayList<>(rows);
  }

  /**
   * Persist an override (insert if new, update in place if existing
   * via OGM's identity tracking). Mints the {@code appId} on first
   * save through {@link GenericDAO#createOrUpdate(Object)}.
   */
  public PluginRuntimeOverride save(PluginRuntimeOverride override) {
    if (override == null) {
      throw new IllegalArgumentException("override must not be null");
    }
    if (override.getPluginId() == null || override.getPluginId().isBlank()) {
      throw new IllegalArgumentException("override.pluginId must not be null/blank");
    }
    return createOrUpdate(override);
  }

  /**
   * Delete the override for a plugin id (no-op if no row exists).
   * Used when the admin resets a plugin to its deploy-time default
   * — keeping the table sparse means {@code findAllOverrides()} at
   * startup only carries rows that actually differ from the default.
   *
   * @return {@code true} iff a row was deleted, {@code false} if
   *         none existed.
   */
  public boolean deleteByPluginId(String pluginId) {
    if (pluginId == null || pluginId.isBlank()) {
      return false;
    }
    String query =
      "MATCH (o:PluginRuntimeOverride {pluginId: $pluginId}) DETACH DELETE o";
    return runQuery(query, Map.of("pluginId", pluginId));
  }
}
