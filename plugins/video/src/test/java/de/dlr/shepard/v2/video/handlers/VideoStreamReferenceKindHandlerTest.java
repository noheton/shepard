package de.dlr.shepard.v2.video.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import jakarta.ws.rs.BadRequestException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-VIDEO-STREAMREF-PATH — unit tests for {@link VideoStreamReferenceKindHandler}.
 *
 * <p>Covers {@link VideoStreamReferenceKindHandler#kind()},
 * {@link VideoStreamReferenceKindHandler#owns},
 * and the name-validation guards on {@link VideoStreamReferenceKindHandler#create}.
 * Integration tests for uploadContent/downloadContent are in the Quarkus IT layer.
 */
class VideoStreamReferenceKindHandlerTest {

  VideoStreamReferenceKindHandler handler;

  @BeforeEach
  void setUp() {
    handler = new VideoStreamReferenceKindHandler();
  }

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

  // ── VID-FFMPEG-TRANSCODE-2026-06-29 — proxy fields in toIO ────────────────

  /** BasicReferenceIO requires a non-null dataObject on the source ref. */
  private static VideoStreamReference newRefWithParent() {
    var ref = new VideoStreamReference();
    ref.setAppId("ref-1");
    var parent = new de.dlr.shepard.context.collection.entities.DataObject();
    parent.setId(123L);
    parent.setShepardId(123L);
    ref.setDataObject(parent);
    return ref;
  }

  @Test
  void toIOSurfacesProxyAvailableTrueWhenReady() {
    var ref = newRefWithParent();
    ref.setProxyStatus("READY");
    ref.setProxyStorageLocator("gridfs:container:proxy-oid");
    var io = handler.toIO(ref);

    assertEquals(Boolean.TRUE, io.getPayload().get("proxyAvailable"));
    assertEquals("READY", io.getPayload().get("proxyStatus"));
    assertEquals("gridfs:container:proxy-oid", io.getPayload().get("proxyOid"));
  }

  @Test
  void toIOSurfacesProxyAvailableFalseWhenPending() {
    var ref = newRefWithParent();
    ref.setProxyStatus("PENDING");
    var io = handler.toIO(ref);

    assertEquals(Boolean.FALSE, io.getPayload().get("proxyAvailable"));
    assertEquals("PENDING", io.getPayload().get("proxyStatus"));
  }

  @Test
  void toIOTreatsNullStatusAsUnavailable() {
    var ref = newRefWithParent();
    var io = handler.toIO(ref);

    assertEquals(Boolean.FALSE, io.getPayload().get("proxyAvailable"));
    assertEquals(null, io.getPayload().get("proxyStatus"));
    assertEquals(null, io.getPayload().get("proxyOid"));
  }

  @Test
  void toIOTreatsFailedAsUnavailableForFallback() {
    var ref = newRefWithParent();
    ref.setProxyStatus("FAILED");
    var io = handler.toIO(ref);

    assertEquals(Boolean.FALSE, io.getPayload().get("proxyAvailable"));
    assertEquals("FAILED", io.getPayload().get("proxyStatus"));
  }
}
