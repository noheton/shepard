package de.dlr.shepard.context.references.git.adapters.gitlab;

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
 * Tests the GitLabRestClient against an in-process {@link HttpServer} stub.
 *
 * <p>HttpServer's built-in context routing uses the **decoded** path and
 * doesn't see {@code %2F} segments inside the project path that GitLab's
 * API requires. We dispatch via a single root context (`/`) plus a
 * substring-match table on the raw URI; tests express the rule by
 * registering a route on a substring of the raw URI.
 */
class GitLabRestClientTest {

  /** Match-rule: raw URI must contain {@link #uriSubstring} to fire. */
  private static class Route {
    final String uriSubstring;
    final int[] statusSequence;
    int callCount = 0;
    final byte[] body;
    final String contentType;
    final String fileSha;

    Route(String uriSubstring, int[] statusSequence, byte[] body, String contentType, String fileSha) {
      this.uriSubstring = uriSubstring;
      this.statusSequence = statusSequence;
      this.body = body;
      this.contentType = contentType;
      this.fileSha = fileSha;
    }
  }

  private HttpServer server;
  private int port;
  private final List<String> requests = new ArrayList<>();
  /** Insertion-ordered: tests can register specific matches before broader ones. */
  private final Map<String, Route> routes = new LinkedHashMap<>();

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::dispatch);
    server.start();
    port = server.getAddress().getPort();
    requests.clear();
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
      if (match.fileSha != null) ex.getResponseHeaders().add("X-Gitlab-File-Last-Commit-Id", match.fileSha);
    }
    ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
    if (body.length > 0) {
      try (OutputStream os = ex.getResponseBody()) {
        os.write(body);
      }
    }
    ex.close();
  }

  private void route(String uriSubstring, int status, byte[] body, String contentType, String fileSha) {
    routes.put(uriSubstring + "@" + System.nanoTime(),
      new Route(uriSubstring, new int[] { status }, body, contentType, fileSha));
  }

  private void routeSequence(String uriSubstring, int[] codes, byte[] finalBody, String contentType, String fileSha) {
    routes.put(uriSubstring + "@" + System.nanoTime(),
      new Route(uriSubstring, codes, finalBody, contentType, fileSha));
  }

  private GitLabRestClient newClient() {
    return new GitLabRestClient()
      .withScheme("http")
      .withTimeout(Duration.ofSeconds(5))
      .withMaxBytes(1_048_576)
      .withExtraHosts("");
  }

  /** repoUrl for the in-process server. */
  private String repoUrl() {
    return "http://127.0.0.1:" + port + "/group/proj";
  }

  // ── files/raw ───────────────────────────────────────────────────────────

  @Test
  void getFile_returns200_andSurfacesFromHeader() {
    route("/files/README.md/raw",
      200, "# hello".getBytes(StandardCharsets.UTF_8),
      "text/markdown; charset=utf-8", "abc123");
    FileResolution r = newClient().getFile(repoUrl(), "main", "README.md", "PAT123");
    assertNotNull(r);
    assertEquals("abc123", r.sha());
    assertEquals("text/markdown", r.mimeType()); // charset stripped
    assertEquals("# hello", new String(r.content(), StandardCharsets.UTF_8));
    assertTrue(requests.get(0).contains("ref=main"));
  }

  @Test
  void getFile_returns404_throwsAdapterException() {
    route("/files/missing.md/raw", 404, null, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile(repoUrl(), "main", "missing.md", "PAT")
    );
    assertEquals(404, ex.getStatus());
    assertTrue(ex.getMessage().contains("File not found"));
  }

  @Test
  void getFile_returns401_throwsAdapterException_withScopeHint() {
    route("/files/x/raw", 401, null, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile(repoUrl(), "main", "x", "PAT")
    );
    assertEquals(401, ex.getStatus());
    assertTrue(ex.getMessage().contains("read_repository"));
  }

  @Test
  void getFile_returns403_throwsAdapterException_withScopeHint() {
    route("/files/x/raw", 403, null, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().getFile(repoUrl(), "main", "x", "PAT")
    );
    assertEquals(403, ex.getStatus());
  }

  @Test
  void getFile_500onceThenSucceeds_retriesAndReturnsContent() {
    routeSequence("/files/README.md/raw",
      new int[] { 500, 200 },
      "# yay".getBytes(StandardCharsets.UTF_8),
      "text/markdown", "sha-after-retry");
    FileResolution r = newClient().getFile(repoUrl(), "main", "README.md", "PAT");
    assertEquals("sha-after-retry", r.sha());
    assertEquals("# yay", new String(r.content(), StandardCharsets.UTF_8));
    assertEquals(2, requests.size()); // retry happened
  }

  @Test
  void getFile_500twice_throwsAdapterException() {
    routeSequence("/files/README.md/raw",
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
    route("/files/big.bin/raw", 200, big, "application/octet-stream", "sha-big");
    FileResolution r = newClient().withMaxBytes(1024).getFile(repoUrl(), "main", "big.bin", "PAT");
    assertEquals("sha-big", r.sha());
    assertNull(r.content()); // truncated
    assertEquals("application/octet-stream", r.mimeType());
  }

  @Test
  void getFile_missingShaHeader_fallsBackToCommitsResolve() {
    // First match: files/raw without the sha header.
    route("/files/x.md/raw", 200, "X".getBytes(StandardCharsets.UTF_8), "text/plain", null);
    // Second match: commits/main returns JSON.
    route("/commits/main", 200,
      "{\"id\":\"fallback-sha\"}".getBytes(StandardCharsets.UTF_8),
      "application/json", null);
    FileResolution r = newClient().getFile(repoUrl(), "main", "x.md", "PAT");
    assertEquals("fallback-sha", r.sha());
  }

  // ── commits/{ref} ───────────────────────────────────────────────────────

  @Test
  void resolveRef_returnsSha() {
    route("/commits/main", 200,
      "{\"id\":\"deadbeef\",\"short_id\":\"dead\"}".getBytes(StandardCharsets.UTF_8),
      "application/json", null);
    assertEquals("deadbeef", newClient().resolveRef(repoUrl(), "main", "PAT"));
  }

  @Test
  void resolveRef_404_throws() {
    route("/commits/missing", 404, null, null, null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().resolveRef(repoUrl(), "missing", "PAT")
    );
    assertEquals(404, ex.getStatus());
  }

  @Test
  void resolveRef_malformedJson_throws502() {
    route("/commits/main", 200, "{}".getBytes(StandardCharsets.UTF_8), "application/json", null);
    GitAdapterException ex = assertThrows(
      GitAdapterException.class,
      () -> newClient().resolveRef(repoUrl(), "main", "PAT")
    );
    assertEquals(502, ex.getStatus());
  }

  // ── supports(host) ──────────────────────────────────────────────────────

  @Test
  void supports_acceptsGitlabHostsBySubstring() {
    GitLabRestClient c = new GitLabRestClient();
    assertTrue(c.supports("gitlab.com"));
    assertTrue(c.supports("gitlab.example.dlr.de"));
    assertTrue(c.supports("internal-gitlab"));
  }

  @Test
  void supports_rejectsNonGitlabByDefault() {
    GitLabRestClient c = new GitLabRestClient();
    assertFalse(c.supports("github.com"));
    assertFalse(c.supports("codeberg.org"));
    assertFalse(c.supports(""));
    assertFalse(c.supports(null));
  }

  @Test
  void supports_extraHostsCsv_widensAllowList() {
    GitLabRestClient c = new GitLabRestClient().withExtraHosts("code.dlr.de, source.example");
    assertTrue(c.supports("code.dlr.de"));
    assertTrue(c.supports("source.example"));
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
}
