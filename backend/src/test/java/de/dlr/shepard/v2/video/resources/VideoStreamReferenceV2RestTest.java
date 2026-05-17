package de.dlr.shepard.v2.video.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.io.VideoStreamReferenceIO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.context.references.videostreamreference.services.VideoStreamReferenceService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * VID1a — plain-Mockito unit tests for {@link VideoStreamReferenceV2Rest}.
 *
 * <p>Covers: 401/403/404 guards, happy-path GET list/single, and DELETE.
 * The POST upload path requires a real multipart FileUpload so it is
 * tested at a higher level; the permission guards follow the same pattern
 * as the other endpoints and are exercised here for coverage.
 */
class VideoStreamReferenceV2RestTest {

  static final String DO_APP_ID = "do-app-id-abc";
  static final long DO_OGM_ID = 42L;
  static final String REF_APP_ID = "ref-app-id-xyz";
  static final String CALLER = "alice";

  @Mock
  VideoStreamReferenceService videoStreamReferenceService;

  @Mock
  VideoStreamReferenceDAO videoStreamReferenceDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext sc;

  @Mock
  Principal principal;

  VideoStreamReferenceV2Rest resource;

  DataObject dataObject;
  VideoStreamReference ref;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new VideoStreamReferenceV2Rest();
    resource.videoStreamReferenceService = videoStreamReferenceService;
    resource.videoStreamReferenceDAO = videoStreamReferenceDAO;
    resource.permissionsService = permissionsService;

    dataObject = new DataObject();
    dataObject.setId(DO_OGM_ID);
    dataObject.setShepardId(DO_OGM_ID);
    dataObject.setAppId(DO_APP_ID);

    ref = new VideoStreamReference();
    ref.setId(7L);
    ref.setShepardId(7L);
    ref.setAppId(REF_APP_ID);
    ref.setName("test-video");
    ref.setDataObject(dataObject);
    ref.setMimeType("video/mp4");

    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
    when(videoStreamReferenceService.getDataObjectOgmId(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER, 0L)).thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Write, CALLER, 0L)).thenReturn(true);
    when(videoStreamReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
  }

  // ── GET list ─────────────────────────────────────────────────────────────

  @Test
  void list_returns200WithRefs() {
    when(videoStreamReferenceService.listByDataObject(DO_APP_ID)).thenReturn(List.of(ref));
    Response r = resource.list(DO_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<VideoStreamReferenceIO> body = (List<VideoStreamReferenceIO>) r.getEntity();
    assertThat(body).hasSize(1);
    assertThat(body.get(0).getAppId()).isEqualTo(REF_APP_ID);
  }

  @Test
  void list_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.list(DO_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(401);
    verify(videoStreamReferenceService, never()).listByDataObject(anyString());
  }

  @Test
  void list_returns404WhenDataObjectMissing() {
    when(videoStreamReferenceService.getDataObjectOgmId(DO_APP_ID)).thenReturn(null);
    Response r = resource.list(DO_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void list_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER, 0L)).thenReturn(false);
    Response r = resource.list(DO_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(403);
    verify(videoStreamReferenceService, never()).listByDataObject(anyString());
  }

  // ── GET single ───────────────────────────────────────────────────────────

  @Test
  void getOne_returns200() {
    Response r = resource.getOne(DO_APP_ID, REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    VideoStreamReferenceIO io = (VideoStreamReferenceIO) r.getEntity();
    assertThat(io.getAppId()).isEqualTo(REF_APP_ID);
  }

  @Test
  void getOne_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.getOne(DO_APP_ID, REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  @Test
  void getOne_returns404WhenRefMissing() {
    when(videoStreamReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    Response r = resource.getOne(DO_APP_ID, REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void getOne_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, CALLER, 0L)).thenReturn(false);
    Response r = resource.getOne(DO_APP_ID, REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(403);
  }

  @Test
  void getOne_returns404WhenParentDataObjectMismatch() {
    // Ref's parent has a different appId than the one in the URL.
    dataObject.setAppId("different-do-app-id");
    Response r = resource.getOne(DO_APP_ID, REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ── DELETE ───────────────────────────────────────────────────────────────

  @Test
  void delete_returns204() {
    Response r = resource.delete(DO_APP_ID, REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(204);
    verify(videoStreamReferenceService).delete(ref);
  }

  @Test
  void delete_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.delete(DO_APP_ID, REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(401);
    verify(videoStreamReferenceService, never()).delete(any());
  }

  @Test
  void delete_returns404WhenRefMissing() {
    when(videoStreamReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    Response r = resource.delete(DO_APP_ID, REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
    verify(videoStreamReferenceService, never()).delete(any());
  }

  @Test
  void delete_returns403WhenNoWritePermission() {
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Write, CALLER, 0L)).thenReturn(false);
    Response r = resource.delete(DO_APP_ID, REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(403);
    verify(videoStreamReferenceService, never()).delete(any());
  }

  @Test
  void delete_returns404WhenParentDataObjectMismatch() {
    dataObject.setAppId("different-do-app-id");
    Response r = resource.delete(DO_APP_ID, REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
    verify(videoStreamReferenceService, never()).delete(any());
  }

  // ── getOne — ref with null dataObject ────────────────────────────────────

  @Test
  void getOne_returns404WhenRefHasNullDataObject() {
    ref.setDataObject(null);
    Response r = resource.getOne(DO_APP_ID, REF_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ── list — empty result ───────────────────────────────────────────────────

  @Test
  void list_returns200EmptyList() {
    when(videoStreamReferenceService.listByDataObject(DO_APP_ID)).thenReturn(List.of());
    Response r = resource.list(DO_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    List<VideoStreamReferenceIO> body = (List<VideoStreamReferenceIO>) r.getEntity();
    assertThat(body).isEmpty();
  }
}
