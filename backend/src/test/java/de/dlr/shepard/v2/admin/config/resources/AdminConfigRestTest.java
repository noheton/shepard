package de.dlr.shepard.v2.admin.config.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.config.spi.ConfigDescriptor;
import de.dlr.shepard.v2.admin.config.spi.ConfigPatchException;
import de.dlr.shepard.v2.admin.config.spi.ConfigRegistry;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * V2CONV-A4 — unit tests for the generic {@link AdminConfigRest}. The
 * {@link ConfigRegistry} is mocked; a fake descriptor exercises the delegation,
 * 404, and validation-to-problem-JSON paths. Role gating is asserted via the
 * class-level annotation (same pattern as the deleted bespoke *RestTests).
 */
class AdminConfigRestTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ConfigRegistry registry;
  private AdminConfigRest rest;

  /** Fake descriptor with a tunable validation hook. */
  private static final class FakeDescriptor implements ConfigDescriptor<String> {

    boolean throwOnPatch = false;

    @Override
    public String featureName() {
      return "fake";
    }

    @Override
    public String description() {
      return "a fake feature";
    }

    @Override
    public String currentShape() {
      return "current";
    }

    @Override
    public String applyMergePatch(JsonNode patch) throws ConfigPatchException {
      if (throwOnPatch) {
        throw ConfigPatchException.badRequest("/problems/fake.bad", "Bad fake", "the value was no good");
      }
      return "patched:" + patch.toString();
    }
  }

  @BeforeEach
  void setUp() {
    registry = Mockito.mock(ConfigRegistry.class);
    rest = new AdminConfigRest();
    rest.registry = registry;
  }

  @Test
  void classIsInstanceAdminGated() {
    RolesAllowed gate = AdminConfigRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "AdminConfigRest must be @RolesAllowed-gated at class level");
    assertEquals(Constants.INSTANCE_ADMIN_ROLE, gate.value()[0]);
  }

  @Test
  void pathIsV2AdminConfig() {
    Path p = AdminConfigRest.class.getAnnotation(Path.class);
    assertNotNull(p);
    assertEquals("/v2/admin/config", p.value());
  }

  @Test
  void listFeaturesReturnsRegisteredRows() {
    Mockito.when(registry.all()).thenReturn(List.of(new FakeDescriptor()));
    Response resp = rest.listFeatures(0, 50);
    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<ConfigFeatureIO> body = (PagedResponseIO<ConfigFeatureIO>) resp.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(1L, body.total());
    assertEquals(0, body.page());
    assertEquals(50, body.pageSize());
    assertEquals("fake", body.items().get(0).feature());
    assertEquals("a fake feature", body.items().get(0).description());
  }

  @Test
  void listFeaturesReturnsSizeInBody() {
    Mockito.when(registry.all()).thenReturn(List.of(new FakeDescriptor(), new FakeDescriptor()));
    Response resp = rest.listFeatures(0, 50);
    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<ConfigFeatureIO> body = (PagedResponseIO<ConfigFeatureIO>) resp.getEntity();
    assertEquals(2, body.items().size());
    assertEquals(2L, body.total());
  }

  @Test
  void listFeaturesEmptyWhenRegistryEmpty() {
    Mockito.when(registry.all()).thenReturn(List.of());
    Response resp = rest.listFeatures(0, 50);
    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<ConfigFeatureIO> body = (PagedResponseIO<ConfigFeatureIO>) resp.getEntity();
    assertEquals(0, body.items().size());
    assertEquals(0L, body.total());
  }

  @Test
  void listFeaturesXTotalCountHeader() {
    Mockito.when(registry.all()).thenReturn(List.of(new FakeDescriptor(), new FakeDescriptor()));
    Response resp = rest.listFeatures(0, 50);
    assertEquals(200, resp.getStatus());
    assertEquals("2", resp.getHeaderString("X-Total-Count"));
  }

  @Test
  void listFeaturesCustomPageParams() {
    Mockito.when(registry.all()).thenReturn(List.of(new FakeDescriptor()));
    Response resp = rest.listFeatures(1, 10);
    assertEquals(200, resp.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<ConfigFeatureIO> body = (PagedResponseIO<ConfigFeatureIO>) resp.getEntity();
    assertEquals(1, body.page());
    assertEquals(10, body.pageSize());
  }

  @Test
  void getKnownFeatureDelegatesToDescriptor() {
    Mockito.when(registry.resolve("fake")).thenReturn(Optional.of(new FakeDescriptor()));
    Response resp = rest.getConfig("fake");
    assertEquals(200, resp.getStatus());
    assertEquals("current", resp.getEntity());
  }

  @Test
  void getUnknownFeatureIs404ProblemJson() {
    Mockito.when(registry.resolve("nope")).thenReturn(Optional.empty());
    Response resp = rest.getConfig("nope");
    assertEquals(404, resp.getStatus());
    assertEquals("application/problem+json", resp.getMediaType().toString());
    ProblemJson body = (ProblemJson) resp.getEntity();
    assertEquals(AdminConfigRest.PROBLEM_TYPE_UNKNOWN_FEATURE, body.type());
    assertTrue(body.detail().contains("nope"));
  }

  @Test
  void patchKnownFeatureDelegatesAndReturnsUpdatedShape() {
    Mockito.when(registry.resolve("fake")).thenReturn(Optional.of(new FakeDescriptor()));
    JsonNode patch = JsonNodeFactory.instance.objectNode().put("k", "v");
    Response resp = rest.patchConfig("fake", patch);
    assertEquals(200, resp.getStatus());
    assertTrue(((String) resp.getEntity()).startsWith("patched:"));
  }

  @Test
  void patchNullBodyIsNoOpObject() {
    Mockito.when(registry.resolve("fake")).thenReturn(Optional.of(new FakeDescriptor()));
    Response resp = rest.patchConfig("fake", null);
    assertEquals(200, resp.getStatus());
    // Empty object delegated to the descriptor.
    assertEquals("patched:{}", resp.getEntity());
  }

  @Test
  void patchUnknownFeatureIs404() {
    Mockito.when(registry.resolve("nope")).thenReturn(Optional.empty());
    Response resp = rest.patchConfig("nope", JsonNodeFactory.instance.objectNode());
    assertEquals(404, resp.getStatus());
  }

  @Test
  void patchNonObjectBodyIs400() {
    Mockito.when(registry.resolve("fake")).thenReturn(Optional.of(new FakeDescriptor()));
    JsonNode arrayBody = JsonNodeFactory.instance.arrayNode();
    Response resp = rest.patchConfig("fake", arrayBody);
    assertEquals(400, resp.getStatus());
    assertEquals("application/problem+json", resp.getMediaType().toString());
  }

  @Test
  void patchValidationFailureMapsToProblemJson() {
    FakeDescriptor d = new FakeDescriptor();
    d.throwOnPatch = true;
    Mockito.when(registry.resolve("fake")).thenReturn(Optional.of(d));
    Response resp = rest.patchConfig("fake", JsonNodeFactory.instance.objectNode());
    assertEquals(400, resp.getStatus());
    assertEquals("application/problem+json", resp.getMediaType().toString());
    ProblemJson body = (ProblemJson) resp.getEntity();
    assertEquals("/problems/fake.bad", body.type());
    assertEquals("Bad fake", body.title());
    assertEquals("the value was no good", body.detail());
  }

  @Test
  void objectMapperParsesMergePatchBody() throws Exception {
    JsonNode node = MAPPER.readTree("{\"enabled\":true,\"hubUrl\":null}");
    assertTrue(node.has("hubUrl"));
    assertTrue(node.get("hubUrl").isNull());
    assertTrue(node.get("enabled").asBoolean());
  }
}
