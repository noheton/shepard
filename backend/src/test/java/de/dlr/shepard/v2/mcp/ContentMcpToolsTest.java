package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import de.dlr.shepard.context.semantic.services.SemanticAnnotationService;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.file.entities.ShepardFile;
import de.dlr.shepard.data.file.services.FileContainerService;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import io.quarkiverse.mcp.server.McpException;
import jakarta.ws.rs.NotFoundException;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link ContentMcpTools}. Post task #80 (MCP type-checking +
 * clean errors, MCP-2 entry in aidocs/34) appId resolution lives in
 * {@link McpToolSupport#resolveOfType(String, String, String)} and tools
 * type-check container appIds before dispatching to the service. This
 * test mirrors {@link TimeseriesMcpToolsTest}: a real {@code support}
 * wired with mocks, and label-aware resolver stubs for every happy path.
 */
class ContentMcpToolsTest {

  static final String FILE_CONTAINER_APP_ID = "018f9c5a-7e26-7000-a000-000000000040";
  static final String SD_CONTAINER_APP_ID   = "018f9c5a-7e26-7000-a000-000000000041";
  static final String DO_APP_ID             = "018f9c5a-7e26-7000-a000-000000000050";
  static final long   CONTAINER_OGM_ID      = 77L;
  static final long   DO_OGM_ID             = 99L;

  @Mock FileContainerService fileContainerService;
  @Mock StructuredDataContainerService structuredDataContainerService;
  @Mock SemanticAnnotationService semanticAnnotationService;
  @Mock EntityIdResolver entityIdResolver;
  @Mock McpContextBridge contextBridge;

  ContentMcpTools tools;
  McpToolSupport support;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    support = new McpToolSupport();
    support.entityIdResolver = entityIdResolver;
    support.objectMapper = new ObjectMapper();

    tools = new ContentMcpTools();
    tools.fileContainerService = fileContainerService;
    tools.structuredDataContainerService = structuredDataContainerService;
    tools.semanticAnnotationService = semanticAnnotationService;
    tools.contextBridge = contextBridge;
    tools.support = support;

    // Default: resolver returns the matching label per appId. Tests that
    // care about the wrong-label / not-found path override these.
    when(entityIdResolver.resolveWithLabels(FILE_CONTAINER_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(CONTAINER_OGM_ID, List.of("FileContainer")));
    when(entityIdResolver.resolveWithLabels(SD_CONTAINER_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(CONTAINER_OGM_ID, List.of("StructuredDataContainer")));
    when(entityIdResolver.resolveWithLabels(DO_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(DO_OGM_ID, List.of("DataObject")));
  }

  // ── list_files ────────────────────────────────────────────────────────────

  @Test
  void listFilesThrowsWhenContainerMissing() {
    // Type-check passes (resolver returns FileContainer label), but the
    // service returns null — tool raises -32602 with an explicit message.
    when(fileContainerService.getContainerByAppId(FILE_CONTAINER_APP_ID)).thenReturn(null);

    McpException ex = assertThrows(McpException.class, () -> tools.listFiles(FILE_CONTAINER_APP_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains(FILE_CONTAINER_APP_ID));
  }

  @Test
  void listFilesRejectsWrongContainerType() {
    when(entityIdResolver.resolveWithLabels(FILE_CONTAINER_APP_ID))
      .thenReturn(new EntityIdResolver.LabeledResolution(CONTAINER_OGM_ID, List.of("StructuredDataContainer")));

    McpException ex = assertThrows(McpException.class, () -> tools.listFiles(FILE_CONTAINER_APP_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("FileContainer"));
    assertTrue(ex.getMessage().contains("StructuredDataContainer"));
  }

  @Test
  void listFilesReturnsFileRows() throws Exception {
    FileContainer container = new FileContainer();
    ShepardFile f = new ShepardFile();
    f.setAppId("file-app-id");
    f.setOid("oid-1");
    f.setFilename("ndt-scan.pdf");
    f.setMd5("md5x");
    f.setFileSize(12345L);
    f.setProviderId("local");
    f.setCreatedAt(new Date(0L));
    container.setFiles(List.of(f));
    when(fileContainerService.getContainerByAppId(FILE_CONTAINER_APP_ID)).thenReturn(container);

    String json = tools.listFiles(FILE_CONTAINER_APP_ID);
    var root = new ObjectMapper().readTree(json);

    assertTrue(root.isArray());
    assertEquals(1, root.size());
    var row = root.get(0);
    assertEquals("file-app-id", row.get("appId").asText());
    assertEquals("oid-1", row.get("oid").asText());
    assertEquals("ndt-scan.pdf", row.get("filename").asText());
    assertEquals(12345L, row.get("fileSize").asLong());
  }

  @Test
  void listFilesReturnsEmptyArrayForEmptyContainer() throws Exception {
    FileContainer container = new FileContainer();
    when(fileContainerService.getContainerByAppId(FILE_CONTAINER_APP_ID)).thenReturn(container);

    String json = tools.listFiles(FILE_CONTAINER_APP_ID);
    var root = new ObjectMapper().readTree(json);
    assertTrue(root.isArray());
    assertEquals(0, root.size());
  }

  // ── list_structured_data ──────────────────────────────────────────────────

  @Test
  void listStructuredDataReturnsRows() throws Exception {
    StructuredDataContainer container = new StructuredDataContainer();
    StructuredData sd = new StructuredData("oid-2", new Date(0L), "test-matrix");
    sd.setAppId("sd-app-id");
    container.setStructuredDatas(List.of(sd));
    when(structuredDataContainerService.getContainerByAppId(SD_CONTAINER_APP_ID)).thenReturn(container);

    String json = tools.listStructuredData(SD_CONTAINER_APP_ID);
    var root = new ObjectMapper().readTree(json);

    assertEquals(1, root.size());
    var row = root.get(0);
    assertEquals("sd-app-id", row.get("appId").asText());
    assertEquals("oid-2", row.get("oid").asText());
    assertEquals("test-matrix", row.get("name").asText());
  }

  @Test
  void listStructuredDataThrowsWhenContainerMissing() {
    when(structuredDataContainerService.getContainerByAppId(SD_CONTAINER_APP_ID)).thenReturn(null);

    McpException ex = assertThrows(McpException.class, () -> tools.listStructuredData(SD_CONTAINER_APP_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── list_annotations ──────────────────────────────────────────────────────

  @Test
  void listAnnotationsReturnsRows() throws Exception {
    SemanticAnnotation ann = new SemanticAnnotation();
    ann.setAppId("ann-1");
    ann.setPropertyName("propellant");
    ann.setPropertyIRI("http://example.org/prop/propellant");
    ann.setValueName("LOX/LH2");
    ann.setValueIRI("http://example.org/val/LOX-LH2");
    ann.setNumericValue(null);
    ann.setUnitIRI(null);
    SemanticRepository repo = new SemanticRepository();
    repo.setName("internal");
    ann.setPropertyRepository(repo);

    when(semanticAnnotationService.getAllAnnotationsByShepardId(DO_OGM_ID)).thenReturn(List.of(ann));

    String json = tools.listAnnotations(DO_APP_ID);
    var root = new ObjectMapper().readTree(json);

    assertEquals(1, root.size());
    var row = root.get(0);
    assertEquals("ann-1", row.get("appId").asText());
    assertEquals("propellant", row.get("propertyName").asText());
    assertEquals("LOX/LH2", row.get("valueName").asText());
    assertEquals("internal", row.get("propertyRepository").asText());
  }

  @Test
  void listAnnotationsQuantified_emitsNumericAndUnit() throws Exception {
    SemanticAnnotation ann = new SemanticAnnotation();
    ann.setAppId("ann-2");
    ann.setPropertyName("targetThrust");
    ann.setNumericValue(25.0);
    ann.setUnitIRI("http://qudt.org/vocab/unit/KN");

    when(semanticAnnotationService.getAllAnnotationsByShepardId(anyLong())).thenReturn(List.of(ann));

    String json = tools.listAnnotations(DO_APP_ID);
    var root = new ObjectMapper().readTree(json);
    var row = root.get(0);
    assertNotNull(row.get("numericValue"));
    assertEquals(25.0, row.get("numericValue").asDouble());
    assertEquals("http://qudt.org/vocab/unit/KN", row.get("unitIRI").asText());
  }

  @Test
  void listAnnotationsThrowsInvalidParamsWhenDataObjectUnknown() {
    when(entityIdResolver.resolveWithLabels(DO_APP_ID)).thenThrow(new NotFoundException());

    McpException ex = assertThrows(McpException.class, () -> tools.listAnnotations(DO_APP_ID));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }
}
