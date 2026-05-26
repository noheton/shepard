package de.dlr.shepard.v2.annotations.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.util.CypherQueryHelper;
import de.dlr.shepard.common.util.CypherQueryHelper.Neighborhood;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import jakarta.enterprise.context.RequestScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * SEMA-V6-004 — DAO for the polymorphic {@code /v2/annotations/*} surface.
 *
 * <p>All query methods use the v6 columns ({@code subjectAppId}, {@code subjectKind},
 * {@code propertyIRI}) that were added by migrations V71/V72/V73.
 * Legacy annotations (pre-v6, {@code subjectAppId = null}) are not returned
 * by the v6 filter methods — they are served by the legacy
 * {@link de.dlr.shepard.context.semantic.daos.SemanticAnnotationDAO}.
 */
@RequestScoped
public class SemanticAnnotationV2DAO extends GenericDAO<SemanticAnnotation> {

  // ─── find by appId ─────────────────────────────────────────────────────────

  /**
   * Returns the annotation with the given {@code appId}, or {@code null} if none.
   */
  public SemanticAnnotation findByAnnotationAppId(String annotationAppId) {
    String query =
      "MATCH (a:SemanticAnnotation {appId: $appId}) WITH a " +
      CypherQueryHelper.getReturnPart("a", Neighborhood.OUTGOING);
    var iter = findByQuery(query, Map.of("appId", annotationAppId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }

  // ─── list with filters ─────────────────────────────────────────────────────

  /**
   * List annotations matching any combination of filters. All filter params are optional.
   *
   * @param subjectAppId  filter by subject appId (exact match)
   * @param subjectKind   filter by subject kind (exact match, e.g. "DataObject")
   * @param predicateIri  filter by predicate IRI (exact match)
   * @param vocabularyId  filter by vocabulary appId (exact match)
   * @param page          zero-based page index
   * @param pageSize      page size (1–200)
   */
  public List<SemanticAnnotation> findFiltered(
    String subjectAppId,
    String subjectKind,
    String predicateIri,
    String vocabularyId,
    int page,
    int pageSize
  ) {
    Map<String, Object> params = new HashMap<>();
    StringBuilder where = new StringBuilder("WHERE 1=1");

    if (subjectAppId != null && !subjectAppId.isBlank()) {
      where.append(" AND a.subjectAppId = $subjectAppId");
      params.put("subjectAppId", subjectAppId);
    }
    if (subjectKind != null && !subjectKind.isBlank()) {
      where.append(" AND a.subjectKind = $subjectKind");
      params.put("subjectKind", subjectKind);
    }
    if (predicateIri != null && !predicateIri.isBlank()) {
      where.append(" AND a.propertyIRI = $predicateIri");
      params.put("predicateIri", predicateIri);
    }
    if (vocabularyId != null && !vocabularyId.isBlank()) {
      where.append(" AND a.vocabularyId = $vocabularyId");
      params.put("vocabularyId", vocabularyId);
    }

    int offset = page * pageSize;
    String query =
      "MATCH (a:SemanticAnnotation) " +
      where +
      " WITH a SKIP " + offset + " LIMIT " + pageSize + " " +
      CypherQueryHelper.getReturnPart("a", Neighborhood.OUTGOING);

    return StreamSupport
      .stream(findByQuery(query, params).spliterator(), false)
      .toList();
  }

  // ─── text search ───────────────────────────────────────────────────────────

  /**
   * Simple case-insensitive text search over annotation value/predicate names.
   *
   * @param q          search text (matched against valueName, propertyName)
   * @param vocabularyId optional vocabulary filter
   * @param page       zero-based page index
   * @param pageSize   page size
   */
  public List<SemanticAnnotation> textSearch(String q, String vocabularyId, int page, int pageSize) {
    Map<String, Object> params = new HashMap<>();
    params.put("q", "(?i).*" + escapeRegex(q) + ".*");

    StringBuilder where = new StringBuilder(
      "WHERE (a.valueName =~ $q OR a.propertyName =~ $q OR a.valueIRI =~ $q OR a.propertyIRI =~ $q)"
    );
    if (vocabularyId != null && !vocabularyId.isBlank()) {
      where.append(" AND a.vocabularyId = $vocabularyId");
      params.put("vocabularyId", vocabularyId);
    }

    int offset = page * pageSize;
    String query =
      "MATCH (a:SemanticAnnotation) " +
      where +
      " WITH a SKIP " + offset + " LIMIT " + pageSize + " " +
      CypherQueryHelper.getReturnPart("a", Neighborhood.OUTGOING);

    return StreamSupport
      .stream(findByQuery(query, params).spliterator(), false)
      .toList();
  }

  // ─── helpers ───────────────────────────────────────────────────────────────

  /** Escape special Cypher regex chars to prevent injection via the text-search query. */
  private static String escapeRegex(String input) {
    if (input == null) return "";
    // Escape the regex metacharacters used in Cypher's =~ operator.
    return input.replaceAll("[\\\\\\[\\]{}()*+?^$|.]", "\\\\$0");
  }

  @Override
  public Class<SemanticAnnotation> getEntityType() {
    return SemanticAnnotation.class;
  }
}
