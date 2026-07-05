package de.dlr.shepard.v2.project.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.common.io.PagedResponseIO;
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

  static final String SECOND_APP_ID = "018f9c5a-7e26-7000-a000-000000000003";

  @Test
  void list_returns200WithAppIds() {
    when(projectsService.countProjectAppIds()).thenReturn(2L);
    when(projectsService.listProjectAppIds(0, 50))
      .thenReturn(List.of(PROJECT_APP_ID, SECOND_APP_ID));
    Response r = resource.list(0, 50);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<String> body = (PagedResponseIO<String>) r.getEntity();
    assertEquals(2, body.items().size());
  }

  @Test
  void list_xTotalCountHeader_reflectsUnpagedTotal() {
    when(projectsService.countProjectAppIds()).thenReturn(2L);
    when(projectsService.listProjectAppIds(0, 50))
      .thenReturn(List.of(PROJECT_APP_ID, SECOND_APP_ID));
    Response r = resource.list(0, 50);
    assertEquals(200, r.getStatus());
    assertEquals("2", String.valueOf(r.getHeaderString("X-Total-Count")));
  }

  @Test
  void list_pageSize1_returnsFirstItemOnly() {
    when(projectsService.countProjectAppIds()).thenReturn(2L);
    when(projectsService.listProjectAppIds(0, 1))
      .thenReturn(List.of(PROJECT_APP_ID));
    Response r = resource.list(0, 1);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<String> body = (PagedResponseIO<String>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(PROJECT_APP_ID, body.items().get(0));
    assertEquals("2", String.valueOf(r.getHeaderString("X-Total-Count")));
  }

  @Test
  void list_pageSizeCappedAt200() {
    List<String> manyIds = java.util.stream.IntStream.range(0, 50)
      .mapToObj(i -> "018f9c5a-7e26-7000-a000-" + String.format("%012d", i))
      .collect(java.util.stream.Collectors.toList());
    when(projectsService.countProjectAppIds()).thenReturn(50L);
    when(projectsService.listProjectAppIds(0, 999)).thenReturn(manyIds);
    Response r = resource.list(0, 999);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<String> body = (PagedResponseIO<String>) r.getEntity();
    assertTrue(body.items().size() <= 200, "pageSize should be capped at 200");
    assertEquals("50", String.valueOf(r.getHeaderString("X-Total-Count")));
  }

  @Test
  void list_pagePastEnd_returnsEmpty() {
    when(projectsService.countProjectAppIds()).thenReturn(2L);
    when(projectsService.listProjectAppIds(990, 10)).thenReturn(List.of());
    Response r = resource.list(99, 10);
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<String> body = (PagedResponseIO<String>) r.getEntity();
    assertTrue(body.items().isEmpty(), "page past end should return empty list");
    assertEquals("2", String.valueOf(r.getHeaderString("X-Total-Count")));
  }

  @Test
  void list_pageParamsCarryValidationAnnotations() throws NoSuchMethodException {
    java.lang.reflect.Method m = ProjectsRest.class.getDeclaredMethod(
        "list", int.class, int.class);
    java.lang.reflect.Parameter page = m.getParameters()[0];
    java.lang.reflect.Parameter size = m.getParameters()[1];
    assertNotNull(page.getAnnotation(jakarta.validation.constraints.PositiveOrZero.class), "page: @PositiveOrZero");
    assertNotNull(size.getAnnotation(jakarta.validation.constraints.Min.class), "pageSize: @Min");
    assertNotNull(size.getAnnotation(jakarta.validation.constraints.Max.class), "pageSize: @Max");
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

  // ─── APISIMP-PROJECTS-BYANNOTATION-PARAMS-UNDOCUMENTED regression ─────────

  private static java.lang.reflect.Parameter findByAnnotationQueryParam(String queryParamName)
      throws NoSuchMethodException {
    java.lang.reflect.Method method = ProjectsRest.class.getMethod(
        "byAnnotation",
        String.class, String.class, String.class,
        String.class, boolean.class, int.class, int.class);
    return java.util.Arrays.stream(method.getParameters())
        .filter(p -> {
          var qp = p.getAnnotation(jakarta.ws.rs.QueryParam.class);
          return qp != null && queryParamName.equals(qp.value());
        })
        .findFirst()
        .orElse(null);
  }

  @Test
  void byAnnotation_includeParam_hasParameterAnnotationWithDescription()
      throws NoSuchMethodException {
    var param = findByAnnotationQueryParam("include");
    assertNotNull(param, "include must carry @QueryParam");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "include must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(),
        "@Parameter.description must be non-blank for include");
  }

  @Test
  void byAnnotation_inheritParam_hasParameterAnnotationWithNotYetHonouredWarning()
      throws NoSuchMethodException {
    var param = findByAnnotationQueryParam("inherit");
    assertNotNull(param, "inherit must carry @QueryParam");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "inherit must carry @Parameter annotation");
    assertTrue(ann.description() != null && ann.description().contains("not yet"),
        "@Parameter.description for inherit must mention 'not yet' (not-yet-implemented note)");
  }

  @Test
  void byAnnotation_pageParam_hasParameterAnnotationWithDescription()
      throws NoSuchMethodException {
    var param = findByAnnotationQueryParam("page");
    assertNotNull(param, "page must carry @QueryParam");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "page must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(),
        "@Parameter.description must be non-blank for page");
  }

  @Test
  void byAnnotation_pageSizeParam_hasParameterAnnotationWithMaxCap()
      throws NoSuchMethodException {
    var param = findByAnnotationQueryParam("pageSize");
    assertNotNull(param, "pageSize must carry @QueryParam");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "pageSize must carry @Parameter annotation");
    assertTrue(ann.description() != null && ann.description().contains("500"),
        "@Parameter.description for pageSize must mention the 500 cap");
  }

  // ─── APISIMP-PROJECT-BY-ANNOTATION-IRI-PATH — predicate/value as @QueryParam ──

  @Test
  void byAnnotation_returns422_whenValueBlank() {
    Response r = resource.byAnnotation(
      PROJECT_APP_ID, "urn:shepard:mffd:layer", "", "identity", true, 0, 100);
    assertEquals(422, r.getStatus());
  }

  @Test
  void byAnnotation_returns422_whenValueNull() {
    Response r = resource.byAnnotation(
      PROJECT_APP_ID, "urn:shepard:mffd:layer", null, "identity", true, 0, 100);
    assertEquals(422, r.getStatus());
  }

  @Test
  void byAnnotation_predicateIsQueryParam() throws NoSuchMethodException {
    var param = findByAnnotationQueryParam("predicate");
    assertNotNull(param, "predicate must carry @QueryParam('predicate')");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "predicate must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(),
        "@Parameter.description must be non-blank for predicate");
  }

  @Test
  void byAnnotation_valueIsQueryParam() throws NoSuchMethodException {
    var param = findByAnnotationQueryParam("value");
    assertNotNull(param, "value must carry @QueryParam('value')");
    var ann = param.getAnnotation(
        org.eclipse.microprofile.openapi.annotations.parameters.Parameter.class);
    assertNotNull(ann, "value must carry @Parameter annotation");
    assertTrue(ann.description() != null && !ann.description().isBlank(),
        "@Parameter.description must be non-blank for value");
  }
}
