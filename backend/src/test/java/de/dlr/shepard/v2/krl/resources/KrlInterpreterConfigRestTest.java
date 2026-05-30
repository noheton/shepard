package de.dlr.shepard.v2.krl.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.krl.entities.KrlInterpreterConfigEntity;
import de.dlr.shepard.v2.krl.io.KrlInterpreterConfigIO;
import de.dlr.shepard.v2.krl.io.KrlInterpreterConfigPatchIO;
import de.dlr.shepard.v2.krl.services.KrlInterpreterConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * KRL-CONFIG-1 — unit tests for the admin REST surface.
 *
 * <p>No Quarkus boot — {@link KrlInterpreterConfigService} is mocked.
 * Tests cover: GET round-trip, PATCH sidecarUrl set, PATCH timeout set,
 * PATCH maxBodySizeMb set, PATCH null-clears-to-default, validation
 * guards for blank sidecarUrl / negative timeout / negative maxBody,
 * annotation-gate assertions.
 */
class KrlInterpreterConfigRestTest {

  private static final String DEFAULT_URL = "http://krl-interpreter-sidecar:8000";
  private static final int DEFAULT_TIMEOUT = 120;
  private static final int DEFAULT_MAX_BODY = 16;

  private KrlInterpreterConfigService service;
  private KrlInterpreterConfigRest rest;

  @BeforeEach
  void setUp() {
    service = mock(KrlInterpreterConfigService.class);
    when(service.getDefaultSidecarUrl()).thenReturn(DEFAULT_URL);
    when(service.getDefaultTimeoutSeconds()).thenReturn(DEFAULT_TIMEOUT);
    when(service.getDefaultMaxBodySizeMb()).thenReturn(DEFAULT_MAX_BODY);

    rest = new KrlInterpreterConfigRest();
    rest.service = service;
  }

  // ─── annotation gates ────────────────────────────────────────────────────

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = KrlInterpreterConfigRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "KrlInterpreterConfigRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2AdminPluginsKrlConfig() {
    Path p = KrlInterpreterConfigRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/plugins/krl/config", p.value(),
      "endpoint lives on the /v2/admin/plugins/ shelf per plugin SPI convention");
  }

  // ─── GET ─────────────────────────────────────────────────────────────────

  @Test
  void getConfig_returnsDefaultsWhenSingletonHasNoOverrides() {
    KrlInterpreterConfigEntity cfg = new KrlInterpreterConfigEntity();
    // all null → falls back to deploy-time defaults
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    KrlInterpreterConfigIO body = (KrlInterpreterConfigIO) r.getEntity();
    assertEquals(DEFAULT_URL, body.sidecarUrl());
    assertEquals(DEFAULT_TIMEOUT, body.timeoutSeconds());
    assertEquals(DEFAULT_MAX_BODY, body.maxBodySizeMb());
  }

  @Test
  void getConfig_returnsRuntimeValues() {
    KrlInterpreterConfigEntity cfg = new KrlInterpreterConfigEntity();
    cfg.setSidecarUrl("http://my-sidecar:9000");
    cfg.setTimeoutSeconds(60);
    cfg.setMaxBodySizeMb(32);
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    KrlInterpreterConfigIO body = (KrlInterpreterConfigIO) r.getEntity();
    assertEquals("http://my-sidecar:9000", body.sidecarUrl());
    assertEquals(60, body.timeoutSeconds());
    assertEquals(32, body.maxBodySizeMb());
  }

  // ─── PATCH ───────────────────────────────────────────────────────────────

  @Test
  void patchConfig_setsSidecarUrl_leavesOthersAlone() {
    KrlInterpreterConfigEntity current = new KrlInterpreterConfigEntity();
    current.setTimeoutSeconds(60);
    current.setMaxBodySizeMb(32);

    KrlInterpreterConfigEntity updated = new KrlInterpreterConfigEntity();
    updated.setSidecarUrl("http://new-sidecar:9000");
    updated.setTimeoutSeconds(60);
    updated.setMaxBodySizeMb(32);

    when(service.current()).thenReturn(current);
    when(service.patch(eq("http://new-sidecar:9000"), eq(60), eq(32))).thenReturn(updated);

    KrlInterpreterConfigPatchIO body = new KrlInterpreterConfigPatchIO();
    body.setSidecarUrl("http://new-sidecar:9000");

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    KrlInterpreterConfigIO out = (KrlInterpreterConfigIO) r.getEntity();
    assertEquals("http://new-sidecar:9000", out.sidecarUrl());
    assertEquals(60, out.timeoutSeconds());
    assertEquals(32, out.maxBodySizeMb());
  }

  @Test
  void patchConfig_setsTimeout_leavesOthersAlone() {
    KrlInterpreterConfigEntity current = new KrlInterpreterConfigEntity();
    current.setSidecarUrl("http://existing-sidecar:8000");
    current.setMaxBodySizeMb(16);

    KrlInterpreterConfigEntity updated = new KrlInterpreterConfigEntity();
    updated.setSidecarUrl("http://existing-sidecar:8000");
    updated.setTimeoutSeconds(300);
    updated.setMaxBodySizeMb(16);

    when(service.current()).thenReturn(current);
    when(service.patch(eq("http://existing-sidecar:8000"), eq(300), eq(16))).thenReturn(updated);

    KrlInterpreterConfigPatchIO body = new KrlInterpreterConfigPatchIO();
    body.setTimeoutSeconds(300);

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    KrlInterpreterConfigIO out = (KrlInterpreterConfigIO) r.getEntity();
    assertEquals(300, out.timeoutSeconds());
    assertEquals(16, out.maxBodySizeMb());
  }

  @Test
  void patchConfig_nullSidecarUrl_clearsToDefault() {
    KrlInterpreterConfigEntity current = new KrlInterpreterConfigEntity();
    current.setSidecarUrl("http://old-sidecar:9000");
    current.setTimeoutSeconds(120);
    current.setMaxBodySizeMb(16);

    KrlInterpreterConfigEntity cleared = new KrlInterpreterConfigEntity();
    cleared.setSidecarUrl(null);
    cleared.setTimeoutSeconds(120);
    cleared.setMaxBodySizeMb(16);

    when(service.current()).thenReturn(current);
    when(service.patch(isNull(), eq(120), eq(16))).thenReturn(cleared);

    KrlInterpreterConfigPatchIO body = new KrlInterpreterConfigPatchIO();
    body.setSidecarUrl(null);

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    KrlInterpreterConfigIO out = (KrlInterpreterConfigIO) r.getEntity();
    // null in entity → falls back to deploy-time default in IO projection
    assertEquals(DEFAULT_URL, out.sidecarUrl());
    verify(service).patch(isNull(), eq(120), eq(16));
  }

  @Test
  void patchConfig_nullBody_returnsCurrentUntouched() {
    KrlInterpreterConfigEntity current = new KrlInterpreterConfigEntity();
    current.setSidecarUrl("http://existing:8000");
    current.setTimeoutSeconds(60);
    current.setMaxBodySizeMb(32);

    when(service.current()).thenReturn(current);
    when(service.patch(eq("http://existing:8000"), eq(60), eq(32))).thenReturn(current);

    Response r = rest.patchConfig(null);

    assertEquals(200, r.getStatus());
    KrlInterpreterConfigIO out = (KrlInterpreterConfigIO) r.getEntity();
    assertEquals("http://existing:8000", out.sidecarUrl());
    assertEquals(60, out.timeoutSeconds());
    assertEquals(32, out.maxBodySizeMb());
  }

  // ─── validation guards ────────────────────────────────────────────────────

  @Test
  void patchConfig_blankSidecarUrl_returns400() {
    KrlInterpreterConfigPatchIO body = new KrlInterpreterConfigPatchIO();
    body.setSidecarUrl("   ");

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson problem = (ProblemJson) r.getEntity();
    assertEquals(KrlInterpreterConfigRest.PROBLEM_TYPE_INVALID_SIDECAR_URL, problem.type());
    assertEquals(400, problem.status());
  }

  @Test
  void patchConfig_zeroTimeout_returns400() {
    KrlInterpreterConfigPatchIO body = new KrlInterpreterConfigPatchIO();
    body.setTimeoutSeconds(0);

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    ProblemJson problem = (ProblemJson) r.getEntity();
    assertEquals(KrlInterpreterConfigRest.PROBLEM_TYPE_INVALID_TIMEOUT, problem.type());
  }

  @Test
  void patchConfig_negativeTimeout_returns400() {
    KrlInterpreterConfigPatchIO body = new KrlInterpreterConfigPatchIO();
    body.setTimeoutSeconds(-5);

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    ProblemJson problem = (ProblemJson) r.getEntity();
    assertEquals(KrlInterpreterConfigRest.PROBLEM_TYPE_INVALID_TIMEOUT, problem.type());
  }

  @Test
  void patchConfig_zeroMaxBody_returns400() {
    KrlInterpreterConfigPatchIO body = new KrlInterpreterConfigPatchIO();
    body.setMaxBodySizeMb(0);

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    ProblemJson problem = (ProblemJson) r.getEntity();
    assertEquals(KrlInterpreterConfigRest.PROBLEM_TYPE_INVALID_MAX_BODY_SIZE, problem.type());
  }

  @Test
  void patchConfig_negativeMaxBody_returns400() {
    KrlInterpreterConfigPatchIO body = new KrlInterpreterConfigPatchIO();
    body.setMaxBodySizeMb(-1);

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    ProblemJson problem = (ProblemJson) r.getEntity();
    assertEquals(KrlInterpreterConfigRest.PROBLEM_TYPE_INVALID_MAX_BODY_SIZE, problem.type());
  }

  @Test
  void patchConfig_positiveTimeout_accepted() {
    KrlInterpreterConfigEntity current = new KrlInterpreterConfigEntity();
    KrlInterpreterConfigEntity updated = new KrlInterpreterConfigEntity();
    updated.setTimeoutSeconds(1);
    when(service.current()).thenReturn(current);
    when(service.patch(isNull(), eq(1), isNull())).thenReturn(updated);

    KrlInterpreterConfigPatchIO body = new KrlInterpreterConfigPatchIO();
    body.setTimeoutSeconds(1);

    Response r = rest.patchConfig(body);
    assertEquals(200, r.getStatus());
  }
}
