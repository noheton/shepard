package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.semantic.entities.Predicate;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

/**
 * SEMA-V6-002 — DAO for {@link Predicate} nodes.
 *
 * <p>Provides lookup by {@code appId}, by vocabulary scope, and
 * listing of all / required predicates.
 *
 * <p>The DAO is {@code @RequestScoped} consistent with other OGM DAOs
 * in this package.
 */
@RequestScoped
public class PredicateDAO extends GenericDAO<Predicate> {

  /**
   * Find a predicate by its stable {@code appId}, or {@code null} when none exists.
   *
   * @param appId the predicate's UUID v7 identifier
   * @return the matching entity, or {@code null}
   */
  public Predicate findByAppId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    Filter f = new Filter("appId", ComparisonOperator.EQUALS, appId);
    Collection<Predicate> hits = session.loadAll(Predicate.class, f, DEPTH_ENTITY);
    if (hits == null || hits.isEmpty()) return null;
    return hits.iterator().next();
  }

  /**
   * All predicates, ordered by {@code label} ASC (case-insensitive).
   *
   * @return list of all predicates; empty list when none exist
   */
  public List<Predicate> listAll() {
    Collection<Predicate> all = session.loadAll(Predicate.class, DEPTH_ENTITY);
    List<Predicate> out = new ArrayList<>(all == null ? List.of() : all);
    out.sort((a, b) -> {
      String al = a == null ? "" : (a.getLabel() == null ? "" : a.getLabel());
      String bl = b == null ? "" : (b.getLabel() == null ? "" : b.getLabel());
      return al.compareToIgnoreCase(bl);
    });
    return out;
  }

  /**
   * All predicates for a given vocabulary (by {@code vocabularyAppId}),
   * ordered by {@code label} ASC.
   *
   * @param vocabularyAppId the {@link de.dlr.shepard.context.semantic.entities.Vocabulary} appId
   * @return predicates scoped to that vocabulary; empty list when none found
   */
  public List<Predicate> listByVocabulary(String vocabularyAppId) {
    if (vocabularyAppId == null || vocabularyAppId.isBlank()) return List.of();
    String query =
      "MATCH (p:Predicate {vocabularyAppId: $vid}) " +
      "RETURN p ORDER BY p.label ASC";
    List<Predicate> out = new ArrayList<>();
    for (Predicate p : findByQuery(query, Map.of("vid", vocabularyAppId))) {
      out.add(p);
    }
    return out;
  }

  /**
   * All {@code required = true} predicates — the baseline for
   * {@code DataQualityRequirement ANNOTATION_REQUIRED} evaluation.
   *
   * @return required predicates ordered by {@code label} ASC
   */
  public List<Predicate> listRequired() {
    List<Predicate> all = listAll();
    List<Predicate> out = new ArrayList<>(all.size());
    for (Predicate p : all) {
      if (p != null && p.isRequired()) out.add(p);
    }
    return out;
  }

  @Override
  public Class<Predicate> getEntityType() {
    return Predicate.class;
  }
}
