package de.dlr.shepard.plugins.jupyter.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.plugins.jupyter.io.JupyterConfigPatchIO;
import org.junit.jupiter.api.Test;

/**
 * J1e-PLUGIN-REFACTOR — verifies the legacy {@code /v2/admin/jupyter/config}
 * compat shim retains the deprecated path, gates on instance-admin, and
 * stamps the {@code Deprecation} + {@code Link} headers expected by
 * draft-ietf-httpapi-deprecation-header.
 */
class JupyterConfigLegacyRestTest {

  @Test
  void pathRetainsLegacyV2AdminJupyterConfig() {
    Path p = JupyterConfigLegacyRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/jupyter/config", p.value(),
        "legacy in-tree path must remain callable for one deprecation window");
  }

  @Test
  void roleGateIsInstanceAdmin() {
    RolesAllowed gate = JupyterConfigLegacyRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate);
    assertEquals(1, gate.value().length);
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void getStampsDeprecationAndLinkHeaders() {
    JupyterConfigRest canonical = mock(JupyterConfigRest.class);
    when(canonical.getConfig()).thenReturn(Response.ok("{}").build());
    JupyterConfigLegacyRest shim = new JupyterConfigLegacyRest();
    shim.canonical = canonical;

    Response r = shim.getConfig();
    assertEquals("true", r.getHeaderString(JupyterConfigLegacyRest.DEPRECATION_HEADER));
    String link = r.getHeaderString(HttpHeaders.LINK);
    assertNotNull(link);
    assertTrue(link.contains("/v2/admin/plugins/jupyter/config"),
        "Link header must point at the canonical path");
    assertTrue(link.contains("successor-version"));
  }

  @Test
  void patchStampsDeprecationAndLinkHeaders() {
    JupyterConfigRest canonical = mock(JupyterConfigRest.class);
    when(canonical.patchConfig(null)).thenReturn(Response.ok("{}").build());
    JupyterConfigLegacyRest shim = new JupyterConfigLegacyRest();
    shim.canonical = canonical;

    Response r = shim.patchConfig(null);
    assertEquals("true", r.getHeaderString(JupyterConfigLegacyRest.DEPRECATION_HEADER));
    String link = r.getHeaderString(HttpHeaders.LINK);
    assertTrue(link != null && link.contains("/v2/admin/plugins/jupyter/config"));
  }

  @Test
  void patchPassesBodyThroughToCanonical() {
    JupyterConfigRest canonical = mock(JupyterConfigRest.class);
    JupyterConfigPatchIO body = new JupyterConfigPatchIO();
    when(canonical.patchConfig(body)).thenReturn(Response.ok().build());
    JupyterConfigLegacyRest shim = new JupyterConfigLegacyRest();
    shim.canonical = canonical;

    Response r = shim.patchConfig(body);
    assertEquals(200, r.getStatus());
  }
}
