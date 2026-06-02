package de.dlr.shepard.v2.project.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.project.io.ProjectByAnnotationIO;
import de.dlr.shepard.v2.project.io.ProjectIO;
import de.dlr.shepard.v2.project.io.SubCollectionsIO;
import de.dlr.shepard.v2.project.services.ProjectsService;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * PROJ-REST-1 + PROJ-REST-2 — unit tests for {@link ProjectsRest}.
 * Mock-based (no Quarkus boot). Covers the 404 path on non-Project appIds
 * across all three sub-resources plus the 422 on a blank predicate.
 */
class ProjectsRestTest {

  static final String PROJECT_APP_ID    = "018f9c5a-7e26-7000-a000-000000000001";
  static final String NONPROJECT_APP_ID = "018f9c5a-7e26-7000-a000-000000000002";

  @Mock
  ProjectsService projectsService;

  ProjectsRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new ProjectsRest();
    resource.projectsService = projectsService;
  }

  // ── list ──────────────────────────────────────────────────────────────────

  @Test
  void list_returns200WithAppIds() {
    when(projectsService.listProjectAppIds())
      .thenReturn(List.of(PROJECT_APP_ID, "018f9c5a-7e26-7000-a000-000000000003"));
    Response r = resource.list();
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<String> body = (List<String>) r.getEntity();
    assertEquals(2, body.size());
  }

  // ── get ───────────────────────────────────────────────────────────────────

  @Test
  void get_returns200_whenProject() {
    ProjectIO io = new ProjectIO();
    io.setAppId(PROJECT_APP_ID);
    io.setName("MFFD Upper Shell");
    when(projectsService.getProject(PROJECT_APP_ID)).thenReturn(io);

    Response r = resource.get(PROJECT_APP_ID);
    assertEquals(200, r.getStatus());
    ProjectIO body = (ProjectIO) r.getEntity();
    assertEquals(PROJECT_APP_ID, body.getAppId());
  }

  @Test
  void get_returns404_whenNotAProject() {
    when(projectsService.getProject(NONPROJECT_APP_ID)).thenReturn(null);
    Response r = resource.get(NONPROJECT_APP_ID);
    assertEquals(404, r.getStatus());
  }

  // ── sub-collections ───────────────────────────────────────────────────────

  @Test
  void subCollections_returns200_whenProject() {
    SubCollectionsIO io = new SubCollectionsIO();
    io.setProjectAppId(PROJECT_APP_ID);
    io.setProgrammes(List.of("Clean Aviation JU"));
    when(projectsService.getSubCollections(PROJECT_APP_ID)).thenReturn(io);

    Response r = resource.subCollections(PROJECT_APP_ID);
    assertEquals(200, r.getStatus());
    SubCollectionsIO body = (SubCollectionsIO) r.getEntity();
    assertEquals(PROJECT_APP_ID, body.getProjectAppId());
    assertEquals(1, body.getProgrammes().size());
  }

  @Test
  void subCollections_returns404_whenNotAProject() {
    when(projectsService.getSubCollections(NONPROJECT_APP_ID)).thenReturn(null);
    Response r = resource.subCollections(NONPROJECT_APP_ID);
    assertEquals(404, r.getStatus());
  }

  // ── by-annotation ─────────────────────────────────────────────────────────

  @Test
  void byAnnotation_returns200_whenProject() {
    when(projectsService.isProject(PROJECT_APP_ID)).thenReturn(true);
    ProjectByAnnotationIO io = new ProjectByAnnotationIO();
    io.setProjectAppId(PROJECT_APP_ID);
    io.setPredicate("urn:shepard:mffd:layer");
    io.setValue("18");
    io.setTotalCount(0L);
    when(projectsService.queryByAnnotation(eq(PROJECT_APP_ID), eq("urn:shepard:mffd:layer"),
        eq("18"), anyBoolean(), anyInt(), anyInt()))
      .thenReturn(io);

    Response r = resource.byAnnotation(
      PROJECT_APP_ID, "urn:shepard:mffd:layer", "18",
      "identity", true, 0, 100);
    assertEquals(200, r.getStatus());
    ProjectByAnnotationIO body = (ProjectByAnnotationIO) r.getEntity();
    assertEquals(PROJECT_APP_ID, body.getProjectAppId());
  }

  @Test
  void byAnnotation_returns404_whenNotAProject() {
    when(projectsService.isProject(NONPROJECT_APP_ID)).thenReturn(false);
    Response r = resource.byAnnotation(
      NONPROJECT_APP_ID, "urn:shepard:mffd:layer", "18",
      "identity", true, 0, 100);
    assertEquals(404, r.getStatus());
  }

  @Test
  void byAnnotation_returns422_whenPredicateBlank() {
    Response r = resource.byAnnotation(
      PROJECT_APP_ID, "  ", "v",
      "identity", true, 0, 100);
    assertEquals(422, r.getStatus());
  }

  @Test
  void byAnnotation_includeAnnotations_flagsThrough() {
    when(projectsService.isProject(PROJECT_APP_ID)).thenReturn(true);
    ProjectByAnnotationIO io = new ProjectByAnnotationIO();
    when(projectsService.queryByAnnotation(any(), any(), any(), eq(true), anyInt(), anyInt()))
      .thenReturn(io);

    Response r = resource.byAnnotation(
      PROJECT_APP_ID, "p", "v",
      "annotations", true, 0, 50);
    assertEquals(200, r.getStatus());
    assertNotNull(r.getEntity());
  }
}
