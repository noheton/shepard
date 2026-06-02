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


  /**
   * M4I-d-3-followup — walk the multi-hop path
   * {@code DataObject → TimeseriesReference → TimeseriesContainer}
   * and for each channel (AnnotatableTimeseries node) that carries a
   * {@value Constants#TS_UNIT_PREDICATE} annotation, return the channel
   * label and the QUDT unit IRI.
   *
   * <p>The label is derived from the channel's
   * {@value Constants#TS_PREDICATE_MEASUREMENT} annotation when present;
   * falls back to the channel's {@code appId} so callers always get a
   * non-null label. Channels without a unit annotation are silently skipped
   * — the renderer must not emit an incomplete {@code m4i:NumericalVariable}.
   *
   * <p>Returns raw result rows (not typed entities) because the query
   * aggregates data from two annotation nodes rather than a single entity.
   * Each row has keys: {@code channelLabel} (String) and {@code unitIri}
   * (String, the QUDT IRI value from {@link SemanticAnnotation#getValueIRI()}).
   *
   * <p>The path uses Neo4j internal IDs for the
   * {@code AnnotatableTimeseries.containerId} property (a long equal to
   * {@code id(tc)}) because that is the FK written by
   * {@link de.dlr.shepard.context.semantic.entities.AnnotatableTimeseries}.
   *
   * @param dataObjectAppId the DataObject's UUID v7 appId
   * @return list of raw result maps; empty when the DataObject has no
   *         channels with unit annotations or when the hop encounters no
   *         matching nodes
   */
  public List<Map<String, Object>> findChannelUnitsByDataObjectAppId(String dataObjectAppId) {
    if (dataObjectAppId == null || dataObjectAppId.isBlank()) return List.of();
    String query =
      // Step 1: reach all TimeseriesContainers from the DataObject
      "MATCH (do:DataObject {appId: $doAppId})" +
      "-[:has_reference]->(tr:TimeseriesReference)" +
      "-[:is_in_container]->(tc:TimeseriesContainer) " +
      "WHERE NOT coalesce(tr.deleted, false) " +
      "WITH DISTINCT id(tc) AS tcNeo4jId " +
      // Step 2: for each container, find AnnotatableTimeseries nodes with a unit annotation
      "MATCH (at:AnnotatableTimeseries {containerId: tcNeo4jId})" +
      "-[:has_annotation]->(ua:SemanticAnnotation {propertyIRI: $unitPredicate}) " +
      "WHERE ua.valueIRI IS NOT NULL AND ua.valueIRI <> '' " +
      // Step 3: also grab the measurement-name annotation for the label (optional)
      "OPTIONAL MATCH (at)-[:has_annotation]->(na:SemanticAnnotation {propertyIRI: $measurementPredicate}) " +
      "RETURN COALESCE(na.valueName, at.appId) AS channelLabel, ua.valueIRI AS unitIri";
    Map<String, Object> params = Map.of(
      "doAppId", dataObjectAppId,
      "unitPredicate", Constants.TS_UNIT_PREDICATE,
      "measurementPredicate", Constants.TS_PREDICATE_MEASUREMENT
    );
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> row : session.query(query, params).queryResults()) {
      if (row.get("unitIri") != null) {
        out.add(Map.of(
          "channelLabel", row.getOrDefault("channelLabel", ""),
          "unitIri", row.get("unitIri")
        ));
      }
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
