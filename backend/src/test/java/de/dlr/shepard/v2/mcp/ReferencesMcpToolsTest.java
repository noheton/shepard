package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.v2.references.io.ReferenceV2IO;
import de.dlr.shepard.v2.references.services.ReferencesV2Service;
import io.quarkiverse.mcp.server.McpException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ReferencesMcpTools} (MCP-COV-02-2-REF-CRUD).
 *
 * <p>Follows the same pattern as {@link CollectionMcpToolsTest}: a real
 * {@link McpToolSupport} bean is constructed with mocked collaborators so the
 * error-mapping logic in {@link McpToolSupport#run} is exercised, while
 * {@link ReferencesV2Service} is mocked to avoid a CDI container.
 */
class ReferencesMcpToolsTest {

  static final String DO_APP_ID     = "018f9c5a-7e26-7000-a000-000000000020";
  static final String REF_APP_ID    = "018f9c5a-7e26-7000-a000-000000000030";
  static final String TS_CON_APP_ID = "018f9c5a-7e26-7000-a000-000000000040";

  @Mock ReferencesV2Service referencesService;
  @Mock McpContextBridge contextBridge;
  @Mock EntityIdResolver entityIdResolver;

  ReferencesMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.entityIdResolver = entityIdResolver;
    support.objectMapper = new ObjectMapper();

    tools = new ReferencesMcpTools();
    tools.referencesService = referencesService;
    tools.contextBridge = contextBridge;
    tools.support = support;
  }

  /** Build a minimal ReferenceV2IO usable for serialisation assertions. */
  private static ReferenceV2IO makeRefIO(String appId, String kind) {
    ReferenceV2IO io = new ReferenceV2IO();
    io.setAppId(appId);
    io.setName("test-ref");
    io.setKind(kind);
    return io;
  }

  // ── list_references ───────────────────────────────────────────────────────

  @Test
  void listReferencesReturnsJsonArray() throws Exception {
    ReferenceV2IO ref = makeRefIO(REF_APP_ID, "timeseries");
    when(referencesService.listByDataObject("timeseries", DO_APP_ID, null))
      .thenReturn(List.of(ref));

    String json = tools.listReferences(DO_APP_ID, "timeseries");

    var root = new ObjectMapper().readTree(json);
    assertTrue(root.isArray());
    assertEquals(1, root.size());
    assertEquals(REF_APP_ID, root.get(0).get("appId").asText());
    assertEquals("timeseries", root.get(0).get("kind").asText());
  }

  @Test
  void listReferencesReturnsEmptyArrayWhenNoRefs() throws Exception {
    when(referencesService.listByDataObject("file", DO_APP_ID, null)).thenReturn(List.of());

    String json = tools.listReferences(DO_APP_ID, "file");

    var root = new ObjectMapper().readTree(json);
    assertTrue(root.isArray());
    assertEquals(0, root.size());
  }

  @Test
  void listReferencesNormalisesKindToLowerCase() {
    when(referencesService.listByDataObject("timeseries", DO_APP_ID, null)).thenReturn(List.of());
    tools.listReferences(DO_APP_ID, "TIMESERIES");
    verify(referencesService).listByDataObject("timeseries", DO_APP_ID, null);
  }

  @Test
  void listReferencesRejectsNullDataObjectAppId() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.listReferences(null, "timeseries"));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(referencesService, never()).listByDataObject(any(), any(), any());
  }

  @Test
  void listReferencesRejectsBlankDataObjectAppId() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.listReferences("  ", "timeseries"));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void listReferencesRejectsNullKind() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.listReferences(DO_APP_ID, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── get_reference ─────────────────────────────────────────────────────────

  @Test
  void getReferenceReturnsJsonObject() throws Exception {
    ReferenceV2IO ref = makeRefIO(REF_APP_ID, "timeseries");
    when(referencesService.getByAppId(REF_APP_ID)).thenReturn(ref);

    String json = tools.getReference(REF_APP_ID);

    var root = new ObjectMapper().readTree(json);
    assertEquals(REF_APP_ID, root.get("appId").asText());
    assertEquals("timeseries", root.get("kind").asText());
  }

  @Test
  void getReferenceThrowsInvalidParamsWhenNotFound() {
    when(referencesService.getByAppId(REF_APP_ID))
      .thenThrow(new NotFoundException("No reference with appId " + REF_APP_ID));

    McpException ex = assertThrows(McpException.class, () -> tools.getReference(REF_APP_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void getReferenceRejectsNullAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.getReference(null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(referencesService, never()).getByAppId(any());
  }

  // ── reference_create ──────────────────────────────────────────────────────

  @Test
  void referenceCreateTimeseriesHappyPath() throws Exception {
    ReferenceV2IO created = makeRefIO(REF_APP_ID, "timeseries");
    when(referencesService.create(eq("timeseries"), eq(DO_APP_ID), any())).thenReturn(created);

    String json = tools.referenceCreate(DO_APP_ID, "timeseries", "TR-004 run",
      1_000_000L, 2_000_000L, TS_CON_APP_ID, null, null);

    var root = new ObjectMapper().readTree(json);
    assertEquals(REF_APP_ID, root.get("appId").asText());
    assertEquals("timeseries", root.get("kind").asText());
  }

  @Test
  void referenceCreatePassesTimeseriesBodyFields() {
    when(referencesService.create(eq("timeseries"), eq(DO_APP_ID), any()))
      .thenReturn(makeRefIO(REF_APP_ID, "timeseries"));

    tools.referenceCreate(DO_APP_ID, "timeseries", "label",
      100L, 200L, TS_CON_APP_ID, null, null);

    verify(referencesService).create(eq("timeseries"), eq(DO_APP_ID), argThat(body ->
      "label".equals(body.get("name")) &&
      Long.valueOf(100L).equals(body.get("start")) &&
      Long.valueOf(200L).equals(body.get("end")) &&
      TS_CON_APP_ID.equals(body.get("timeseriesContainerAppId"))
    ));
  }

  @Test
  void referenceCreateFileHappyPath() throws Exception {
    ReferenceV2IO created = makeRefIO(REF_APP_ID, "file");
    created.setReferenceShape("singleton");
    when(referencesService.create(eq("file"), eq(DO_APP_ID), any())).thenReturn(created);

    String json = tools.referenceCreate(DO_APP_ID, "file", "robot.urdf",
      null, null, null, null, null);

    var root = new ObjectMapper().readTree(json);
    assertEquals("file", root.get("kind").asText());
    assertEquals("singleton", root.get("referenceShape").asText());
  }

  @Test
  void referenceCreateUriHappyPath() throws Exception {
    ReferenceV2IO created = makeRefIO(REF_APP_ID, "uri");
    when(referencesService.create(eq("uri"), eq(DO_APP_ID), any())).thenReturn(created);

    String json = tools.referenceCreate(DO_APP_ID, "uri", "Link to paper",
      null, null, null, "https://doi.org/10.1234/example", "isDocumentedBy");

    var root = new ObjectMapper().readTree(json);
    assertEquals("uri", root.get("kind").asText());
    verify(referencesService).create(eq("uri"), eq(DO_APP_ID), argThat(body ->
      "https://doi.org/10.1234/example".equals(body.get("uri")) &&
      "isDocumentedBy".equals(body.get("relationship"))
    ));
  }

  @Test
  void referenceCreateNormalisesKindToLowerCase() {
    when(referencesService.create(eq("file"), eq(DO_APP_ID), any()))
      .thenReturn(makeRefIO(REF_APP_ID, "file"));

    tools.referenceCreate(DO_APP_ID, "FILE", "report.pdf", null, null, null, null, null);

    verify(referencesService).create(eq("file"), eq(DO_APP_ID), any());
  }

  @Test
  void referenceCreateRejectsNullDataObjectAppId() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.referenceCreate(null, "timeseries", null, null, null, null, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(referencesService, never()).create(any(), any(), any());
  }

  @Test
  void referenceCreateRejectsNullKind() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.referenceCreate(DO_APP_ID, null, null, null, null, null, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void referenceCreateExcludesNullOptionalFieldsFromBody() {
    when(referencesService.create(eq("timeseries"), eq(DO_APP_ID), any()))
      .thenReturn(makeRefIO(REF_APP_ID, "timeseries"));

    tools.referenceCreate(DO_APP_ID, "timeseries", null, null, null, null, null, null);

    // No null keys should appear in the body map (handler decides what's required).
    verify(referencesService).create(eq("timeseries"), eq(DO_APP_ID), argThat(Map::isEmpty));
  }

  // ── reference_update ──────────────────────────────────────────────────────

  @Test
  void referenceUpdatePatchesName() throws Exception {
    ReferenceV2IO updated = makeRefIO(REF_APP_ID, "timeseries");
    when(referencesService.patchByAppId(eq(REF_APP_ID), any())).thenReturn(updated);

    String json = tools.referenceUpdate(REF_APP_ID, "new-name", null, null, null, null);

    var root = new ObjectMapper().readTree(json);
    assertEquals(REF_APP_ID, root.get("appId").asText());
    verify(referencesService).patchByAppId(eq(REF_APP_ID), argThat(p ->
      "new-name".equals(p.get("name")) && p.size() == 1
    ));
  }

  @Test
  void referenceUpdatePatchesStartAndEnd() {
    when(referencesService.patchByAppId(eq(REF_APP_ID), any()))
      .thenReturn(makeRefIO(REF_APP_ID, "timeseries"));

    tools.referenceUpdate(REF_APP_ID, null, 100L, 200L, null, null);

    verify(referencesService).patchByAppId(eq(REF_APP_ID), argThat(p ->
      Long.valueOf(100L).equals(p.get("start")) &&
      Long.valueOf(200L).equals(p.get("end")) &&
      !p.containsKey("name")
    ));
  }

  @Test
  void referenceUpdatePatchesUriAndRelationship() {
    when(referencesService.patchByAppId(eq(REF_APP_ID), any()))
      .thenReturn(makeRefIO(REF_APP_ID, "uri"));

    tools.referenceUpdate(REF_APP_ID, null, null, null,
      "https://example.com", "isDocumentedBy");

    verify(referencesService).patchByAppId(eq(REF_APP_ID), argThat(p ->
      "https://example.com".equals(p.get("uri")) &&
      "isDocumentedBy".equals(p.get("relationship"))
    ));
  }

  @Test
  void referenceUpdateRejectsEmptyPatch() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.referenceUpdate(REF_APP_ID, null, null, null, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(referencesService, never()).patchByAppId(any(), any());
  }

  @Test
  void referenceUpdateRejectsNullAppId() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.referenceUpdate(null, "name", null, null, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void referenceUpdateThrowsInvalidParamsWhenNotFound() {
    when(referencesService.patchByAppId(eq(REF_APP_ID), any()))
      .thenThrow(new NotFoundException("No reference with appId " + REF_APP_ID));

    McpException ex = assertThrows(McpException.class,
      () -> tools.referenceUpdate(REF_APP_ID, "name", null, null, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── reference_delete ──────────────────────────────────────────────────────

  @Test
  void referenceDeleteHappyPath() throws Exception {
    String json = tools.referenceDelete(REF_APP_ID);

    var root = new ObjectMapper().readTree(json);
    assertTrue(root.get("deleted").asBoolean());
    assertEquals(REF_APP_ID, root.get("referenceAppId").asText());
    verify(referencesService).deleteByAppId(REF_APP_ID);
  }

  @Test
  void referenceDeleteRejectsNullAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.referenceDelete(null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    verify(referencesService, never()).deleteByAppId(any());
  }

  @Test
  void referenceDeleteRejectsBlankAppId() {
    McpException ex = assertThrows(McpException.class, () -> tools.referenceDelete("  "));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void referenceDeleteThrowsInvalidParamsWhenNotFound() {
    doThrow(new NotFoundException("No reference with appId " + REF_APP_ID))
      .when(referencesService).deleteByAppId(REF_APP_ID);

    McpException ex = assertThrows(McpException.class, () -> tools.referenceDelete(REF_APP_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }
}
