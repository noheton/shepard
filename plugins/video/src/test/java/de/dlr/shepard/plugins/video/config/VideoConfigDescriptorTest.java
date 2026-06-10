package de.dlr.shepard.plugins.video.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.video.entities.VideoConfig;
import de.dlr.shepard.plugins.video.io.VideoConfigIO;
import de.dlr.shepard.plugins.video.services.VideoConfigService;
import de.dlr.shepard.plugins.video.services.VideoConfigService.VideoPatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** V2CONV-A7 — unit tests for {@link VideoConfigDescriptor}. */
class VideoConfigDescriptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private VideoConfigService service;
  private VideoConfigDescriptor descriptor;

  private static VideoConfig cfg(boolean ffprobe, Long maxMb) {
    VideoConfig c = new VideoConfig();
    c.setFfprobeEnabled(ffprobe);
    c.setMaxFileSizeMb(maxMb);
    return c;
  }

  @BeforeEach
  void setUp() {
    service = Mockito.mock(VideoConfigService.class);
    descriptor = new VideoConfigDescriptor();
    descriptor.service = service;
  }

  @Test
  void featureNameIsVideo() {
    assertEquals("video", descriptor.featureName());
  }

  @Test
  void descriptionIsNonBlank() {
    String desc = descriptor.description();
    assertNotNull(desc);
    assertTrue(desc.length() > 10);
  }

  @Test
  void currentShape_delegatesToService() {
    VideoConfig c = cfg(true, 500L);
    Mockito.when(service.current()).thenReturn(c);

    VideoConfigIO io = descriptor.currentShape();

    assertTrue(io.ffprobeEnabled());
    assertEquals(500L, io.maxFileSizeMb());
  }

  @Test
  void currentShape_unlimitedWhenMaxFileSizeMbNull() {
    Mockito.when(service.current()).thenReturn(cfg(true, null));

    VideoConfigIO io = descriptor.currentShape();

    assertNull(io.maxFileSizeMb(), "null maxFileSizeMb = unlimited");
  }

  @Test
  void ffprobeEnabledFlip() throws Exception {
    ArgumentCaptor<VideoPatch> cap = ArgumentCaptor.forClass(VideoPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(false, null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"ffprobeEnabled\":false}"));

    assertEquals(Boolean.FALSE, cap.getValue().ffprobeEnabled);
  }

  @Test
  void explicitNullOnFfprobeEnabledLeavesAlone() throws Exception {
    ArgumentCaptor<VideoPatch> cap = ArgumentCaptor.forClass(VideoPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(true, null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"ffprobeEnabled\":null}"));

    assertNull(cap.getValue().ffprobeEnabled, "null ffprobeEnabled must leave field untouched");
  }

  @Test
  void maxFileSizeMbSet() throws Exception {
    ArgumentCaptor<VideoPatch> cap = ArgumentCaptor.forClass(VideoPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(true, 1024L));

    descriptor.applyMergePatch(MAPPER.readTree("{\"maxFileSizeMb\":1024}"));

    assertEquals(1024L, cap.getValue().maxFileSizeMb);
    assertTrue(cap.getValue().maxFileSizeMbTouched);
  }

  @Test
  void maxFileSizeMbNullClears() throws Exception {
    ArgumentCaptor<VideoPatch> cap = ArgumentCaptor.forClass(VideoPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(true, null));

    descriptor.applyMergePatch(MAPPER.readTree("{\"maxFileSizeMb\":null}"));

    assertNull(cap.getValue().maxFileSizeMb, "explicit null clears the cap");
    assertTrue(cap.getValue().maxFileSizeMbTouched, "touched flag must be set even for null");
  }

  @Test
  void absentFieldsAreNotTouched() throws Exception {
    ArgumentCaptor<VideoPatch> cap = ArgumentCaptor.forClass(VideoPatch.class);
    Mockito.when(service.patch(cap.capture())).thenReturn(cfg(true, null));

    descriptor.applyMergePatch(MAPPER.readTree("{}"));

    assertNull(cap.getValue().ffprobeEnabled, "absent ffprobeEnabled → null (leave alone)");
    assertTrue(!cap.getValue().maxFileSizeMbTouched, "absent maxFileSizeMb → touched=false");
  }
}
