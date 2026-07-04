package de.dlr.shepard.v2.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.references.file.entities.FileReference;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
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
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
  @Mock SingletonFileReferenceService singletonFileReferenceService;
  @Mock PermissionsService permissionsService;
  @Mock AuthenticationContext authenticationContext;
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
    tools.singletonFileReferenceService = singletonFileReferenceService;
    tools.permissionsService = permissionsService;
    tools.authenticationContext = authenticationContext;
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

    String json = tools.listAnnotations(DO_APP_ID, null, null);
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

    String json = tools.listAnnotations(DO_APP_ID, null, null);
    var root = new ObjectMapper().readTree(json);
    var row = root.get(0);
    assertNotNull(row.get("numericValue"));
    assertEquals(25.0, row.get("numericValue").asDouble());
    assertEquals("http://qudt.org/vocab/unit/KN", row.get("unitIRI").asText());
  }

  @Test
  void listAnnotationsThrowsInvalidParamsWhenDataObjectUnknown() {
    when(entityIdResolver.resolveWithLabels(DO_APP_ID)).thenThrow(new NotFoundException());

    McpException ex = assertThrows(McpException.class, () -> tools.listAnnotations(DO_APP_ID, null, null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void listAnnotationsPaginates_page1of2() throws Exception {
    // Build 3 annotations; request page=1, pageSize=2 → returns only the 3rd
    SemanticAnnotation a1 = ann("ann-a1", "prop1");
    SemanticAnnotation a2 = ann("ann-a2", "prop2");
    SemanticAnnotation a3 = ann("ann-a3", "prop3");
    when(semanticAnnotationService.getAllAnnotationsByShepardId(DO_OGM_ID)).thenReturn(List.of(a1, a2, a3));

    String json = tools.listAnnotations(DO_APP_ID, 1, 2);
    var root = new ObjectMapper().readTree(json);

    assertEquals(1, root.size(), "page 1 with pageSize=2 of 3 total must return 1 row");
    assertEquals("ann-a3", root.get(0).get("appId").asText());
  }

  @Test
  void listAnnotationsCapsPagesizeAt200() throws Exception {
    // 5 annotations, pageSize=999 → clamped to 200, all 5 returned on page 0
    List<SemanticAnnotation> anns = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) anns.add(ann("ann-" + i, "p" + i));
    when(semanticAnnotationService.getAllAnnotationsByShepardId(DO_OGM_ID)).thenReturn(anns);

    String json = tools.listAnnotations(DO_APP_ID, 0, 999);
    var root = new ObjectMapper().readTree(json);

    assertEquals(5, root.size(), "all 5 fit within the 200-cap on page 0");
  }

  private static SemanticAnnotation ann(String appId, String propertyName) {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setAppId(appId);
    a.setPropertyName(propertyName);
    return a;
  }

  // ── file_upload (MCP-COV-04) ──────────────────────────────────────────────

  private static String b64(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  private FileReference singletonWithFile(String appId, long parentOgmId, String name, String filename, long size, String md5) {
    FileReference ref = new FileReference();
    ref.setAppId(appId);
    ref.setName(name);
    ShepardFile f = new ShepardFile();
    f.setFilename(filename);
    f.setFileSize(size);
    f.setMd5(md5);
    ref.setFile(f);
    DataObject parent = new DataObject();
    parent.setId(parentOgmId);
    ref.setDataObject(parent);
    return ref;
  }

  @Test
  void fileUploadHappyPathReturnsCreatedSingleton() throws Exception {
    byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
    when(authenticationContext.getCurrentUserName()).thenReturn("flo");
    when(singletonFileReferenceService.getDataObjectOgmId(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Write, "flo")).thenReturn(true);

    FileReference created = singletonWithFile(
      "018f9c5a-7e26-7000-a000-000000000060", DO_OGM_ID,
      "calibration", "calibration.csv", payload.length, "deadbeef"
    );
    when(singletonFileReferenceService.createSingleton(
      eq(DO_APP_ID), eq("calibration"), eq("calibration.csv"), any(), eq((long) payload.length))
    ).thenReturn(created);

    String json = tools.fileUpload(DO_APP_ID, "calibration", "calibration.csv", b64(payload), "text/csv");

    var root = new ObjectMapper().readTree(json);
    assertEquals("018f9c5a-7e26-7000-a000-000000000060", root.get("appId").asText());
    assertEquals("calibration", root.get("name").asText());
    assertEquals(DO_APP_ID, root.get("dataObjectAppId").asText());
    assertEquals("calibration.csv", root.get("filename").asText());
    assertEquals(payload.length, root.get("fileSize").asLong());
    assertEquals("text/csv", root.get("mimeTypeHint").asText());
  }

  @Test
  void fileUploadUsesFilenameWhenNameOmitted() throws Exception {
    byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
    when(authenticationContext.getCurrentUserName()).thenReturn("flo");
    when(singletonFileReferenceService.getDataObjectOgmId(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Write, "flo")).thenReturn(true);
    FileReference created = singletonWithFile("018f9c5a-7e26-7000-a000-000000000061", DO_OGM_ID, "a.txt", "a.txt", 1, "x");
    when(singletonFileReferenceService.createSingleton(eq(DO_APP_ID), eq("a.txt"), eq("a.txt"), any(), eq(1L))).thenReturn(created);

    String json = tools.fileUpload(DO_APP_ID, null, "a.txt", b64(payload), null);

    var root = new ObjectMapper().readTree(json);
    assertEquals("a.txt", root.get("name").asText());
    assertTrue(root.get("mimeTypeHint") == null || root.get("mimeTypeHint").isNull(),
      "mimeType not supplied -> mimeTypeHint omitted");
  }

  @Test
  void fileUploadRejectsBlankRequiredFields() {
    assertEquals(-32602,
      assertThrows(McpException.class, () -> tools.fileUpload("", "n", "f.csv", b64(new byte[]{1}), null))
        .getJsonRpcErrorCode());
    assertEquals(-32602,
      assertThrows(McpException.class, () -> tools.fileUpload(DO_APP_ID, "n", "", b64(new byte[]{1}), null))
        .getJsonRpcErrorCode());
    assertEquals(-32602,
      assertThrows(McpException.class, () -> tools.fileUpload(DO_APP_ID, "n", "f.csv", "", null))
        .getJsonRpcErrorCode());
  }

  @Test
  void fileUploadRejectsInvalidBase64() {
    McpException ex = assertThrows(McpException.class,
      () -> tools.fileUpload(DO_APP_ID, "n", "f.csv", "not-base64!!!@@@", null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().toLowerCase().contains("base64"));
  }

  @Test
  void fileUploadRejectsOversizedPayload() {
    // 11 MiB of 'A' bytes – above the 10 MiB cap.
    byte[] big = new byte[ContentMcpTools.FILE_UPLOAD_MAX_BYTES + 1];
    McpException ex = assertThrows(McpException.class,
      () -> tools.fileUpload(DO_APP_ID, "n", "big.bin", b64(big), null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("POST /v2/references?kind=file"));
  }

  @Test
  void fileUpload401WhenNoCaller() {
    when(authenticationContext.getCurrentUserName()).thenReturn(null);
    McpException ex = assertThrows(McpException.class,
      () -> tools.fileUpload(DO_APP_ID, "n", "f.csv", b64(new byte[]{1}), null));
    assertEquals(-32001, ex.getJsonRpcErrorCode());
  }

  @Test
  void fileUpload403WhenWriteDenied() {
    when(authenticationContext.getCurrentUserName()).thenReturn("flo");
    when(singletonFileReferenceService.getDataObjectOgmId(DO_APP_ID)).thenReturn(DO_OGM_ID);
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Write, "flo")).thenReturn(false);
    McpException ex = assertThrows(McpException.class,
      () -> tools.fileUpload(DO_APP_ID, "n", "f.csv", b64(new byte[]{1}), null));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
  }

  @Test
  void fileUpload404WhenParentMissing() {
    when(authenticationContext.getCurrentUserName()).thenReturn("flo");
    when(singletonFileReferenceService.getDataObjectOgmId(DO_APP_ID)).thenReturn(null);
    McpException ex = assertThrows(McpException.class,
      () -> tools.fileUpload(DO_APP_ID, "n", "f.csv", b64(new byte[]{1}), null));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  // ── file_content (MCP-COV-04) ─────────────────────────────────────────────

  @Test
  void fileContentHappyPathReturnsBase64Bytes() throws Exception {
    byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
    String refAppId = "018f9c5a-7e26-7000-a000-000000000070";
    FileReference ref = singletonWithFile(refAppId, DO_OGM_ID, "n", "f.txt", payload.length, "md5");
    when(singletonFileReferenceService.getByAppId(refAppId)).thenReturn(ref);
    when(authenticationContext.getCurrentUserName()).thenReturn("flo");
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, "flo")).thenReturn(true);
    NamedInputStream named = new NamedInputStream("oid-x", new ByteArrayInputStream(payload), "f.txt", (long) payload.length);
    when(singletonFileReferenceService.getPayload(refAppId)).thenReturn(named);

    String json = tools.fileContent(refAppId);

    var root = new ObjectMapper().readTree(json);
    assertEquals(refAppId, root.get("appId").asText());
    assertEquals("f.txt", root.get("filename").asText());
    assertEquals(payload.length, root.get("fileSize").asInt());
    byte[] back = Base64.getDecoder().decode(root.get("contentBase64").asText());
    assertEquals("hello", new String(back, StandardCharsets.UTF_8));
  }

  @Test
  void fileContent404WhenUnknownAppId() {
    when(singletonFileReferenceService.getByAppId("missing")).thenReturn(null);
    McpException ex = assertThrows(McpException.class, () -> tools.fileContent("missing"));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
  }

  @Test
  void fileContentRejectsDeletedReference() {
    String refAppId = "018f9c5a-7e26-7000-a000-000000000071";
    FileReference ref = singletonWithFile(refAppId, DO_OGM_ID, "n", "f.txt", 1, "md5");
    ref.setDeleted(true);
    when(singletonFileReferenceService.getByAppId(refAppId)).thenReturn(ref);
    McpException ex = assertThrows(McpException.class, () -> tools.fileContent(refAppId));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().toLowerCase().contains("delete"));
  }

  @Test
  void fileContentRejectsOversizedStoredFile() {
    String refAppId = "018f9c5a-7e26-7000-a000-000000000072";
    FileReference ref = singletonWithFile(refAppId, DO_OGM_ID, "n", "big.bin", ContentMcpTools.FILE_UPLOAD_MAX_BYTES + 1L, "md5");
    when(singletonFileReferenceService.getByAppId(refAppId)).thenReturn(ref);
    when(authenticationContext.getCurrentUserName()).thenReturn("flo");
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, "flo")).thenReturn(true);
    NamedInputStream named = new NamedInputStream("oid-x", new ByteArrayInputStream(new byte[1]), "big.bin", ContentMcpTools.FILE_UPLOAD_MAX_BYTES + 1L);
    when(singletonFileReferenceService.getPayload(refAppId)).thenReturn(named);

    McpException ex = assertThrows(McpException.class, () -> tools.fileContent(refAppId));
    assertEquals(-32602, ex.getJsonRpcErrorCode());
    assertTrue(ex.getMessage().contains("/v2/files/"));
  }

  @Test
  void fileContent403WhenReadDenied() {
    String refAppId = "018f9c5a-7e26-7000-a000-000000000073";
    FileReference ref = singletonWithFile(refAppId, DO_OGM_ID, "n", "f.txt", 1, "md5");
    when(singletonFileReferenceService.getByAppId(refAppId)).thenReturn(ref);
    when(authenticationContext.getCurrentUserName()).thenReturn("flo");
    when(permissionsService.isAccessTypeAllowedForUser(DO_OGM_ID, AccessType.Read, "flo")).thenReturn(false);
    McpException ex = assertThrows(McpException.class, () -> tools.fileContent(refAppId));
    assertEquals(-32002, ex.getJsonRpcErrorCode());
  }
}
