package de.dlr.shepard.v2.admin.jupyter.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.v2.admin.jupyter.entities.JupyterConfig;
import org.junit.jupiter.api.Test;

/** J1e — projection from entity → IO, with deploy-time-default fallback. */
class JupyterConfigIOTest {

  @Test
  void from_runtimeValueWinsOverDefault() {
    JupyterConfig cfg = new JupyterConfig();
    cfg.setEnabled(true);
    cfg.setHubUrl("https://runtime.test");

    JupyterConfigIO io = JupyterConfigIO.from(cfg, "https://default.test");

    assertTrue(io.enabled());
    assertEquals("https://runtime.test", io.hubUrl());
  }

  @Test
  void from_nullHubUrlFallsBackToDefault() {
    JupyterConfig cfg = new JupyterConfig();
    cfg.setEnabled(false);
    cfg.setHubUrl(null);

    JupyterConfigIO io = JupyterConfigIO.from(cfg, "https://default.test");

    assertFalse(io.enabled());
    assertEquals("https://default.test", io.hubUrl());
  }

  @Test
  void from_blankHubUrlFallsBackToDefault() {
    JupyterConfig cfg = new JupyterConfig();
    cfg.setEnabled(true);
    cfg.setHubUrl("   ");

    JupyterConfigIO io = JupyterConfigIO.from(cfg, "https://default.test");

    assertEquals("https://default.test", io.hubUrl());
  }

  @Test
  void from_bothNullProducesNull() {
    JupyterConfig cfg = new JupyterConfig();
    cfg.setEnabled(false);

    JupyterConfigIO io = JupyterConfigIO.from(cfg, null);

    assertNull(io.hubUrl());
  }
}
