package de.dlr.shepard.common.container.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

/**
 * #27-CONTAINER-STATUS-01 — unit tests for the BasicContainer lifecycle status
 * closed-enum enforcement and transition guard.
 *
 * <p>Tests mirror the {@code StatusTransitionGuardTest} pattern (DataObject guard)
 * but without MFG1 quality statuses (containers use the base set only).
 */
public class ContainerStatusGuardTest {

  // ─── validateOnCreate ─────────────────────────────────────────────────────

  @Test
  public void validateOnCreate_nullStatus_allowed() {
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnCreate(null));
  }

  @Test
  public void validateOnCreate_validStatus_allowed() {
    for (String status : ContainerStatusGuard.VALID_STATUSES) {
      assertDoesNotThrow(() -> ContainerStatusGuard.validateOnCreate(status));
    }
  }

  @Test
  public void validateOnCreate_draft_allowed() {
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnCreate("DRAFT"));
  }

  @Test
  public void validateOnCreate_inReview_allowed() {
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnCreate("IN_REVIEW"));
  }

  @Test
  public void validateOnCreate_ready_allowed() {
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnCreate("READY"));
  }

  @Test
  public void validateOnCreate_published_allowed() {
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnCreate("PUBLISHED"));
  }

  @Test
  public void validateOnCreate_archived_allowed() {
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnCreate("ARCHIVED"));
  }

  @Test
  public void validateOnCreate_invalidStatus_throws400() {
    assertThrows(InvalidBodyException.class,
      () -> ContainerStatusGuard.validateOnCreate("DELETED"));
  }

  @Test
  public void validateOnCreate_ncrOpenNotValid_throws400() {
    // MFG1 quality statuses are NOT valid for containers (DataObject-only).
    assertThrows(InvalidBodyException.class,
      () -> ContainerStatusGuard.validateOnCreate("NCR_OPEN"));
  }

  @Test
  public void validateOnCreate_certifiedNotValid_throws400() {
    assertThrows(InvalidBodyException.class,
      () -> ContainerStatusGuard.validateOnCreate("CERTIFIED"));
  }

  // ─── validateOnUpdate ─────────────────────────────────────────────────────

  @Test
  public void validateOnUpdate_nullProposed_allowed() {
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnUpdate("PUBLISHED", null));
  }

  @Test
  public void validateOnUpdate_nullCurrent_anyForwardMoveAllowed() {
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnUpdate(null, "PUBLISHED"));
  }

  @Test
  public void validateOnUpdate_nullCurrentNullProposed_allowed() {
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnUpdate(null, null));
  }

  @Test
  public void validateOnUpdate_sameState_allowed() {
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnUpdate("PUBLISHED", "PUBLISHED"));
  }

  @Test
  public void validateOnUpdate_sameStateArchived_allowed() {
    // Same-state on ARCHIVED is idempotent (a full-replace PUT re-sending current status).
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnUpdate("ARCHIVED", "ARCHIVED"));
  }

  @Test
  public void validateOnUpdate_draftToPublished_allowed() {
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnUpdate("DRAFT", "PUBLISHED"));
  }

  @Test
  public void validateOnUpdate_publishedToDraft_throws409() {
    WebApplicationException ex = assertThrows(WebApplicationException.class,
      () -> ContainerStatusGuard.validateOnUpdate("PUBLISHED", "DRAFT"));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  public void validateOnUpdate_publishedToInReview_throws409() {
    WebApplicationException ex = assertThrows(WebApplicationException.class,
      () -> ContainerStatusGuard.validateOnUpdate("PUBLISHED", "IN_REVIEW"));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  public void validateOnUpdate_archivedToDraft_throws409() {
    WebApplicationException ex = assertThrows(WebApplicationException.class,
      () -> ContainerStatusGuard.validateOnUpdate("ARCHIVED", "DRAFT"));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  public void validateOnUpdate_archivedToInReview_throws409() {
    WebApplicationException ex = assertThrows(WebApplicationException.class,
      () -> ContainerStatusGuard.validateOnUpdate("ARCHIVED", "IN_REVIEW"));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  public void validateOnUpdate_archivedToReady_throws409() {
    WebApplicationException ex = assertThrows(WebApplicationException.class,
      () -> ContainerStatusGuard.validateOnUpdate("ARCHIVED", "READY"));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  public void validateOnUpdate_archivedToPublished_throws409() {
    WebApplicationException ex = assertThrows(WebApplicationException.class,
      () -> ContainerStatusGuard.validateOnUpdate("ARCHIVED", "PUBLISHED"));
    assertEquals(409, ex.getResponse().getStatus());
  }

  @Test
  public void validateOnUpdate_invalidStatus_throws400() {
    assertThrows(InvalidBodyException.class,
      () -> ContainerStatusGuard.validateOnUpdate("DRAFT", "BOGUS_STATE"));
  }

  @Test
  public void validateOnUpdate_publishedToReady_allowed() {
    // PUBLISHED → READY is not in the forbidden set; allowed.
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnUpdate("PUBLISHED", "READY"));
  }

  @Test
  public void validateOnUpdate_publishedToArchived_allowed() {
    // PUBLISHED → ARCHIVED is a forward move; allowed.
    assertDoesNotThrow(() -> ContainerStatusGuard.validateOnUpdate("PUBLISHED", "ARCHIVED"));
  }
}
