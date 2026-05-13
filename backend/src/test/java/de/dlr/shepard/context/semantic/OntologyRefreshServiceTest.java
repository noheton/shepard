package de.dlr.shepard.context.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * Unit tests for {@link OntologyRefreshService}. Two layers:
 *
 * <ol>
 *   <li>Mocked-classloader tests for the bundle-routing logic
 *       (filter by id, unknown-id error, hash-equal skip,
 *       force re-import, per-bundle failure isolation).</li>
 *   <li>Loopback HTTP server tests for the canonical-fetch path
 *       (200 happy path, 404, connect error).</li>
 * </ol>
 *
 * <p>Mirrors {@link OntologySeedServiceTest}'s structure; we
 * deliberately do NOT exercise n10s itself (its happy path is
 * dedicated to the seed-service test).
 */
class OntologyRefreshServiceTest {

  private static final String SAMPLE_TTL =
    "@prefix ex: <http://example.org/> .\nex:a a ex:Thing .\n";

  private static final String OTHER_TTL =
    "@prefix ex2: <http://example.org/v2/> .\nex2:b a ex2:Updated .\n";

  /** SHA-256 of {@link #SAMPLE_TTL} bytes (UTF-8) — the "bundled" hash. */
  private static final String SAMPLE_SHA = OntologySeedService.sha256Hex(
    SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
  );

  /** SHA-256 of {@link #OTHER_TTL} bytes (UTF-8) — the "newer canonical" hash. */
  private static final String OTHER_SHA = OntologySeedService.sha256Hex(
    OTHER_TTL.getBytes(StandardCharsets.UTF_8)
  );

  // ---------- bundle-routing tests ------------------------------------------

  @Test
  void refresh_unknownBundleId_reportedAsUpFrontError() throws Exception {
    try (HarnessServer srv = HarnessServer.start("/prov", 200, SAMPLE_TTL)) {
      Session session = mock(Session.class);
      String manifest = manifestJson(
        List.of(entryWithUrl("prov-o", "prov.ttl", SAMPLE_SHA, SAMPLE_TTL.length(), srv.urlFor("/prov")))
      );
      OntologySeedService delegate = new OntologySeedService(
        session,
        true,
        Set.of(),
        new ObjectMapper(),
        classLoaderWith(Map.of("ontologies/ontologies-manifest.json", manifest.getBytes(StandardCharsets.UTF_8),
          "ontologies/prov.ttl", SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)))
      );
      OntologyRefreshService svc = new OntologyRefreshService(session, defaultClient(), delegate);

      var outcome = svc.refresh(List.of("bogus-id", "prov-o"), false);

      assertEquals(2, outcome.requested, "request counted both: real + bogus");
      assertEquals(0, outcome.refreshed);
      assertEquals(1, outcome.alreadyCurrent, "prov-o's hash already matches the served body");
      assertEquals(1, outcome.errors.size());
      assertEquals("bogus-id", outcome.errors.get(0).bundle);
      assertTrue(outcome.errors.get(0).reason.toLowerCase().contains("unknown"));
    }
  }

  @Test
  void refresh_filtersByRequestedIds() throws Exception {
    try (HarnessServer srv = HarnessServer.start("/p", 200, OTHER_TTL)) {
      Session session = mock(Session.class);
      String manifest = manifestJson(
        List.of(
          entryWithUrl("prov-o", "prov.ttl", SAMPLE_SHA, SAMPLE_TTL.length(), srv.urlFor("/p")),
          entryWithUrl("qudt", "qudt.ttl", SAMPLE_SHA, SAMPLE_TTL.length(), srv.urlFor("/q"))
        )
      );
      OntologySeedService delegate = makeDelegate(session, manifest, Map.of(
        "ontologies/prov.ttl", SAMPLE_TTL.getBytes(StandardCharsets.UTF_8),
        "ontologies/qudt.ttl", SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      ));
      Result imp = singleRow(Map.of("status", "OK", "loaded", 4L));
      when(session.query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any())).thenReturn(imp);

      OntologyRefreshService svc = new OntologyRefreshService(session, defaultClient(), delegate);

      var outcome = svc.refresh(List.of("prov-o"), false);

      assertEquals(1, outcome.requested, "only the requested id is in scope");
      assertEquals(1, outcome.refreshed);
      assertEquals(0, outcome.alreadyCurrent);
      assertTrue(outcome.errors.isEmpty());
      // Only one import call — qudt was filtered out entirely.
      verify(session, org.mockito.Mockito.times(1))
        .query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any());
    }
  }

  @Test
  void refresh_alreadyCurrent_skipsImport_withoutForce() throws Exception {
    try (HarnessServer srv = HarnessServer.start("/p", 200, SAMPLE_TTL)) {
      Session session = mock(Session.class);
      String manifest = manifestJson(
        List.of(entryWithUrl("prov-o", "prov.ttl", SAMPLE_SHA, SAMPLE_TTL.length(), srv.urlFor("/p")))
      );
      OntologySeedService delegate = makeDelegate(session, manifest, Map.of(
        "ontologies/prov.ttl", SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      ));

      OntologyRefreshService svc = new OntologyRefreshService(session, defaultClient(), delegate);

      var outcome = svc.refresh(List.of(), false);

      assertEquals(1, outcome.alreadyCurrent);
      assertEquals(0, outcome.refreshed);
      verify(session, never()).query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any());
    }
  }

  @Test
  void refresh_force_reImports_evenWhenHashMatches() throws Exception {
    try (HarnessServer srv = HarnessServer.start("/p", 200, SAMPLE_TTL)) {
      Session session = mock(Session.class);
      String manifest = manifestJson(
        List.of(entryWithUrl("prov-o", "prov.ttl", SAMPLE_SHA, SAMPLE_TTL.length(), srv.urlFor("/p")))
      );
      OntologySeedService delegate = makeDelegate(session, manifest, Map.of(
        "ontologies/prov.ttl", SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      ));
      Result imp = singleRow(Map.of("status", "OK", "loaded", 7L));
      when(session.query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any())).thenReturn(imp);

      OntologyRefreshService svc = new OntologyRefreshService(session, defaultClient(), delegate);

      var outcome = svc.refresh(List.of(), true);

      assertEquals(1, outcome.refreshed);
      assertEquals(0, outcome.alreadyCurrent);
      verify(session).query(
        eq(OntologySeedService.IMPORT_INLINE_CYPHER),
        eq(Map.of("rdf", SAMPLE_TTL, "format", "Turtle"))
      );
    }
  }

  @Test
  void refresh_differentHash_reImports_andReturnsRefreshedCount() throws Exception {
    try (HarnessServer srv = HarnessServer.start("/p", 200, OTHER_TTL)) {
      Session session = mock(Session.class);
      // Bundled stub is SAMPLE_TTL (SAMPLE_SHA); the canonical URL serves OTHER_TTL.
      String manifest = manifestJson(
        List.of(entryWithUrl("prov-o", "prov.ttl", SAMPLE_SHA, SAMPLE_TTL.length(), srv.urlFor("/p")))
      );
      OntologySeedService delegate = makeDelegate(session, manifest, Map.of(
        "ontologies/prov.ttl", SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      ));
      Result imp = singleRow(Map.of("status", "OK", "loaded", 11L));
      when(session.query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any())).thenReturn(imp);

      OntologyRefreshService svc = new OntologyRefreshService(session, defaultClient(), delegate);

      var outcome = svc.refresh(List.of(), false);

      assertEquals(1, outcome.refreshed);
      assertEquals(0, outcome.alreadyCurrent);
      verify(session).query(
        eq(OntologySeedService.IMPORT_INLINE_CYPHER),
        eq(Map.of("rdf", OTHER_TTL, "format", "Turtle"))
      );
    }
  }

  @Test
  void refresh_fetch404_isPerBundleError_continuesWithNext() throws Exception {
    try (HarnessServer srv = HarnessServer.start("/p", 404, "<not found>")) {
      Session session = mock(Session.class);
      String manifest = manifestJson(
        List.of(
          entryWithUrl("bad", "bad.ttl", SAMPLE_SHA, SAMPLE_TTL.length(), srv.urlFor("/p")),
          entryWithUrl("good", "good.ttl", SAMPLE_SHA, SAMPLE_TTL.length(), srv.urlFor("/p2"))
        )
      );
      // Register a second route for "good"
      srv.registerRoute("/p2", 200, SAMPLE_TTL);
      OntologySeedService delegate = makeDelegate(session, manifest, Map.of(
        "ontologies/bad.ttl", SAMPLE_TTL.getBytes(StandardCharsets.UTF_8),
        "ontologies/good.ttl", SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      ));

      OntologyRefreshService svc = new OntologyRefreshService(session, defaultClient(), delegate);

      var outcome = svc.refresh(List.of(), false);

      assertEquals(2, outcome.requested);
      assertEquals(0, outcome.refreshed);
      assertEquals(1, outcome.alreadyCurrent, "good's body matched its bundled hash");
      assertEquals(1, outcome.errors.size());
      assertEquals("bad", outcome.errors.get(0).bundle);
      assertTrue(outcome.errors.get(0).reason.contains("404"), "reason should cite 404");
    }
  }

  @Test
  void refresh_emptyManifestEntry_canonicalUrlIsBlank_isPerBundleError() throws Exception {
    Session session = mock(Session.class);
    // Build a manifest entry with empty canonicalUrl
    Map<String, Object> rawEntry = new LinkedHashMap<>();
    rawEntry.put("id", "stubless");
    rawEntry.put("file", "stub.ttl");
    rawEntry.put("format", "Turtle");
    rawEntry.put("sha256", SAMPLE_SHA);
    rawEntry.put("sizeBytes", SAMPLE_TTL.length());
    // no canonicalUrl
    String manifest = manifestJson(List.of(rawEntry));
    OntologySeedService delegate = makeDelegate(session, manifest, Map.of(
      "ontologies/stub.ttl", SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
    ));

    OntologyRefreshService svc = new OntologyRefreshService(session, defaultClient(), delegate);

    var outcome = svc.refresh(List.of(), false);

    assertEquals(1, outcome.requested);
    assertEquals(1, outcome.errors.size());
    assertEquals("stubless", outcome.errors.get(0).bundle);
    assertTrue(outcome.errors.get(0).reason.toLowerCase().contains("canonicalurl"));
  }

  @Test
  void refresh_n10sNonOkStatus_isPerBundleError() throws Exception {
    try (HarnessServer srv = HarnessServer.start("/p", 200, OTHER_TTL)) {
      Session session = mock(Session.class);
      String manifest = manifestJson(
        List.of(entryWithUrl("prov-o", "prov.ttl", SAMPLE_SHA, SAMPLE_TTL.length(), srv.urlFor("/p")))
      );
      OntologySeedService delegate = makeDelegate(session, manifest, Map.of(
        "ontologies/prov.ttl", SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      ));
      Result imp = singleRow(Map.of("status", "KO", "loaded", 0L));
      when(session.query(eq(OntologySeedService.IMPORT_INLINE_CYPHER), any())).thenReturn(imp);

      OntologyRefreshService svc = new OntologyRefreshService(session, defaultClient(), delegate);

      var outcome = svc.refresh(List.of(), false);

      assertEquals(0, outcome.refreshed);
      assertEquals(1, outcome.errors.size());
      assertEquals("prov-o", outcome.errors.get(0).bundle);
      assertTrue(outcome.errors.get(0).reason.contains("KO"));
    }
  }

  @Test
  void refresh_manifestLoadFailure_isSingleResultError() {
    Session session = mock(Session.class);
    // No manifest in classpath → loadManifest throws.
    OntologySeedService delegate = new OntologySeedService(
      session,
      true,
      Set.of(),
      new ObjectMapper(),
      classLoaderWith(Map.of())
    );
    OntologyRefreshService svc = new OntologyRefreshService(session, defaultClient(), delegate);

    var outcome = svc.refresh(List.of(), false);

    assertEquals(0, outcome.requested);
    assertEquals(0, outcome.refreshed);
    assertEquals(1, outcome.errors.size());
    assertEquals("<manifest>", outcome.errors.get(0).bundle);
  }

  @Test
  void refresh_duplicateIdsInRequest_areDeduped() throws Exception {
    try (HarnessServer srv = HarnessServer.start("/p", 200, SAMPLE_TTL)) {
      AtomicInteger callCount = new AtomicInteger();
      Session session = mock(Session.class);
      String manifest = manifestJson(
        List.of(entryWithUrl("prov-o", "prov.ttl", SAMPLE_SHA, SAMPLE_TTL.length(), srv.urlFor("/p")))
      );
      OntologySeedService delegate = makeDelegate(session, manifest, Map.of(
        "ontologies/prov.ttl", SAMPLE_TTL.getBytes(StandardCharsets.UTF_8)
      ));

      // Wrap default client to count fetches.
      HttpClient counting = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
      OntologyRefreshService svc = new OntologyRefreshService(session, counting, delegate) {
        @Override
        byte[] fetchCanonical(String url) {
          callCount.incrementAndGet();
          return super.fetchCanonical(url);
        }
      };

      var outcome = svc.refresh(List.of("prov-o", "prov-o", "prov-o"), false);

      assertEquals(1, callCount.get(), "deduped to one fetch");
      assertEquals(1, outcome.requested);
      assertEquals(1, outcome.alreadyCurrent);
    }
  }

  // ---------- helpers --------------------------------------------------------

  private static HttpClient defaultClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  private static OntologySeedService makeDelegate(
    Session session,
    String manifestJson,
    Map<String, byte[]> bundleFiles
  ) {
    Map<String, byte[]> all = new java.util.HashMap<>();
    all.put("ontologies/ontologies-manifest.json", manifestJson.getBytes(StandardCharsets.UTF_8));
    all.putAll(bundleFiles);
    return new OntologySeedService(session, true, Set.of(), new ObjectMapper(), classLoaderWith(all));
  }

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

  private static Map<String, Object> entryWithUrl(String id, String file, String sha, long size, String url) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id);
    m.put("file", file);
    m.put("format", "Turtle");
    m.put("canonicalUrl", url);
    m.put("sha256", sha);
    m.put("sizeBytes", size);
    return m;
  }

  static Result singleRow(Map<String, Object> row) {
    Result result = mock(Result.class);
    when(result.queryResults()).thenReturn(List.<Map<String, Object>>of(new LinkedHashMap<>(row)));
    return result;
  }

  private static ClassLoader classLoaderWith(Map<String, byte[]> resources) {
    return new ClassLoader(OntologyRefreshServiceTest.class.getClassLoader()) {
      @Override
      public InputStream getResourceAsStream(String name) {
        byte[] data = resources.get(name);
        if (data != null) return new ByteArrayInputStream(data);
        return null;
      }

      @Override
      public URL getResource(String name) {
        return null;
      }
    };
  }

  /** Tiny loopback HTTP server for the fetch path. */
  private static final class HarnessServer implements AutoCloseable {

    private final HttpServer server;

    private HarnessServer(HttpServer server) {
      this.server = server;
    }

    static HarnessServer start(String path, int status, String body) throws IOException {
      HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      addRoute(s, path, status, body);
      s.start();
      assertNotNull(s);
      return new HarnessServer(s);
    }

    void registerRoute(String path, int status, String body) {
      addRoute(server, path, status, body);
    }

    private static void addRoute(HttpServer s, String path, int status, String body) {
      s.createContext(path, exchange -> {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/turtle");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
      });
    }

    String urlFor(String path) {
      return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
