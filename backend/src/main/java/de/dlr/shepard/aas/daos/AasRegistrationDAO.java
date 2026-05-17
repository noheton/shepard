package de.dlr.shepard.aas.daos;

import de.dlr.shepard.aas.entities.AasRegistration;
import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import jakarta.enterprise.context.RequestScoped;
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
}
