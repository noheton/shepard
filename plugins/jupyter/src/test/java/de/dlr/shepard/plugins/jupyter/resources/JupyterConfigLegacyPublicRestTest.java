package de.dlr.shepard.plugins.jupyter.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

/**
 * J1e-PLUGIN-REFACTOR — verifies the legacy {@code /v2/jupyter/config}
 * public compat shim retains the deprecated path and stamps the
 * {@code Deprecation} + {@code Link} headers.
 */
class JupyterConfigLegacyPublicRestTest {

  @Test
  void pathRetainsLegacyV2JupyterConfig() {
    Path p = JupyterConfigLegacyPublicRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/jupyter/config", p.value(),
        "legacy in-tree path must remain callable for one deprecation window");
  }

  @Test
  void getStampsDeprecationAndLinkHeaders() {
    JupyterConfigPublicRest canonical = mock(JupyterConfigPublicRest.class);
    SecurityContext sc = mock(SecurityContext.class);
    when(canonical.getConfig(any(SecurityContext.class))).thenReturn(Response.ok("{}").build());
    JupyterConfigLegacyPublicRest shim = new JupyterConfigLegacyPublicRest();
    shim.canonical = canonical;

    Response r = shim.getConfig(sc);
    assertEquals("true", r.getHeaderString(JupyterConfigLegacyPublicRest.DEPRECATION_HEADER));
    String link = r.getHeaderString(HttpHeaders.LINK);
    assertNotNull(link);
    assertTrue(link.contains("/v2/plugins/jupyter/config"),
        "Link header must point at the canonical path");
    assertTrue(link.contains("successor-version"));
  }
}
