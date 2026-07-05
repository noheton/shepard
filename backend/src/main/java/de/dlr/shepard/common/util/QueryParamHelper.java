package de.dlr.shepard.common.util;

import de.dlr.shepard.common.neo4j.endpoints.OrderByAttribute;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class QueryParamHelper {

  private String name;
  private String status;
  private PaginationHelper pagination;
  private Long parentId;
  private Long predecessorId;
  private Long successorId;
  private OrderByAttribute orderByAttribute;
  private Boolean orderDesc;
  /** COLL-TIMELINE-DRILLDOWN-FILTER-2: annotation predicate IRI for lane filter. */
  private String annotationFilterPredicateIri;
  /** COLL-TIMELINE-DRILLDOWN-FILTER-2: annotation literal value for lane filter. */
  private String annotationFilterValue;
  /** COLL-TIMELINE-DRILLDOWN-FILTER-1: inclusive lower bound (epoch-millis, Neo4j @DateLong). */
  private Long createdAfterMs;
  /** COLL-TIMELINE-DRILLDOWN-FILTER-1: exclusive upper bound (epoch-millis, Neo4j @DateLong). */
  private Long createdBeforeMs;

  public QueryParamHelper withName(String name) {
    this.name = name;
    return this;
  }

  public boolean hasName() {
    return this.name != null;
  }

  public QueryParamHelper withStatus(String status) {
    this.status = status;
    return this;
  }

  public boolean hasStatus() {
    return this.status != null;
  }

  public QueryParamHelper withPageAndSize(int page, int size) {
    this.pagination = new PaginationHelper(page, size);
    return this;
  }

  public boolean hasPagination() {
    return this.pagination != null;
  }

  public QueryParamHelper withPredecessorId(long predecessorId) {
    this.predecessorId = predecessorId;
    return this;
  }

  public boolean hasPredecessorId() {
    return this.predecessorId != null;
  }

  public QueryParamHelper withSuccessorId(long successorId) {
    this.successorId = successorId;
    return this;
  }

  public boolean hasSuccessorId() {
    return this.successorId != null;
  }

  public QueryParamHelper withParentId(long parentId) {
    this.parentId = parentId;
    return this;
  }

  public boolean hasParentId() {
    return this.parentId != null;
  }

  /**
   * SIDEBAR-LAZY-TREE — restrict the result to the direct children of the
   * DataObject identified by {@code parentShepardId}. This is the appId-native
   * {@code ?parentAppId=} list filter resolved to its shepardId at the resource
   * boundary; it reuses the existing {@code parentId} DAO Cypher path
   * ({@code <-[:has_child]-(parent {shepardId})}).
   *
   * @param parentShepardId the numeric shepardId of the parent DataObject
   *                        (resolved from the wire {@code parentAppId})
   * @return this (fluent)
   */
  public QueryParamHelper withParentShepardId(long parentShepardId) {
    this.parentId = parentShepardId;
    return this;
  }

  /**
   * SIDEBAR-LAZY-TREE — restrict the result to root (top-level) DataObjects:
   * those with no parent DataObject inside the Collection. Implemented via the
   * existing {@code parentId == -1} sentinel, which the DAO translates to
   * {@code NOT EXISTS((d)<-[:has_child]-(:DataObject {deleted: FALSE}))}.
   *
   * @return this (fluent)
   */
  public QueryParamHelper withTopLevelOnly() {
    this.parentId = -1L;
    return this;
  }

  /**
   * BUG-COLL-APPID-ROUTE-006-V2-LIST — restrict to DataObjects that are direct
   * successors of the DataObject identified by {@code predecessorShepardId}.
   * This is the appId-native {@code ?predecessorAppId=} list filter resolved to
   * its shepardId at the resource boundary; it reuses the existing
   * {@code predecessorId} DAO Cypher path
   * ({@code <-[:has_successor]-(predecessor {shepardId})}).
   *
   * @param predecessorShepardId the numeric shepardId of the predecessor DataObject
   *                             (resolved from the wire {@code predecessorAppId})
   * @return this (fluent)
   */
  public QueryParamHelper withPredecessorShepardId(long predecessorShepardId) {
    this.predecessorId = predecessorShepardId;
    return this;
  }

  /**
   * BUG-COLL-APPID-ROUTE-006-V2-LIST — restrict to DataObjects that are direct
   * predecessors of the DataObject identified by {@code successorShepardId}.
   * This is the appId-native {@code ?successorAppId=} list filter resolved to
   * its shepardId at the resource boundary; it reuses the existing
   * {@code successorId} DAO Cypher path
   * ({@code -[:has_successor]->(successor {shepardId})}).
   *
   * @param successorShepardId the numeric shepardId of the successor DataObject
   *                           (resolved from the wire {@code successorAppId})
   * @return this (fluent)
   */
  public QueryParamHelper withSuccessorShepardId(long successorShepardId) {
    this.successorId = successorShepardId;
    return this;
  }

  public QueryParamHelper withOrderByAttribute(OrderByAttribute orderBy, Boolean orderDesc) {
    this.orderByAttribute = orderBy;
    this.orderDesc = orderDesc;
    return this;
  }

  public boolean hasOrderByAttribute() {
    return this.orderByAttribute != null;
  }

  /**
   * COLL-TIMELINE-DRILLDOWN-FILTER-2: sets the annotation predicate/value pair.
   * Format: "predicateIri=value" — split on the first '=' character.
   * Silently ignores malformed input (no '=' or blank parts).
   */
  public QueryParamHelper withAnnotationFilter(String annotationFilter) {
    if (annotationFilter == null || annotationFilter.isBlank()) return this;
    int sep = annotationFilter.indexOf('=');
    if (sep < 1 || sep >= annotationFilter.length() - 1) return this;
    this.annotationFilterPredicateIri = annotationFilter.substring(0, sep);
    this.annotationFilterValue = annotationFilter.substring(sep + 1);
    return this;
  }

  public boolean hasAnnotationFilter() {
    return annotationFilterPredicateIri != null && annotationFilterValue != null;
  }

  public String getAnnotationFilterPredicateIri() {
    return annotationFilterPredicateIri;
  }

  public String getAnnotationFilterValue() {
    return annotationFilterValue;
  }

  /**
   * COLL-TIMELINE-DRILLDOWN-FILTER-1: sets the inclusive lower and exclusive
   * upper bounds for the {@code createdAt} window filter.
   * Both parameters are ISO-8601 instant strings (e.g. {@code 2024-06-02T00:00:00Z}).
   * Silently ignores malformed or null inputs — a half-parsed range is not
   * stored (caller must supply both valid instants for the filter to activate).
   */
  public QueryParamHelper withCreatedRange(String afterIso, String beforeIso) {
    if (afterIso == null || beforeIso == null) return this;
    try {
      this.createdAfterMs = Instant.parse(afterIso).toEpochMilli();
      this.createdBeforeMs = Instant.parse(beforeIso).toEpochMilli();
    } catch (DateTimeParseException e) {
      this.createdAfterMs = null;
      this.createdBeforeMs = null;
    }
    return this;
  }

  public boolean hasCreatedRange() {
    return createdAfterMs != null && createdBeforeMs != null;
  }
}
