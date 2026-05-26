package de.dlr.shepard.context.semantic.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

@RequestScoped
public class SemanticAnnotationDAO extends GenericDAO<SemanticAnnotation> {

  public List<SemanticAnnotation> findAllSemanticAnnotationsByNeo4jId(long entityId) {
    // L2c read-path swap: query by appId rather than the deprecated id() function.
    // Public method signature stays long for caller-compat until L2d.
    String query;
    query = "MATCH (e {appId: $entityAppId})-[ha:has_annotation]->(a:SemanticAnnotation) WITH a %s".formatted(
        CypherQueryHelper.getReturnPart("a", Neighborhood.OUTGOING)
      );
    var queryResult = findByQuery(query, Map.of("entityAppId", resolveAppIdOrEmpty(entityId)));
    var ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  public List<SemanticAnnotation> findAllSemanticAnnotationsByShepardId(long shepardId) {
    String query;
    query = String.format(
      "MATCH (e)-[ha:has_annotation]->(a:SemanticAnnotation) WHERE e." + Constants.SHEPARD_ID + "=%d WITH a %s",
      shepardId,
      CypherQueryHelper.getReturnPart("a", Neighborhood.OUTGOING)
    );
    var queryResult = findByQuery(query, Collections.emptyMap());
    var ret = StreamSupport.stream(queryResult.spliterator(), false).toList();
    return ret;
  }

  /**
   * SEMA-V6-006 — find a single annotation by its stable {@code appId}.
   *
   * @param appId the annotation's UUID v7 identifier
   * @return the matching entity, or {@code null} when none exists
   */
  public SemanticAnnotation findByAppId(String appId) {
    if (appId == null || appId.isBlank()) return null;
    Filter f = new Filter("appId", ComparisonOperator.EQUALS, appId);
    Collection<SemanticAnnotation> hits = session.loadAll(SemanticAnnotation.class, f, DEPTH_ENTITY);
    if (hits == null || hits.isEmpty()) return null;
    return hits.iterator().next();
  }

  /**
   * SEMA-V6-006 — all annotations whose {@code subjectAppId} equals the given value.
   * Used by {@code find_annotated} to retrieve the annotation set for an arbitrary entity.
   *
   * @param subjectAppId the entity's UUID v7 appId
   * @return list of annotations; empty when none found
   */
  public List<SemanticAnnotation> findBySubjectAppId(String subjectAppId) {
    if (subjectAppId == null || subjectAppId.isBlank()) return List.of();
    String query =
      "MATCH (a:SemanticAnnotation {subjectAppId: $sid}) WITH a " +
      CypherQueryHelper.getReturnPart("a", Neighborhood.OUTGOING);
    List<SemanticAnnotation> out = new ArrayList<>();
    for (SemanticAnnotation a : findByQuery(query, Map.of("sid", subjectAppId))) {
      out.add(a);
    }
    return out;
  }

  /**
   * SEMA-V6-006 — find annotations by predicate IRI + value (text or IRI),
   * optionally filtered by subject kind, with basic offset/limit pagination.
   * Used by {@code find_annotated}.
   *
   * @param predicateIri the property IRI to match (e.g. {@code "http://purl.org/dc/terms/type"})
   * @param objectValue  the value to match against {@code valueName} or {@code valueIRI}
   * @param subjectKind  optional subject-kind filter (e.g. {@code "DataObject"}); null = any
   * @param page         zero-based page index
   * @param size         page size (max results)
   * @return matching annotations, empty list when none found
   */
  public List<SemanticAnnotation> findByPredicateAndValue(
    String predicateIri,
    String objectValue,
    String subjectKind,
    int page,
    int size
  ) {
    if (predicateIri == null || predicateIri.isBlank()) return List.of();
    String kindClause = (subjectKind != null && !subjectKind.isBlank())
      ? " AND a.subjectKind = $subjectKind"
      : "";
    String query =
      "MATCH (a:SemanticAnnotation) " +
      "WHERE a.propertyIRI = $piri AND (a.valueName = $val OR a.valueIRI = $val)" +
      kindClause +
      " WITH a " +
      CypherQueryHelper.getReturnPart("a", Neighborhood.OUTGOING) +
      " SKIP $skip LIMIT $lim";
    Map<String, Object> params = new java.util.HashMap<>();
    params.put("piri", predicateIri);
    params.put("val", objectValue != null ? objectValue : "");
    params.put("skip", (long) (page * size));
    params.put("lim", (long) size);
    if (subjectKind != null && !subjectKind.isBlank()) {
      params.put("subjectKind", subjectKind);
    }
    List<SemanticAnnotation> out = new ArrayList<>();
    for (SemanticAnnotation a : findByQuery(query, params)) {
      out.add(a);
    }
    return out;
  }

  /**
   * SEMA-V6-006 — collect distinct {@code valueName} values used with a given
   * predicate IRI, optionally filtered by a case-insensitive prefix, ordered by
   * frequency DESC. Used by {@code search_values} for autocomplete.
   *
   * @param predicateIri the property IRI to scope the lookup
   * @param filter       optional prefix filter (case-insensitive); null = no filter
   * @param limit        maximum number of distinct values to return
   * @return list of distinct {@code valueName} strings; empty list when none found
   */
  public List<String> aggregateValuesForPredicate(String predicateIri, String filter, int limit) {
    if (predicateIri == null || predicateIri.isBlank()) return List.of();
    String filterClause = (filter != null && !filter.isBlank())
      ? " AND toLower(a.valueName) STARTS WITH toLower($filter)"
      : "";
    String query =
      "MATCH (a:SemanticAnnotation) " +
      "WHERE a.propertyIRI = $piri AND a.valueName IS NOT NULL" +
      filterClause +
      " RETURN a.valueName AS v, count(*) AS cnt " +
      "ORDER BY cnt DESC " +
      "LIMIT $lim";
    Map<String, Object> params = new java.util.HashMap<>();
    params.put("piri", predicateIri);
    params.put("lim", (long) limit);
    if (filter != null && !filter.isBlank()) {
      params.put("filter", filter);
    }
    List<String> out = new ArrayList<>();
    // Raw result query — session.query returns Result, not typed entities
    org.neo4j.ogm.model.Result result = session.query(query, params);
    for (Map<String, Object> row : result.queryResults()) {
      Object v = row.get("v");
      if (v != null) out.add(v.toString());
    }
    return out;
  }

  @Override
  public Class<SemanticAnnotation> getEntityType() {
    return SemanticAnnotation.class;
  }
}
