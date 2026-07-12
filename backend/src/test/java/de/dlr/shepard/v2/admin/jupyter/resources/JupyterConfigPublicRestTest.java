package de.dlr.shepard.v2.admin.jupyter.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * Tombstone tests for the moved {@code /v2/jupyter/config} path.
 *
 * <p>The endpoint now returns 301 → {@code /v2/config/jupyter}.
 * The canonical implementation is {@link de.dlr.shepard.v2.config.resources.PublicConfigRest}.
 */
class JupyterConfigPublicRestTest {

  private final JupyterConfigPublicRest rest = new JupyterConfigPublicRest();

  @Test
  void pathIsStillV2JupyterConfig() {
    Path p = JupyterConfigPublicRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/jupyter/config", p.value());
  }

  @Test
  void getConfig_returns301() {
    Response r = rest.getConfig();
    assertEquals(301, r.getStatus());
  }

  @Test
  void getConfig_locationIsCanonicalPath() {
    Response r = rest.getConfig();
    assertEquals(URI.create("/v2/config/jupyter"), r.getLocation());
  }
}
