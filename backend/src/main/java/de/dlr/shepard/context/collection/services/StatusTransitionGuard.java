package de.dlr.shepard.context.collection.services;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import java.util.Map;
import java.util.Set;

/**
 * MFG5 — closed-enum guard for DataObject lifecycle status.
 *
 * <p>Valid statuses: {@code DRAFT}, {@code IN_REVIEW}, {@code READY},
 * {@code PUBLISHED}, {@code ARCHIVED}. {@code null} is always valid
 * (nullable field, may not be declared yet).
 *
 * <p>Forbidden downgrade transitions (current → proposed):
 * <ul>
 *   <li>{@code PUBLISHED} → {@code DRAFT} or {@code IN_REVIEW}
 *   <li>{@code ARCHIVED} → anything (terminal state)
 * </ul>
 *
 * <p>Same-state transitions (e.g. {@code PUBLISHED → PUBLISHED}) are allowed
 * so that a full-replace PUT that re-sends the current status is idempotent.
 *
 * <p>Source: {@code aidocs/agent-findings/manufacturing-quality.md §11.2} (MFG5).
 */
class StatusTransitionGuard {

  /** The closed set of acceptable status values. */
  static final Set<String> VALID_STATUSES = Set.of(
    "DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED"
  );

  /**
   * Statuses that may NOT be reached from the given current status.
   * Only populated for terminal / constrained states; absent key means
   * all forward moves are permitted.
   */
  static final Map<String, Set<String>> FORBIDDEN_TRANSITIONS = Map.of(
    "PUBLISHED", Set.of("DRAFT", "IN_REVIEW"),
    "ARCHIVED",  Set.of("DRAFT", "IN_REVIEW", "READY", "PUBLISHED")
  );

  private StatusTransitionGuard() {
    // static utility — never instantiated
  }

  /**
   * Validate a status value on <em>create</em>. Accepts {@code null} (status
   * not yet declared). Throws {@link InvalidBodyException} (HTTP 400) for any
   * unrecognised value.
   *
   * @param status the proposed status, or {@code null}
   * @throws InvalidBodyException if the value is not in {@link #VALID_STATUSES}
   */
  static void validateOnCreate(String status) {
    if (status == null) return;
    if (!VALID_STATUSES.contains(status)) {
      throw new InvalidBodyException(
        "Invalid status '%s'. Allowed values: %s".formatted(status, VALID_STATUSES)
      );
    }
  }

  /**
   * Validate a status transition on <em>update/patch</em>. Accepts {@code null}
   * proposed status (field cleared). Also accepts {@code null} current status
   * (no prior state — any forward move is allowed). Throws:
   * <ul>
   *   <li>{@link InvalidBodyException} (HTTP 400) for unrecognised value
   *   <li>{@link WebApplicationException} with HTTP 409 for a forbidden downgrade
   * </ul>
   *
   * @param currentStatus the persisted status before the update, or {@code null}
   * @param proposedStatus the requested new status, or {@code null}
   */
  static void validateOnUpdate(String currentStatus, String proposedStatus) {
    if (proposedStatus == null) return;
    if (!VALID_STATUSES.contains(proposedStatus)) {
      throw new InvalidBodyException(
        "Invalid status '%s'. Allowed values: %s".formatted(proposedStatus, VALID_STATUSES)
      );
    }
    if (currentStatus == null) return;
    Set<String> forbidden = FORBIDDEN_TRANSITIONS.get(currentStatus);
    if (forbidden != null && forbidden.contains(proposedStatus)) {
      throw new WebApplicationException(
        "Forbidden status transition: '%s' → '%s'".formatted(currentStatus, proposedStatus),
        Status.CONFLICT
      );
    }
  }
}
