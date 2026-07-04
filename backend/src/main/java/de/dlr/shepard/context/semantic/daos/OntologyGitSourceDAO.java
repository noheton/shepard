package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.context.semantic.entities.OntologyGitSource;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

/**
 * TPL5 — DAO for {@link OntologyGitSource} nodes.
 *
 * <p>Standard OGM DAO mirroring {@link UserOntologyBundleDAO}. Lookup
 * by {@code appId} is used by the REST layer for single-item GET /
 * DELETE; {@link #listAll()} is the scheduler's input to
 * {@code OntologyGitIngestService.ingestAll()}.
 *
 * <p>APISIMP-GIT-SOURCE-IN-MEMORY-PAGING: {@link #listPaged} and
 * {@link #count} push SKIP/LIMIT to the database, replacing the
 * in-memory slice previously done in the REST layer.
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
   * APISIMP-GIT-SOURCE-IN-MEMORY-PAGING — total count of all git sources.
   * Used by the REST layer to populate the {@code total} field of the
   * paged envelope without loading entity objects.
   */
  public long count() {
    String query = "MATCH (s:OntologyGitSource) RETURN count(s) AS c";
    var result = session.query(query, Map.of());
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return 0L;
    Object c = it.next().get("c");
    return c instanceof Number n ? n.longValue() : 0L;
  }

  /**
   * APISIMP-GIT-SOURCE-IN-MEMORY-PAGING — page of git sources, ordered by
   * {@code name} ASC, with SKIP/LIMIT pushed to the database.
   *
   * @param skip  number of rows to skip (= page * pageSize)
   * @param limit maximum rows to return (= pageSize)
   * @return ordered page; empty list when {@code skip} exceeds total
   */
  public List<OntologyGitSource> listPaged(long skip, int limit) {
    String query =
      "MATCH (s:OntologyGitSource) " +
      "RETURN s ORDER BY toLower(s.name) ASC " +
      "SKIP $skip LIMIT $limit";
    List<OntologyGitSource> out = new ArrayList<>();
    for (OntologyGitSource s : findByQuery(query, Map.of("skip", skip, "limit", limit))) {
      out.add(s);
    }
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
