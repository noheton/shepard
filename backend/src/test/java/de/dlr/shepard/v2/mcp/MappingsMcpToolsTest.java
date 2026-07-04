package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.spi.transform.TransformException;
import de.dlr.shepard.spi.transform.TransformExecutor;
import de.dlr.shepard.spi.transform.TransformExecutorRegistry;
import de.dlr.shepard.spi.transform.TransformRequest;
import de.dlr.shepard.spi.transform.TransformResult;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import io.quarkiverse.mcp.server.McpException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-MAPPING-RECIPE-1 — unit tests for {@link MappingsMcpTools}.
 */
class MappingsMcpToolsTest {

  static final String TPL_APP_ID   = "018f9c5a-7e26-7000-e100-000000000001";
  static final String TPL_APP_ID_2 = "018f9c5a-7e26-7000-e100-000000000002";
  static final String REF_APP_ID   = "018f9c5a-7e26-7000-e100-000000000099";
  static final String SHAPE_IRI    = "http://semantics.dlr.de/shepard/transform#TestShape";

  static final String BODY_WITH_SHAPE =
    "{\"mappingRecipeShape\":\"" + SHAPE_IRI + "\",\"inputs\":[\"urdfFileAppId\"]}";
  static final String BODY_NO_SHAPE = "{\"someField\":\"someValue\"}";

  @Mock ShepardTemplateDAO     templateDAO;
  @Mock TransformExecutorRegistry executorRegistry;
  @Mock TransformExecutor       executor;
  @Mock McpContextBridge        contextBridge;

  MappingsMcpTools tools;
  McpToolSupport   support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.objectMapper = new ObjectMapper();

    tools = new MappingsMcpTools();
    tools.templateDAO     = templateDAO;
    tools.executorRegistry = executorRegistry;
    tools.contextBridge   = contextBridge;
    tools.support         = support;

    when(executor.name()).thenReturn("TestExecutor");
  }

  // ─── helpers ──────────────────────────────────────────────────────────────

  private ShepardTemplate mkTemplate(String appId, String name, String kind, String body) {
    ShepardTemplate t = new ShepardTemplate();
    t.setAppId(appId);
    t.setName(name);
    t.setDescription("desc-" + appId);
    t.setTemplateKind(kind);
    t.setBody(body);
    return t;
  }

  // ─── parseMappingRecipeShape ───────────────────────────────────────────────

  @Test
  void parseShapeIriExtractsFromBody() {
    assertEquals(SHAPE_IRI, MappingsMcpTools.parseMappingRecipeShape(BODY_WITH_SHAPE));
  }

  @Test
  void parseShapeIriReturnsNullOnMissingField() {
    assertNull(MappingsMcpTools.parseMappingRecipeShape(BODY_NO_SHAPE));
  }

  @Test
  void parseShapeIriReturnsNullOnNull() {
    assertNull(MappingsMcpTools.parseMappingRecipeShape(null));
  }

  @Test
  void parseShapeIriReturnsNullOnMalformedJson() {
    assertNull(MappingsMcpTools.parseMappingRecipeShape("not-json"));
  }

  @Test
  void parseShapeIriReturnsNullOnBlankValue() {
    assertNull(MappingsMcpTools.parseMappingRecipeShape("{\"mappingRecipeShape\":\"   \"}"));
  }

  // ─── mapping_list ─────────────────────────────────────────────────────────

  @Test
  void listReturnsEmptyWhenNoTemplates() throws Exception {
    when(templateDAO.list("MAPPING_RECIPE", false)).thenReturn(List.of());

    String json = tools.mappingList();
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(0, root.get("count").asInt());
    assertEquals(0, root.get("recipes").size());
  }

  @Test
  void listIncludesShapeIriAndExecutorAvailableFlag() throws Exception {
    ShepardTemplate t1 = mkTemplate(TPL_APP_ID, "Recipe-A", "MAPPING_RECIPE", BODY_WITH_SHAPE);
    ShepardTemplate t2 = mkTemplate(TPL_APP_ID_2, "Recipe-B", "MAPPING_RECIPE", BODY_NO_SHAPE);
    when(templateDAO.list("MAPPING_RECIPE", false)).thenReturn(List.of(t1, t2));
    when(executorRegistry.resolve(SHAPE_IRI)).thenReturn(Optional.of(executor));

    String json = tools.mappingList();
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(2, root.get("count").asInt());

    JsonNode r0 = root.get("recipes").get(0);
    assertEquals(TPL_APP_ID, r0.get("appId").asText());
    assertEquals("Recipe-A", r0.get("name").asText());
    assertEquals(SHAPE_IRI, r0.get("mappingRecipeShape").asText());
    assertTrue(r0.get("executorAvailable").asBoolean());

    JsonNode r1 = root.get("recipes").get(1);
    assertEquals(TPL_APP_ID_2, r1.get("appId").asText());
    assertTrue(r1.get("mappingRecipeShape").isNull());
    assertFalse(r1.get("executorAvailable").asBoolean());
  }

  @Test
  void listMarksExecutorUnavailableWhenPluginNotInstalled() throws Exception {
    ShepardTemplate t = mkTemplate(TPL_APP_ID, "Recipe-C", "MAPPING_RECIPE", BODY_WITH_SHAPE);
    when(templateDAO.list("MAPPING_RECIPE", false)).thenReturn(List.of(t));
    when(executorRegistry.resolve(SHAPE_IRI)).thenReturn(Optional.empty());

    String json = tools.mappingList();
    JsonNode root = new ObjectMapper().readTree(json);
    assertFalse(root.get("recipes").get(0).get("executorAvailable").asBoolean());
  }

  // ─── mapping_materialize — input validation ────────────────────────────────

  @Test
  void materializeRejectsNullTemplateAppId() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.mappingMaterialize(null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("templateAppId"));
  }

  @Test
  void materializeRejectsBlankTemplateAppId() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.mappingMaterialize("   ", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void materializeRejectsUnknownTemplateAppId() {
    when(templateDAO.findByAppId(TPL_APP_ID)).thenReturn(Optional.empty());
    McpException ex = assertThrows(McpException.class,
      () -> tools.mappingMaterialize(TPL_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(TPL_APP_ID));
  }

  @Test
  void materializeRejectsNonMappingRecipeTemplate() {
    ShepardTemplate t = mkTemplate(TPL_APP_ID, "View", "VIEW_RECIPE", BODY_WITH_SHAPE);
    when(templateDAO.findByAppId(TPL_APP_ID)).thenReturn(Optional.of(t));
    McpException ex = assertThrows(McpException.class,
      () -> tools.mappingMaterialize(TPL_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("MAPPING_RECIPE"));
  }

  @Test
  void materializeRejectsTemplateWithNoShapeIri() {
    ShepardTemplate t = mkTemplate(TPL_APP_ID, "No-Shape", "MAPPING_RECIPE", BODY_NO_SHAPE);
    when(templateDAO.findByAppId(TPL_APP_ID)).thenReturn(Optional.of(t));
    McpException ex = assertThrows(McpException.class,
      () -> tools.mappingMaterialize(TPL_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("mappingRecipeShape"));
  }

  @Test
  void materializeRejectsWhenExecutorNotInstalled() {
    ShepardTemplate t = mkTemplate(TPL_APP_ID, "Recipe", "MAPPING_RECIPE", BODY_WITH_SHAPE);
    when(templateDAO.findByAppId(TPL_APP_ID)).thenReturn(Optional.of(t));
    when(executorRegistry.resolve(SHAPE_IRI)).thenReturn(Optional.empty());
    McpException ex = assertThrows(McpException.class,
      () -> tools.mappingMaterialize(TPL_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(SHAPE_IRI));
  }

  // ─── mapping_materialize — successful execution ───────────────────────────

  @Test
  void materializeReturnsReferenceOutput() throws Exception {
    ShepardTemplate t = mkTemplate(TPL_APP_ID, "Recipe", "MAPPING_RECIPE", BODY_WITH_SHAPE);
    when(templateDAO.findByAppId(TPL_APP_ID)).thenReturn(Optional.of(t));
    when(executorRegistry.resolve(SHAPE_IRI)).thenReturn(Optional.of(executor));
    when(executor.materialize(any(TransformRequest.class)))
      .thenReturn(TransformResult.reference(REF_APP_ID, "TestExecutor"));

    String json = tools.mappingMaterialize(TPL_APP_ID, Map.of("urdfFileAppId", REF_APP_ID));
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(TPL_APP_ID, root.get("templateAppId").asText());
    assertEquals("REFERENCE", root.get("outputKind").asText());
    assertEquals(REF_APP_ID, root.get("derivedReferenceAppId").asText());
    assertTrue(root.get("viewModel").isNull());
    assertEquals("TestExecutor", root.get("executor").asText());
  }

  @Test
  void materializeReturnsViewOutput() throws Exception {
    ShepardTemplate t = mkTemplate(TPL_APP_ID, "Recipe", "MAPPING_RECIPE", BODY_WITH_SHAPE);
    when(templateDAO.findByAppId(TPL_APP_ID)).thenReturn(Optional.of(t));
    when(executorRegistry.resolve(SHAPE_IRI)).thenReturn(Optional.of(executor));
    Map<String, Object> vm = Map.of("frames", List.of("f1", "f2"), "durationMs", 500);
    when(executor.materialize(any(TransformRequest.class)))
      .thenReturn(TransformResult.view(vm, "TestExecutor"));

    String json = tools.mappingMaterialize(TPL_APP_ID, null);
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("VIEW", root.get("outputKind").asText());
    assertTrue(root.get("derivedReferenceAppId").isNull());
    assertNotNull(root.get("viewModel"));
    assertEquals("TestExecutor", root.get("executor").asText());
  }

  @Test
  void materializeAcceptsEmptyInputMap() throws Exception {
    ShepardTemplate t = mkTemplate(TPL_APP_ID, "Recipe", "MAPPING_RECIPE", BODY_WITH_SHAPE);
    when(templateDAO.findByAppId(TPL_APP_ID)).thenReturn(Optional.of(t));
    when(executorRegistry.resolve(SHAPE_IRI)).thenReturn(Optional.of(executor));
    when(executor.materialize(any(TransformRequest.class)))
      .thenReturn(TransformResult.reference(REF_APP_ID, "TestExecutor"));

    String json = tools.mappingMaterialize(TPL_APP_ID, Map.of());
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("REFERENCE", root.get("outputKind").asText());
  }

  // ─── mapping_materialize — executor failures ──────────────────────────────

  @Test
  void materializeTranslatesTransformExceptionToInvalidParams() {
    ShepardTemplate t = mkTemplate(TPL_APP_ID, "Recipe", "MAPPING_RECIPE", BODY_WITH_SHAPE);
    when(templateDAO.findByAppId(TPL_APP_ID)).thenReturn(Optional.of(t));
    when(executorRegistry.resolve(SHAPE_IRI)).thenReturn(Optional.of(executor));
    when(executor.materialize(any(TransformRequest.class)))
      .thenThrow(new TransformException("transform.input.missing", "Required input absent"));

    McpException ex = assertThrows(McpException.class,
      () -> tools.mappingMaterialize(TPL_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("transform.input.missing"));
    assertTrue(ex.getMessage().contains("Required input absent"));
  }

  @Test
  void materializeTranslatesTransformExceptionWithNullCode() {
    ShepardTemplate t = mkTemplate(TPL_APP_ID, "Recipe", "MAPPING_RECIPE", BODY_WITH_SHAPE);
    when(templateDAO.findByAppId(TPL_APP_ID)).thenReturn(Optional.of(t));
    when(executorRegistry.resolve(SHAPE_IRI)).thenReturn(Optional.of(executor));
    when(executor.materialize(any(TransformRequest.class)))
      .thenThrow(new TransformException(null, "Something broke"));

    McpException ex = assertThrows(McpException.class,
      () -> tools.mappingMaterialize(TPL_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("transform.unknown-error"));
  }

  @Test
  void materializeWrapsUnexpectedRuntimeExceptionAsInternalError() {
    ShepardTemplate t = mkTemplate(TPL_APP_ID, "Recipe", "MAPPING_RECIPE", BODY_WITH_SHAPE);
    when(templateDAO.findByAppId(TPL_APP_ID)).thenReturn(Optional.of(t));
    when(executorRegistry.resolve(SHAPE_IRI)).thenReturn(Optional.of(executor));
    when(executor.materialize(any(TransformRequest.class)))
      .thenThrow(new RuntimeException("NPE inside executor"));

    McpException ex = assertThrows(McpException.class,
      () -> tools.mappingMaterialize(TPL_APP_ID, null));
    // McpToolSupport.run() wraps unhandled RuntimeException as -32603 internal error
    assertEquals(-32603, ex.getJsonRpcErrorCode());
  }
}
