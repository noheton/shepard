package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.QueryParamHelper;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestScoped
public class SemanticRepositoryDAO extends GenericDAO<SemanticRepository> {

  public List<SemanticRepository> findAllSemanticRepositories(QueryParamHelper params) {
    Map<String, Object> paramsMap = new HashMap<>();
    if (params.hasName()) paramsMap.put("name", params.getName());
    var query =
      "MATCH %s WITH r".formatted(CypherQueryHelper.getObjectPart("r", "SemanticRepository", params.hasName()));
    if (params.hasOrderByAttribute()) {
      query += " " + CypherQueryHelper.getOrderByPart("r", params.getOrderByAttribute(), params.getOrderDesc());
    }
    if (params.hasPagination()) {
      paramsMap.put("offset", params.getPagination().getOffset());
      paramsMap.put("size", params.getPagination().getSize());
      query += " " + CypherQueryHelper.getPaginationPart();
    }
    query += " " + CypherQueryHelper.getReturnPart("r");
    var result = new ArrayList<SemanticRepository>();
    for (var rep : findByQuery(query, paramsMap)) {
      if (matchName(rep, params.getName())) {
        result.add(rep);
      }
    }

    return result;
  }

  /**
   * N1f — look up a {@link SemanticRepository} by its stable
   * application-level identifier ({@code appId}, UUID v7 minted at
   * creation by {@code GenericDAO#createOrUpdate}).
   *
   * <p>Returns {@code null} when no non-deleted repository with the given
   * {@code appId} exists (mirrors the pattern in
   * {@code TimeseriesReferenceDAO#findByAppId}).
   *
   * @param appId the UUID v7 identifier to look up
   * @return the matching repository or {@code null}
   */
  public SemanticRepository findByAppId(String appId) {
    String query =
      "MATCH " +
      CypherQueryHelper.getObjectPart("r", "SemanticRepository", false) +
      " WHERE r.appId = $appId " +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of("appId", appId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  /**
   * C3 / task #244 — resolve the bootstrapped {@code INTERNAL} repository.
   *
   * <p>The frontend SPARQL playground (N1f) defaults the repository
   * selector to the string {@code "internal"} — both because that's the
   * common-sense URL fragment for "talk to the in-process n10s store"
   * and because the bootstrapped {@code :SemanticRepository {type:
   * INTERNAL}} row (V49 migration) carries a freshly-minted UUID v7
   * appId that no user can be expected to memorise. Looking up by
   * {@code type = INTERNAL} is the right shape because the bootstrap
   * row is unique by type (V49 uses {@code MERGE ON
   * SemanticRepository {type: 'INTERNAL'}}).
   *
   * <p>Returns the bootstrapped INTERNAL repository, or {@code null} if
   * none exists (e.g. during a fresh install before the V49 migration
   * has run). Soft-deleted rows are excluded the same way
   * {@link #findByAppId(String)} excludes them.
   *
   * @return the singleton INTERNAL repository, or {@code null}
   */
  public SemanticRepository findInternal() {
    String query =
      "MATCH " +
      CypherQueryHelper.getObjectPart("r", "SemanticRepository", false) +
      " WHERE r.type = 'INTERNAL' " +
      CypherQueryHelper.getReturnPart("r");
    var iter = findByQuery(query, Map.of()).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  private boolean matchName(SemanticRepository rep, String name) {
    return name == null || rep.getName().equalsIgnoreCase(name);
  }

  @Override
  public Class<SemanticRepository> getEntityType() {
    return SemanticRepository.class;
  }
}
