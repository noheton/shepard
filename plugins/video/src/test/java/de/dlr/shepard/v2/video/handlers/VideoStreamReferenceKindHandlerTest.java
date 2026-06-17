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
}
