package de.dlr.shepard.v2.config.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.config.spi.ConfigRegistry;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-JUPYTER-PUBLIC-CONFIG-GENERIC — unit tests for {@link PublicConfigRest}.
 * {@link ConfigRegistry} is mocked; two fake descriptors cover publicRead=true/false.
 */
class PublicConfigRestTest {

  private ConfigRegistry registry;
  private PublicConfigRest rest;

  private static final class PublicDescriptor implements ConfigDescriptor<String> {
    @Override public String featureName() { return "public-feature"; }
    @Override public String description() { return "publicly readable feature"; }
    @Override public String currentShape() { return "public-shape"; }
    @Override public String applyMergePatch(JsonNode patch) throws ConfigPatchException { return "patched"; }
    @Override public boolean publicRead() { return true; }
  }

  private static final class PrivateDescriptor implements ConfigDescriptor<String> {
    @Override public String featureName() { return "private-feature"; }
    @Override public String description() { return "admin-only feature"; }
    @Override public String currentShape() { return "private-shape"; }
    @Override public String applyMergePatch(JsonNode patch) throws ConfigPatchException { return "patched"; }
    // publicRead() defaults to false
  }

  @BeforeEach
  void setUp() {
    registry = mock(ConfigRegistry.class);
    rest = new PublicConfigRest();
    rest.registry = registry;
  }

  @Test
  void classIsAuthenticated() {
    assertNotNull(PublicConfigRest.class.getAnnotation(Authenticated.class),
      "PublicConfigRest must be @Authenticated at class level");
  }

  @Test
  void pathIsV2Config() {
    Path p = PublicConfigRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/config", p.value());
  }

  @Test
  void getConfig_returns200ForPublicReadFeature() {
    when(registry.resolve("public-feature")).thenReturn(Optional.of(new PublicDescriptor()));

    Response r = rest.getConfig("public-feature");

    assertEquals(200, r.getStatus());
    assertEquals("public-shape", r.getEntity());
  }

  @Test
  void getConfig_returns404ForPrivateFeature() {
    when(registry.resolve("private-feature")).thenReturn(Optional.of(new PrivateDescriptor()));

    Response r = rest.getConfig("private-feature");

    assertEquals(404, r.getStatus());
  }

  @Test
  void getConfig_returns404ForUnknownFeature() {
    when(registry.resolve("nope")).thenReturn(Optional.empty());

    Response r = rest.getConfig("nope");

    assertEquals(404, r.getStatus());
  }
}
