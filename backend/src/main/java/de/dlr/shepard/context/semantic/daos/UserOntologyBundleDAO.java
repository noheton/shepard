package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.semantic.entities.UserOntologyBundle;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

/**
 * N1c2 — DAO for operator-uploaded {@link UserOntologyBundle}
 * catalogue entries.
 *
 * <p>Lookups are by {@code bundleId} (the slug the operator picked
 * at upload); the V28 uniqueness constraint is on {@code appId},
 * but {@code bundleId} uniqueness is enforced at the service layer
 * in {@code OntologyConfigService.uploadBundle} via a duplicate-id
 * check before save.
 */
@RequestScoped
public class UserOntologyBundleDAO extends GenericDAO<UserOntologyBundle> {

  /**
   * Find a single user bundle by its {@code bundleId}, or
   * {@code null} when none exists. Used by the upload duplicate
   * check + the {@code DELETE} endpoint.
   */
  public UserOntologyBundle findByBundleId(String bundleId) {
    if (bundleId == null || bundleId.isBlank()) return null;
    Filter f = new Filter("bundleId", ComparisonOperator.EQUALS, bundleId);
    Collection<UserOntologyBundle> hits = session.loadAll(UserOntologyBundle.class, f, DEPTH_ENTITY);
    if (hits == null || hits.isEmpty()) return null;
    return hits.iterator().next();
  }

  /** All user bundles, snapshot. Stable ordering by {@code bundleId} ASC. */
  public List<UserOntologyBundle> listAll() {
    Collection<UserOntologyBundle> all = session.loadAll(UserOntologyBundle.class, DEPTH_ENTITY);
    List<UserOntologyBundle> out = new ArrayList<>(all == null ? List.of() : all);
    out.sort((a, b) -> {
      String ai = a == null ? "" : (a.getBundleId() == null ? "" : a.getBundleId());
      String bi = b == null ? "" : (b.getBundleId() == null ? "" : b.getBundleId());
      return ai.compareTo(bi);
    });
    return out;
  }

  @Override
  public Class<UserOntologyBundle> getEntityType() {
    return UserOntologyBundle.class;
  }
}
