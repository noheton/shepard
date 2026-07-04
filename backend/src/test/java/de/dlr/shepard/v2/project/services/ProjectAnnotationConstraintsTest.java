package de.dlr.shepard.v2.project.services;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.v2.project.daos.ProjectsDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * PROJ-SEMA-WRITE-GATE-1 / PROJ-SEMA-DUAL-OWNERSHIP-1 — unit tests for the
 * runtime SHACL + parent-Write gate.
 *
 * <p>The cross-target check ("partOf target must itself be a Project") is the
 * non-trivial SHACL one; the parent-Write check is the non-trivial permission
 * one. Tests pin down each constraint independently.
 */
class ProjectAnnotationConstraintsTest {

  static final String PROJECT_APP_ID    = "018f9c5a-7e26-7000-a000-000000000001";
  static final String NONPROJECT_APP_ID = "018f9c5a-7e26-7000-a000-000000000002";
  static final String SELF_APP_ID       = "018f9c5a-7e26-7000-a000-000000000099";
  static final long   PROJECT_OGM_ID    = 4242L;
  static final String CALLER            = "flo";

  @Mock
  ProjectsDAO projectsDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  EntityIdResolver entityIdResolver;

  ProjectAnnotationConstraints gate;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    gate = new ProjectAnnotationConstraints();
    gate.projectsDAO = projectsDAO;
    gate.permissionsService = permissionsService;
    gate.entityIdResolver = entityIdResolver;
  }

  // ── irrelevant predicates fall through ───────────────────────────────────

  @Test
  void unrelatedPredicate_returnsNull() {
    assertNull(gate.check(SELF_APP_ID, "Collection",
      "urn:shepard:mffd:layer", "18", null));
  }

  // ── urn:shepard:project ───────────────────────────────────────────────────

  @Test
  void project_acceptsLiteralTrue() {
    assertNull(gate.check(SELF_APP_ID, "Collection",
      ProjectAnnotationConstraints.PRED_PROJECT, "true", null));
  }

  @Test
  void project_rejectsLiteralFalse() {
    String msg = gate.check(SELF_APP_ID, "Collection",
      ProjectAnnotationConstraints.PRED_PROJECT, "false", null);
    assertNotNull(msg);
    assertTrue(msg.contains("must be the literal string \"true\""));
  }

  @Test
  void project_rejectsIriValue() {
    String msg = gate.check(SELF_APP_ID, "Collection",
      ProjectAnnotationConstraints.PRED_PROJECT, null, "http://example.org/foo");
    assertNotNull(msg);
  }

  @Test
  void project_rejectsOnDataObjectSubject() {
    String msg = gate.check(SELF_APP_ID, "DataObject",
      ProjectAnnotationConstraints.PRED_PROJECT, "true", null);
    assertNotNull(msg);
    assertTrue(msg.contains("Collection"));
  }

  // ── urn:shepard:partOf ────────────────────────────────────────────────────

  @Test
  void partOf_acceptsLiteralProjectAppId() {
    when(projectsDAO.isProject(PROJECT_APP_ID)).thenReturn(true);
    assertNull(gate.check(SELF_APP_ID, "Collection",
      ProjectAnnotationConstraints.PRED_PART_OF, PROJECT_APP_ID, null));
  }

  @Test
  void partOf_rejects_whenTargetNotAProject() {
    when(projectsDAO.isProject(NONPROJECT_APP_ID)).thenReturn(false);
    String msg = gate.check(SELF_APP_ID, "Collection",
      ProjectAnnotationConstraints.PRED_PART_OF, NONPROJECT_APP_ID, null);
    assertNotNull(msg);
    assertTrue(msg.contains("is not a Project"));
  }

  @Test
  void partOf_rejects_selfReference() {
    String msg = gate.check(SELF_APP_ID, "Collection",
      ProjectAnnotationConstraints.PRED_PART_OF, SELF_APP_ID, null);
    assertNotNull(msg);
    assertTrue(msg.contains("itself"));
  }

  @Test
  void partOf_rejectsIriValue() {
    String msg = gate.check(SELF_APP_ID, "Collection",
      ProjectAnnotationConstraints.PRED_PART_OF, null, "http://example.org/foo");
    assertNotNull(msg);
  }

  @Test
  void partOf_rejectsBlankValue() {
    String msg = gate.check(SELF_APP_ID, "Collection",
      ProjectAnnotationConstraints.PRED_PART_OF, "", null);
    assertNotNull(msg);
  }

  @Test
  void partOf_rejectsOnDataObjectSubject() {
    String msg = gate.check(SELF_APP_ID, "DataObject",
      ProjectAnnotationConstraints.PRED_PART_OF, PROJECT_APP_ID, null);
    assertNotNull(msg);
    assertTrue(msg.contains("Collection"));
  }

  // ── urn:shepard:programme ─────────────────────────────────────────────────

  @Test
  void programme_acceptsOnProject() {
    when(projectsDAO.isProject(SELF_APP_ID)).thenReturn(true);
    assertNull(gate.check(SELF_APP_ID, "Collection",
      ProjectAnnotationConstraints.PRED_PROGRAMME, "Clean Aviation JU", null));
  }

  @Test
  void programme_rejects_whenSubjectNotAProject() {
    when(projectsDAO.isProject(SELF_APP_ID)).thenReturn(false);
    String msg = gate.check(SELF_APP_ID, "Collection",
      ProjectAnnotationConstraints.PRED_PROGRAMME, "Clean Aviation JU", null);
    assertNotNull(msg);
    assertTrue(msg.contains("Project Collection"));
  }

  @Test
  void programme_rejectsBlankValue() {
    when(projectsDAO.isProject(SELF_APP_ID)).thenReturn(true);
    String msg = gate.check(SELF_APP_ID, "Collection",
      ProjectAnnotationConstraints.PRED_PROGRAMME, "", null);
    assertNotNull(msg);
  }

  // ── PROJ-SEMA-DUAL-OWNERSHIP-1 — parent-Write gate ────────────────────────

  @Test
  void parentWrite_partOf_allowsWhenWriteOnParent() {
    when(entityIdResolver.resolveLong(PROJECT_APP_ID)).thenReturn(PROJECT_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(
        eq(PROJECT_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    assertNull(gate.checkParentWritePermission(
      ProjectAnnotationConstraints.PRED_PART_OF, PROJECT_APP_ID, CALLER, false));
  }

  @Test
  void parentWrite_partOf_denies_whenNoWriteOnParent() {
    when(entityIdResolver.resolveLong(PROJECT_APP_ID)).thenReturn(PROJECT_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(
        eq(PROJECT_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(false);
    String msg = gate.checkParentWritePermission(
      ProjectAnnotationConstraints.PRED_PART_OF, PROJECT_APP_ID, CALLER, false);
    assertNotNull(msg);
    assertTrue(msg.contains("lacks Write permission"));
    assertTrue(msg.contains(PROJECT_APP_ID));
    assertTrue(msg.contains("PROJ-SEMA-DUAL-OWNERSHIP-1"));
  }

  @Test
  void parentWrite_partOf_instanceAdmin_bypassesCheck() {
    // No permissions stubbing — admins skip the check entirely.
    assertNull(gate.checkParentWritePermission(
      ProjectAnnotationConstraints.PRED_PART_OF, PROJECT_APP_ID, CALLER, true));
    verify(permissionsService, never()).isAccessTypeAllowedForUser(
      anyLong(), eq(AccessType.Write), anyString(), anyLong());
  }

  @Test
  void parentWrite_projectPredicate_isNoOp() {
    // urn:shepard:project has no separate parent — checkParentWritePermission
    // must not gate on it.
    assertNull(gate.checkParentWritePermission(
      ProjectAnnotationConstraints.PRED_PROJECT, "true", CALLER, false));
    verify(permissionsService, never()).isAccessTypeAllowedForUser(
      anyLong(), eq(AccessType.Write), anyString(), anyLong());
  }

  @Test
  void parentWrite_programmePredicate_isNoOp() {
    // urn:shepard:programme is Project metadata on the subject — no parent.
    assertNull(gate.checkParentWritePermission(
      ProjectAnnotationConstraints.PRED_PROGRAMME, "Clean Aviation JU", CALLER, false));
    verify(permissionsService, never()).isAccessTypeAllowedForUser(
      anyLong(), eq(AccessType.Write), anyString(), anyLong());
  }

  @Test
  void parentWrite_unrelatedPredicate_isNoOp() {
    assertNull(gate.checkParentWritePermission(
      "urn:shepard:mffd:layer", "18", CALLER, false));
    verify(permissionsService, never()).isAccessTypeAllowedForUser(
      anyLong(), eq(AccessType.Write), anyString(), anyLong());
  }

  @Test
  void parentWrite_partOf_blankTarget_isNoOp() {
    // Blank target is a SHACL violation surfaced by check(); the parent-Write
    // method tolerates it (returns null) so the SHACL message wins.
    assertNull(gate.checkParentWritePermission(
      ProjectAnnotationConstraints.PRED_PART_OF, "", CALLER, false));
    verify(permissionsService, never()).isAccessTypeAllowedForUser(
      anyLong(), eq(AccessType.Write), anyString(), anyLong());
  }

  @Test
  void parentWrite_partOf_unresolvableTarget_denies() {
    when(entityIdResolver.resolveLong(PROJECT_APP_ID))
      .thenThrow(new jakarta.ws.rs.NotFoundException("not found"));
    String msg = gate.checkParentWritePermission(
      ProjectAnnotationConstraints.PRED_PART_OF, PROJECT_APP_ID, CALLER, false);
    assertNotNull(msg);
    assertTrue(msg.contains("not resolvable"));
  }
}
