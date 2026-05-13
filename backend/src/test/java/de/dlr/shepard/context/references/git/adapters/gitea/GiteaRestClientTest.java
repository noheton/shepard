package de.dlr.shepard.context.references.git.adapters.gitea;

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
 * Tests the {@link GiteaRestClient} against an in-process
 * {@link HttpServer} stub — mirrors the {@code GitLabRestClientTest}
 * harness (substring-match routing on the raw URI, optional status
 * sequences for 5xx → 2xx retry tests). G1d ADR-0021.
 */
class GiteaRestClientTest {

  private static class Route {
    final String uriSubstring;
    final int[] statusSequence;
    int callCount = 0;
    final byte[] body;
    final String contentType;
    final String commitSha;

    Route(String uriSubstring, int[] statusSequence, byte[] body, String contentType, String commitSha) {
      this.uriSubstring = uriSubstring;
      this.statusSequence = statusSequence;
      this.body = body;
      this.contentType = contentType;
      this.commitSha = commitSha;
    }
  }

  private HttpServer server;
  private int port;
  private final List<String> requests = new ArrayList<>();
  private final Map<String, String> lastRequestAuth = new LinkedHashMap<>();
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
    if (status >= 200 && status < 300) {
      if (match.contentType != null) ex.getResponseHeaders().add("Content-Type", match.contentType);
      if (match.commitSha != null) ex.getResponseHeaders().add("X-Gitea-Commit-Id", match.commitSha);
    }
    ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
    if (body.length > 0) {
      try (OutputStream os = ex.getResponseBody()) {
        os.write(body);
      }
    }
    ex.close();
  }

  private void route(String uriSubstring, int status, byte[] body, String contentType, String commitSha) {
    routes.put(uriSubstring + "@" + System.nanoTime(),
      new Route(uriSubstring, new int[] { status }, body, contentType, commitSha));
  }

  private void routeSequence(String uriSubstring, int[] codes, byte[] finalBody, String contentType, String commitSha) {
    routes.put(uriSubstring + "@" + System.nanoTime(),
      new Route(uriSubstring, codes, finalBody, contentType, commitSha));
  }

  private GiteaRestClient newClient() {
    return new GiteaRestClient()
      .withScheme("http")
      .withTimeout(Duration.ofSeconds(5))
      .withMaxBytes(1_048_576)
      .withExtraHosts("");
  }

  /** repoUrl for the in-process server. */
  private String repoUrl() {
    return "http://127.0.0.1:" + port + "/octo/hello";
  }

  // ── raw file ────────────────────────────────────────────────────────────

  @Test
  void getFile_returns200_andSurfacesShaFromHeader() {
    route("/api/v1/repos/octo/hello/raw/README.md",
      200, "# hi".getBytes(StandardCharsets.UTF_8),
      "text/markdown; charset=utf-8", "abc123");
    FileResolution r = newClient().getFile(repoUrl(), "main", "README.md", "PAT123");
    assertNotNull(r);
    assertEquals("abc123", r.sha());
    assertEquals("text/markdown", r.mimeType()); // charset stripped
    assertEquals("# hi", new String(r.content(), StandardCharsets.UTF_8));
    assertTrue(requests.get(0).contains("ref=main"));
  }

  @Test
  void getFile_returns404_throwsAdapterException() {
    route("/api/v1/repos/octo/hello/raw/missing.md", 404, null, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile(repoUrl(), "main", "missing.md", "PAT")
    );
    assertEquals(404, ex.getStatus());
    assertTrue(ex.getMessage().contains("File not found"));
  }

  @Test
  void getFile_returns401_throwsAdapterException_withScopeHint() {
    route("/api/v1/repos/octo/hello/raw/x", 401, null, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile(repoUrl(), "main", "x", "PAT")
    );
    assertEquals(401, ex.getStatus());
    assertTrue(ex.getMessage().contains("read:repository"));
  }

  @Test
  void getFile_returns403_throwsAdapterException_withScopeHint() {
    route("/api/v1/repos/octo/hello/raw/x", 403, null, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile(repoUrl(), "main", "x", "PAT")
    );
    assertEquals(403, ex.getStatus());
  }

  @Test
  void getFile_500onceThenSucceeds_retriesAndReturnsContent() {
    routeSequence("/api/v1/repos/octo/hello/raw/README.md",
      new int[] { 500, 200 },
      "# yay".getBytes(StandardCharsets.UTF_8),
      "text/markdown", "sha-after-retry");
    FileResolution r = newClient().getFile(repoUrl(), "main", "README.md", "PAT");
    assertEquals("sha-after-retry", r.sha());
    assertEquals("# yay", new String(r.content(), StandardCharsets.UTF_8));
    assertEquals(2, requests.size()); // retry happened, no fallback to commits
  }

  @Test
  void getFile_500twice_throwsAdapterException() {
    routeSequence("/api/v1/repos/octo/hello/raw/README.md",
      new int[] { 500, 500 }, null, null, null);
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
    route("/api/v1/repos/octo/hello/raw/big.bin", 200, big, "application/octet-stream", "sha-big");
    FileResolution r = newClient().withMaxBytes(1024).getFile(repoUrl(), "main", "big.bin", "PAT");
    assertEquals("sha-big", r.sha());
    assertNull(r.content()); // truncated
    assertEquals("application/octet-stream", r.mimeType());
  }

  @Test
  void getFile_missingShaHeader_fallsBackToCommitsResolve() {
    // First match: raw without the commit-id header.
    route("/api/v1/repos/octo/hello/raw/x.md", 200, "X".getBytes(StandardCharsets.UTF_8), "text/plain", null);
    // Second match: commits/main returns JSON.
    route("/api/v1/repos/octo/hello/commits/main", 200,
      "{\"sha\":\"fallback-sha\"}".getBytes(StandardCharsets.UTF_8),
      "application/json", null);
    FileResolution r = newClient().getFile(repoUrl(), "main", "x.md", "PAT");
    assertEquals("fallback-sha", r.sha());
  }

  @Test
  void getFile_nestedPath_preservesSlashSeparators() {
    route("/api/v1/repos/octo/hello/raw/src/main/java/Hello.java",
      200, "class Hello{}".getBytes(StandardCharsets.UTF_8),
      "text/x-java-source", "nested-sha");
    FileResolution r = newClient().getFile(repoUrl(), "main", "src/main/java/Hello.java", "PAT");
    assertEquals("nested-sha", r.sha());
    assertTrue(requests.get(0).contains("/raw/src/main/java/Hello.java"),
      "nested path should keep `/` separators literal; was: " + requests.get(0));
  }

  @Test
  void getFile_omitsAuthorizationHeader_whenPatIsBlank() {
    route("/api/v1/repos/octo/hello/raw/x",
      200, "x".getBytes(StandardCharsets.UTF_8), "text/plain", "shaA");
    newClient().getFile(repoUrl(), "main", "x", "  ");
    assertTrue(lastRequestAuth.isEmpty(),
      "blank PAT must not send an Authorization header; got: " + lastRequestAuth);
  }

  @Test
  void getFile_sendsTokenAuthorization() {
    route("/api/v1/repos/octo/hello/raw/x",
      200, "x".getBytes(StandardCharsets.UTF_8), "text/plain", "sha");
    newClient().getFile(repoUrl(), "main", "x", "MYPAT");
    assertTrue(lastRequestAuth.values().stream().allMatch(v -> v.equals("token MYPAT")),
      "expected `token <pat>` auth (Gitea convention); got: " + lastRequestAuth);
  }

  // ── commits/{ref} ───────────────────────────────────────────────────────

  @Test
  void resolveRef_returnsSha() {
    route("/api/v1/repos/octo/hello/commits/main", 200,
      "{\"sha\":\"feedface\",\"created\":\"…\"}".getBytes(StandardCharsets.UTF_8),
      "application/json", null);
    assertEquals("feedface", newClient().resolveRef(repoUrl(), "main", "PAT"));
  }

  @Test
  void resolveRef_acceptsArrayResponse() {
    // Some older Gitea / Forgejo builds return an array for `commits/{ref}`.
    route("/api/v1/repos/octo/hello/commits/main", 200,
      "[{\"sha\":\"a1b2c3\"},{\"sha\":\"d4e5f6\"}]".getBytes(StandardCharsets.UTF_8),
      "application/json", null);
    assertEquals("a1b2c3", newClient().resolveRef(repoUrl(), "main", "PAT"));
  }

  @Test
  void resolveRef_404_throws() {
    route("/api/v1/repos/octo/hello/commits/missing", 404, null, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().resolveRef(repoUrl(), "missing", "PAT")
    );
    assertEquals(404, ex.getStatus());
  }

  @Test
  void resolveRef_malformedJson_throws502() {
    route("/api/v1/repos/octo/hello/commits/main", 200, "{}".getBytes(StandardCharsets.UTF_8), "application/json", null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().resolveRef(repoUrl(), "main", "PAT")
    );
    assertEquals(502, ex.getStatus());
  }

  @Test
  void resolveRef_5xxStatus_surfacesOriginalCode() {
    routeSequence("/api/v1/repos/octo/hello/commits/main",
      new int[] { 503, 503 }, null, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().resolveRef(repoUrl(), "main", "PAT")
    );
    assertEquals(503, ex.getStatus());
    assertTrue(ex.getMessage().contains("503"));
  }

  @Test
  void resolveRef_invalidJson_throws502() {
    route("/api/v1/repos/octo/hello/commits/main", 200, "not-json".getBytes(StandardCharsets.UTF_8), "application/json", null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().resolveRef(repoUrl(), "main", "PAT")
    );
    assertEquals(502, ex.getStatus());
  }

  // ── supports(host) ──────────────────────────────────────────────────────

  @Test
  void supports_acceptsGiteaHostsBySubstring() {
    GiteaRestClient c = new GiteaRestClient();
    assertTrue(c.supports("gitea.com"));
    assertTrue(c.supports("gitea.example.dlr.de"));
    assertTrue(c.supports("internal-gitea"));
  }

  @Test
  void supports_rejectsNonGiteaByDefault() {
    GiteaRestClient c = new GiteaRestClient();
    assertFalse(c.supports("github.com"));
    assertFalse(c.supports("gitlab.com"));
    assertFalse(c.supports("codeberg.org"));
    assertFalse(c.supports(""));
    assertFalse(c.supports(null));
  }

  @Test
  void supports_extraHostsCsv_widensAllowList() {
    // Useful for Forgejo / codeberg.org / opt-in hostnames.
    GiteaRestClient c = new GiteaRestClient().withExtraHosts("codeberg.org, forge.example");
    assertTrue(c.supports("codeberg.org"));
    assertTrue(c.supports("forge.example"));
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
    GiteaRestClient c = new GiteaRestClient();
    assertEquals("gitea", c.name());
    assertEquals(GiteaRestClient.PRIORITY, c.priority());
    assertTrue(c.priority() < 1000, "Gitea priority must beat the default GitLab priority");
  }
}
