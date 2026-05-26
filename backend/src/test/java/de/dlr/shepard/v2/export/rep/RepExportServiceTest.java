package de.dlr.shepard.v2.export.rep;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.collection.daos.CollectionDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.services.ProvJsonLdRenderer;
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link RepExportService} (TPL14).
 *
 * <p>Pure Mockito — no @QuarkusTest, no container, no database.
 * Field injection is done directly (same pattern as {@code CollectionExportUrlRestTest}).
 */
class RepExportServiceTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000042";
  static final String CALLER = "alice";

  @Mock CollectionDAO collectionDAO;
  @Mock ActivityDAO activityDAO;
  @Mock ProvJsonLdRenderer provRenderer;

  RepExportService service;
  ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new RepExportService();
    service.collectionDAO = collectionDAO;
    service.activityDAO = activityDAO;
    service.provRenderer = provRenderer;

    // Default: renderer returns an empty PROV-O document.
    when(
      provRenderer.renderProvO(any())
    ).thenReturn(Map.of("@context", Map.of("prov", "http://www.w3.org/ns/prov#"), "@graph", List.of()));

    // Default: no activities.
    when(activityDAO.list(isNull(), eq("Collection"), eq(COLL_APP_ID), isNull(), isNull(), anyInt()))
      .thenReturn(List.of());
  }

  // --- Test 1: 404 when collection is missing. ----------------------------

  @Test
  void throws404WhenCollectionMissing() {
    when(collectionDAO.findByAppId(COLL_APP_ID, CALLER)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> service.buildExport(COLL_APP_ID, CALLER));
  }

  // --- Test 2: BagIt structure is valid (mandatory files present). ---------

  @Test
  void bagZipContainsMandatoryFiles() throws Exception {
    when(collectionDAO.findByAppId(COLL_APP_ID, CALLER)).thenReturn(makeCollection(0));

    RepExportIO io = service.buildExport(COLL_APP_ID, CALLER);

    assertNotNull(io.getBagBase64(), "bag should be returned inline for a tiny collection");
    byte[] bagBytes = Base64.getDecoder().decode(io.getBagBase64());

    List<String> entries = listZipEntries(bagBytes);
    assertTrue(entries.contains("bagit.txt"), "bagit.txt must be present (RFC 8493 §2.1.1)");
    assertTrue(entries.contains("bag-info.txt"), "bag-info.txt must be present");
    assertTrue(entries.contains("manifest-sha256.txt"), "payload manifest must be present");
    assertTrue(entries.contains("tagmanifest-sha256.txt"), "tag manifest must be present");
    assertTrue(entries.contains("data/ro-crate-metadata.json"), "RO-Crate metadata must be in data/");
    assertTrue(entries.contains("data/PROV-O.jsonld"), "PROV-O JSON-LD must be in data/");
  }

  // --- Test 3: RO-Crate document has mandatory @context. ------------------

  @Test
  void roCrateHasRequiredContext() throws Exception {
    when(collectionDAO.findByAppId(COLL_APP_ID, CALLER)).thenReturn(makeCollection(2));

    RepExportIO io = service.buildExport(COLL_APP_ID, CALLER);

    Map<String, Object> roCrate = readZipEntry(io, "data/ro-crate-metadata.json");
    Object ctx = roCrate.get("@context");
    assertNotNull(ctx, "@context must be present");
    assertEquals("https://w3id.org/ro/crate/1.1/context", ctx, "@context must be RO-Crate 1.1 canonical URL");

    // Root Dataset must have @id = "./"
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> graph = (List<Map<String, Object>>) roCrate.get("@graph");
    assertNotNull(graph);
    boolean hasRoot = graph.stream().anyMatch(n -> "./".equals(n.get("@id")));
    assertTrue(hasRoot, "Root Dataset with @id './' must be present");
  }

  // --- Test 4: PROV-O document contains @context and @graph. --------------

  @Test
  void provOHasContextAndGraph() throws Exception {
    Activity act = new Activity();
    act.setAppId("act-001");
    act.setActionKind("CREATE");
    act.setTargetAppId(COLL_APP_ID);
    act.setTargetKind("Collection");
    act.setAgentUsername(CALLER);
    act.setStartedAtMillis(1_700_000_000_000L);
    act.setEndedAtMillis(1_700_000_001_000L);

    when(activityDAO.list(isNull(), eq("Collection"), eq(COLL_APP_ID), isNull(), isNull(), anyInt()))
      .thenReturn(List.of(act));

    // Use real renderer to verify PROV-O content.
    ProvJsonLdRenderer realRenderer = new ProvJsonLdRenderer();
    service.provRenderer = realRenderer;

    when(collectionDAO.findByAppId(COLL_APP_ID, CALLER)).thenReturn(makeCollection(1));

    RepExportIO io = service.buildExport(COLL_APP_ID, CALLER);

    Map<String, Object> provO = readZipEntry(io, "data/PROV-O.jsonld");
    assertNotNull(provO.get("@context"), "PROV-O must have @context");
    assertNotNull(provO.get("@graph"), "PROV-O must have @graph");
  }

  // --- Test 5: manifest checksums are SHA-256 hex (64 chars). -------------

  @Test
  void manifestChecksumsAreSha256Hex() throws Exception {
    when(collectionDAO.findByAppId(COLL_APP_ID, CALLER)).thenReturn(makeCollection(1));

    RepExportIO io = service.buildExport(COLL_APP_ID, CALLER);
    byte[] bagBytes = Base64.getDecoder().decode(io.getBagBase64());

    String manifestContent = readZipEntryAsString(bagBytes, "manifest-sha256.txt");
    assertNotNull(manifestContent);
    for (String line : manifestContent.split("\n")) {
      if (line.isBlank()) continue;
      String[] parts = line.split("  ", 2);
      assertEquals(2, parts.length, "manifest line must have checksum + path: " + line);
      String checksum = parts[0];
      assertEquals(64, checksum.length(), "SHA-256 hex must be 64 chars: " + checksum);
      assertTrue(checksum.matches("[0-9a-f]+"), "checksum must be lowercase hex: " + checksum);
    }
  }

  // --- Test 6: IO fields are populated correctly. -------------------------

  @Test
  void ioFieldsPopulated() {
    Collection coll = makeCollection(3);
    when(collectionDAO.findByAppId(COLL_APP_ID, CALLER)).thenReturn(coll);

    RepExportIO io = service.buildExport(COLL_APP_ID, CALLER);

    assertEquals("READY", io.getStatus());
    assertNotNull(io.getExportId());
    assertEquals(36, io.getExportId().length(), "exportId should be UUID length");
    assertNotNull(io.getExportedAt());
    assertTrue(io.getFileName().contains(COLL_APP_ID.substring(0, 8)), "fileName should reference collectionAppId");
    assertTrue(io.getFileName().endsWith(".zip"), "fileName should end with .zip");
    assertEquals(3, io.getDataObjectCount());
    assertTrue(io.getBagSizeBytes() > 0);
  }

  // --- Test 7: DataObject count in IO matches collection content. ---------

  @Test
  void dataObjectCountMatchesCollectionSize() {
    int expectedCount = 5;
    when(collectionDAO.findByAppId(COLL_APP_ID, CALLER)).thenReturn(makeCollection(expectedCount));

    RepExportIO io = service.buildExport(COLL_APP_ID, CALLER);

    assertEquals(expectedCount, io.getDataObjectCount());
  }

  // --- Test 8: bagit.txt content follows RFC 8493 §2.1.1. -----------------

  @Test
  void bagitTxtHasCorrectContent() throws Exception {
    when(collectionDAO.findByAppId(COLL_APP_ID, CALLER)).thenReturn(makeCollection(0));

    RepExportIO io = service.buildExport(COLL_APP_ID, CALLER);
    byte[] bagBytes = Base64.getDecoder().decode(io.getBagBase64());

    String bagitTxt = readZipEntryAsString(bagBytes, "bagit.txt");
    assertNotNull(bagitTxt);
    assertTrue(bagitTxt.contains("BagIt-Version: 1.0"), "bagit.txt must declare BagIt-Version: 1.0");
    assertTrue(
      bagitTxt.contains("Tag-File-Character-Encoding: UTF-8"),
      "bagit.txt must declare Tag-File-Character-Encoding: UTF-8"
    );
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static Collection makeCollection(int doCount) {
    Collection coll = new Collection();
    coll.setAppId(COLL_APP_ID);
    coll.setName("Test Collection");
    coll.setDescription("A test collection for TPL14 unit tests");
    List<DataObject> dataObjects = new ArrayList<>();
    for (int i = 0; i < doCount; i++) {
      DataObject dobj = new DataObject();
      dobj.setAppId("018f9c5a-7e26-7000-b000-" + String.format("%012d", i));
      dobj.setName("DataObject " + i);
      dataObjects.add(dobj);
    }
    coll.setDataObjects(dataObjects);
    return coll;
  }

  private static List<String> listZipEntries(byte[] zipBytes) throws IOException {
    List<String> names = new ArrayList<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry e;
      while ((e = zis.getNextEntry()) != null) {
        names.add(e.getName());
        zis.closeEntry();
      }
    }
    return names;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readZipEntry(RepExportIO io, String entryName) throws IOException {
    byte[] bagBytes = Base64.getDecoder().decode(io.getBagBase64());
    String json = readZipEntryAsString(bagBytes, entryName);
    assertNotNull(json, "ZIP entry not found: " + entryName);
    return objectMapper.readValue(json, Map.class);
  }

  private static String readZipEntryAsString(byte[] zipBytes, String entryName) throws IOException {
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry e;
      while ((e = zis.getNextEntry()) != null) {
        if (e.getName().equals(entryName)) {
          return new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        zis.closeEntry();
      }
    }
    return null;
  }
}
