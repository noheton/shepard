package de.dlr.shepard.v2.video.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.v2.video.daos.VideoAnnotationDAO;
import de.dlr.shepard.v2.video.io.VideoAnnotationIO;
import de.dlr.shepard.v2.video.model.VideoAnnotation;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class VideoAnnotationRestTest {

  static final String DO_APP_ID = "do-appid-1";
  static final String REF_APP_ID = "ref-appid-1";
  static final String ANN_APP_ID = "ann-appid-1";
  static final long DO_OGM_ID = 99L;
  static final String CALLER = "alice";

  @Mock
  VideoAnnotationDAO annotationDAO;

  @Mock
  VideoStreamReferenceDAO videoStreamReferenceDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  VideoAnnotationRest resource;
  VideoStreamReference ref;
  DataObject dataObject;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new VideoAnnotationRest();
    resource.annotationDAO = annotationDAO;
    resource.videoStreamReferenceDAO = videoStreamReferenceDAO;
    resource.permissionsService = permissionsService;

    dataObject = new DataObject();
    dataObject.setId(DO_OGM_ID);
    dataObject.setAppId(DO_APP_ID);

    ref = new VideoStreamReference();
    ref.setAppId(REF_APP_ID);
    ref.setDataObject(dataObject);

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(videoStreamReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Read, CALLER)).thenReturn(true);
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Write, CALLER)).thenReturn(true);
  }

  // ── list ────────────────────────────────────────────────────────────────

  @Test
  void list_returns200WithAnnotations() {
    var a = annotation(ANN_APP_ID, 0.0, 5.0, "ignition");
    when(annotationDAO.findByVideoReferenceAppId(REF_APP_ID)).thenReturn(List.of(a));
    var r = resource.list(DO_APP_ID, REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    var rows = (List<VideoAnnotationIO>) r.getEntity();
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getLabel()).isEqualTo("ignition");
  }

  @Test
  void list_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    assertThat(resource.list(DO_APP_ID, REF_APP_ID, sc).getStatus()).isEqualTo(401);
  }

  @Test
  void list_returns404WhenRefMissing() {
    when(videoStreamReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    assertThat(resource.list(DO_APP_ID, REF_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  @Test
  void list_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Read, CALLER)).thenReturn(false);
    assertThat(resource.list(DO_APP_ID, REF_APP_ID, sc).getStatus()).isEqualTo(403);
  }

  // ── create ──────────────────────────────────────────────────────────────

  @Test
  void create_returns201AndPersists() {
    var body = new VideoAnnotationIO();
    body.setStartSeconds(0.0);
    body.setEndSeconds(5.0);
    body.setLabel("ignition");

    var r = resource.create(DO_APP_ID, REF_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(201);
    verify(annotationDAO).createOrUpdate(any(VideoAnnotation.class));
    verify(annotationDAO).linkToReference(eq(REF_APP_ID), any());
  }

  @Test
  void create_returns400WhenStartSecondsMissing() {
    var body = new VideoAnnotationIO();
    body.setLabel("ignition");
    assertThat(resource.create(DO_APP_ID, REF_APP_ID, body, sc).getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void create_returns400WhenLabelMissing() {
    var body = new VideoAnnotationIO();
    body.setStartSeconds(0.0);
    assertThat(resource.create(DO_APP_ID, REF_APP_ID, body, sc).getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void create_returns400WhenLabelBlank() {
    var body = new VideoAnnotationIO();
    body.setStartSeconds(0.0);
    body.setLabel("   ");
    assertThat(resource.create(DO_APP_ID, REF_APP_ID, body, sc).getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  @Test
  void create_returns403WhenNoWritePermission() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Write, CALLER)).thenReturn(false);
    var body = new VideoAnnotationIO();
    body.setStartSeconds(0.0);
    body.setLabel("ignition");
    assertThat(resource.create(DO_APP_ID, REF_APP_ID, body, sc).getStatus()).isEqualTo(403);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  // ── read ────────────────────────────────────────────────────────────────

  @Test
  void read_returns200() {
    var a = annotation(ANN_APP_ID, 5.0, 35.0, "burn");
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(a);
    var r = resource.read(DO_APP_ID, REF_APP_ID, ANN_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    var io = (VideoAnnotationIO) r.getEntity();
    assertThat(io.getLabel()).isEqualTo("burn");
    assertThat(io.getEndSeconds()).isEqualTo(35.0);
  }

  @Test
  void read_returns404WhenAnnotationMissing() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(null);
    assertThat(resource.read(DO_APP_ID, REF_APP_ID, ANN_APP_ID, sc).getStatus()).isEqualTo(404);
  }

  // ── update ──────────────────────────────────────────────────────────────

  @Test
  void update_patchesLabel() {
    var a = annotation(ANN_APP_ID, 0.0, 5.0, "old-label");
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(a);
    var body = new VideoAnnotationIO();
    body.setLabel("new-label");
    var r = resource.update(DO_APP_ID, REF_APP_ID, ANN_APP_ID, body, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(((VideoAnnotationIO) r.getEntity()).getLabel()).isEqualTo("new-label");
    verify(annotationDAO).createOrUpdate(a);
  }

  @Test
  void update_returns400WhenLabelBlank() {
    var a = annotation(ANN_APP_ID, 0.0, 5.0, "old-label");
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(a);
    var body = new VideoAnnotationIO();
    body.setLabel("  ");
    assertThat(resource.update(DO_APP_ID, REF_APP_ID, ANN_APP_ID, body, sc).getStatus()).isEqualTo(400);
    verify(annotationDAO, never()).createOrUpdate(any());
  }

  // ── delete ──────────────────────────────────────────────────────────────

  @Test
  void delete_returns204AndUnlinks() {
    var a = annotation(ANN_APP_ID, 35.0, 50.0, "cooldown");
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(a);
    var r = resource.delete(DO_APP_ID, REF_APP_ID, ANN_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(204);
    verify(annotationDAO).unlinkAndDelete(REF_APP_ID, a);
  }

  @Test
  void delete_returns404WhenMissing() {
    when(annotationDAO.findByAppId(ANN_APP_ID)).thenReturn(null);
    assertThat(resource.delete(DO_APP_ID, REF_APP_ID, ANN_APP_ID, sc).getStatus()).isEqualTo(404);
    verify(annotationDAO, never()).unlinkAndDelete(any(), any());
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private static VideoAnnotation annotation(String appId, double startSeconds, Double endSeconds, String label) {
    var a = new VideoAnnotation();
    a.setAppId(appId);
    a.setStartSeconds(startSeconds);
    a.setEndSeconds(endSeconds);
    a.setLabel(label);
    return a;
  }
}
