package de.dlr.shepard.v2.project.services;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.project.daos.ProjectsDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * PROJ-SEMA-WRITE-GATE-1 — unit tests for the runtime SHACL gate.
 *
 * <p>The cross-target check ("partOf target must itself be a Project") is the
 * non-trivial one; tests pin down each constraint independently.
 */
class ProjectAnnotationConstraintsTest {

  static final String PROJECT_APP_ID    = "018f9c5a-7e26-7000-a000-000000000001";
  static final String NONPROJECT_APP_ID = "018f9c5a-7e26-7000-a000-000000000002";
  static final String SELF_APP_ID       = "018f9c5a-7e26-7000-a000-000000000099";

  @Mock
  ProjectsDAO projectsDAO;

  ProjectAnnotationConstraints gate;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    gate = new ProjectAnnotationConstraints();
    gate.projectsDAO = projectsDAO;
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
}
