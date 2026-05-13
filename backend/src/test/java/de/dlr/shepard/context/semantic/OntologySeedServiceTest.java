package de.dlr.shepard.context.semantic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * Unit tests for {@link OntologySeedService}. Two layers:
 *
 * <ol>
 *   <li><b>Mocked-classloader tests</b> — synthesise an in-memory
 *       manifest + bundle bytes via a custom {@link ClassLoader},
 *       exercising every per-bundle branch (happy / SHA mismatch /
 *       missing file / skipped / failed n10s call). The Cypher
 *       composition is verified via {@code verify(session).query(...)}.</li>
 *   <li><b>Real-classpath sanity test</b> — load the actual
 *       {@code /ontologies/ontologies-manifest.json} shipped with the
 *       backend; assert every declared file exists on the classpath
 *       and every declared SHA-256 matches the file on disk.</li>
 * </ol>
 *
 * <p>We deliberately do NOT test Turtle parsing — that's n10s's job.
 */
class OntologySeedServiceTest {

  // ---------- Mocked-classloader tests --------------------------------------

  private static final String SAMPLE_TTL =
    "@prefix ex: <http://example.org/> .\nex:a a ex:Thing .\n";

  /** SHA-256 of {@link #SAMPLE_TTL} bytes (UTF-8). */
  private static final String SAMPLE_SHA = OntologySeedService.sha256Hex(
    SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
  );

  @Test
  void seed_disabledByConfig_isNoOp() {
    Session session = mock(Session.class);
    // Empty manifest + master-off → early return, no Cypher at all.
    // (Pre-N1c2 contract preserved: deploy-time enabled=false is the
    // "bare n10s" exit when there's nothing required to seed.)
    var svc = new OntologySeedService(session, false, Set.of(), new ObjectMapper(), classLoaderWith(Map.of()));
    svc.seedIfNeeded();
    verify(session, never()).query(any(String.class), any());
  }

  @Test
  void seed_disabledByConfig_butRequiredBundlePresent_stillSeedsRequired() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result count = singleRow(Map.of("total", 0L));
    Result imp = singleRow(Map.of("status", "OK", "loaded", 5L, "parsed", 5L, "info", ""));
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(OntologySeedService.RESOURCE_COUNT_CYPHER), any())).thenReturn(count);
    when(session.query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any())).thenReturn(imp);

    String manifest = manifestJson(List.of(entryRequired("must", "must.ttl", SAMPLE_SHA, SAMPLE_TTL.length(), true)));
    ClassLoader cl = classLoaderWith(
      Map.of(
        "ontologies/ontologies-manifest.json",
        manifest.getBytes(StandardCharsets.UTF_8),
        "ontologies/must.ttl",
        SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      )
    );

    var svc = new OntologySeedService(session, false, Set.of(), new ObjectMapper(), cl);
    svc.seedIfNeeded();

    // Required bundles bypass the master-off check.
    verify(session).query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any());
  }

  @Test
  void seed_skipsWhenN10sAbsent() {
    Session session = mock(Session.class);
    Result detect = singleRow(Map.of("available", Boolean.FALSE));
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any())).thenReturn(detect);

    // N1c2 reorder: the seed service loads the manifest before
    // probing n10s (so a master-off + no-required-bundles case can
    // skip the detect probe). A single-entry manifest gets us past
    // the emptiness short-circuit so detect runs.
    ClassLoader cl = classLoaderWith(
      Map.of(
        "ontologies/ontologies-manifest.json",
        manifestJson(List.of(entry("ex", "ex.ttl", SAMPLE_SHA, SAMPLE_TTL.length()))).getBytes(StandardCharsets.UTF_8),
        "ontologies/ex.ttl",
        SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      )
    );
    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    svc.seedIfNeeded();

    verify(session).query(eq(OntologySeedService.DETECT_CYPHER), any());
    verify(session, never()).query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any());
  }

  @Test
  void seed_handlesNullSession() {
    var svc = new OntologySeedService(null, true, Set.of(), new ObjectMapper(), classLoaderWith(Map.of()));
    assertDoesNotThrow(svc::seedIfNeeded);
  }

  @Test
  void seed_importsValidBundle_andSendsCorrectCypher() {
    Session session = mock(Session.class);
    String manifest = manifestJson(List.of(entry("one", "one.ttl", SAMPLE_SHA, SAMPLE_TTL.length())));
    ClassLoader cl = classLoaderWith(
      Map.of(
        "ontologies/ontologies-manifest.json",
        manifest.getBytes(StandardCharsets.UTF_8),
        "ontologies/one.ttl",
        SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      )
    );
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result count = singleRow(Map.of("total", 0L));
    Result imp = singleRow(Map.of("status", "OK", "loaded", 7L, "parsed", 7L, "info", "fresh"));
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(OntologySeedService.RESOURCE_COUNT_CYPHER), any())).thenReturn(count);
    when(session.query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any())).thenReturn(imp);

    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    svc.seedIfNeeded();

    verify(session).query(
      eq(OntologySeedService.IMPORT_INLINE_CYPHER),
      eq(Map.of("rdf", SAMPLE_TTL, "format", "Turtle"))
    );
  }

  @Test
  void seed_rerun_isIdempotent_n10sReportsZeroLoaded() {
    Session session = mock(Session.class);
    String manifest = manifestJson(List.of(entry("one", "one.ttl", SAMPLE_SHA, SAMPLE_TTL.length())));
    ClassLoader cl = classLoaderWith(
      Map.of(
        "ontologies/ontologies-manifest.json",
        manifest.getBytes(StandardCharsets.UTF_8),
        "ontologies/one.ttl",
        SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      )
    );
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result count = singleRow(Map.of("total", 42L));
    // n10s constraint-deduped re-run: status=OK + loaded=0 is the
    // idempotent contract — service must treat it as success, not a warn.
    Result reRun = singleRow(Map.of("status", "OK", "loaded", 0L, "parsed", 7L, "info", "duplicate"));
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(OntologySeedService.RESOURCE_COUNT_CYPHER), any())).thenReturn(count);
    when(session.query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any())).thenReturn(reRun);

    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    assertDoesNotThrow(svc::seedIfNeeded);

    verify(session).query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any());
  }

  @Test
  void seed_shaMismatch_skipsThatBundle_continuesWithNext() {
    Session session = mock(Session.class);
    String badSha = "0".repeat(64);
    String manifest = manifestJson(
      List.of(
        entry("bad", "bad.ttl", badSha, SAMPLE_TTL.length()),
        entry("good", "good.ttl", SAMPLE_SHA, SAMPLE_TTL.length())
      )
    );
    ClassLoader cl = classLoaderWith(
      Map.of(
        "ontologies/ontologies-manifest.json",
        manifest.getBytes(StandardCharsets.UTF_8),
        "ontologies/bad.ttl",
        SAMPLE_TTL.getBytes(StandardCharsets.UTF_8),
        "ontologies/good.ttl",
        SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      )
    );
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result count = singleRow(Map.of("total", 0L));
    Result imp = singleRow(Map.of("status", "OK", "loaded", 5L));
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(OntologySeedService.RESOURCE_COUNT_CYPHER), any())).thenReturn(count);
    when(session.query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any())).thenReturn(imp);

    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    assertDoesNotThrow(svc::seedIfNeeded);

    // The bad-SHA bundle short-circuits BEFORE invokeImport; the good one fires.
    verify(session).query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any());
  }

  @Test
  void seed_missingBundleFile_isPerBundleFailure_neverThrows() {
    Session session = mock(Session.class);
    String manifest = manifestJson(List.of(entry("missing", "missing.ttl", SAMPLE_SHA, SAMPLE_TTL.length())));
    ClassLoader cl = classLoaderWith(
      Map.of(
        "ontologies/ontologies-manifest.json",
        manifest.getBytes(StandardCharsets.UTF_8)
        // missing.ttl is NOT in the map
      )
    );
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result count = singleRow(Map.of("total", 0L));
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(OntologySeedService.RESOURCE_COUNT_CYPHER), any())).thenReturn(count);

    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    assertDoesNotThrow(svc::seedIfNeeded);

    verify(session, never()).query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any());
  }

  @Test
  void seed_skipBundles_setHonoured() {
    Session session = mock(Session.class);
    String manifest = manifestJson(
      List.of(
        entry("a", "a.ttl", SAMPLE_SHA, SAMPLE_TTL.length()),
        entry("b", "b.ttl", SAMPLE_SHA, SAMPLE_TTL.length())
      )
    );
    ClassLoader cl = classLoaderWith(
      Map.of(
        "ontologies/ontologies-manifest.json",
        manifest.getBytes(StandardCharsets.UTF_8),
        "ontologies/a.ttl",
        SAMPLE_TTL.getBytes(StandardCharsets.UTF_8),
        "ontologies/b.ttl",
        SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      )
    );
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result count = singleRow(Map.of("total", 0L));
    Result imp = singleRow(Map.of("status", "OK", "loaded", 3L));
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(OntologySeedService.RESOURCE_COUNT_CYPHER), any())).thenReturn(count);
    when(session.query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any())).thenReturn(imp);

    var svc = new OntologySeedService(session, true, Set.of("a"), new ObjectMapper(), cl);
    svc.seedIfNeeded();

    // Only one import call (for "b"); skip was applied to "a".
    verify(session, org.mockito.Mockito.times(1)).query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any());
  }

  @Test
  void seed_missingManifest_skipsEntirelyAndDoesNotThrow() {
    Session session = mock(Session.class);
    ClassLoader cl = classLoaderWith(Map.of());
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any())).thenReturn(detect);

    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    assertDoesNotThrow(svc::seedIfNeeded);

    verify(session, never()).query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any());
  }

  @Test
  void seed_emptyManifest_skips() {
    Session session = mock(Session.class);
    String manifest = "{\"version\":1,\"ontologies\":[]}";
    ClassLoader cl = classLoaderWith(
      Map.of("ontologies/ontologies-manifest.json", manifest.getBytes(StandardCharsets.UTF_8))
    );
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any())).thenReturn(detect);

    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    svc.seedIfNeeded();

    verify(session, never()).query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any());
  }

  @Test
  void seed_nonOkStatusFromN10s_isWarnedNotRaised() {
    Session session = mock(Session.class);
    String manifest = manifestJson(List.of(entry("one", "one.ttl", SAMPLE_SHA, SAMPLE_TTL.length())));
    ClassLoader cl = classLoaderWith(
      Map.of(
        "ontologies/ontologies-manifest.json",
        manifest.getBytes(StandardCharsets.UTF_8),
        "ontologies/one.ttl",
        SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      )
    );
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result count = singleRow(Map.of("total", 0L));
    Result imp = singleRow(Map.of("status", "KO", "loaded", 0L, "info", "syntax error"));
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(OntologySeedService.RESOURCE_COUNT_CYPHER), any())).thenReturn(count);
    when(session.query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any())).thenReturn(imp);

    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    assertDoesNotThrow(svc::seedIfNeeded);
  }

  @Test
  void seed_importThrows_isPerBundleFailureNotFatal() {
    Session session = mock(Session.class);
    String manifest = manifestJson(
      List.of(
        entry("explode", "explode.ttl", SAMPLE_SHA, SAMPLE_TTL.length()),
        entry("ok", "ok.ttl", SAMPLE_SHA, SAMPLE_TTL.length())
      )
    );
    ClassLoader cl = classLoaderWith(
      Map.of(
        "ontologies/ontologies-manifest.json",
        manifest.getBytes(StandardCharsets.UTF_8),
        "ontologies/explode.ttl",
        SAMPLE_TTL.getBytes(StandardCharsets.UTF_8),
        "ontologies/ok.ttl",
        SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      )
    );
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result count = singleRow(Map.of("total", 0L));
    Result okRow = singleRow(Map.of("status", "OK", "loaded", 1L));
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(OntologySeedService.RESOURCE_COUNT_CYPHER), any())).thenReturn(count);
    // First (explode.ttl) raises, then OK for the next bundle.
    when(session.query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any()))
      .thenThrow(new RuntimeException("connection reset"))
      .thenReturn(okRow);

    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    assertDoesNotThrow(svc::seedIfNeeded);

    verify(session, org.mockito.Mockito.times(2)).query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any());
  }

  @Test
  void parseSkipBundles_acceptsCsv() {
    Set<String> set = OntologySeedService.parseSkipBundles("qudt, om-2, ,foaf");
    assertEquals(Set.of("qudt", "om-2", "foaf"), set);
  }

  @Test
  void parseSkipBundles_emptyOrNullYieldsEmpty() {
    assertTrue(OntologySeedService.parseSkipBundles(null).isEmpty());
    assertTrue(OntologySeedService.parseSkipBundles("").isEmpty());
    assertTrue(OntologySeedService.parseSkipBundles("   ").isEmpty());
  }

  @Test
  void manifestEntry_rejectsInvalidSha() {
    Session session = mock(Session.class);
    String manifest = manifestJson(List.of(entry("bad", "x.ttl", "not-hex", 0)));
    ClassLoader cl = classLoaderWith(
      Map.of("ontologies/ontologies-manifest.json", manifest.getBytes(StandardCharsets.UTF_8))
    );
    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    assertThrows(RuntimeException.class, svc::loadManifest);
  }

  @Test
  void manifestEntry_rejectsDuplicateId() {
    Session session = mock(Session.class);
    String manifest = manifestJson(
      List.of(
        entry("same", "a.ttl", SAMPLE_SHA, 10),
        entry("same", "b.ttl", SAMPLE_SHA, 10)
      )
    );
    ClassLoader cl = classLoaderWith(
      Map.of("ontologies/ontologies-manifest.json", manifest.getBytes(StandardCharsets.UTF_8))
    );
    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    assertThrows(RuntimeException.class, svc::loadManifest);
  }

  @Test
  void manifestEntry_rejectsMissingRequiredFields() {
    Session session = mock(Session.class);
    String manifest = "{\"ontologies\":[{\"id\":\"only\"}]}";
    ClassLoader cl = classLoaderWith(
      Map.of("ontologies/ontologies-manifest.json", manifest.getBytes(StandardCharsets.UTF_8))
    );
    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    assertThrows(RuntimeException.class, svc::loadManifest);
  }

  @Test
  void seed_resourceCountFailure_isSwallowed() {
    Session session = mock(Session.class);
    String manifest = manifestJson(List.of(entry("one", "one.ttl", SAMPLE_SHA, SAMPLE_TTL.length())));
    ClassLoader cl = classLoaderWith(
      Map.of(
        "ontologies/ontologies-manifest.json",
        manifest.getBytes(StandardCharsets.UTF_8),
        "ontologies/one.ttl",
        SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      )
    );
    Result detect = singleRow(Map.of("available", Boolean.TRUE));
    Result imp = singleRow(Map.of("status", "OK", "loaded", 5L));
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any())).thenReturn(detect);
    when(session.query(eq(OntologySeedService.RESOURCE_COUNT_CYPHER), any()))
      .thenThrow(new RuntimeException("read denied"));
    when(session.query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any())).thenReturn(imp);

    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), cl);
    assertDoesNotThrow(svc::seedIfNeeded);
  }

  @Test
  void sha256Hex_isLowercaseHex_64chars() {
    String s = OntologySeedService.sha256Hex("hello".getBytes(StandardCharsets.UTF_8));
    assertEquals(64, s.length());
    assertEquals(s.toLowerCase(java.util.Locale.ROOT), s);
    assertTrue(s.chars().allMatch(c -> Character.digit(c, 16) >= 0));
  }

  @Test
  void detectProbeException_treatedAsAbsent() {
    Session session = mock(Session.class);
    when(session.query(eq(OntologySeedService.DETECT_CYPHER), any()))
      .thenThrow(new RuntimeException("permission denied"));

    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), classLoaderWith(Map.of()));
    assertDoesNotThrow(svc::seedIfNeeded);
    verify(session, never()).query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any());
  }

  // ---------- Real-classpath sanity test ------------------------------------

  /**
   * Load the actual {@code /ontologies/ontologies-manifest.json}
   * shipped with the backend. For every entry:
   * <ul>
   *   <li>The bundled file exists on the classpath.</li>
   *   <li>The bundled file's SHA-256 matches the manifest.</li>
   *   <li>The manifest's SHA-256 field is 64 hex chars.</li>
   * </ul>
   */
  @Test
  void realManifest_everyEntryIsConsistentWithBundledFile() {
    Session session = mock(Session.class);
    // Use the real classloader → reads from src/main/resources.
    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), getClass().getClassLoader());
    List<OntologySeedService.OntologyEntry> entries = svc.loadManifest();

    assertFalse(entries.isEmpty(), "real manifest must declare at least one ontology");
    for (OntologySeedService.OntologyEntry e : entries) {
      // 64 hex chars (defensive — fromJson already enforces this).
      assertEquals(64, e.sha256.length(), "sha256 length for " + e.id);
      // File exists on classpath.
      byte[] bytes = assertDoesNotThrow(() -> svc.readBundleBytes(e.file), "bundle " + e.file + " missing");
      // SHA matches.
      String actual = OntologySeedService.sha256Hex(bytes);
      assertEquals(e.sha256.toLowerCase(java.util.Locale.ROOT), actual.toLowerCase(java.util.Locale.ROOT),
        "SHA-256 mismatch for " + e.id + " (file=" + e.file + ")");
    }
  }

  @Test
  void realManifest_declaresEveryRequiredOntology() {
    Session session = mock(Session.class);
    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), getClass().getClassLoader());
    List<String> ids = svc.loadManifest().stream().map(e -> e.id).toList();
    // Per aidocs/48 §3.2 + ONT1a (aidocs/58 §6) + ONT1b the bundle ships exactly these 10 ontologies.
    assertEquals(10, ids.size(), "expected 10 bundled ontologies (8 from N1b + RO from ONT1a + metadata4ing from ONT1b)");
    assertTrue(ids.contains("prov-o"), "manifest missing prov-o");
    assertTrue(ids.contains("dublin-core"), "manifest missing dublin-core");
    assertTrue(ids.contains("schema-org"), "manifest missing schema-org");
    assertTrue(ids.contains("foaf"), "manifest missing foaf");
    assertTrue(ids.contains("qudt"), "manifest missing qudt");
    assertTrue(ids.contains("om-2"), "manifest missing om-2");
    assertTrue(ids.contains("time"), "manifest missing time");
    assertTrue(ids.contains("geosparql"), "manifest missing geosparql");
    assertTrue(ids.contains("obo-relations"), "manifest missing obo-relations (ONT1a)");
    assertTrue(ids.contains("metadata4ing"), "manifest missing metadata4ing (ONT1b)");
  }

  /**
   * ONT1a — the RO bundle has its own row + own IRI prefix
   * ({@code http://purl.obolibrary.org/obo/RO_}) and the {@code
   * skip-bundles=obo-relations} toggle removes it cleanly from the
   * seed pass without dragging any of the eight original N1b
   * bundles with it.
   */
  @Test
  void realManifest_roBundleCarriesObolibraryIriPrefixAndCc0Licence() {
    Session session = mock(Session.class);
    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), getClass().getClassLoader());
    var ro = svc
      .loadManifest()
      .stream()
      .filter(e -> "obo-relations".equals(e.id))
      .findFirst()
      .orElseThrow(() -> new AssertionError("RO bundle missing from manifest"));
    assertEquals("http://purl.obolibrary.org/obo/RO_", ro.iriPrefix, "RO IRI prefix");
    assertEquals("CC0 1.0", ro.license, "RO licence string");
    assertEquals("obo-relations.ttl", ro.file, "RO bundle file name");
    assertEquals("Turtle", ro.format, "RO bundled format");
  }

  @Test
  void realManifest_skipBundlesObolibrary_excludesRoButRetainsOthers() {
    Session session = mock(Session.class);
    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), getClass().getClassLoader());
    Set<String> skip = OntologySeedService.parseSkipBundles("obo-relations");
    List<String> kept = svc
      .loadManifest()
      .stream()
      .map(e -> e.id)
      .filter(id -> !skip.contains(id))
      .toList();
    assertEquals(9, kept.size(), "skip-bundles=obo-relations should leave 9 entries");
    assertFalse(kept.contains("obo-relations"), "obo-relations should be excluded by skip-bundles");
    assertTrue(kept.contains("prov-o"), "skip-bundles=obo-relations must not affect prov-o");
    assertTrue(kept.contains("geosparql"), "skip-bundles=obo-relations must not affect geosparql");
    assertTrue(kept.contains("metadata4ing"), "skip-bundles=obo-relations must not affect metadata4ing");
  }

  // ONT1b — metadata4ing (NFDI4Ing) bundle.

  /**
   * ONT1b — manifest declares the metadata4ing bundle as the tenth
   * entry alongside the eight original N1b bundles and the ONT1a RO
   * bundle. Cardinality check is what catches an accidental drop /
   * dup of the ONT1b entry in a future refactor.
   */
  @Test
  void realManifest_metadata4ingIsTenthBundle() {
    Session session = mock(Session.class);
    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), getClass().getClassLoader());
    List<String> ids = svc.loadManifest().stream().map(e -> e.id).toList();
    assertEquals(10, ids.size(), "ONT1b lifts the bundle count to 10 (8 N1b + RO + metadata4ing)");
    assertTrue(ids.contains("metadata4ing"), "manifest missing metadata4ing (ONT1b)");
  }

  /**
   * ONT1b — the metadata4ing bundle carries the canonical NFDI4Ing
   * IRI prefix ({@code http://w3id.org/nfdi4ing/metadata4ing/}), is
   * licensed CC BY 4.0, ships as {@code metadata4ing.ttl} in Turtle
   * format, and pins the canonical w3id.org/1.4.0/ URL. These four
   * fields are what an admin or a downstream tool keys off — any of
   * them silently drifting would be a wire-break.
   */
  @Test
  void realManifest_metadata4ingCarriesNfdi4ingIriPrefixAndCcBy4Licence() {
    Session session = mock(Session.class);
    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), getClass().getClassLoader());
    var m4i = svc
      .loadManifest()
      .stream()
      .filter(e -> "metadata4ing".equals(e.id))
      .findFirst()
      .orElseThrow(() -> new AssertionError("metadata4ing bundle missing from manifest"));
    assertEquals("http://w3id.org/nfdi4ing/metadata4ing/", m4i.iriPrefix, "metadata4ing IRI prefix");
    assertEquals("CC BY 4.0", m4i.license, "metadata4ing licence string");
    assertEquals("metadata4ing.ttl", m4i.file, "metadata4ing bundle file name");
    assertEquals("Turtle", m4i.format, "metadata4ing bundled format");
    assertEquals(
      "https://w3id.org/nfdi4ing/metadata4ing/1.4.0/",
      m4i.canonicalUrl,
      "metadata4ing canonical URL pinned to the 1.4.0 release"
    );
  }

  /**
   * ONT1b — {@code skip-bundles=metadata4ing} excludes only the
   * metadata4ing entry from the seed pass and leaves the other nine
   * bundles untouched (including the ONT1a RO bundle). Symmetric
   * with the ONT1a skip-bundles test above.
   */
  @Test
  void realManifest_skipBundlesMetadata4ing_excludesM4iButRetainsOthers() {
    Session session = mock(Session.class);
    var svc = new OntologySeedService(session, true, Set.of(), new ObjectMapper(), getClass().getClassLoader());
    Set<String> skip = OntologySeedService.parseSkipBundles("metadata4ing");
    List<String> kept = svc
      .loadManifest()
      .stream()
      .map(e -> e.id)
      .filter(id -> !skip.contains(id))
      .toList();
    assertEquals(9, kept.size(), "skip-bundles=metadata4ing should leave 9 entries");
    assertFalse(kept.contains("metadata4ing"), "metadata4ing should be excluded by skip-bundles");
    assertTrue(kept.contains("prov-o"), "skip-bundles=metadata4ing must not affect prov-o");
    assertTrue(kept.contains("obo-relations"), "skip-bundles=metadata4ing must not affect obo-relations");
  }

  // ---------- helpers -------------------------------------------------------

  static Result singleRow(Map<String, Object> row) {
    Result result = mock(Result.class);
    when(result.queryResults()).thenReturn(List.<Map<String, Object>>of(new LinkedHashMap<>(row)));
    return result;
  }

  static Result emptyResult() {
    Result result = mock(Result.class);
    when(result.queryResults()).thenReturn(Collections.<Map<String, Object>>emptyList());
    return result;
  }

  /** Mini JSON manifest builder for the in-memory tests. */
  private static String manifestJson(List<Map<String, Object>> entries) {
    StringBuilder sb = new StringBuilder("{\"version\":1,\"ontologies\":[");
    boolean first = true;
    for (Map<String, Object> e : entries) {
      if (!first) sb.append(',');
      first = false;
      sb.append('{');
      boolean firstField = true;
      for (var ent : e.entrySet()) {
        if (!firstField) sb.append(',');
        firstField = false;
        sb.append('"').append(ent.getKey()).append("\":");
        Object v = ent.getValue();
        if (v instanceof Number) sb.append(v);
        else sb.append('"').append(v).append('"');
      }
      sb.append('}');
    }
    sb.append("]}");
    return sb.toString();
  }

  private static Map<String, Object> entry(String id, String file, String sha, long size) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id);
    m.put("file", file);
    m.put("format", "Turtle");
    m.put("sha256", sha);
    m.put("sizeBytes", size);
    return m;
  }

  /** Like {@link #entry} but with N1c2's {@code required} field set. */
  private static Map<String, Object> entryRequired(String id, String file, String sha, long size, boolean required) {
    Map<String, Object> m = entry(id, file, sha, size);
    m.put("required", required);
    return m;
  }

  /**
   * Build an in-memory ClassLoader that serves the given path → bytes
   * map via {@link ClassLoader#getResourceAsStream(String)}. Any other
   * path returns {@code null}.
   */
  private static ClassLoader classLoaderWith(Map<String, byte[]> resources) {
    return new ClassLoader(OntologySeedServiceTest.class.getClassLoader()) {
      @Override
      public InputStream getResourceAsStream(String name) {
        byte[] data = resources.get(name);
        if (data != null) return new ByteArrayInputStream(data);
        return null;
      }

      @Override
      public URL getResource(String name) {
        // Not used by the service — return null so the test isn't tempted to depend on it.
        return null;
      }
    };
  }
}
