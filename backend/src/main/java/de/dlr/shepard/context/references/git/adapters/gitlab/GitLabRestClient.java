package de.dlr.shepard.context.references.git.adapters.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.references.git.adapters.FileResolution;
import de.dlr.shepard.context.references.git.adapters.GitAdapter;
import de.dlr.shepard.context.references.git.adapters.GitAdapterException;
import de.dlr.shepard.context.references.git.adapters.ParsedRepoUrl;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * GitLab v4 REST adapter for the G1b tracked-artifact slice
 * ({@code aidocs/38 §5}). Java 21 {@link HttpClient}; no new HTTP deps.
 *
 * <p>Endpoints used (per
 * <a href="https://docs.gitlab.com/api/repository_files/">GitLab API docs</a>):
 * <ul>
 *   <li>{@code GET /api/v4/projects/{urlEncodedProject}/repository/files/{urlEncodedPath}/raw?ref={ref}}
 *       — single-file fetch.</li>
 *   <li>{@code GET /api/v4/projects/{urlEncodedProject}/repository/commits/{ref}}
 *       — resolve a branch / tag to its SHA.</li>
 * </ul>
 *
 * <p>Auth: {@code Authorization: Bearer {pat}}. GitLab also accepts
 * {@code PRIVATE-TOKEN: …} but Bearer is the canonical OAuth-style
 * header; this matches what the {@code GitCredentialIO} doc string
 * recommends ("PAT with read_repository scope").
 *
 * <p>Retry: 5xx is retried once with a 250ms back-off, then surfaces
 * as {@link GitAdapterException}. 4xx is non-retryable.
 */
@ApplicationScoped
public class GitLabRestClient implements GitAdapter {

  /** {@code raw} endpoint emits the file body verbatim with a Content-Type best-guess. */
  private static final String FILES_RAW_PATH = "/api/v4/projects/%s/repository/files/%s/raw";

  /** {@code commits/{ref}} returns {@code {"id": "<sha>", …}} JSON. */
  private static final String COMMITS_PATH = "/api/v4/projects/%s/repository/commits/%s";

  /** Adapter id surfaced in problem responses + logs. */
  public static final String NAME = "gitlab";

  @ConfigProperty(name = "shepard.git.adapter.timeout", defaultValue = "PT15S")
  Duration timeout;

  /**
   * Comma-separated extra hostnames whose traffic should be routed to this
   * adapter, on top of the default "anything containing 'gitlab'" rule.
   * Useful for self-hosted instances behind a non-obvious DNS name
   * (e.g. {@code code.dlr.de}).
   */
  @ConfigProperty(name = "shepard.git.adapter.gitlab.hosts", defaultValue = "")
  Optional<String> extraHosts;

  @ConfigProperty(name = "shepard.git.preview.max-bytes", defaultValue = "1048576")
  long maxBytes;

  /**
   * Scheme used for built URLs. {@code https} in production; {@code http}
   * is a test-only knob. Visible-for-test via {@link #withScheme}; users
   * never set this in {@code application.properties}.
   */
  String scheme = "https";

  private final ObjectMapper json = new ObjectMapper();

  /**
   * Default constructor wires the HTTP client lazily; the per-request
   * client honours the configured {@link #timeout}. Visible for testing
   * via {@link #withHttpClient}.
   */
  public GitLabRestClient() {}

  /** Test seam — inject a stub HttpClient to mock the wire. */
  HttpClient httpClient() {
    return HttpClient.newBuilder()
      .connectTimeout(timeout != null ? timeout : Duration.ofSeconds(15))
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();
  }

  /** Package-visible setter used by tests to swap the client. */
  GitLabRestClient withTimeout(Duration t) {
    this.timeout = t;
    return this;
  }

  /** Package-visible setter used by tests to widen the host allow-list. */
  GitLabRestClient withExtraHosts(String csv) {
    this.extraHosts = Optional.ofNullable(csv);
    return this;
  }

  /** Package-visible setter used by tests to lower the byte cap. */
  GitLabRestClient withMaxBytes(long max) {
    this.maxBytes = max;
    return this;
  }

  /** Package-visible setter used by tests to point at an http test server. */
  GitLabRestClient withScheme(String s) {
    this.scheme = s;
    return this;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public boolean supports(String host) {
    if (host == null || host.isBlank()) return false;
    String h = host.toLowerCase(Locale.ROOT);
    if (h.contains("gitlab")) return true;
    if (extraHosts == null || extraHosts.isEmpty()) return false;
    String csv = extraHosts.get();
    if (csv == null || csv.isBlank()) return false;
    List<String> allow = Arrays.asList(csv.toLowerCase(Locale.ROOT).split(","));
    for (String entry : allow) {
      String trimmed = entry.trim();
      if (!trimmed.isEmpty() && h.equals(trimmed)) return true;
    }
    return false;
  }

  @Override
  public FileResolution getFile(String repoUrl, String ref, String path, String pat) {
    ParsedRepoUrl parsed = ParsedRepoUrl.parse(repoUrl);
    if (ref == null || ref.isBlank()) {
      throw new GitAdapterException(400, "ref is required for tracked-artifact fetch");
    }
    if (path == null || path.isBlank()) {
      throw new GitAdapterException(400, "path is required for tracked-artifact fetch");
    }
    String url = scheme + "://" + parsed.authority() +
      FILES_RAW_PATH.formatted(urlEncode(parsed.projectPath()), urlEncode(path)) +
      "?ref=" + urlEncode(ref);

    HttpResponse<byte[]> response = sendWithRetry(buildGet(url, pat), HttpResponse.BodyHandlers.ofByteArray());

    if (response.statusCode() == 404) {
      throw new GitAdapterException(404,
        "File not found at " + parsed.projectPath() + ":" + ref + "/" + path +
        " — verify the repoUrl / ref / path are correct."
      );
    }
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new GitAdapterException(response.statusCode(),
        "Repository not found or access denied — verify your PAT has read_repository scope " +
        "and is valid for " + parsed.host() + "."
      );
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new GitAdapterException(response.statusCode(),
        "GitLab returned status " + response.statusCode() + " for files/raw — " +
        "retry later or contact the GitLab admin."
      );
    }

    byte[] body = response.body();
    Long byteSize = response.headers().firstValueAsLong("Content-Length").stream().boxed().findFirst().orElse(
      body == null ? null : (long) body.length
    );
    String mimeType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
    // Strip charset suffix — `text/markdown; charset=utf-8` → `text/markdown`.
    int semi = mimeType.indexOf(';');
    if (semi >= 0) mimeType = mimeType.substring(0, semi).trim();
    if (mimeType.isEmpty()) mimeType = "application/octet-stream";

    // Best-effort SHA: the raw endpoint returns it in the
    // `X-Gitlab-File-Last-Commit-Id` header; otherwise fall back to
    // a follow-up resolveRef call.
    String sha = response.headers().firstValue("x-gitlab-file-last-commit-id").orElse(null);
    if (sha == null || sha.isBlank()) {
      sha = resolveRef(repoUrl, ref, pat);
    }

    boolean truncated = body != null && body.length > maxBytes;
    byte[] payload = truncated ? null : body;
    return new FileResolution(sha, payload, mimeType, byteSize);
  }

  @Override
  public String resolveRef(String repoUrl, String ref, String pat) {
    ParsedRepoUrl parsed = ParsedRepoUrl.parse(repoUrl);
    if (ref == null || ref.isBlank()) {
      throw new GitAdapterException(400, "ref is required to resolve a SHA");
    }
    String url = scheme + "://" + parsed.authority() +
      COMMITS_PATH.formatted(urlEncode(parsed.projectPath()), urlEncode(ref));

    HttpResponse<byte[]> response = sendWithRetry(buildGet(url, pat), HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() == 404) {
      throw new GitAdapterException(404,
        "Branch / tag " + ref + " not found on " + parsed.projectPath() +
        " — verify the ref exists."
      );
    }
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new GitAdapterException(response.statusCode(),
        "Repository not found or access denied — verify your PAT has read_repository scope " +
        "and is valid for " + parsed.host() + "."
      );
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new GitAdapterException(response.statusCode(),
        "GitLab returned status " + response.statusCode() + " for commits/" + ref + " — " +
        "retry later or contact the GitLab admin."
      );
    }
    byte[] body = response.body();
    try {
      JsonNode tree = json.readTree(body == null ? new byte[0] : body);
      JsonNode id = tree.get("id");
      if (id == null || !id.isTextual() || id.asText().isBlank()) {
        throw new GitAdapterException(502,
          "GitLab commits/" + ref + " response is missing the `id` field"
        );
      }
      return id.asText();
    } catch (IOException ioe) {
      throw new GitAdapterException(502, "GitLab commits/" + ref + " response is not JSON", ioe);
    }
  }

  // ── HTTP plumbing ────────────────────────────────────────────────────────

  private HttpRequest buildGet(String url, String pat) {
    HttpRequest.Builder b = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .timeout(timeout != null ? timeout : Duration.ofSeconds(15))
      .header("Accept", "*/*")
      .GET();
    if (pat != null && !pat.isBlank()) {
      b.header("Authorization", "Bearer " + pat);
    }
    return b.build();
  }

  private <T> HttpResponse<T> sendWithRetry(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    HttpClient client = httpClient();
    HttpResponse<T> response;
    try {
      response = client.send(request, handler);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new GitAdapterException(0, "GitLab request failed: " + e.getMessage(), e);
    }
    if (response.statusCode() >= 500 && response.statusCode() < 600) {
      Log.debugf("GitLab returned 5xx (%d); retrying once after 250ms", response.statusCode());
      try {
        Thread.sleep(250);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new GitAdapterException(0, "GitLab retry interrupted", ie);
      }
      try {
        response = client.send(request, handler);
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        throw new GitAdapterException(0, "GitLab retry failed: " + e.getMessage(), e);
      }
    }
    return response;
  }

  private static String urlEncode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
