package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.semantic.entities.OntologyGitSource;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

/**
 * TPL5 — DAO for {@link OntologyGitSource} nodes.
 *
 * <p>Standard OGM DAO mirroring {@link UserOntologyBundleDAO}. Lookup
 * by {@code appId} is used by the REST layer for single-item GET /
 * DELETE; {@link #listAll()} is the scheduler's input to
 * {@code OntologyGitIngestService.ingestAll()}.
 */
@RequestScoped
public class OntologyGitSourceDAO extends GenericDAO<OntologyGitSource> {

  /**
   * Find a single git source by its stable {@code appId}, or
   * {@code null} when none exists.
   */
  public OntologyGitSource findByAppId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    Filter f = new Filter("appId", ComparisonOperator.EQUALS, appId);
    Collection<OntologyGitSource> hits = session.loadAll(OntologyGitSource.class, f, DEPTH_ENTITY);
    if (hits == null || hits.isEmpty()) return null;
    return hits.iterator().next();
  }

  /**
   * All git sources, ordered by {@code name} ASC. Called by the
   * scheduler and the admin list endpoint.
   */
  public List<OntologyGitSource> listAll() {
    Collection<OntologyGitSource> all = session.loadAll(OntologyGitSource.class, DEPTH_ENTITY);
    List<OntologyGitSource> out = new ArrayList<>(all == null ? List.of() : all);
    out.sort((a, b) -> {
      String an = a == null ? "" : (a.getName() == null ? "" : a.getName());
      String bn = b == null ? "" : (b.getName() == null ? "" : b.getName());
      return an.compareToIgnoreCase(bn);
    });
    return out;
  }

  /**
   * All enabled git sources — the subset the nightly scheduler
   * actually runs.
   */
  public List<OntologyGitSource> listEnabled() {
    List<OntologyGitSource> all = listAll();
    List<OntologyGitSource> out = new ArrayList<>(all.size());
    for (OntologyGitSource s : all) {
      if (s != null && s.isEnabled()) out.add(s);
    }
    return out;
  }

  @Override
  public Class<OntologyGitSource> getEntityType() {
    return OntologyGitSource.class;
  }
}
