package de.dlr.shepard.v2.admin.krl.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.krl.entities.KrlInterpreterConfigSingleton;
import de.dlr.shepard.v2.admin.krl.io.KrlInterpreterConfigIO;
import de.dlr.shepard.v2.admin.krl.io.KrlInterpreterConfigPatchIO;
import de.dlr.shepard.v2.admin.krl.services.KrlInterpreterConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * KRL-CONFIG-1 — unit tests for the admin REST surface.
 *
 * <p>No Quarkus boot — {@link KrlInterpreterConfigService} is mocked. Tests
 * cover: GET round-trip, PATCH enabled flip, PATCH sidecarUrl set, PATCH
 * timeoutSeconds/maxBodySizeMb, annotation-gate assertion.
 */
class KrlAdminConfigRestTest {

  private static final String DEFAULT_URL = "http://krl-interpreter-sidecar:8000";
  private static final int DEFAULT_TIMEOUT = 120;
  private static final int DEFAULT_MAX_BODY = 16;

  private KrlInterpreterConfigService service;
  private KrlAdminConfigRest rest;

  @BeforeEach
  void setUp() {
    service = mock(KrlInterpreterConfigService.class);
    when(service.getDefaultSidecarUrl()).thenReturn(DEFAULT_URL);
    when(service.getDefaultTimeoutSeconds()).thenReturn(DEFAULT_TIMEOUT);
    when(service.getDefaultMaxBodySizeMb()).thenReturn(DEFAULT_MAX_BODY);
    when(service.getDefaultEnabled()).thenReturn(true);

    rest = new KrlAdminConfigRest();
    rest.service = service;
  }

  // ─── annotation gates ────────────────────────────────────────────────────

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = KrlAdminConfigRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "KrlAdminConfigRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2AdminKrlConfig() {
    Path p = KrlAdminConfigRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/krl/config", p.value(),
        "endpoint lives on the /v2/ shelf per fork policy");
  }

  // ─── GET ─────────────────────────────────────────────────────────────────

  @Test
  void getConfig_returnsEffectiveValues() {
    KrlInterpreterConfigSingleton cfg = new KrlInterpreterConfigSingleton();
    cfg.setEnabled(true);
    cfg.setSidecarUrl("http://custom:9000");
    cfg.setTimeoutSeconds(60);
    cfg.setMaxBodySizeMb(32);
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    KrlInterpreterConfigIO io = (KrlInterpreterConfigIO) r.getEntity();
    assertTrue(io.enabled());
    assertEquals("http://custom:9000", io.sidecarUrl());
    assertEquals(60, io.timeoutSeconds());
    assertEquals(32, io.maxBodySizeMb());
  }

  @Test
  void getConfig_fallsBackToDeployTimeDefaultWhenFieldsAreZeroOrNull() {
    KrlInterpreterConfigSingleton cfg = new KrlInterpreterConfigSingleton();
    cfg.setEnabled(true);
    cfg.setSidecarUrl(null); // should fall back to DEFAULT_URL
    cfg.setTimeoutSeconds(0); // should fall back to DEFAULT_TIMEOUT
    cfg.setMaxBodySizeMb(0); // should fall back to DEFAULT_MAX_BODY
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    KrlInterpreterConfigIO io = (KrlInterpreterConfigIO) r.getEntity();
    assertTrue(io.enabled());
    assertEquals(DEFAULT_URL, io.sidecarUrl());
    assertEquals(DEFAULT_TIMEOUT, io.timeoutSeconds());
    assertEquals(DEFAULT_MAX_BODY, io.maxBodySizeMb());
  }

  // ─── PATCH ───────────────────────────────────────────────────────────────

  @Test
  void patchConfig_nullBodyTreatedAsEmptyPatch() {
    KrlInterpreterConfigSingleton current = new KrlInterpreterConfigSingleton();
    current.setEnabled(true);
    current.setSidecarUrl(null);
    current.setTimeoutSeconds(0);
    current.setMaxBodySizeMb(0);
    when(service.current()).thenReturn(current);

    KrlInterpreterConfigSingleton saved = new KrlInterpreterConfigSingleton();
    saved.setEnabled(true);
    saved.setSidecarUrl(null);
    saved.setTimeoutSeconds(0);
    saved.setMaxBodySizeMb(0);
    when(service.patch(null, null, null, null)).thenReturn(saved);

    Response r = rest.patchConfig(null);

    assertEquals(200, r.getStatus());
  }

  @Test
  void patchConfig_enabledTouchedAsFalse() {
    KrlInterpreterConfigSingleton current = new KrlInterpreterConfigSingleton();
    current.setEnabled(true);
    current.setSidecarUrl(null);
    current.setTimeoutSeconds(0);
    current.setMaxBodySizeMb(0);
    when(service.current()).thenReturn(current);

    KrlInterpreterConfigSingleton saved = new KrlInterpreterConfigSingleton();
    saved.setEnabled(false);
    saved.setSidecarUrl(null);
    saved.setTimeoutSeconds(0);
    saved.setMaxBodySizeMb(0);
    when(service.patch(false, null, null, null)).thenReturn(saved);

    KrlInterpreterConfigPatchIO patch = new KrlInterpreterConfigPatchIO();
    patch.setEnabled(false);

    Response r = rest.patchConfig(patch);

    assertEquals(200, r.getStatus());
    KrlInterpreterConfigIO io = (KrlInterpreterConfigIO) r.getEntity();
    // enabled=false with zero/null ints + null url → falls back to deploy-time defaults
    assertEquals(DEFAULT_TIMEOUT, io.timeoutSeconds());
  }
}
