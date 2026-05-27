package de.dlr.shepard.context.collection.services;

import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import java.util.Map;
import java.util.Set;

/**
 * MFG5 + MFG1 — closed-enum guard for DataObject lifecycle status.
 *
 * <p>Valid statuses (full set):
 * {@code DRAFT}, {@code IN_REVIEW}, {@code READY}, {@code PUBLISHED},
 * {@code ARCHIVED} (base set, any authenticated user),
 * plus {@code NCR_OPEN}, {@code ON_HOLD}, {@code REJECTED}, {@code CERTIFIED}
 * (MFG1 quality-engineering statuses — require the {@code quality-engineer} role).
 *
 * <p>{@code null} is always valid (nullable field, may not be declared yet).
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
 * <p>Source: {@code aidocs/agent-findings/manufacturing-quality.md §11.2} (MFG5);
 * MFG1 extension adds quality-engineer-gated statuses (EN 9100 / EASA Part 21G).
 */
class StatusTransitionGuard {

  /** The closed set of acceptable status values (all users). */
  static final Set<String> VALID_STATUSES = Set.of(
    "DRAFT", "IN_REVIEW", "READY", "PUBLISHED", "ARCHIVED",
    // MFG1 — EN 9100 / EASA quality-engineering statuses (role-gated on write)
    "NCR_OPEN", "ON_HOLD", "REJECTED", "CERTIFIED"
  );

  /**
   * MFG1 — statuses that require the {@code quality-engineer} role to SET.
   * Any user may READ a DataObject that carries one of these statuses; only a
   * quality engineer may WRITE (create or transition TO) these values.
   */
  static final Set<String> QUALITY_RESTRICTED_STATUSES = Set.of(
    "NCR_OPEN", "ON_HOLD", "REJECTED", "CERTIFIED"
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

  /**
   * MFG1 — enforce the quality-engineer role gate on quality-restricted statuses.
   *
   * <p>If {@code proposedStatus} is in {@link #QUALITY_RESTRICTED_STATUSES} and
   * the caller does not hold the {@code quality-engineer} role, an
   * {@link InvalidAuthException} (HTTP 403) is thrown.
   *
   * @param proposedStatus the status value being written (may be {@code null})
   * @param hasQualityRole {@code true} if the authenticated caller holds
   *                       the {@code quality-engineer} Keycloak/Neo4j role
   * @throws InvalidAuthException if caller lacks the role for a quality status
   */
  static void validateQualityRole(String proposedStatus, boolean hasQualityRole) {
    if (proposedStatus == null) return;
    if (QUALITY_RESTRICTED_STATUSES.contains(proposedStatus) && !hasQualityRole) {
      throw new InvalidAuthException(
        "Status '%s' requires the 'quality-engineer' role".formatted(proposedStatus)
      );
    }
  }
}
