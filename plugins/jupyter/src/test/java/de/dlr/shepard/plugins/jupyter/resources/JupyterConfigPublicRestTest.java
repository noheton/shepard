package de.dlr.shepard.plugins.jupyter.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.jupyter.entities.JupyterConfig;
import de.dlr.shepard.plugins.jupyter.io.JupyterConfigIO;
import de.dlr.shepard.plugins.jupyter.services.JupyterConfigService;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * J1e — unit tests for the public read-only view of the JupyterHub
 * config. Same shape as the admin endpoint but no instance-admin gate.
 */
class JupyterConfigPublicRestTest {

  private JupyterConfigService service;
  private JupyterConfigPublicRest rest;
  private SecurityContext sc;
  private Principal principal;

  @BeforeEach
  void setUp() {
    service = mock(JupyterConfigService.class);
    when(service.getDefaultHubUrl()).thenReturn(null);
    rest = new JupyterConfigPublicRest();
    rest.service = service;
    sc = mock(SecurityContext.class);
    principal = mock(Principal.class);
    when(principal.getName()).thenReturn("alice");
  }

  @Test
  void pathIsV2PluginsJupyterConfig() {
    Path p = JupyterConfigPublicRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/plugins/jupyter/config", p.value(),
        "canonical public path per J1e-PLUGIN-REFACTOR (2026-05-29) plugin-routing convention");
  }

  @Test
  void getConfig_returns200WithRuntimeValues() {
    when(sc.getUserPrincipal()).thenReturn(principal);
    JupyterConfig cfg = new JupyterConfig();
    cfg.setEnabled(true);
    cfg.setHubUrl("https://hub.test");
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig(sc);

    assertEquals(200, r.getStatus());
    JupyterConfigIO body = (JupyterConfigIO) r.getEntity();
    assertTrue(body.enabled());
    assertEquals("https://hub.test", body.hubUrl());
  }

  @Test
  void getConfig_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);

    Response r = rest.getConfig(sc);

    assertEquals(401, r.getStatus());
  }

  @Test
  void getConfig_returnsDefaultsForFreshSingleton() {
    when(sc.getUserPrincipal()).thenReturn(principal);
    JupyterConfig cfg = new JupyterConfig();
    when(service.current()).thenReturn(cfg);

    Response r = rest.getConfig(sc);

    assertEquals(200, r.getStatus());
    JupyterConfigIO body = (JupyterConfigIO) r.getEntity();
    assertFalse(body.enabled());
    assertNull(body.hubUrl());
  }
}
