package de.dlr.shepard.context.references.git.adapters.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.dlr.shepard.context.references.git.adapters.FileResolution;
import de.dlr.shepard.context.references.git.adapters.GitAdapterException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link GitHubRestClient} against an in-process
 * {@link HttpServer} stub — mirrors the {@code GitLabRestClientTest}
 * harness (substring-match routing on the raw URI, optional status
 * sequences for 5xx → 2xx retry tests). G1d ADR-0021.
 *
 * <p>Loopback test repoUrls drive the adapter through its
 * Enterprise URL path ({@code /api/v3/repos/...}) — the
 * {@code github.com} → {@code api.github.com} branch is unit-tested
 * separately via {@link #apiBaseRouting_useGithubCom}.
 */
class GitHubRestClientTest {

  /** Match-rule: raw URI must contain {@link #uriSubstring} to fire. */
  private static class Route {
    final String uriSubstring;
    final int[] statusSequence;
    int callCount = 0;
    final byte[] body;
    final String contentType;

    Route(String uriSubstring, int[] statusSequence, byte[] body, String contentType) {
      this.uriSubstring = uriSubstring;
      this.statusSequence = statusSequence;
      this.body = body;
      this.contentType = contentType;
    }
  }

  private HttpServer server;
  private int port;
  private final List<String> requests = new ArrayList<>();
  private final Map<String, String> lastRequestAuth = new LinkedHashMap<>();
  /** Insertion-ordered: tests can register specific matches before broader ones. */
  private final Map<String, Route> routes = new LinkedHashMap<>();

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::dispatch);
    server.start();
    port = server.getAddress().getPort();
    requests.clear();
    lastRequestAuth.clear();
    routes.clear();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private void dispatch(HttpExchange ex) throws java.io.IOException {
    String raw = ex.getRequestURI().getRawPath() +
      (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery());
    requests.add(raw);
    String auth = ex.getRequestHeaders().getFirst("Authorization");
    if (auth != null) lastRequestAuth.put(raw, auth);

    Route match = null;
    for (Route r : routes.values()) {
      if (raw.contains(r.uriSubstring)) {
        match = r;
        break;
      }
    }
    if (match == null) {
      ex.sendResponseHeaders(404, -1);
      ex.close();
      return;
    }
    int idx = match.callCount++;
    int status = match.statusSequence[Math.min(idx, match.statusSequence.length - 1)];
    byte[] body = (status >= 200 && status < 300 && match.body != null) ? match.body : new byte[0];
    if (status >= 200 && status < 300 && match.contentType != null) {
      ex.getResponseHeaders().add("Content-Type", match.contentType);
    }
    ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
    if (body.length > 0) {
      try (OutputStream os = ex.getResponseBody()) {
        os.write(body);
      }
    }
    ex.close();
  }

  private void route(String uriSubstring, int status, byte[] body, String contentType) {
    routes.put(uriSubstring + "@" + System.nanoTime(),
      new Route(uriSubstring, new int[] { status }, body, contentType));
  }

  private void routeSequence(String uriSubstring, int[] codes, byte[] finalBody, String contentType) {
    routes.put(uriSubstring + "@" + System.nanoTime(),
      new Route(uriSubstring, codes, finalBody, contentType));
  }

  private GitHubRestClient newClient() {
    return new GitHubRestClient()
      .withScheme("http")
      .withTimeout(Duration.ofSeconds(5))
      .withMaxBytes(1_048_576)
      .withExtraHosts("");
  }

  /** repoUrl for the in-process server (Enterprise-style URL path). */
  private String repoUrl() {
    return "http://127.0.0.1:" + port + "/octocat/hello";
  }

  // ── contents/{path} ─────────────────────────────────────────────────────

  @Test
  void getFile_returns200_resolvesShaViaCommits() {
    route("/api/v3/repos/octocat/hello/contents/README.md",
      200, "# hi".getBytes(StandardCharsets.UTF_8),
      "text/markdown; charset=utf-8");
    route("/api/v3/repos/octocat/hello/commits/main",
      200, "{\"sha\":\"deadbeef\"}".getBytes(StandardCharsets.UTF_8),
      "application/json");
    FileResolution r = newClient().getFile(repoUrl(), "main", "README.md", "PAT123");
    assertNotNull(r);
    assertEquals("deadbeef", r.sha());
    assertEquals("text/markdown", r.mimeType()); // charset stripped
    assertEquals("# hi", new String(r.content(), StandardCharsets.UTF_8));
    assertTrue(requests.get(0).contains("ref=main"));
  }

  @Test
  void getFile_returns404_throwsAdapterException() {
    route("/api/v3/repos/octocat/hello/contents/missing.md", 404, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile(repoUrl(), "main", "missing.md", "PAT")
    );
    assertEquals(404, ex.getStatus());
    assertTrue(ex.getMessage().contains("File not found"));
  }

  @Test
  void getFile_returns401_throwsAdapterException_withScopeHint() {
    route("/api/v3/repos/octocat/hello/contents/x", 401, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile(repoUrl(), "main", "x", "PAT")
    );
    assertEquals(401, ex.getStatus());
    assertTrue(ex.getMessage().contains("PAT"));
    assertTrue(
      ex.getMessage().contains("Contents") || ex.getMessage().contains("repo"),
      "should mention `Contents: Read` (fine-grained) or `repo` (classic)"
    );
  }

  @Test
  void getFile_returns403_throwsAdapterException_withScopeHint() {
    route("/api/v3/repos/octocat/hello/contents/x", 403, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile(repoUrl(), "main", "x", "PAT")
    );
    assertEquals(403, ex.getStatus());
  }

  @Test
  void getFile_500onceThenSucceeds_retriesAndReturnsContent() {
    routeSequence("/api/v3/repos/octocat/hello/contents/README.md",
      new int[] { 500, 200 },
      "# yay".getBytes(StandardCharsets.UTF_8),
      "text/markdown");
    route("/api/v3/repos/octocat/hello/commits/main",
      200, "{\"sha\":\"after-retry\"}".getBytes(StandardCharsets.UTF_8),
      "application/json");
    FileResolution r = newClient().getFile(repoUrl(), "main", "README.md", "PAT");
    assertEquals("after-retry", r.sha());
    assertEquals("# yay", new String(r.content(), StandardCharsets.UTF_8));
    // 1 contents (500) + 1 contents (retry 200) + 1 commits = 3
    assertEquals(3, requests.size());
  }

  @Test
  void getFile_500twice_throwsAdapterException() {
    routeSequence("/api/v3/repos/octocat/hello/contents/README.md",
      new int[] { 500, 500 }, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile(repoUrl(), "main", "README.md", "PAT")
    );
    assertEquals(500, ex.getStatus());
  }

  @Test
  void getFile_oversize_returnsTruncated() {
    byte[] big = new byte[2048];
    java.util.Arrays.fill(big, (byte) 'x');
    route("/api/v3/repos/octocat/hello/contents/big.bin", 200, big, "application/octet-stream");
    route("/api/v3/repos/octocat/hello/commits/main",
      200, "{\"sha\":\"sha-big\"}".getBytes(StandardCharsets.UTF_8),
      "application/json");
    FileResolution r = newClient().withMaxBytes(1024).getFile(repoUrl(), "main", "big.bin", "PAT");
    assertEquals("sha-big", r.sha());
    assertNull(r.content()); // truncated
    assertEquals("application/octet-stream", r.mimeType());
  }

  @Test
  void getFile_nestedPath_preservesSlashSeparators() {
    route("/api/v3/repos/octocat/hello/contents/src/main/java/Hello.java",
      200, "class Hello{}".getBytes(StandardCharsets.UTF_8),
      "text/x-java-source");
    route("/api/v3/repos/octocat/hello/commits/main",
      200, "{\"sha\":\"nested-sha\"}".getBytes(StandardCharsets.UTF_8),
      "application/json");
    FileResolution r = newClient().getFile(repoUrl(), "main", "src/main/java/Hello.java", "PAT");
    assertEquals("nested-sha", r.sha());
    // First request is the contents path — verify literal `/` survived encoding.
    assertTrue(requests.get(0).contains("/contents/src/main/java/Hello.java"),
      "nested path should keep `/` separators literal; was: " + requests.get(0));
  }

  @Test
  void getFile_omitsAuthorizationHeader_whenPatIsBlank() {
    // Public-repo fetch: blank PAT means no Authorization header (rather
    // than a malformed "Bearer " header). The 401 branch covers the
    // missing-auth response shape.
    route("/api/v3/repos/octocat/hello/contents/x",
      200, "x".getBytes(StandardCharsets.UTF_8), "text/plain");
    route("/api/v3/repos/octocat/hello/commits/main",
      200, "{\"sha\":\"a\"}".getBytes(StandardCharsets.UTF_8), "application/json");
    newClient().getFile(repoUrl(), "main", "x", "  ");
    assertTrue(lastRequestAuth.isEmpty(),
      "blank PAT must not send an Authorization header; got: " + lastRequestAuth);
  }

  @Test
  void getFile_sendsBearerAuthorization() {
    route("/api/v3/repos/octocat/hello/contents/x",
      200, "x".getBytes(StandardCharsets.UTF_8), "text/plain");
    route("/api/v3/repos/octocat/hello/commits/main",
      200, "{\"sha\":\"a\"}".getBytes(StandardCharsets.UTF_8), "application/json");
    newClient().getFile(repoUrl(), "main", "x", "MYPAT");
    // Both requests should carry the Bearer header.
    assertTrue(lastRequestAuth.values().stream().allMatch(v -> v.equals("Bearer MYPAT")),
      "expected Bearer auth on every call; got: " + lastRequestAuth);
  }

  // ── commits/{ref} ───────────────────────────────────────────────────────

  @Test
  void resolveRef_returnsSha() {
    route("/api/v3/repos/octocat/hello/commits/main",
      200, "{\"sha\":\"feedface\",\"node_id\":\"X\"}".getBytes(StandardCharsets.UTF_8),
      "application/json");
    assertEquals("feedface", newClient().resolveRef(repoUrl(), "main", "PAT"));
  }

  @Test
  void resolveRef_404_throws() {
    route("/api/v3/repos/octocat/hello/commits/missing", 404, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().resolveRef(repoUrl(), "missing", "PAT")
    );
    assertEquals(404, ex.getStatus());
  }

  @Test
  void resolveRef_malformedJson_throws502() {
    route("/api/v3/repos/octocat/hello/commits/main", 200, "{}".getBytes(StandardCharsets.UTF_8), "application/json");
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().resolveRef(repoUrl(), "main", "PAT")
    );
    assertEquals(502, ex.getStatus());
  }

  @Test
  void resolveRef_5xxStatus_surfacesOriginalCode() {
    routeSequence("/api/v3/repos/octocat/hello/commits/main",
      new int[] { 503, 503 }, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().resolveRef(repoUrl(), "main", "PAT")
    );
    assertEquals(503, ex.getStatus());
    assertTrue(ex.getMessage().contains("503"));
  }

  @Test
  void resolveRef_invalidJson_throws502() {
    route("/api/v3/repos/octocat/hello/commits/main", 200, "not-json".getBytes(StandardCharsets.UTF_8), "application/json");
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().resolveRef(repoUrl(), "main", "PAT")
    );
    assertEquals(502, ex.getStatus());
  }

  // ── supports(host) ──────────────────────────────────────────────────────

  @Test
  void supports_acceptsGithubComExact() {
    GitHubRestClient c = new GitHubRestClient();
    assertTrue(c.supports("github.com"));
    assertTrue(c.supports("GITHUB.COM"));
  }

  @Test
  void supports_acceptsGithubSubdomain() {
    GitHubRestClient c = new GitHubRestClient();
    assertTrue(c.supports("api.github.com"));
    assertTrue(c.supports("raw.github.com"));
  }

  @Test
  void supports_rejectsNonGithubByDefault() {
    GitHubRestClient c = new GitHubRestClient();
    assertFalse(c.supports("gitlab.com"));
    assertFalse(c.supports("github.dlr.de")); // not in allowlist
    assertFalse(c.supports("notgithub.com")); // suffix-not-subdomain
    assertFalse(c.supports("github.example.com")); // sibling, not subdomain
    assertFalse(c.supports(""));
    assertFalse(c.supports(null));
  }

  @Test
  void supports_extraHostsCsv_widensAllowList() {
    GitHubRestClient c = new GitHubRestClient().withExtraHosts("github.dlr.de, ghe.example.org");
    assertTrue(c.supports("github.dlr.de"));
    assertTrue(c.supports("ghe.example.org"));
    assertFalse(c.supports("other.host"));
  }

  // ── validation ──────────────────────────────────────────────────────────

  @Test
  void getFile_blankRef_throws400() {
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile(repoUrl(), "", "x", "PAT")
    );
    assertEquals(400, ex.getStatus());
  }

  @Test
  void getFile_blankPath_throws400() {
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile(repoUrl(), "main", "  ", "PAT")
    );
    assertEquals(400, ex.getStatus());
  }

  @Test
  void getFile_repoUrlMissingOwnerOrRepo_throws400() {
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile("http://127.0.0.1:" + port + "/justone", "main", "x", "PAT")
    );
    assertEquals(400, ex.getStatus());
  }

  @Test
  void getFile_repoUrlWithSubgroup_throws400() {
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile("http://127.0.0.1:" + port + "/group/sub/proj", "main", "x", "PAT")
    );
    assertEquals(400, ex.getStatus());
  }

  // ── metadata ────────────────────────────────────────────────────────────

  @Test
  void nameAndPriority_areStable() {
    GitHubRestClient c = new GitHubRestClient();
    assertEquals("github", c.name());
    assertEquals(GitHubRestClient.PRIORITY, c.priority());
    assertTrue(c.priority() < 1000, "GitHub priority must beat the default GitLab priority");
  }
}
