package de.dlr.shepard.v2.video.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.videostreamreference.model.VideoStreamReference;
import jakarta.ws.rs.BadRequestException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PLUGIN-REF-HANDLER-VIDEO — unit tests for {@link VideoStreamReferenceKindHandler}.
 * Covers {@link VideoStreamReferenceKindHandler#kind()},
 * {@link VideoStreamReferenceKindHandler#owns(de.dlr.shepard.context.references.basicreference.entities.BasicReference)},
 * and the binary-upload rejection guard on {@link VideoStreamReferenceKindHandler#create}.
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
  void createRejectsBinaryUpload() {
    assertThrows(BadRequestException.class, () -> handler.create("do-app-1", Map.of()));
  }
}
