package de.dlr.shepard.plugins.video.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.video.daos.VideoConfigDAO;
import de.dlr.shepard.plugins.video.entities.VideoConfig;
import de.dlr.shepard.plugins.video.services.VideoConfigService.VideoPatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * VID1c — unit tests for {@link VideoConfigService}.
 */
class VideoConfigServiceTest {

  private VideoConfigDAO dao;
  private VideoConfigService service;

  @BeforeEach
  void setUp() {
    dao = mock(VideoConfigDAO.class);
    service = new VideoConfigService();
    service.dao = dao;
    service.installDefaultFfprobeEnabled = true;
  }

  // ─── seedIfNeeded ────────────────────────────────────────────────────────

  @Test
  void seedIfNeeded_createsNodeWhenAbsent() {
    when(dao.findSingleton()).thenReturn(null);
    VideoConfig seeded = new VideoConfig(42L);
    seeded.setFfprobeEnabled(true);
    when(dao.createOrUpdate(any())).thenReturn(seeded);

    VideoConfig result = service.seedIfNeeded();

    assertNotNull(result);
    assertTrue(result.isFfprobeEnabled());
    verify(dao).createOrUpdate(any());
  }

  @Test
  void seedIfNeeded_isIdempotentWhenNodeExists() {
    VideoConfig existing = new VideoConfig(1L);
    existing.setFfprobeEnabled(false);
    when(dao.findSingleton()).thenReturn(existing);

    VideoConfig result = service.seedIfNeeded();

    assertEquals(existing, result);
    verify(dao, never()).createOrUpdate(any());
  }

  // ─── current ─────────────────────────────────────────────────────────────

  @Test
  void current_returnsSingletonWhenPresent() {
    VideoConfig cfg = new VideoConfig(10L);
    cfg.setFfprobeEnabled(true);
    when(dao.findSingleton()).thenReturn(cfg);

    VideoConfig result = service.current();

    assertEquals(cfg, result);
  }

  @Test
  void current_seedsWhenAbsent() {
    VideoConfig seeded = new VideoConfig(99L);
    seeded.setFfprobeEnabled(true);
    // First call (findSingleton in current) returns null; second (in seedIfNeeded) also null.
    when(dao.findSingleton()).thenReturn(null);
    when(dao.createOrUpdate(any())).thenReturn(seeded);

    VideoConfig result = service.current();

    assertNotNull(result);
    verify(dao).createOrUpdate(any());
  }

  // ─── patch ───────────────────────────────────────────────────────────────

  @Test
  void patch_flipsFfprobeEnabled() {
    VideoConfig cfg = new VideoConfig(5L);
    cfg.setFfprobeEnabled(true);
    when(dao.findSingleton()).thenReturn(cfg);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    VideoPatch patch = new VideoPatch();
    patch.ffprobeEnabled = Boolean.FALSE;

    VideoConfig result = service.patch(patch);

    assertFalse(result.isFfprobeEnabled());
  }

  @Test
  void patch_setsMaxFileSizeMb() {
    VideoConfig cfg = new VideoConfig(5L);
    cfg.setFfprobeEnabled(true);
    when(dao.findSingleton()).thenReturn(cfg);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    VideoPatch patch = new VideoPatch();
    patch.maxFileSizeMbTouched = true;
    patch.maxFileSizeMb = 2048L;

    VideoConfig result = service.patch(patch);

    assertEquals(2048L, result.getMaxFileSizeMb());
  }

  @Test
  void patch_clearsMaxFileSizeMbWhenNullWithTouched() {
    VideoConfig cfg = new VideoConfig(5L);
    cfg.setFfprobeEnabled(true);
    cfg.setMaxFileSizeMb(500L);
    when(dao.findSingleton()).thenReturn(cfg);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    VideoPatch patch = new VideoPatch();
    patch.maxFileSizeMbTouched = true;
    patch.maxFileSizeMb = null; // clear

    VideoConfig result = service.patch(patch);

    assertNull(result.getMaxFileSizeMb(), "explicit null with touched=true must clear the cap");
  }

  @Test
  void patch_leavesMaxFileSizeMbAloneWhenNotTouched() {
    VideoConfig cfg = new VideoConfig(5L);
    cfg.setFfprobeEnabled(true);
    cfg.setMaxFileSizeMb(500L);
    when(dao.findSingleton()).thenReturn(cfg);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    VideoPatch patch = new VideoPatch();
    // maxFileSizeMbTouched = false (default), maxFileSizeMb = null — "absent" in RFC 7396
    patch.ffprobeEnabled = Boolean.FALSE; // touch something else

    VideoConfig result = service.patch(patch);

    assertEquals(500L, result.getMaxFileSizeMb(), "absent field must not clear existing value");
  }

  @Test
  void patch_absentFfprobeEnabled_leavesCurrentValue() {
    VideoConfig cfg = new VideoConfig(5L);
    cfg.setFfprobeEnabled(true);
    when(dao.findSingleton()).thenReturn(cfg);
    when(dao.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    VideoPatch patch = new VideoPatch();
    // ffprobeEnabled = null = absent, maxFileSizeMbTouched = true
    patch.maxFileSizeMbTouched = true;
    patch.maxFileSizeMb = 100L;

    VideoConfig result = service.patch(patch);

    assertTrue(result.isFfprobeEnabled(), "absent ffprobeEnabled must not change the existing value");
  }
}
