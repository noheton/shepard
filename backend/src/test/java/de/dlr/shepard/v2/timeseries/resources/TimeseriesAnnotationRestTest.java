package de.dlr.shepard.v2.timeseries.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.timeseriesreference.daos.TimeseriesReferenceDAO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.v2.timeseries.daos.TimeseriesAnnotationDAO;
import de.dlr.shepard.v2.timeseries.io.TimeseriesAnnotationIO;
import de.dlr.shepard.v2.timeseries.model.TimeseriesAnnotation;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TimeseriesAnnotationRestTest {

  static final String REF_APP_ID = "ref-appid-1";
  static final String ANN_APP_ID = "ann-appid-1";
  static final long DO_OGM_ID = 99L;
  static final String CALLER = "alice";

  @Mock
  TimeseriesAnnotationDAO annotationDAO;

  @Mock
  TimeseriesReferenceDAO timeseriesReferenceDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  TimeseriesAnnotationRest resource;
  TimeseriesReference ref;
  DataObject dataObject;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new TimeseriesAnnotationRest();
    resource.annotationDAO = annotationDAO;
    resource.timeseriesReferenceDAO = timeseriesReferenceDAO;
    resource.permissionsService = permissionsService;

    dataObject = new DataObject();
    dataObject.setId(DO_OGM_ID);

    ref = new TimeseriesReference();
    ref.setAppId(REF_APP_ID);
    ref.setDataObject(dataObject);

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER)).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Write, CALLER)).thenReturn(true);
  }

  // ── list ────────────────────────────────────────────────────────────────

  @Test
  void list_returns200WithAnnotations() {
    var a = annotation(ANN_APP_ID, 1_000_000L, null, "spike");
    when(annotationDAO.findByTimeseriesReferenceAppId(REF_APP_ID)).thenReturn(List.of(a));
    var r = resource.list(REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var rows = (List<TimeseriesAnnotationIO>) r.getEntity();
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getLabel()).isEqualTo("spike");
  }

  @Test
  void list_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.list(REF_APP_ID, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void list_returns404WhenRefMissing() {
    when(timeseriesReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    assertThat(resource.list(REF_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void list_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER)).thenReturn(false);
    assertThat(resource.list(REF_APP_ID, sc).getStatus()).isEqualTo(403);
  }

  // ── create ──────────────────────────────────────────────────────────────

  @Test
  void create_returns201AndPersists() {
    var body = new TimeseriesAnnotationIO();
    body.setStartNs(1_000_000L);
    body.setLabel("anomaly");

    var r = resource.create(REF_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(201);
    verify(annotationDAO).createOrUpdate(any(TimeseriesAnnotation.class));
    verify(annotationDAO).linkToReference(eq(REF_APP_ID), any());
  }

  @Test
  void create_returns400WhenStartNsMissing() {
    var body = new TimeseriesAnnotationIO();
    body.setLabel("anomaly");
    assertThat(resource.create(REF_APP_ID, body, sc).getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void create_returns400WhenLabelMissing() {
    var body = new TimeseriesAnnotationIO();
    body.setStartNs(1_000_000L);
    assertThat(resource.create(REF_APP_ID, body, sc).getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void create_returns400WhenLabelBlank() {
    var body = new TimeseriesAnnotationIO();
    body.setStartNs(1_000_000L);
    body.setLabel("   ");
    assertThat(resource.create(REF_APP_ID, body, sc).getStatus()).isEqualTo(400);
  }

  @Test
  void create_returns403WhenNoWritePermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Write, CALLER)).thenReturn(false);
    var body = new TimeseriesAnnotationIO();
    body.setStartNs(1_000_000L);
    body.setLabel("anomaly");
    assertThat(resource.create(REF_APP_ID, body, sc).getStatus()).isEqualTo(403);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  // ── read ────────────────────────────────────────────────────────────────

  @Test
  void read_returns200() {
    var a = annotation(ANN_APP_ID, 1_000_000L, 2_000_000L, "interval");
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(a);
    var r = resource.read(REF_APP_ID, ANN_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var io = (TimeseriesAnnotationIO) r.getEntity();
    assertThat(io.getLabel()).isEqualTo("interval");
    assertThat(io.getEndNs()).isEqualTo(2_000_000L);
  }

  @Test
  void read_returns404WhenAnnotationMissing() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(null);
    assertThat(resource.read(REF_APP_ID, ANN_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  // ── update ──────────────────────────────────────────────────────────────

  @Test
  void update_patchesLabel() {
    var a = annotation(ANN_APP_ID, 1_000_000L, null, "old");
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(a);
    var body = new TimeseriesAnnotationIO();
    body.setLabel("new");
    var r = resource.update(REF_APP_ID, ANN_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(((TimeseriesAnnotationIO) r.getEntity()).getLabel()).isEqualTo("new");
    verify(annotationDAO).createOrUpdate(a);
  }

  @Test
  void update_returns400WhenLabelBlank() {
    var a = annotation(ANN_APP_ID, 1_000_000L, null, "old");
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(a);
    var body = new TimeseriesAnnotationIO();
    body.setLabel("  ");
    assertThat(resource.update(REF_APP_ID, ANN_APP_ID, body, sc).getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  // ── delete ──────────────────────────────────────────────────────────────

  @Test
  void delete_returns204AndUnlinks() {
    var a = annotation(ANN_APP_ID, 1_000_000L, null, "to-delete");
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(a);
    var r = resource.delete(REF_APP_ID, ANN_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(204);
    verify(annotationDAO).unlinkAndDelete(REF_APP_ID, a);
  }

  @Test
  void delete_returns404WhenMissing() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(null);
    assertThat(resource.delete(REF_APP_ID, ANN_APP_ID, sc).getStatus()).isEqualTo(404);
    verify(annotationDAO, never()).unlinkAndDelete(any(), any());
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private static TimeseriesAnnotation annotation(String appId, long startNs, Long endNs, String label) {
    var a = new TimeseriesAnnotation();
    a.setAppId(appId);
    a.setStartNs(startNs);
    a.setEndNs(endNs);
    a.setLabel(label);
    return a;
  }
}
