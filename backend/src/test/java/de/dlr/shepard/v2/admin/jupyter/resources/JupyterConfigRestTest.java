package de.dlr.shepard.v2.admin.jupyter.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.jupyter.entities.JupyterConfig;
import de.dlr.shepard.v2.admin.jupyter.io.JupyterConfigIO;
import de.dlr.shepard.v2.admin.jupyter.io.JupyterConfigPatchIO;
import de.dlr.shepard.v2.admin.jupyter.services.JupyterConfigService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * J1e — unit tests for the admin REST surface.
 *
 * <p>No Quarkus boot — {@link JupyterConfigService} is mocked. Tests
 * cover: GET round-trip, PATCH enabled flip, PATCH hubUrl set, PATCH
 * hubUrl null clears to default, invalid hubUrl → 400, helper URL
 * validator, annotation-gate assertion. Mirrors
 * {@code SqlTimeseriesConfigRestTest} structure.
 */
class JupyterConfigRestTest {

  private static final String DEFAULT_HUB_URL = null;

  private JupyterConfigService service;
  private JupyterConfigRest rest;

  @BeforeEach
  void setUp() {
    service = mock(JupyterConfigService.class);
    when(service.getDefaultEnabled()).thenReturn(false);
    when(service.getDefaultHubUrl()).thenReturn(DEFAULT_HUB_URL);

    rest = new JupyterConfigRest();
    rest.service = service;
  }

  // ─── annotation gates ────────────────────────────────────────────────────

  @Test
  void classCarriesInstanceAdminGate() {
    RolesAllowed gate = JupyterConfigRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "JupyterConfigRest must be @RolesAllowed-gated at class level");
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2AdminJupyterConfig() {
    Path p = JupyterConfigRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/jupyter/config", p.value(),
        "endpoint lives on the /v2/ shelf per fork policy");
  }

  // ─── GET ─────────────────────────────────────────────────────────────────

  @Test
  void getConfig_returnsDefaultsWhenSingletonHasNoValues() {
    JupyterConfig cfg = new JupyterConfig();
    // enabled defaults to false (primitive), hubUrl is null
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    JupyterConfigIO body = (JupyterConfigIO) r.getEntity();
    assertFalse(body.enabled());
    assertNull(body.hubUrl(), "hubUrl falls back to null when neither runtime nor deploy default set");
  }

  @Test
  void getConfig_returnsRuntimeValues() {
    JupyterConfig cfg = new JupyterConfig();
    cfg.setEnabled(true);
    cfg.setHubUrl("https://hub.test");
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig();

    assertEquals(200, r.getStatus());
    JupyterConfigIO body = (JupyterConfigIO) r.getEntity();
    assertTrue(body.enabled());
    assertEquals("https://hub.test", body.hubUrl());
  }

  // ─── PATCH ───────────────────────────────────────────────────────────────

  @Test
  void patchConfig_flipsEnabled_leavesHubUrlAlone() {
    JupyterConfig current = new JupyterConfig();
    current.setEnabled(false);
    current.setHubUrl("https://hub.example.org");

    JupyterConfig updated = new JupyterConfig();
    updated.setEnabled(true);
    updated.setHubUrl("https://hub.example.org");

    when(service.current()).thenReturn(current);
    when(service.patch(eq(true), eq("https://hub.example.org"))).thenReturn(updated);

    JupyterConfigPatchIO body = new JupyterConfigPatchIO();
    body.setEnabled(true);

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    JupyterConfigIO out = (JupyterConfigIO) r.getEntity();
    assertTrue(out.enabled());
    assertEquals("https://hub.example.org", out.hubUrl(), "hubUrl unchanged");
  }

  @Test
  void patchConfig_setsHubUrl_leavesEnabledAlone() {
    JupyterConfig current = new JupyterConfig();
    current.setEnabled(true);
    current.setHubUrl(null);

    JupyterConfig updated = new JupyterConfig();
    updated.setEnabled(true);
    updated.setHubUrl("https://new-hub.example.org");

    when(service.current()).thenReturn(current);
    when(service.patch(eq(true), eq("https://new-hub.example.org"))).thenReturn(updated);

    JupyterConfigPatchIO body = new JupyterConfigPatchIO();
    body.setHubUrl("https://new-hub.example.org");

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    JupyterConfigIO out = (JupyterConfigIO) r.getEntity();
    assertTrue(out.enabled(), "enabled unchanged");
    assertEquals("https://new-hub.example.org", out.hubUrl());
  }

  @Test
  void patchConfig_nullHubUrl_clearsToDefault() {
    JupyterConfig current = new JupyterConfig();
    current.setEnabled(true);
    current.setHubUrl("https://hub.test");

    JupyterConfig cleared = new JupyterConfig();
    cleared.setEnabled(true);
    cleared.setHubUrl(null);

    when(service.current()).thenReturn(current);
    when(service.patch(eq(true), isNull())).thenReturn(cleared);

    JupyterConfigPatchIO body = new JupyterConfigPatchIO();
    body.setHubUrl(null);

    Response r = rest.patchConfig(body);

    assertEquals(200, r.getStatus());
    JupyterConfigIO out = (JupyterConfigIO) r.getEntity();
    assertNull(out.hubUrl());
  }

  @Test
  void patchConfig_invalidHubUrl_returns400() {
    JupyterConfigPatchIO body = new JupyterConfigPatchIO();
    body.setHubUrl("not-a-url");

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    assertEquals("application/problem+json", r.getMediaType().toString());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(JupyterConfigRest.PROBLEM_TYPE_INVALID_HUB_URL, problem.type());
    assertEquals(400, problem.status());
  }

  @Test
  void patchConfig_relativeHubUrl_returns400() {
    JupyterConfigPatchIO body = new JupyterConfigPatchIO();
    body.setHubUrl("/hub/spawn");

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(JupyterConfigRest.PROBLEM_TYPE_INVALID_HUB_URL, problem.type());
  }

  @Test
  void patchConfig_ftpHubUrl_returns400() {
    JupyterConfigPatchIO body = new JupyterConfigPatchIO();
    body.setHubUrl("ftp://hub.example.org");

    Response r = rest.patchConfig(body);

    assertEquals(400, r.getStatus());
    ProblemJson problem = assertInstanceOf(ProblemJson.class, r.getEntity());
    assertEquals(JupyterConfigRest.PROBLEM_TYPE_INVALID_HUB_URL, problem.type());
  }

  @Test
  void patchConfig_nullBody_returnsCurrentUntouched() {
    JupyterConfig current = new JupyterConfig();
    current.setEnabled(true);
    current.setHubUrl("https://hub.test");

    when(service.current()).thenReturn(current);
    when(service.patch(eq(true), eq("https://hub.test"))).thenReturn(current);

    Response r = rest.patchConfig(null);

    assertEquals(200, r.getStatus());
    JupyterConfigIO out = (JupyterConfigIO) r.getEntity();
    assertTrue(out.enabled());
    assertEquals("https://hub.test", out.hubUrl());
  }

  // ─── URL validator helper ────────────────────────────────────────────────

  @Test
  void urlValidator_acceptsHttp() {
    assertTrue(JupyterConfigRest.isValidAbsoluteHttpUrl("http://hub.example.org"));
  }

  @Test
  void urlValidator_acceptsHttps() {
    assertTrue(JupyterConfigRest.isValidAbsoluteHttpUrl("https://hub.example.org"));
  }

  @Test
  void urlValidator_acceptsHttpsWithPort() {
    assertTrue(JupyterConfigRest.isValidAbsoluteHttpUrl("https://hub.example.org:8443"));
  }

  @Test
  void urlValidator_acceptsHttpsWithPath() {
    assertTrue(JupyterConfigRest.isValidAbsoluteHttpUrl("https://hub.example.org/jupyterhub"));
  }

  @Test
  void urlValidator_rejectsNull() {
    assertFalse(JupyterConfigRest.isValidAbsoluteHttpUrl(null));
  }

  @Test
  void urlValidator_rejectsBlank() {
    assertFalse(JupyterConfigRest.isValidAbsoluteHttpUrl(""));
    assertFalse(JupyterConfigRest.isValidAbsoluteHttpUrl("   "));
  }

  @Test
  void urlValidator_rejectsRelative() {
    assertFalse(JupyterConfigRest.isValidAbsoluteHttpUrl("/hub/spawn"));
    assertFalse(JupyterConfigRest.isValidAbsoluteHttpUrl("hub.example.org"));
  }

  @Test
  void urlValidator_rejectsNonHttpScheme() {
    assertFalse(JupyterConfigRest.isValidAbsoluteHttpUrl("ftp://hub.example.org"));
    assertFalse(JupyterConfigRest.isValidAbsoluteHttpUrl("file:///etc/passwd"));
    assertFalse(JupyterConfigRest.isValidAbsoluteHttpUrl("javascript:alert(1)"));
  }

  @Test
  void urlValidator_rejectsMissingHost() {
    assertFalse(JupyterConfigRest.isValidAbsoluteHttpUrl("https://"));
  }
}
