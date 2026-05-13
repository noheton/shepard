package de.dlr.shepard.context.semantic.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.context.semantic.OntologySeedService.OntologyEntry;
import de.dlr.shepard.context.semantic.RuntimeConfig;
import de.dlr.shepard.context.semantic.daos.SemanticConfigDAO;
import de.dlr.shepard.context.semantic.daos.UserOntologyBundleDAO;
import de.dlr.shepard.context.semantic.entities.SemanticConfig;
import de.dlr.shepard.context.semantic.entities.UserOntologyBundle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link OntologyConfigService}. Two surfaces:
 *
 * <ul>
 *   <li>Runtime knobs: first-start seeding, enable/disable enforcement,
 *       precedence-rules helpers, the merged-view projection.</li>
 *   <li>Upload/remove: SHA-256, on-disk write under a {@code @TempDir}
 *       (the {@code shepard.semantic.internal.user-bundles-dir}
 *       property is forced for the duration of each test), id
 *       collision refusal, oversize refusal, malformed-TTL refusal,
 *       built-in delete refusal.</li>
 * </ul>
 *
 * <p>Coverage target per CLAUDE.md (≥ 85% line / ≥ 70% branch on this
 * service; ≥ 70% line on new code overall).
 */
class OntologyConfigServiceTest {

  @TempDir
  Path tempDir;

  private SemanticConfigDAO configDAO;
  private UserOntologyBundleDAO userBundleDAO;
  private OntologyConfigService service;

  private String previousUserDirProp;

  @BeforeEach
  void setUp() {
    configDAO = mock(SemanticConfigDAO.class);
    userBundleDAO = mock(UserOntologyBundleDAO.class);
    when(userBundleDAO.listAll()).thenReturn(List.of());
    service = new OntologyConfigService(configDAO, userBundleDAO);
    previousUserDirProp = System.getProperty("shepard.semantic.internal.user-bundles-dir");
    System.setProperty("shepard.semantic.internal.user-bundles-dir", tempDir.toString());
  }

  @AfterEach
  void tearDown() {
    if (previousUserDirProp == null) {
      System.clearProperty("shepard.semantic.internal.user-bundles-dir");
    } else {
      System.setProperty("shepard.semantic.internal.user-bundles-dir", previousUserDirProp);
    }
  }

  // ---------- helpers --------------------------------------------------------

  private static OntologyEntry builtin(String id, boolean required) {
    return new OntologyEntry(
      id,
      id,
      id + ".ttl",
      "http://example.org/" + id + "/",
      "Turtle",
      "http://example.org/" + id + ".ttl",
      "CC0 1.0",
      "1",
      "0".repeat(64),
      512L,
      required,
      OntologyEntry.Source.BUILTIN
    );
  }

  private static SemanticConfig fresh() {
    SemanticConfig c = new SemanticConfig();
    c.setPreseedEnabled(true);
    c.setDisabledBundles(new ArrayList<>());
    return c;
  }

  // ---------- loadSingleton --------------------------------------------------

  @Test
  void loadSingleton_firstStart_seedsFromDeployTimeDefaults() {
    when(configDAO.findFirst()).thenReturn(null);
    when(configDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    SemanticConfig out = service.loadSingleton();

    assertNotNull(out);
    assertTrue(out.isPreseedEnabled(), "default deploy-time preseed-enabled=true");
    verify(configDAO).createOrUpdate(any());
  }

  @Test
  void loadSingleton_idempotent_returnsExistingRow() {
    SemanticConfig existing = fresh();
    existing.setPreseedEnabled(false);
    existing.setDisabledBundles(List.of("qudt"));
    when(configDAO.findFirst()).thenReturn(existing);

    SemanticConfig out = service.loadSingleton();

    assertFalse(out.isPreseedEnabled());
    assertEquals(List.of("qudt"), out.getDisabledBundles());
    verify(configDAO, never()).createOrUpdate(any());
  }

  @Test
  void loadRuntimeConfig_projectsImmutableValue() {
    SemanticConfig row = fresh();
    row.setPreseedEnabled(true);
    row.setDisabledBundles(List.of("foaf", "qudt"));
    when(configDAO.findFirst()).thenReturn(row);

    RuntimeConfig rc = service.loadRuntimeConfig();

    assertTrue(rc.preseedEnabled());
    assertEquals(2, rc.disabledBundles().size());
    assertTrue(rc.disabledBundles().contains("foaf"));
    assertTrue(rc.disabledBundles().contains("qudt"));
  }

  // ---------- setBundleEnabled ----------------------------------------------

  @Test
  void disable_required_returnsREQUIRED_CANNOT_DISABLE() {
    when(configDAO.findFirst()).thenReturn(fresh());
    when(userBundleDAO.findByBundleId("prov-o")).thenReturn(null);

    var result = service.setBundleEnabled("prov-o", false, "alice", List.of(builtin("prov-o", true)));

    assertEquals(OntologyConfigService.SetEnabledResult.REQUIRED_CANNOT_DISABLE, result);
    verify(configDAO, never()).createOrUpdate(any());
  }

  @Test
  void disable_optional_addsIdToDisabledBundles() {
    SemanticConfig row = fresh();
    when(configDAO.findFirst()).thenReturn(row);
    when(configDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));
    when(userBundleDAO.findByBundleId("qudt")).thenReturn(null);

    var result = service.setBundleEnabled("qudt", false, "alice", List.of(builtin("qudt", false)));

    assertEquals(OntologyConfigService.SetEnabledResult.OK, result);
    assertTrue(row.getDisabledBundles().contains("qudt"));
    assertEquals("alice", row.getUpdatedBy());
    verify(configDAO).createOrUpdate(row);
  }

  @Test
  void enable_removesIdFromDisabledBundles() {
    SemanticConfig row = fresh();
    row.setDisabledBundles(new ArrayList<>(List.of("qudt")));
    when(configDAO.findFirst()).thenReturn(row);
    when(configDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.setBundleEnabled("qudt", true, "bob", List.of(builtin("qudt", false)));

    assertEquals(OntologyConfigService.SetEnabledResult.OK, result);
    assertFalse(row.getDisabledBundles().contains("qudt"));
  }

  @Test
  void enable_alreadyEnabled_isNoOp() {
    SemanticConfig row = fresh();
    when(configDAO.findFirst()).thenReturn(row);

    var result = service.setBundleEnabled("qudt", true, "bob", List.of(builtin("qudt", false)));

    assertEquals(OntologyConfigService.SetEnabledResult.OK, result);
    verify(configDAO, never()).createOrUpdate(any());
  }

  @Test
  void disable_unknownId_returnsNOT_FOUND() {
    when(userBundleDAO.findByBundleId("ghost")).thenReturn(null);

    var result = service.setBundleEnabled("ghost", false, "alice", List.of(builtin("prov-o", true)));

    assertEquals(OntologyConfigService.SetEnabledResult.NOT_FOUND, result);
  }

  @Test
  void disable_blankId_returnsNOT_FOUND() {
    assertEquals(
      OntologyConfigService.SetEnabledResult.NOT_FOUND,
      service.setBundleEnabled("", false, "a", List.of())
    );
    assertEquals(
      OntologyConfigService.SetEnabledResult.NOT_FOUND,
      service.setBundleEnabled(null, false, "a", List.of())
    );
  }

  // ---------- uploadBundle ---------------------------------------------------

  private static final String VALID_TTL =
    "@prefix ex: <http://example.org/> .\nex:a a ex:Thing .\n";

  private OntologyConfigService.UploadMetadata uploadMeta(String id) {
    return new OntologyConfigService.UploadMetadata(id, "Sample", "http://example.org/" + id + "/", null, "CC0 1.0");
  }

  @Test
  void upload_happyPath_writesBytesAndPersistsRow() {
    when(userBundleDAO.findByBundleId("custom")).thenReturn(null);
    when(userBundleDAO.createOrUpdate(any())).thenAnswer(inv -> inv.getArgument(0));

    var result = service.uploadBundle(VALID_TTL.getBytes(StandardCharsets.UTF_8), uploadMeta("custom"), "alice", List.of());

    assertEquals(OntologyConfigService.UploadResult.Status.CREATED, result.status);
    assertNotNull(result.saved);
    assertEquals("custom", result.saved.getBundleId());
    assertNotNull(result.saved.getSha256());
    assertEquals(VALID_TTL.length(), result.saved.getByteSize());
    assertTrue(Files.exists(tempDir.resolve("custom.ttl")));
  }

  @Test
  void upload_idCollidesWithBuiltin_isDUPLICATE_ID() {
    var result = service.uploadBundle(
      VALID_TTL.getBytes(StandardCharsets.UTF_8),
      uploadMeta("prov-o"),
      "alice",
      List.of(builtin("prov-o", true))
    );

    assertEquals(OntologyConfigService.UploadResult.Status.DUPLICATE_ID, result.status);
    assertTrue(result.reason.contains("built-in"));
  }

  @Test
  void upload_idCollidesWithUser_isDUPLICATE_ID() {
    UserOntologyBundle existing = new UserOntologyBundle();
    existing.setBundleId("custom");
    when(userBundleDAO.findByBundleId("custom")).thenReturn(existing);

    var result = service.uploadBundle(VALID_TTL.getBytes(StandardCharsets.UTF_8), uploadMeta("custom"), "alice", List.of());

    assertEquals(OntologyConfigService.UploadResult.Status.DUPLICATE_ID, result.status);
  }

  @Test
  void upload_invalidId_isBAD_METADATA() {
    var result = service.uploadBundle(
      VALID_TTL.getBytes(StandardCharsets.UTF_8),
      new OntologyConfigService.UploadMetadata("UPPER_CASE", "x", "http://x/", null, "CC0"),
      "alice",
      List.of()
    );

    assertEquals(OntologyConfigService.UploadResult.Status.BAD_METADATA, result.status);
  }

  @Test
  void upload_missingIriPrefix_isBAD_METADATA() {
    var result = service.uploadBundle(
      VALID_TTL.getBytes(StandardCharsets.UTF_8),
      new OntologyConfigService.UploadMetadata("custom", "x", null, null, "CC0"),
      "alice",
      List.of()
    );

    assertEquals(OntologyConfigService.UploadResult.Status.BAD_METADATA, result.status);
  }

  @Test
  void upload_missingLicense_isBAD_METADATA() {
    var result = service.uploadBundle(
      VALID_TTL.getBytes(StandardCharsets.UTF_8),
      new OntologyConfigService.UploadMetadata("custom", "x", "http://x/", null, ""),
      "alice",
      List.of()
    );

    assertEquals(OntologyConfigService.UploadResult.Status.BAD_METADATA, result.status);
  }

  @Test
  void upload_emptyPayload_isINVALID_TTL() {
    var result = service.uploadBundle(new byte[0], uploadMeta("custom"), "alice", List.of());

    assertEquals(OntologyConfigService.UploadResult.Status.INVALID_TTL, result.status);
  }

  @Test
  void upload_garbagePayload_isINVALID_TTL() {
    var result = service.uploadBundle(
      "{\"not\":\"turtle\"}".getBytes(StandardCharsets.UTF_8),
      uploadMeta("custom"),
      "alice",
      List.of()
    );

    assertEquals(OntologyConfigService.UploadResult.Status.INVALID_TTL, result.status);
  }

  @Test
  void upload_oversize_isTOO_LARGE() {
    byte[] huge = new byte[(int) (OntologyConfigService.MAX_UPLOAD_BYTES + 1)];
    // Fill enough bytes with @prefix so the turtle-shape heuristic accepts it,
    // ensuring the test asserts the size check rather than the shape check.
    byte[] head = "@prefix ex: <http://example.org/> .\n".getBytes(StandardCharsets.UTF_8);
    System.arraycopy(head, 0, huge, 0, head.length);

    var result = service.uploadBundle(huge, uploadMeta("custom"), "alice", List.of());

    assertEquals(OntologyConfigService.UploadResult.Status.TOO_LARGE, result.status);
  }

  // ---------- removeBundle ---------------------------------------------------

  @Test
  void remove_builtin_isBUILTIN_NOT_REMOVABLE() {
    var result = service.removeBundle("prov-o", "alice", List.of(builtin("prov-o", true)));

    assertEquals(OntologyConfigService.RemoveResult.BUILTIN_NOT_REMOVABLE, result);
    verify(userBundleDAO, never()).deleteByNeo4jId(any(Long.class).longValue());
  }

  @Test
  void remove_unknownUser_isNOT_FOUND() {
    when(userBundleDAO.findByBundleId("ghost")).thenReturn(null);

    var result = service.removeBundle("ghost", "alice", List.of());

    assertEquals(OntologyConfigService.RemoveResult.NOT_FOUND, result);
  }

  @Test
  void remove_user_dropsRowAndFile() throws Exception {
    Path file = tempDir.resolve("custom.ttl");
    Files.writeString(file, VALID_TTL);
    UserOntologyBundle existing = new UserOntologyBundle();
    existing.setId(42L);
    existing.setBundleId("custom");
    when(userBundleDAO.findByBundleId("custom")).thenReturn(existing);
    when(userBundleDAO.deleteByNeo4jId(42L)).thenReturn(true);

    var result = service.removeBundle("custom", "alice", List.of());

    assertEquals(OntologyConfigService.RemoveResult.REMOVED, result);
    assertFalse(Files.exists(file));
    verify(userBundleDAO, times(1)).deleteByNeo4jId(42L);
  }

  @Test
  void remove_blankId_isNOT_FOUND() {
    assertEquals(
      OntologyConfigService.RemoveResult.NOT_FOUND,
      service.removeBundle("", "alice", List.of())
    );
    assertEquals(
      OntologyConfigService.RemoveResult.NOT_FOUND,
      service.removeBundle(null, "alice", List.of())
    );
  }

  // ---------- merged listing -------------------------------------------------

  @Test
  void listMerged_marksRequiredAsAlwaysEnabled_evenIfDisabled() {
    SemanticConfig row = fresh();
    row.setDisabledBundles(new ArrayList<>(List.of("prov-o", "qudt")));
    when(configDAO.findFirst()).thenReturn(row);

    List<OntologyEntry> manifest = List.of(builtin("prov-o", true), builtin("qudt", false));
    var rows = service.listMerged(manifest);

    assertEquals(2, rows.size());
    var first = rows.get(0);
    assertEquals("prov-o", first.id);
    assertTrue(first.enabled, "required wins even when listed in disabledBundles");

    var second = rows.get(1);
    assertEquals("qudt", second.id);
    assertFalse(second.enabled, "non-required + in disabledBundles → disabled");
  }

  @Test
  void listMerged_includesUserBundles_underUserSource() {
    SemanticConfig row = fresh();
    when(configDAO.findFirst()).thenReturn(row);
    UserOntologyBundle uploaded = new UserOntologyBundle();
    uploaded.setBundleId("custom");
    uploaded.setIriPrefix("http://example.org/custom/");
    uploaded.setLicense("CC0 1.0");
    uploaded.setSha256("abc");
    uploaded.setByteSize(123L);
    uploaded.setFormat("Turtle");
    when(userBundleDAO.listAll()).thenReturn(List.of(uploaded));

    var rows = service.listMerged(List.of(builtin("prov-o", true)));

    assertEquals(2, rows.size());
    assertEquals("builtin", rows.get(0).source);
    assertEquals("user", rows.get(1).source);
    assertEquals("custom", rows.get(1).id);
    assertFalse(rows.get(1).required, "user uploads are never required");
  }

  @Test
  void findBundle_unknown_isEmpty() {
    when(configDAO.findFirst()).thenReturn(fresh());

    Optional<OntologyConfigService.BundleView> v = service.findBundle("ghost", List.of(builtin("prov-o", true)));

    assertTrue(v.isEmpty());
  }

  @Test
  void findBundle_blank_isEmpty() {
    assertTrue(service.findBundle(null, List.of()).isEmpty());
    assertTrue(service.findBundle("", List.of()).isEmpty());
  }

  @Test
  void findBundle_returnsMatchingRow() {
    when(configDAO.findFirst()).thenReturn(fresh());
    Optional<OntologyConfigService.BundleView> v = service.findBundle("prov-o", List.of(builtin("prov-o", true)));
    assertTrue(v.isPresent());
    assertEquals("prov-o", v.get().id);
  }

  // ---------- listUserEntries ------------------------------------------------

  @Test
  void listUserEntries_mapsToOntologyEntry_withUserSource() {
    UserOntologyBundle row = new UserOntologyBundle();
    row.setBundleId("custom");
    row.setIriPrefix("http://example.org/custom/");
    row.setLicense("CC0 1.0");
    row.setSha256("deadbeef");
    row.setByteSize(99L);
    row.setFormat("Turtle");
    when(userBundleDAO.listAll()).thenReturn(List.of(row));

    var entries = service.listUserEntries();

    assertEquals(1, entries.size());
    assertEquals("custom", entries.get(0).id);
    assertEquals(OntologyEntry.Source.USER, entries.get(0).source);
    assertFalse(entries.get(0).required);
  }

  // ---------- Turtle heuristic ---------------------------------------------

  @Test
  void looksLikeTurtle_acceptsPrefixDirective() {
    assertTrue(OntologyConfigService.looksLikeTurtle("@prefix x: <http://x/> .\n"));
  }

  @Test
  void looksLikeTurtle_acceptsBaseDirective() {
    assertTrue(OntologyConfigService.looksLikeTurtle("@base <http://x/> .\n"));
  }

  @Test
  void looksLikeTurtle_acceptsAbsoluteIRIPlusDot() {
    assertTrue(OntologyConfigService.looksLikeTurtle("<http://x/a> <http://x/p> <http://x/b> .\n"));
  }

  @Test
  void looksLikeTurtle_rejectsBlank() {
    assertFalse(OntologyConfigService.looksLikeTurtle(""));
    assertFalse(OntologyConfigService.looksLikeTurtle(null));
  }

  @Test
  void looksLikeTurtle_rejectsJson() {
    assertFalse(OntologyConfigService.looksLikeTurtle("{\"id\":\"x\"}"));
  }
}
