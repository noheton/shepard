package de.dlr.shepard.plugins.aas.daos;

import de.dlr.shepard.plugins.aas.entities.AasRegistration;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

/**
 * AAS1-reg DAO for the {@link AasRegistration} outbox entity.
 *
 * <p>{@code @RequestScoped} because registration queries are low-frequency
 * (startup sync + per-write side-effect) and the OGM session is
 * short-lived per HTTP request.
 */
@RequestScoped
public class AasRegistrationDAO extends GenericDAO<AasRegistration> {

  @Override
  public Class<AasRegistration> getEntityType() {
    return AasRegistration.class;
  }

  /**
   * List all outbox rows for a specific registry (irrespective of status).
   * Used by {@code AasRegistryOutboxService} to decide which shells need
   * (re-)registration at a given target.
   */
  public List<AasRegistration> findByRegistryUrl(String registryUrl) {
    return List.copyOf(findMatching(new Filter("registryUrl", ComparisonOperator.EQUALS, registryUrl)));
  }

  /**
   * List every outbox row across all registries. Used by the admin listing
   * endpoint {@code GET /v2/admin/aas/registrations} (AAS1-reg Commit 3).
   */
  public List<AasRegistration> listAll() {
    return List.copyOf(findAll());
  }

  /**
   * Return the distinct registry URLs that have at least one
   * {@link AasRegistration.Status#SYNCED} row, i.e. registries where
   * registration has succeeded for ≥ 1 shell.
   *
   * <p>Used by {@code AasServerSelfDescriptionService.describe()} to
   * populate the {@code registryRegistrations} list in the well-known doc.
   */
  public List<String> distinctSyncedRegistryUrls() {
    var result = session.query(
      String.class,
      "MATCH (r:AasRegistration) WHERE r.status = 'SYNCED' RETURN DISTINCT r.registryUrl",
      Map.of()
    );
    return StreamSupport.stream(result.spliterator(), false).toList();
  }

  /**
   * Find the outbox row for a specific (shell, registry) pair.
   * Returns {@code null} when no row exists yet (not yet enqueued).
   *
   * <p>Used by {@code AasRegistryOutboxService.syncAll()} to decide
   * whether to create a new PENDING row or skip an already-tracked shell.
   */
  public AasRegistration findByShellAndRegistry(String shellAppId, String registryUrl) {
    var iter = findByQuery(
      "MATCH (r:AasRegistration) WHERE r.shellAppId = $shellAppId AND r.registryUrl = $registryUrl RETURN r",
      Map.of("shellAppId", shellAppId, "registryUrl", registryUrl)
    ).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /**
   * Return all PENDING or FAILED rows targeting the given registry URL.
   *
   * <p>Used by {@code AasRegistryOutboxService} to determine which shells
   * need a (re-)registration attempt.
   */
  public List<AasRegistration> listPendingOrFailed(String registryUrl) {
    List<AasRegistration> result = new ArrayList<>();
    findByQuery(
      "MATCH (r:AasRegistration) WHERE r.registryUrl = $registryUrl"
        + " AND r.status IN ['PENDING', 'FAILED'] RETURN r",
      Map.of("registryUrl", registryUrl)
    ).forEach(result::add);
    return result;
  }

  /**
   * Return the {@code appId}s of all non-deleted {@code :Collection} nodes.
   *
   * <p>Used by {@code AasRegistryOutboxService.syncAll()} to seed the
   * outbox on startup — one PENDING row per (collection, registry) pair
   * if no row exists yet.
   */
  public List<String> listNonDeletedCollectionAppIds() {
    var result = session.query(
      String.class,
      "MATCH (c:Collection)"
        + " WHERE (c.deleted IS NULL OR c.deleted = false)"
        + " AND c.appId IS NOT NULL RETURN c.appId",
      Map.of()
    );
    return StreamSupport.stream(result.spliterator(), false).toList();
  }
}
