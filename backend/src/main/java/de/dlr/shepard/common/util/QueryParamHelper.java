package de.dlr.shepard.common.util;

import de.dlr.shepard.common.neo4j.endpoints.OrderByAttribute;
import java.time.Instant;
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
  /** Epoch-millis lower bound for {@code d.createdAt} (inclusive). Null → no lower bound. */
  private Long createdAfterMs;
  /** Epoch-millis upper bound for {@code d.createdAt} (exclusive). Null → no upper bound. */
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

  public QueryParamHelper withOrderByAttribute(OrderByAttribute orderBy, Boolean orderDesc) {
    this.orderByAttribute = orderBy;
    this.orderDesc = orderDesc;
    return this;
  }

  public boolean hasOrderByAttribute() {
    return this.orderByAttribute != null;
  }

  /**
   * Set a creation-date range filter. Both strings must be ISO-8601 instants
   * (e.g. {@code "2024-06-15T00:00:00Z"}). Either may be null to suppress that
   * bound. Malformed strings are silently ignored (no bound is set).
   */
  public QueryParamHelper withCreatedRange(String afterIso, String beforeIso) {
    if (afterIso != null) {
      try { this.createdAfterMs = Instant.parse(afterIso).toEpochMilli(); }
      catch (Exception ignored) {}
    }
    if (beforeIso != null) {
      try { this.createdBeforeMs = Instant.parse(beforeIso).toEpochMilli(); }
      catch (Exception ignored) {}
    }
    return this;
  }

  public boolean hasCreatedRange() {
    return createdAfterMs != null || createdBeforeMs != null;
  }
}
