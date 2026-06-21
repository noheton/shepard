package de.dlr.shepard.v2.video.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.videostreamreference.daos.VideoStreamReferenceDAO;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import de.dlr.shepard.context.references.videostreamreference.services.VideoStreamReferenceService;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import jakarta.ws.rs.BadRequestException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-VIDEO-STREAMREF-PATH — unit tests for {@link VideoStreamReferenceKindHandler}.
 *
 * <p>Covers {@link VideoStreamReferenceKindHandler#kind()},
 * {@link VideoStreamReferenceKindHandler#owns},
 * the name-validation guards on {@link VideoStreamReferenceKindHandler#create},
 * and the REF-EDIT-2 patch cases for {@code name} and {@code wallClockTimestamp}.
 * Integration tests for uploadContent/downloadContent are in the Quarkus IT layer.
 */
class VideoStreamReferenceKindHandlerTest {

  @Mock
  VideoStreamReferenceService videoStreamReferenceService;

  @Mock
  VideoStreamReferenceDAO videoStreamReferenceDAO;

  @Mock
  UserService userService;

  @Mock
  DateHelper dateHelper;

  VideoStreamReferenceKindHandler handler;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new VideoStreamReferenceKindHandler();
    handler.videoStreamReferenceService = videoStreamReferenceService;
    handler.videoStreamReferenceDAO = videoStreamReferenceDAO;
    handler.userService = userService;
    handler.dateHelper = dateHelper;
    // Stub out the side-effects used by patch() on the changed path.
    when(userService.getCurrentUser()).thenReturn(new User());
    when(dateHelper.getDate()).thenReturn(new Date());
  }

  // ── kind / owns ─────────────────────────────────────────────────────────────

  @Test
  void kindIsVideo() {
    assertEquals("video", handler.kind());
  }

  @Test
  void ownsVideoStreamReference() {
    assertTrue(handler.owns(new VideoStreamReference()));
  }

  @Test
  void doesNotOwnOtherReference() {
    assertFalse(handler.owns(new FileReference()));
  }

  // ── create validation ────────────────────────────────────────────────────────

  @Test
  void createRejectsMissingName() {
    assertThrows(BadRequestException.class, () -> handler.create("do-app-1", Map.of()));
  }

  @Test
  void createRejectsBlankName() {
    Map<String, Object> body = new HashMap<>();
    body.put("name", "   ");
    assertThrows(BadRequestException.class, () -> handler.create("do-app-1", body));
  }

  @Test
  void createRejectsNullBody() {
    assertThrows(BadRequestException.class, () -> handler.create("do-app-1", null));
  }

  @Test
  void createRejectsNonStringName() {
    Map<String, Object> body = new HashMap<>();
    body.put("name", 42);
    assertThrows(BadRequestException.class, () -> handler.create("do-app-1", body));
  }

  // ── patch — REF-EDIT-2 ───────────────────────────────────────────────────────

  /**
   * patch_name_updatesName — patch {"name":"new"} → entity saved with new name.
   */
  @Test
  void patch_name_updatesName() {
    VideoStreamReference ref = refWithName("old-name");
    when(videoStreamReferenceService.findByAppId("app-1")).thenReturn(ref);
    when(videoStreamReferenceDAO.createOrUpdate(ref)).thenReturn(ref);

    Map<String, Object> patch = Map.of("name", "new-name");
    ReferenceV2IO result = handler.patch("app-1", patch);

    verify(videoStreamReferenceDAO).createOrUpdate(ref);
    assertEquals("new-name", ref.getName());
  }

  /**
   * patch_wallClockTimestamp_updatesTimestamp — patch with a numeric value → saved.
   */
  @Test
  void patch_wallClockTimestamp_updatesTimestamp() {
    VideoStreamReference ref = refWithName("video.mp4");
    ref.setWallClockTimestamp(null);
    when(videoStreamReferenceService.findByAppId("app-2")).thenReturn(ref);
    when(videoStreamReferenceDAO.createOrUpdate(ref)).thenReturn(ref);

    Map<String, Object> patch = new HashMap<>();
    patch.put("wallClockTimestamp", 1700000000000L);
    handler.patch("app-2", patch);

    verify(videoStreamReferenceDAO).createOrUpdate(ref);
    assertEquals(Long.valueOf(1700000000000L), ref.getWallClockTimestamp());
  }

  /**
   * patch_wallClockTimestampNull_clearsTimestamp — explicit null clears the field.
   */
  @Test
  void patch_wallClockTimestampNull_clearsTimestamp() {
    VideoStreamReference ref = refWithName("video.mp4");
    ref.setWallClockTimestamp(9999999999999L);
    when(videoStreamReferenceService.findByAppId("app-3")).thenReturn(ref);
    when(videoStreamReferenceDAO.createOrUpdate(ref)).thenReturn(ref);

    Map<String, Object> patch = new HashMap<>();
    patch.put("wallClockTimestamp", null);
    handler.patch("app-3", patch);

    verify(videoStreamReferenceDAO).createOrUpdate(ref);
    assertNull(ref.getWallClockTimestamp());
  }

  /**
   * patch_absentKey_isNoOp — empty patch → no createOrUpdate call.
   */
  @Test
  void patch_absentKey_isNoOp() {
    VideoStreamReference ref = refWithName("video.mp4");
    ref.setWallClockTimestamp(1234L);
    when(videoStreamReferenceService.findByAppId("app-4")).thenReturn(ref);

    handler.patch("app-4", Map.of());

    verify(videoStreamReferenceDAO, never()).createOrUpdate(any());
    // Original values must be unchanged.
    assertEquals("video.mp4", ref.getName());
    assertEquals(Long.valueOf(1234L), ref.getWallClockTimestamp());
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private static VideoStreamReference refWithName(String name) {
    VideoStreamReference ref = new VideoStreamReference();
    ref.setName(name);
    return ref;
  }
}
