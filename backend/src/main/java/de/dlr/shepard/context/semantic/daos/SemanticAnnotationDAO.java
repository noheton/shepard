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

  /**
   * IMP1 — collect all distinct {@link SemanticAnnotation} nodes attached to
   * non-deleted {@code DataObject} nodes that belong to the given Collection.
   *
   * <p>The query uses the graph-edge pattern
   * {@code (Collection)-[:has_dataobject]->(DataObject)-[:has_annotation]->(SemanticAnnotation)}
   * and returns {@code DISTINCT} annotations to avoid duplicates when multiple
   * DataObjects share the same annotation node (e.g. after the V69 backfill).
   *
   * <p>Permission enforcement is the caller's responsibility — the collection
   * scope already constrains the result set to annotations the caller has Read
   * access to (they passed the permission check in {@code ImportV2Rest}).
   *
   * @param collectionAppId the {@code appId} of the target Collection
   * @return distinct annotations on DataObjects in this Collection; empty list when none found
   */
  public List<SemanticAnnotation> findByCollectionAppId(String collectionAppId) {
    if (collectionAppId == null || collectionAppId.isBlank()) return List.of();
    String query =
      "MATCH (c:Collection {appId: $cAppId})-[:has_dataobject]->(d:DataObject)-[:has_annotation]->(a:SemanticAnnotation) " +
      "WHERE (d.deleted IS NULL OR d.deleted = false) " +
      "WITH DISTINCT a " +
      CypherQueryHelper.getReturnPart("a", Neighborhood.OUTGOING);
    List<SemanticAnnotation> out = new ArrayList<>();
    for (SemanticAnnotation a : findByQuery(query, Map.of("cAppId", collectionAppId))) {
      out.add(a);
    }
    return out;
  }


  // ── SEMA-V6-PRED-UI: per-predicate usage statistics ─────────────────────

  /**
   * SEMA-V6-PRED-UI — total {@link SemanticAnnotation} rows whose
   * {@code propertyIRI} equals the given IRI (exact match, no prefix walk).
   *
   * @param predicateIri the property IRI to count
   * @return number of matching annotations; 0 when the IRI is blank or unused
   */
  public long countByPredicate(String predicateIri) {
    if (predicateIri == null || predicateIri.isBlank()) return 0L;
    String query = "MATCH (a:SemanticAnnotation) WHERE a.propertyIRI = $piri RETURN count(a) AS cnt";
    org.neo4j.ogm.model.Result result = session.query(query, Map.of("piri", predicateIri));
    for (Map<String, Object> row : result.queryResults()) {
      Object cnt = row.get("cnt");
      if (cnt instanceof Number n) return n.longValue();
    }
    return 0L;
  }

  /**
   * SEMA-V6-PRED-UI — most-used object values for a predicate, ordered by
   * descending frequency. Groups on the {@code (valueIRI, valueName)} pair so
   * literal-valued and IRI-valued annotations each aggregate sensibly.
   *
   * <p>Each returned map carries three keys:
   * <ul>
   *   <li>{@code objectIri} — the {@code valueIRI} (null when the annotation is literal-only).</li>
   *   <li>{@code objectLabel} — the {@code valueName} (may be null for IRI-only).</li>
   *   <li>{@code count} — number of annotations carrying this pair.</li>
   * </ul>
   *
   * @param predicateIri the property IRI to scope the lookup
   * @param limit        maximum number of distinct value rows to return
   * @return list of {@code {objectIri, objectLabel, count}} maps; empty list when none found
   */
  public List<Map<String, Object>> topValuesForPredicate(String predicateIri, int limit) {
    if (predicateIri == null || predicateIri.isBlank()) return List.of();
    int effectiveLimit = Math.max(1, limit);
    String query =
      "MATCH (a:SemanticAnnotation) WHERE a.propertyIRI = $piri " +
      "WITH coalesce(a.valueIRI, '') AS iri, coalesce(a.valueName, '') AS label " +
      "RETURN iri AS objectIri, label AS objectLabel, count(*) AS cnt " +
      "ORDER BY cnt DESC, label ASC " +
      "LIMIT $lim";
    Map<String, Object> params = new java.util.HashMap<>();
    params.put("piri", predicateIri);
    params.put("lim", (long) effectiveLimit);
    List<Map<String, Object>> out = new ArrayList<>();
    org.neo4j.ogm.model.Result result = session.query(query, params);
    for (Map<String, Object> row : result.queryResults()) {
      Map<String, Object> shaped = new java.util.LinkedHashMap<>();
      Object iri = row.get("objectIri");
      Object label = row.get("objectLabel");
      Object cnt = row.get("cnt");
      shaped.put("objectIri", (iri == null || iri.toString().isEmpty()) ? null : iri.toString());
      shaped.put("objectLabel", (label == null || label.toString().isEmpty()) ? null : label.toString());
      shaped.put("count", cnt instanceof Number n ? n.longValue() : 0L);
      out.add(shaped);
    }
    return out;
  }

  /**
   * SEMA-V6-PRED-UI — sample entities annotated with a given predicate.
   *
   * <p>Walks {@code :SemanticAnnotation.subjectAppId} back to the entity node
   * so the result spans every label kind (DataObject, FileReference,
   * Collection, AnnotatableTimeseries, …). The entity type is read from
   * Neo4j {@code labels(e)} with the legacy
   * {@link SemanticAnnotation#getSubjectKind() subjectKind} property as
   * fallback when no entity node resolves (orphaned annotation).
   *
   * @param predicateIri the property IRI to scope the lookup
   * @param limit        maximum sample entities to return
   * @return list of {@code {appId, name, type}} maps; empty when none found
   */
  public List<Map<String, Object>> sampleEntitiesForPredicate(String predicateIri, int limit) {
    if (predicateIri == null || predicateIri.isBlank()) return List.of();
    int effectiveLimit = Math.max(1, limit);
    String query =
      "MATCH (a:SemanticAnnotation) WHERE a.propertyIRI = $piri AND a.subjectAppId IS NOT NULL " +
      "WITH DISTINCT a.subjectAppId AS appId, a.subjectKind AS legacyKind " +
      "LIMIT $lim " +
      "OPTIONAL MATCH (e {appId: appId}) " +
      "RETURN appId AS appId, " +
      "       coalesce(e.name, e.label) AS name, " +
      "       coalesce(head(labels(e)), legacyKind) AS type";
    Map<String, Object> params = new java.util.HashMap<>();
    params.put("piri", predicateIri);
    params.put("lim", (long) effectiveLimit);
    List<Map<String, Object>> out = new ArrayList<>();
    org.neo4j.ogm.model.Result result = session.query(query, params);
    for (Map<String, Object> row : result.queryResults()) {
      Map<String, Object> shaped = new java.util.LinkedHashMap<>();
      Object appId = row.get("appId");
      Object name = row.get("name");
      Object type = row.get("type");
      if (appId == null) continue;
      shaped.put("appId", appId.toString());
      shaped.put("name", name == null ? null : name.toString());
      shaped.put("type", type == null ? null : type.toString());
      out.add(shaped);
    }
    return out;
  }

  // N1l — paginated load for the snapshot-refresh job
  public List<SemanticAnnotation> findPaginated(int skip, int limit) {
    String query =
        "MATCH (a:SemanticAnnotation) WITH a "
        + CypherQueryHelper.getReturnPart("a", Neighborhood.OUTGOING)
        + " SKIP $skip LIMIT $lim";
    Map<String, Object> params = new java.util.HashMap<>();
    params.put("skip", (long) skip);
    params.put("lim", (long) limit);
    List<SemanticAnnotation> out = new ArrayList<>();
    for (SemanticAnnotation a : findByQuery(query, params)) {
      out.add(a);
    }
    return out;
  }

  @Override
  public Class<SemanticAnnotation> getEntityType() {
    return SemanticAnnotation.class;
  }
}
