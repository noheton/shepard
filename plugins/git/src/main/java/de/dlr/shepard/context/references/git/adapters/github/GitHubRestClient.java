package de.dlr.shepard.context.references.git.adapters.github;

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
 * GitHub v3 REST adapter for the G1d tracked-artifact slice
 * ({@code aidocs/38 §5} + {@code aidocs/63} ADR-0021). Java 21
 * {@link HttpClient}; no new HTTP deps. Mirrors the
 * {@code GitLabRestClient} shape.
 *
 * <p>Endpoints used (per
 * <a href="https://docs.github.com/en/rest/repos/contents">GitHub REST</a>):
 * <ul>
 *   <li>{@code GET /repos/{owner}/{repo}/contents/{path}?ref={ref}}
 *       with {@code Accept: application/vnd.github.raw} — single-file
 *       fetch returning raw bytes.</li>
 *   <li>{@code GET /repos/{owner}/{repo}/commits/{ref}} — resolves a
 *       branch / tag to its commit SHA. The raw contents endpoint
 *       doesn't surface the commit SHA in headers (only the blob SHA
 *       via {@code etag}), so we always issue a follow-up resolveRef
 *       call. The PT5M cache layer absorbs the extra call.</li>
 * </ul>
 *
 * <p>Auth: {@code Authorization: Bearer {pat}} (works for both
 * classic PATs with {@code repo} scope and fine-grained PATs with
 * "Contents: read"; this matches what GitHub recommends in its
 * REST overview).
 *
 * <p>Retry: 5xx is retried once with a 250ms back-off, then surfaces
 * as {@link GitAdapterException}. 4xx is non-retryable.
 */
@ApplicationScoped
public class GitHubRestClient implements GitAdapter {

  /** Raw file fetch: {@code Accept: application/vnd.github.raw} yields the body verbatim. */
  private static final String CONTENTS_PATH = "/repos/%s/%s/contents/%s";

  /** {@code commits/{ref}} returns {@code {"sha": "<sha>", …}} JSON. */
  private static final String COMMITS_PATH = "/repos/%s/%s/commits/%s";

  /** Adapter id surfaced in problem responses + logs. */
  public static final String NAME = "github";

  /**
   * Priority for {@link de.dlr.shepard.context.references.git.adapters.GitAdapterRegistry}
   * dispatch ordering. Lower = checked first; the GitHub adapter sits ahead of
   * GitLab so a host like {@code github.dlr.de} (in the allowlist) is not stolen
   * by a future GitLab substring tweak. See ADR-0021 + this PR's body for the
   * rationale.
   */
  public static final int PRIORITY = 100;

  @ConfigProperty(name = "shepard.git.adapter.timeout", defaultValue = "PT15S")
  Duration timeout;

  /**
   * Comma-separated extra hostnames whose traffic should be routed to this
   * adapter, on top of the default "github.com or *.github.com" rule.
   * Useful for GitHub Enterprise behind a non-obvious DNS name
   * (e.g. {@code github.example.dlr.de}).
   */
  @ConfigProperty(name = "shepard.git.adapter.github.hosts", defaultValue = "")
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

  /** Default constructor for CDI. */
  public GitHubRestClient() {}

  /** Test seam — build a fresh HttpClient honouring the configured timeout. */
  HttpClient httpClient() {
    return HttpClient.newBuilder()
      .connectTimeout(timeout != null ? timeout : Duration.ofSeconds(15))
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();
  }

  /** Package-visible setter used by tests to swap the timeout. */
  GitHubRestClient withTimeout(Duration t) {
    this.timeout = t;
    return this;
  }

  /** Package-visible setter used by tests to widen the host allow-list. */
  GitHubRestClient withExtraHosts(String csv) {
    this.extraHosts = Optional.ofNullable(csv);
    return this;
  }

  /** Package-visible setter used by tests to lower the byte cap. */
  GitHubRestClient withMaxBytes(long max) {
    this.maxBytes = max;
    return this;
  }

  /** Package-visible setter used by tests to point at an http test server. */
  GitHubRestClient withScheme(String s) {
    this.scheme = s;
    return this;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public int priority() {
    return PRIORITY;
  }

  @Override
  public boolean supports(String host) {
    if (host == null || host.isBlank()) return false;
    String h = host.toLowerCase(Locale.ROOT);
    if (h.equals("github.com") || h.endsWith(".github.com")) return true;
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
    OwnerRepo or = splitOwnerRepo(parsed);
    // GitHub Enterprise sits at {host}/api/v3/...; github.com sits at api.github.com/...
    String apiBase = apiBase(parsed.host(), parsed.port());
    String url = apiBase +
      CONTENTS_PATH.formatted(urlEncode(or.owner()), urlEncode(or.repo()), encodePath(path)) +
      "?ref=" + urlEncode(ref);

    HttpResponse<byte[]> response = sendWithRetry(
      buildRawGet(url, pat), HttpResponse.BodyHandlers.ofByteArray()
    );

    if (response.statusCode() == 404) {
      throw new GitAdapterException(404,
        "File not found at " + or.owner() + "/" + or.repo() + ":" + ref + "/" + path +
        " — verify the repoUrl / ref / path are correct."
      );
    }
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new GitAdapterException(response.statusCode(),
        "Repository not found or access denied — verify your PAT scope: " +
        "classic PATs need `repo` (private) or `public_repo`; fine-grained PATs need " +
        "Contents: Read for " + parsed.host() + "."
      );
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new GitAdapterException(response.statusCode(),
        "GitHub returned status " + response.statusCode() + " for contents — " +
        "retry later or contact the GitHub admin."
      );
    }

    byte[] body = response.body();
    Long byteSize = response.headers().firstValueAsLong("Content-Length").stream().boxed().findFirst().orElse(
      body == null ? null : (long) body.length
    );
    String mimeType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
    int semi = mimeType.indexOf(';');
    if (semi >= 0) mimeType = mimeType.substring(0, semi).trim();
    if (mimeType.isEmpty()) mimeType = "application/octet-stream";

    // The raw response does not expose the commit SHA — resolve it
    // separately. (The `etag` carries the blob SHA, not what
    // FileResolution.sha is documented to mean.)
    String sha = resolveRef(repoUrl, ref, pat);

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
    OwnerRepo or = splitOwnerRepo(parsed);
    String apiBase = apiBase(parsed.host(), parsed.port());
    String url = apiBase +
      COMMITS_PATH.formatted(urlEncode(or.owner()), urlEncode(or.repo()), urlEncode(ref));

    HttpResponse<byte[]> response = sendWithRetry(
      buildJsonGet(url, pat), HttpResponse.BodyHandlers.ofByteArray()
    );
    if (response.statusCode() == 404) {
      throw new GitAdapterException(404,
        "Branch / tag " + ref + " not found on " + or.owner() + "/" + or.repo() +
        " — verify the ref exists."
      );
    }
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new GitAdapterException(response.statusCode(),
        "Repository not found or access denied — verify your PAT scope: " +
        "classic PATs need `repo` (private) or `public_repo`; fine-grained PATs need " +
        "Contents: Read for " + parsed.host() + "."
      );
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new GitAdapterException(response.statusCode(),
        "GitHub returned status " + response.statusCode() + " for commits/" + ref + " — " +
        "retry later or contact the GitHub admin."
      );
    }
    byte[] body = response.body();
    try {
      JsonNode tree = json.readTree(body == null ? new byte[0] : body);
      JsonNode id = tree.get("sha");
      if (id == null || !id.isTextual() || id.asText().isBlank()) {
        throw new GitAdapterException(502,
          "GitHub commits/" + ref + " response is missing the `sha` field"
        );
      }
      return id.asText();
    } catch (IOException ioe) {
      throw new GitAdapterException(502, "GitHub commits/" + ref + " response is not JSON", ioe);
    }
  }

  // ── HTTP plumbing ────────────────────────────────────────────────────────

  /**
   * Returns the API base URL for {@code host}, accommodating both
   * github.com (api.github.com) and GitHub Enterprise ({host}/api/v3).
   * For test servers (loopback or explicit port), defaults to the
   * Enterprise-style {@code /api/v3} so test routes match the production
   * Enterprise shape.
   */
  private String apiBase(String host, int port) {
    String authority = port < 0 ? host : host + ":" + port;
    if (host.equals("github.com")) {
      return scheme + "://api.github.com";
    }
    return scheme + "://" + authority + "/api/v3";
  }

  private HttpRequest buildRawGet(String url, String pat) {
    HttpRequest.Builder b = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .timeout(timeout != null ? timeout : Duration.ofSeconds(15))
      .header("Accept", "application/vnd.github.raw")
      .header("X-GitHub-Api-Version", "2022-11-28")
      .GET();
    if (pat != null && !pat.isBlank()) {
      b.header("Authorization", "Bearer " + pat);
    }
    return b.build();
  }

  private HttpRequest buildJsonGet(String url, String pat) {
    HttpRequest.Builder b = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .timeout(timeout != null ? timeout : Duration.ofSeconds(15))
      .header("Accept", "application/vnd.github+json")
      .header("X-GitHub-Api-Version", "2022-11-28")
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
      throw new GitAdapterException(0, "GitHub request failed: " + e.getMessage(), e);
    }
    if (response.statusCode() >= 500 && response.statusCode() < 600) {
      Log.debugf("GitHub returned 5xx (%d); retrying once after 250ms", response.statusCode());
      try {
        Thread.sleep(250);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new GitAdapterException(0, "GitHub retry interrupted", ie);
      }
      try {
        response = client.send(request, handler);
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        throw new GitAdapterException(0, "GitHub retry failed: " + e.getMessage(), e);
      }
    }
    return response;
  }

  private static OwnerRepo splitOwnerRepo(ParsedRepoUrl parsed) {
    String pp = parsed.projectPath();
    int slash = pp.indexOf('/');
    if (slash <= 0 || slash >= pp.length() - 1) {
      throw new GitAdapterException(400,
        "GitHub repo URL must be of the form https://" + parsed.host() + "/{owner}/{repo} — got: "
        + pp
      );
    }
    String owner = pp.substring(0, slash);
    String repo = pp.substring(slash + 1);
    if (repo.contains("/")) {
      throw new GitAdapterException(400,
        "GitHub repo URL must be of the form https://" + parsed.host() + "/{owner}/{repo} (no subgroups) — got: "
        + pp
      );
    }
    return new OwnerRepo(owner, repo);
  }

  private static String urlEncode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  /**
   * URL-encodes a file path segment-by-segment so {@code /} separators
   * are preserved (GitHub's contents endpoint interprets {@code %2F} as
   * a literal slash in the filename, which is wrong for nested paths).
   */
  private static String encodePath(String path) {
    StringBuilder out = new StringBuilder(path.length());
    String[] segs = path.split("/", -1);
    for (int i = 0; i < segs.length; i++) {
      if (i > 0) out.append('/');
      out.append(urlEncode(segs[i]));
    }
    return out.toString();
  }

  /** Tiny record-style carrier for the split repo URL. */
  private record OwnerRepo(String owner, String repo) {}
}
