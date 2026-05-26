package de.dlr.shepard.plugins.video.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.video.entities.VideoConfig;
import de.dlr.shepard.plugins.video.io.VideoConfigIO;
import de.dlr.shepard.plugins.video.io.VideoConfigPatchIO;
import de.dlr.shepard.plugins.video.services.VideoConfigService;
import de.dlr.shepard.plugins.video.services.VideoConfigService.VideoPatch;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * VID1c — pure unit tests for {@link VideoAdminRest}.
 *
 * <p>No Quarkus test harness — mocks only, matching the
 * UnhideAdminRestTest pattern.
 */
class VideoAdminRestTest {

  private VideoConfigService service;
  private VideoAdminRest rest;

  @BeforeEach
  void setUp() {
    service = mock(VideoConfigService.class);
    rest = new VideoAdminRest();
    rest.service = service;
  }

  // ─── annotation gates ────────────────────────────────────────────────────

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = VideoAdminRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "VideoAdminRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2() {
    Path p = VideoAdminRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/video", p.value(), "endpoint lives on the /v2/ shelf per fork policy");
  }

  // ─── GET /config ─────────────────────────────────────────────────────────

  @Test
  void getConfig_returnsCurrentSingleton() {
    VideoConfig cfg = new VideoConfig();
    cfg.setFfprobeEnabled(true);
    cfg.setMaxFileSizeMb(500L);
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    VideoConfigIO body = (VideoConfigIO) r.getEntity();
    assertEquals(true, body.ffprobeEnabled());
    assertEquals(500L, body.maxFileSizeMb());
  }

  @Test
  void getConfig_unlimitedWhenMaxFileSizeMbNull() {
    VideoConfig cfg = new VideoConfig();
    cfg.setFfprobeEnabled(true);
    // maxFileSizeMb = null (unlimited)
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    VideoConfigIO body = (VideoConfigIO) r.getEntity();
    assertNull(body.maxFileSizeMb(), "null maxFileSizeMb = unlimited, must be absent in IO");
  }

  // ─── PATCH /config ───────────────────────────────────────────────────────

  @Test
  void patchConfig_appliesFfprobeEnabledFalse() {
    VideoConfig cfg = new VideoConfig();
    cfg.setFfprobeEnabled(false);
    when(service.patch(org.mockito.ArgumentMatchers.any(VideoPatch.class))).thenReturn(cfg);

    VideoConfigPatchIO body = new VideoConfigPatchIO();
    body.setFfprobeEnabled(Boolean.FALSE);

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    VideoConfigIO out = (VideoConfigIO) r.getEntity();
    assertEquals(false, out.ffprobeEnabled());
  }

  @Test
  void patchConfig_passesPatchFieldsThroughToService() {
    VideoConfig cfg = new VideoConfig();
    ArgumentCaptor<VideoPatch> captor = ArgumentCaptor.forClass(VideoPatch.class);
    when(service.patch(captor.capture())).thenReturn(cfg);

    VideoConfigPatchIO body = new VideoConfigPatchIO();
    body.setFfprobeEnabled(Boolean.TRUE);
    body.setMaxFileSizeMb(1024L); // setter flips maxFileSizeMbTouched

    rest.patchConfig(body);

    VideoPatch captured = captor.getValue();
    assertEquals(Boolean.TRUE, captured.ffprobeEnabled);
    assertEquals(1024L, captured.maxFileSizeMb);
    assertEquals(true, captured.maxFileSizeMbTouched, "setMaxFileSizeMb flips the touched flag");
  }

  @Test
  void patchConfig_maxFileSizeMbNull_clearsTheCap() {
    VideoConfig cfg = new VideoConfig();
    cfg.setFfprobeEnabled(true);
    // maxFileSizeMb stays null = unlimited after clear
    ArgumentCaptor<VideoPatch> captor = ArgumentCaptor.forClass(VideoPatch.class);
    when(service.patch(captor.capture())).thenReturn(cfg);

    VideoConfigPatchIO body = new VideoConfigPatchIO();
    body.setMaxFileSizeMb(null); // explicit null = clear the cap, touched=true

    rest.patchConfig(body);

    VideoPatch captured = captor.getValue();
    assertNull(captured.maxFileSizeMb, "explicit null clears the cap");
    assertEquals(true, captured.maxFileSizeMbTouched, "touched flag must be set even for null");
  }

  @Test
  void patchConfig_nullBody_treatedAsEmptyPatch() {
    VideoConfig cfg = new VideoConfig();
    when(service.patch(org.mockito.ArgumentMatchers.any(VideoPatch.class))).thenReturn(cfg);

    Response r = rest.patchConfig(null);

    assertEquals(200, r.getStatus(), "null body is a legal RFC 7396 no-op patch");
  }
}
