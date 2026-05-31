package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.export.rep.RepExportIO;
import de.dlr.shepard.v2.export.rep.RepExportService;
import de.dlr.shepard.v2.shapes.validator.JenaShaclValidator;
import io.quarkiverse.mcp.server.McpException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * MCP-COV-10 — unit tests for {@link ShapesMcpTools}.
 */
class ShapesMcpToolsTest {

  static final String TPL_APP_ID  = "018f9c5a-7e26-7000-e100-000000000001";
  static final String DO_APP_ID   = "018f9c5a-7e26-7000-e100-000000000010";
  static final String COLL_APP_ID = "018f9c5a-7e26-7000-e100-000000000020";
  static final String CALLER      = "alice";
  static final long   COLL_OGM_ID = 7L;

  static final String VIEW_BODY =
    "{\"renderer\":\"tresjs\",\"channelBindings\":[" +
    "{\"role\":\"x\",\"channelSelector\":\"sel-x\",\"unit\":\"m\",\"required\":true}," +
    "{\"role\":\"y\",\"channelSelector\":\"sel-y\",\"required\":false}" +
    "]}";

  @Mock ShepardTemplateDAO templateDAO;
  @Mock JenaShaclValidator shaclValidator;
  @Mock RepExportService repExportService;
  @Mock CollectionPropertiesDAO collectionPropertiesDAO;
  @Mock PermissionsService permissionsService;
  @Mock AuthenticationContext authenticationContext;
  @Mock McpContextBridge contextBridge;

  ShapesMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.objectMapper = new ObjectMapper();

    tools = new ShapesMcpTools();
    tools.templateDAO = templateDAO;
    tools.shaclValidator = shaclValidator;
    tools.repExportService = repExportService;
    tools.collectionPropertiesDAO = collectionPropertiesDAO;
    tools.permissionsService = permissionsService;
    tools.authenticationContext = authenticationContext;
    tools.contextBridge = contextBridge;
    tools.support = support;

    when(authenticationContext.getCurrentUserName()).thenReturn(CALLER);
  }

  private ShepardTemplate mkTemplate(String kind, String body) {
    ShepardTemplate t = new ShepardTemplate();
    t.setAppId(TPL_APP_ID);
    t.setTemplateKind(kind);
    t.setBody(body);
    return t;
  }

  // ── shape_render ─────────────────────────────────────────────────────────

  @Test
  void renderProjectsChannelBindingsForViewRecipe() throws Exception {
    when(templateDAO.findByAppId(TPL_APP_ID))
      .thenReturn(Optional.of(mkTemplate("VIEW_RECIPE", VIEW_BODY)));

    String json = tools.shapeRender(TPL_APP_ID, DO_APP_ID, null);
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals(TPL_APP_ID, root.get("templateAppId").asText());
    assertEquals(DO_APP_ID, root.get("focusShepardId").asText());
    assertEquals("tresjs", root.get("renderer").asText());
    var bindings = root.get("channelBindings");
    assertEquals(2, bindings.size());
    assertEquals("x", bindings.get(0).get("role").asText());
    assertEquals("DECLARED", bindings.get(0).get("status").asText());
    assertEquals("m", bindings.get(0).get("unit").asText());
  }

  @Test
  void renderRejectsNonViewRecipeKind() {
    when(templateDAO.findByAppId(TPL_APP_ID))
      .thenReturn(Optional.of(mkTemplate("DATA_RECIPE", "{}")));
    McpException ex = assertThrows(McpException.class,
      () -> tools.shapeRender(TPL_APP_ID, DO_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("VIEW_RECIPE"));
  }

  @Test
  void renderMapsMissingTemplateToInvalidParams() {
    when(templateDAO.findByAppId(TPL_APP_ID)).thenReturn(Optional.empty());
    McpException ex = assertThrows(McpException.class,
      () -> tools.shapeRender(TPL_APP_ID, DO_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void renderRejectsBlankTemplate() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.shapeRender("", DO_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── shape_validate ───────────────────────────────────────────────────────

  @Test
  void validateReturnsConformingReport() throws Exception {
    JenaShaclValidator.Report ok = new JenaShaclValidator.Report(true, null, null, List.of());
    when(shaclValidator.validate("data", "shapes")).thenReturn(ok);

    String json = tools.shapeValidate("data", "shapes");
    JsonNode root = new ObjectMapper().readTree(json);
    assertTrue(root.get("conforms").asBoolean());
    assertEquals(0, root.get("findings").size());
    assertTrue(root.get("parseError").isNull());
  }

  @Test
  void validateReturnsFindingsWhenNonConformant() throws Exception {
    JenaShaclValidator.Finding f = new JenaShaclValidator.Finding(
      "http://example/focus", "http://example/path", "bad-value",
      "Violation", "expected literal"
    );
    JenaShaclValidator.Report report =
      new JenaShaclValidator.Report(false, null, null, List.of(f));
    when(shaclValidator.validate("d", "s")).thenReturn(report);

    String json = tools.shapeValidate("d", "s");
    JsonNode root = new ObjectMapper().readTree(json);
    assertFalse(root.get("conforms").asBoolean());
    assertEquals(1, root.get("findings").size());
    var fin = root.get("findings").get(0);
    assertEquals("http://example/focus", fin.get("focusNode").asText());
    assertEquals("Violation", fin.get("severity").asText());
  }

  @Test
  void validateSurfacesParseErrorAsReportField() throws Exception {
    JenaShaclValidator.Report parseErr =
      JenaShaclValidator.Report.parseError("data graph parse error");
    when(shaclValidator.validate("bad", "shape")).thenReturn(parseErr);

    String json = tools.shapeValidate("bad", "shape");
    JsonNode root = new ObjectMapper().readTree(json);
    assertFalse(root.get("conforms").asBoolean());
    assertEquals("data graph parse error", root.get("parseError").asText());
  }

  // ── rep_export ───────────────────────────────────────────────────────────

  @Test
  void repExportReturnsBagInfo() throws Exception {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID))
      .thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER, 0L))
      .thenReturn(true);
    RepExportIO io = new RepExportIO();
    io.setExportId("exp-1");
    io.setStatus("READY");
    io.setBagBase64("base64bytes");
    io.setFileName(COLL_APP_ID + "-rep.bag.zip");
    io.setExportedAt(Instant.parse("2026-01-01T00:00:00Z"));
    io.setDataObjectCount(15);
    io.setBagSizeBytes(4096);
    when(repExportService.buildExport(COLL_APP_ID, CALLER)).thenReturn(io);

    String json = tools.repExport(COLL_APP_ID, "EN-9100");
    JsonNode root = new ObjectMapper().readTree(json);
    assertEquals("exp-1", root.get("exportId").asText());
    assertEquals("READY", root.get("status").asText());
    assertEquals("base64bytes", root.get("bagBase64").asText());
    assertEquals(15, root.get("dataObjectCount").asInt());
    assertEquals("EN-9100", root.get("profileEcho").asText());
  }

  @Test
  void repExportRejectsMissingCollection() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID))
      .thenReturn(Optional.empty());
    McpException ex = assertThrows(McpException.class,
      () -> tools.repExport(COLL_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void repExportPropagatesForbidden() {
    when(collectionPropertiesDAO.findCollectionIdByAppId(COLL_APP_ID))
      .thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER, 0L))
      .thenReturn(false);
    McpException ex = assertThrows(McpException.class,
      () -> tools.repExport(COLL_APP_ID, null));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
  }

  @Test
  void repExportRejectsBlankAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.repExport("", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  @Test
  void parseChannelBindingsReturnsEmptyOnNullOrMalformed() {
    assertEquals(0, ShapesMcpTools.parseChannelBindings(null).size());
    assertEquals(0, ShapesMcpTools.parseChannelBindings("{}").size());
    assertEquals(0, ShapesMcpTools.parseChannelBindings("not json").size());
  }

  @Test
  void parseChannelBindingsDefaultsRequiredToTrue() {
    List<Map<String, Object>> out =
      ShapesMcpTools.parseChannelBindings("{\"channelBindings\":[{\"role\":\"r\"}]}");
    assertEquals(1, out.size());
    assertEquals(true, out.get(0).get("required"));
    assertEquals("DECLARED", out.get(0).get("status"));
  }

  @Test
  void parseRendererHandlesAbsentField() {
    assertEquals(null, ShapesMcpTools.parseRenderer(null));
    assertEquals(null, ShapesMcpTools.parseRenderer("{}"));
    assertEquals("tresjs", ShapesMcpTools.parseRenderer("{\"renderer\":\"tresjs\"}"));
  }
}
