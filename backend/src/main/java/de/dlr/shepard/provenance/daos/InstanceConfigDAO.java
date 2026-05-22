package de.dlr.shepard.provenance.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.provenance.entities.InstanceConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;

/**
 * DAO for the {@link InstanceConfig} singleton. "Singleton" is
 * enforced at the service boundary
 * ({@code InstanceConfigService.current()}), not at the DAO — the
 * DAO is a thin OGM wrapper consistent with the rest of the
 * provenance package.
 *
 * <p>{@code @ApplicationScoped} (not {@code RequestScoped} like
 * {@link ActivityDAO}) because the singleton is read on the hot
 * path of every Activity write — the request-scoped lifecycle would
 * mean a fresh OGM session on every call.
 */
@ApplicationScoped
public class InstanceConfigDAO extends GenericDAO<InstanceConfig> {

  /**
   * Find the single {@link InstanceConfig} row if one exists.
   *
   * @return the singleton if persisted, else {@code null}.
   */
  public InstanceConfig findSingleton() {
    Collection<InstanceConfig> all = findAll();
    if (all == null || all.isEmpty()) return null;
    // The service layer guarantees "exactly one"; if a developer's
    // test setup leaks a second row, prefer the lowest-id one (the
    // first ever minted) so verification stays deterministic.
    return all.stream().min((a, b) -> Long.compare(a.getId(), b.getId())).orElse(null);
  }

  @Override
  public Class<InstanceConfig> getEntityType() {
    return InstanceConfig.class;
  }
}
