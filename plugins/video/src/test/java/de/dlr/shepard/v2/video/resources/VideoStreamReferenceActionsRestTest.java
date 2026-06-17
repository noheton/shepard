package de.dlr.shepard.v2.video.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.context.references.videostreamreference.services.VideoStreamReferenceService;
import de.dlr.shepard.storage.StorageGetResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.ByteArrayInputStream;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-VIDEO-STREAMREF-PATH — plain-Mockito unit tests for
 * {@link VideoStreamReferenceActionsRest}.
 *
 * <p>Covers: 401/403/404 guards and the happy-path download cases
 * (200 full body, 206 partial, 416 unsatisfiable range).
 */
class VideoStreamReferenceActionsRestTest {

  static final String DO_APP_ID = "do-app-id-abc";
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

  VideoStreamReferenceActionsRest resource;

  DataObject dataObject;
  VideoStreamReference ref;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new VideoStreamReferenceActionsRest();
    resource.videoStreamReferenceService = videoStreamReferenceService;
    resource.videoStreamReferenceDAO = videoStreamReferenceDAO;
    resource.permissionsService = permissionsService;

    dataObject = new DataObject();
    dataObject.setId(42L);
    dataObject.setShepardId(42L);
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
    when(videoStreamReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(ref);
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Read, CALLER)).thenReturn(true);
  }

  // ── auth guards ────────────────────────────────────────────────────────────

  @Test
  void download_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.download(REF_APP_ID, null, sc);
    assertThat(r.getStatus()).isEqualTo(401);
  }

  @Test
  void download_returns404WhenRefMissing() {
    when(videoStreamReferenceDAO.findByAppId(REF_APP_ID)).thenReturn(null);
    Response r = resource.download(REF_APP_ID, null, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void download_returns404WhenRefDeleted() {
    ref.setDeleted(true);
    Response r = resource.download(REF_APP_ID, null, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void download_returns404WhenRefHasNullDataObject() {
    ref.setDataObject(null);
    Response r = resource.download(REF_APP_ID, null, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void download_returns403WhenNoReadPermission() {
    when(permissionsService.isAccessAllowedForDataObjectAppId(DO_APP_ID, AccessType.Read, CALLER)).thenReturn(false);
    Response r = resource.download(REF_APP_ID, null, sc);
    assertThat(r.getStatus()).isEqualTo(403);
  }

  // ── happy paths ────────────────────────────────────────────────────────────

  private StorageGetResponse fakePayload(byte[] bytes) {
    return new StorageGetResponse(
      "gridfs", "test.mp4", "video/mp4", (long) bytes.length, new ByteArrayInputStream(bytes)
    );
  }

  @Test
  void download_noRangeHeader_returns200WithAcceptRanges() throws Exception {
    byte[] bytes = new byte[100];
    when(videoStreamReferenceService.getPayload(ref)).thenReturn(fakePayload(bytes));

    Response r = resource.download(REF_APP_ID, null, sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(r.getHeaderString("Accept-Ranges")).isEqualTo("bytes");
    assertThat(r.getHeaderString("Content-Length")).isEqualTo("100");
    assertThat(r.getHeaderString("Content-Disposition")).contains("test.mp4");
  }

  @Test
  void download_validRange_returns206() throws Exception {
    byte[] bytes = new byte[1000];
    when(videoStreamReferenceService.getPayload(ref)).thenReturn(fakePayload(bytes));

    Response r = resource.download(REF_APP_ID, "bytes=100-199", sc);
    assertThat(r.getStatus()).isEqualTo(206);
    assertThat(r.getHeaderString("Content-Range")).isEqualTo("bytes 100-199/1000");
    assertThat(r.getHeaderString("Content-Length")).isEqualTo("100");
    assertThat(r.getHeaderString("Accept-Ranges")).isEqualTo("bytes");
  }

  @Test
  void download_openEndedRange_returns206ToEnd() throws Exception {
    byte[] bytes = new byte[500];
    when(videoStreamReferenceService.getPayload(ref)).thenReturn(fakePayload(bytes));

    Response r = resource.download(REF_APP_ID, "bytes=200-", sc);
    assertThat(r.getStatus()).isEqualTo(206);
    assertThat(r.getHeaderString("Content-Range")).isEqualTo("bytes 200-499/500");
    assertThat(r.getHeaderString("Content-Length")).isEqualTo("300");
  }

  @Test
  void download_unsatisfiableRange_returns416() throws Exception {
    byte[] bytes = new byte[100];
    when(videoStreamReferenceService.getPayload(ref)).thenReturn(fakePayload(bytes));

    Response r = resource.download(REF_APP_ID, "bytes=500-600", sc);
    assertThat(r.getStatus()).isEqualTo(416);
    assertThat(r.getHeaderString("Content-Range")).isEqualTo("bytes */100");
    assertThat(r.getHeaderString("Accept-Ranges")).isEqualTo("bytes");
  }

  @Test
  void download_unknownTotalSize_fallsBackToFullBody() throws Exception {
    byte[] bytes = new byte[50];
    StorageGetResponse legacy = new StorageGetResponse(
      "gridfs", "legacy.mp4", "video/mp4", null, new ByteArrayInputStream(bytes)
    );
    when(videoStreamReferenceService.getPayload(ref)).thenReturn(legacy);

    Response r = resource.download(REF_APP_ID, "bytes=10-20", sc);
    assertThat(r.getStatus()).isEqualTo(200);
    assertThat(r.getHeaderString("Accept-Ranges")).isEqualTo("bytes");
  }

  @Test
  void download_malformedRange_returns416() throws Exception {
    byte[] bytes = new byte[100];
    when(videoStreamReferenceService.getPayload(ref)).thenReturn(fakePayload(bytes));

    Response r = resource.download(REF_APP_ID, "bytes=abc-def", sc);
    assertThat(r.getStatus()).isEqualTo(416);
  }
}
