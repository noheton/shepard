package de.dlr.shepard.v2.admin.instance.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.admin.instance.entities.InstanceRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

/**
 * FE-PROV-INSTANCE-REGISTRY — DAO for the singleton {@link InstanceRegistry} node.
 *
 * <p>The singleton invariant (exactly one {@code :InstanceRegistry} node) is
 * maintained by:
 *
 * <ol>
 *   <li>The startup hook in
 *       {@link de.dlr.shepard.v2.admin.instance.services.InstanceRegistryService#seedIfNeeded()}
 *       creating the node when none exists.</li>
 *   <li>The {@code V92} migration adding {@code REQUIRE n.appId IS UNIQUE}
 *       on {@code :InstanceRegistry}.</li>
 * </ol>
 *
 * <p>Mirrors the {@code InstanceRorConfigDAO} shape exactly — per the UH1a
 * pattern that all admin-config singletons follow.
 */
@ApplicationScoped
public class InstanceRegistryDAO extends GenericDAO<InstanceRegistry> {

  @Override
  public Class<InstanceRegistry> getEntityType() {
    return InstanceRegistry.class;
  }

  /**
   * Load the single {@link InstanceRegistry} node, or {@code null} if none
   * has been seeded yet. Callers should treat {@code null} as "config not
   * yet initialised" and seed the default via the service layer.
   */
  public InstanceRegistry findSingleton() {
    Collection<InstanceRegistry> all = findAll();
    if (all.isEmpty()) {
      return null;
    }
    // V91 uniqueness constraint + service-layer seed guarantees at most one
    // row; multiple rows are a bug — return the smallest Neo4j id
    // deterministically.
    return all
      .stream()
      .min((a, b) -> Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId()))
      .orElse(null);
  }
}
