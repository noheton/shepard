package de.dlr.shepard.plugins.video.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * VID1c — unit tests for {@link VideoConfig} entity invariants.
 */
class VideoConfigTest {

  @Test
  void defaultFfprobeEnabledIsTrue() {
    VideoConfig cfg = new VideoConfig();
    assertTrue(cfg.isFfprobeEnabled(), "ffprobeEnabled must default to true");
  }

  @Test
  void defaultMaxFileSizeMbIsNull() {
    VideoConfig cfg = new VideoConfig();
    assertNull(cfg.getMaxFileSizeMb(), "maxFileSizeMb must default to null (unlimited)");
  }

  @Test
  void equalsBasedOnAppId() {
    VideoConfig a = new VideoConfig();
    a.setAppId("abc-123");
    VideoConfig b = new VideoConfig();
    b.setAppId("abc-123");
    VideoConfig c = new VideoConfig();
    c.setAppId("xyz-999");

    assertEquals(a, b, "same appId = equal");
    assertNotEquals(a, c, "different appId = not equal");
  }

  @Test
  void hashCodeBasedOnAppId() {
    VideoConfig a = new VideoConfig();
    a.setAppId("test-appid");
    VideoConfig b = new VideoConfig();
    b.setAppId("test-appid");

    assertEquals(a.hashCode(), b.hashCode(), "same appId must produce same hashCode");
  }
}
