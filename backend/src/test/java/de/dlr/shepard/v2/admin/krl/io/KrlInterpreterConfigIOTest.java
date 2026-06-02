package de.dlr.shepard.v2.admin.krl.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.v2.admin.krl.entities.KrlInterpreterConfigSingleton;
import org.junit.jupiter.api.Test;

/** KRL-CONFIG-1 — projection from entity → IO, with deploy-time-default fallback. */
class KrlInterpreterConfigIOTest {

  private static final String DEFAULT_URL = "http://krl-interpreter-sidecar:8000";
  private static final int DEFAULT_TIMEOUT = 120;
  private static final int DEFAULT_MAX_BODY = 16;

  @Test
  void from_runtimeUrlWinsOverDefault() {
    KrlInterpreterConfigSingleton cfg = new KrlInterpreterConfigSingleton();
    cfg.setEnabled(true);
    cfg.setSidecarUrl("http://custom-sidecar:9000");
    cfg.setTimeoutSeconds(60);
    cfg.setMaxBodySizeMb(32);

    KrlInterpreterConfigIO io =
        KrlInterpreterConfigIO.from(cfg, DEFAULT_URL, DEFAULT_TIMEOUT, DEFAULT_MAX_BODY);

    assertTrue(io.enabled());
    assertEquals("http://custom-sidecar:9000", io.sidecarUrl());
    assertEquals(60, io.timeoutSeconds());
    assertEquals(32, io.maxBodySizeMb());
  }

  @Test
  void from_nullSidecarUrlFallsBackToDefault() {
    KrlInterpreterConfigSingleton cfg = new KrlInterpreterConfigSingleton();
    cfg.setEnabled(false);
    cfg.setSidecarUrl(null);
    cfg.setTimeoutSeconds(0); // 0 = use default

    KrlInterpreterConfigIO io =
        KrlInterpreterConfigIO.from(cfg, DEFAULT_URL, DEFAULT_TIMEOUT, DEFAULT_MAX_BODY);

    assertFalse(io.enabled());
    assertEquals(DEFAULT_URL, io.sidecarUrl());
    assertEquals(DEFAULT_TIMEOUT, io.timeoutSeconds());
    assertEquals(DEFAULT_MAX_BODY, io.maxBodySizeMb());
  }

  @Test
  void from_blankSidecarUrlFallsBackToDefault() {
    KrlInterpreterConfigSingleton cfg = new KrlInterpreterConfigSingleton();
    cfg.setEnabled(true);
    cfg.setSidecarUrl("   ");
    cfg.setTimeoutSeconds(0);
    cfg.setMaxBodySizeMb(0);

    KrlInterpreterConfigIO io =
        KrlInterpreterConfigIO.from(cfg, DEFAULT_URL, DEFAULT_TIMEOUT, DEFAULT_MAX_BODY);

    assertEquals(DEFAULT_URL, io.sidecarUrl());
    assertEquals(DEFAULT_TIMEOUT, io.timeoutSeconds());
    assertEquals(DEFAULT_MAX_BODY, io.maxBodySizeMb());
  }

  @Test
  void from_zeroIntsFallsBackToDefault() {
    KrlInterpreterConfigSingleton cfg = new KrlInterpreterConfigSingleton();
    cfg.setEnabled(true);
    cfg.setSidecarUrl(null);
    cfg.setTimeoutSeconds(0);
    cfg.setMaxBodySizeMb(0);

    KrlInterpreterConfigIO io =
        KrlInterpreterConfigIO.from(cfg, DEFAULT_URL, DEFAULT_TIMEOUT, DEFAULT_MAX_BODY);

    assertEquals(DEFAULT_TIMEOUT, io.timeoutSeconds());
    assertEquals(DEFAULT_MAX_BODY, io.maxBodySizeMb());
  }

  @Test
  void from_bothNullProducesNullUrl() {
    KrlInterpreterConfigSingleton cfg = new KrlInterpreterConfigSingleton();
    cfg.setEnabled(false);
    cfg.setSidecarUrl(null);
    cfg.setTimeoutSeconds(30);
    cfg.setMaxBodySizeMb(8);

    KrlInterpreterConfigIO io = KrlInterpreterConfigIO.from(cfg, null, DEFAULT_TIMEOUT, DEFAULT_MAX_BODY);

    assertNull(io.sidecarUrl());
    assertEquals(30, io.timeoutSeconds());
    assertEquals(8, io.maxBodySizeMb());
  }

  @Test
  void from_positiveRuntimeIntsTakePrecedenceOverDefault() {
    KrlInterpreterConfigSingleton cfg = new KrlInterpreterConfigSingleton();
    cfg.setEnabled(true);
    cfg.setSidecarUrl(null);
    cfg.setTimeoutSeconds(300);
    cfg.setMaxBodySizeMb(64);

    KrlInterpreterConfigIO io =
        KrlInterpreterConfigIO.from(cfg, DEFAULT_URL, DEFAULT_TIMEOUT, DEFAULT_MAX_BODY);

    assertEquals(300, io.timeoutSeconds());
    assertEquals(64, io.maxBodySizeMb());
  }
}
