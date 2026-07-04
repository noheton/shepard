package de.dlr.shepard.v2.project.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.v2.project.daos.ProjectsDAO;
import de.dlr.shepard.v2.project.io.ProjectByAnnotationIO;
import de.dlr.shepard.v2.project.io.ProjectByAnnotationItemIO;
import de.dlr.shepard.v2.project.io.ProjectIO;
import de.dlr.shepard.v2.project.io.SubCollectionItemIO;
import de.dlr.shepard.v2.project.io.SubCollectionsIO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * PROJ-REST-1 + PROJ-REST-2 — unit tests for the ProjectsService composition layer.
 * Mock-based — no Quarkus boot, no Neo4j; the DAO is mocked so the test
 * focuses on the service-layer 404-on-non-project gate, programme stamping,
 * and pagination clamps.
 */
class ProjectsServiceTest {

  static final String PROJECT_APP_ID  = "018f9c5a-7e26-7000-a000-000000000001";
  static final String NONPROJECT_APP_ID = "018f9c5a-7e26-7000-a000-000000000002";

  @Mock
  ProjectsDAO projectsDAO;

  ProjectsService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new ProjectsService();
    service.projectsDAO = projectsDAO;
  }

  @Test
  void getProject_returnsEnvelope_whenProjectExists() {
    ProjectIO io = new ProjectIO();
    io.setAppId(PROJECT_APP_ID);
    io.setName("MFFD Upper Shell");
    when(projectsDAO.findProject(PROJECT_APP_ID)).thenReturn(io);

    ProjectIO result = service.getProject(PROJECT_APP_ID);
    assertNotNull(result);
    assertEquals(PROJECT_APP_ID, result.getAppId());
    assertEquals("MFFD Upper Shell", result.getName());
  }

  @Test
  void getProject_returnsNull_whenAppIdNotAProject() {
    when(projectsDAO.findProject(NONPROJECT_APP_ID)).thenReturn(null);
    assertNull(service.getProject(NONPROJECT_APP_ID));
  }

  @Test
  void getSubCollections_returnsNull_whenNotAProject() {
    when(projectsDAO.isProject(NONPROJECT_APP_ID)).thenReturn(false);
    assertNull(service.getSubCollections(NONPROJECT_APP_ID));
    verify(projectsDAO, never()).findSubCollections(NONPROJECT_APP_ID);
    verify(projectsDAO, never()).findProgrammes(NONPROJECT_APP_ID);
  }

  @Test
  void getSubCollections_emptyChildren_returnsEmptyEnvelope() {
    when(projectsDAO.isProject(PROJECT_APP_ID)).thenReturn(true);
    when(projectsDAO.findProgrammes(PROJECT_APP_ID))
      .thenReturn(List.of("Clean Aviation JU"));
    when(projectsDAO.findSubCollections(PROJECT_APP_ID)).thenReturn(List.of());

    SubCollectionsIO io = service.getSubCollections(PROJECT_APP_ID);
    assertNotNull(io);
    assertEquals(PROJECT_APP_ID, io.getProjectAppId());
    assertEquals(List.of("Clean Aviation JU"), io.getProgrammes());
    assertTrue(io.getSubCollections().isEmpty());
  }

  @Test
  void getSubCollections_returnsChildrenWithProgrammeStrip() {
    when(projectsDAO.isProject(PROJECT_APP_ID)).thenReturn(true);
    when(projectsDAO.findProgrammes(PROJECT_APP_ID))
      .thenReturn(List.of("Clean Aviation JU", "DLR Project Line 4"));
    SubCollectionItemIO row = new SubCollectionItemIO();
    row.setAppId("018f9c5a-7e26-7000-a000-000000000010");
    row.setName("mffd-afp-tapelaying");
    row.setDoCount(8251L);
    when(projectsDAO.findSubCollections(PROJECT_APP_ID))
      .thenReturn(List.of(row));

    SubCollectionsIO io = service.getSubCollections(PROJECT_APP_ID);
    assertNotNull(io);
    assertEquals(2, io.getProgrammes().size());
    assertEquals(1, io.getSubCollections().size());
    assertEquals("mffd-afp-tapelaying", io.getSubCollections().get(0).getName());
  }

  @Test
  void queryByAnnotation_returnsNull_whenNotAProject() {
    when(projectsDAO.isProject(NONPROJECT_APP_ID)).thenReturn(false);
    assertNull(service.queryByAnnotation(
      NONPROJECT_APP_ID, "urn:shepard:mffd:layer", "18", false, 0, 100));
    verify(projectsDAO, never()).pageByAnnotation(any(), any(), any(), anyBoolean(), anyInt(), anyInt());
    verify(projectsDAO, never()).countByAnnotation(any(), any(), any());
  }

  @Test
  void queryByAnnotation_clampsPageSize() {
    when(projectsDAO.isProject(PROJECT_APP_ID)).thenReturn(true);
    when(projectsDAO.countByAnnotation(eq(PROJECT_APP_ID), eq("p"), eq("v")))
      .thenReturn(42L);
    when(projectsDAO.pageByAnnotation(eq(PROJECT_APP_ID), eq("p"), eq("v"),
        eq(false), eq(0), eq(500)))
      .thenReturn(List.of());

    ProjectByAnnotationIO io = service.queryByAnnotation(
      PROJECT_APP_ID, "p", "v", false, -1, 10_000);
    assertNotNull(io);
    assertEquals(PROJECT_APP_ID, io.getProjectAppId());
    assertEquals("p", io.getPredicate());
    assertEquals("v", io.getValue());
    assertEquals(0, io.getPage());                 // clamped from -1
    assertEquals(500, io.getPageSize());           // clamped to 500
    assertEquals(42L, io.getTotalCount());
  }

  @Test
  void queryByAnnotation_includeAnnotations_stamps_direct_match() {
    when(projectsDAO.isProject(PROJECT_APP_ID)).thenReturn(true);
    when(projectsDAO.countByAnnotation(any(), any(), any())).thenReturn(1L);
    ProjectByAnnotationItemIO row = new ProjectByAnnotationItemIO();
    row.setAppId("018f9c5a-7e26-7000-a000-000000000020");
    row.setName("Run_S2_M3_L18_F4_R1");
    // DAO already stamped direct match.
    ProjectByAnnotationItemIO.MatchedAnnotation m =
      new ProjectByAnnotationItemIO.MatchedAnnotation();
    m.setPredicate("urn:shepard:mffd:layer");
    m.setValue("18");
    m.setSource("direct");
    row.addMatched(m);
    when(projectsDAO.pageByAnnotation(any(), any(), any(), eq(true), anyInt(), anyInt()))
      .thenReturn(List.of(row));

    ProjectByAnnotationIO io = service.queryByAnnotation(
      PROJECT_APP_ID, "urn:shepard:mffd:layer", "18", true, 0, 100);
    assertNotNull(io);
    assertEquals(1, io.getResults().size());
    assertNotNull(io.getResults().get(0).getMatchedAnnotations());
    assertEquals("direct", io.getResults().get(0).getMatchedAnnotations().get(0).getSource());
  }

  @Test
  void listProjectAppIds_delegatesToDAO() {
    when(projectsDAO.findAllProjectAppIds())
      .thenReturn(List.of(PROJECT_APP_ID, "018f9c5a-7e26-7000-a000-000000000003"));
    List<String> ids = service.listProjectAppIds();
    assertEquals(2, ids.size());
    assertEquals(PROJECT_APP_ID, ids.get(0));
  }
}
