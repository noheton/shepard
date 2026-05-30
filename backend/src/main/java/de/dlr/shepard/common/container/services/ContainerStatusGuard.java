package de.dlr.shepard.common.container.services;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import java.util.Map;
import java.util.Set;

/**
 * #27-CONTAINER-STATUS-01 — closed-enum guard for BasicContainer lifecycle status.
 *
 * <p>Valid statuses (base set — containers do not carry MFG1 quality statuses):
 * {@code DRAFT}, {@code IN_REVIEW}, {@code READY}, {@code PUBLISHED},
 * {@code ARCHIVED}.
 *
 * <p>{@code null} is always valid (nullable additive field; pre-feature containers
 * have no status).
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
 * <p>Mirrors {@code StatusTransitionGuard} in
 * {@code de.dlr.shepard.context.collection.services} but without the MFG1
 * quality-engineering statuses — those are DataObject-only.
 *
 * <p>TODO (#27-ARCHIVED-02): wire this guard into the container PATCH endpoint
 * once the status write path ships.
 */
public class ContainerStatusGuard {

  /** The closed set of acceptable container status values. */
  public static final Set<String> VALID_STATUSES = Set.of(
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

  private ContainerStatusGuard() {
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
  public static void validateOnCreate(String status) {
    if (status == null) return;
    if (!VALID_STATUSES.contains(status)) {
      throw new InvalidBodyException(
        "Invalid container status '%s'. Allowed values: %s".formatted(status, VALID_STATUSES)
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
   * @param currentStatus  the persisted status before the update, or {@code null}
   * @param proposedStatus the requested new status, or {@code null}
   */
  public static void validateOnUpdate(String currentStatus, String proposedStatus) {
    if (proposedStatus == null) return;
    if (!VALID_STATUSES.contains(proposedStatus)) {
      throw new InvalidBodyException(
        "Invalid container status '%s'. Allowed values: %s".formatted(proposedStatus, VALID_STATUSES)
      );
    }
    if (currentStatus == null) return;
    Set<String> forbidden = FORBIDDEN_TRANSITIONS.get(currentStatus);
    if (forbidden != null && forbidden.contains(proposedStatus)) {
      throw new WebApplicationException(
        "Forbidden container status transition: '%s' → '%s'".formatted(currentStatus, proposedStatus),
        Status.CONFLICT
      );
    }
  }
}
