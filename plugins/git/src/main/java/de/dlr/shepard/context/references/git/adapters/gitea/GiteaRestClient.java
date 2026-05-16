package de.dlr.shepard.context.references.git.adapters.gitea;

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
 * Gitea v1 REST adapter for the G1d tracked-artifact slice
 * ({@code aidocs/38 §5} + {@code aidocs/63} ADR-0021). Java 21
 * {@link HttpClient}; no new HTTP deps. Mirrors the
 * {@code GitLabRestClient} shape.
 *
 * <p>Endpoints used (per
 * <a href="https://docs.gitea.com/api/1.20/">Gitea API</a>):
 * <ul>
 *   <li>{@code GET /api/v1/repos/{owner}/{repo}/raw/{path}?ref={ref}}
 *       — single-file raw fetch.</li>
 *   <li>{@code GET /api/v1/repos/{owner}/{repo}/commits/{ref}} —
 *       resolves a branch / tag to its commit SHA.</li>
 * </ul>
 *
 * <p>Auth: {@code Authorization: token {pat}} (Gitea convention; the
 * canonical header form named in Gitea's docs alongside the
 * {@code Sudo} / cookie variants). Forgejo accepts the same header,
 * so this adapter also supports Forgejo hosts via the
 * {@code shepard.git.adapter.gitea.hosts} allowlist.
 *
 * <p>Retry: 5xx is retried once with a 250ms back-off, then surfaces
 * as {@link GitAdapterException}. 4xx is non-retryable.
 */
@ApplicationScoped
public class GiteaRestClient implements GitAdapter {

  /** Raw file fetch — returns the body verbatim. */
  private static final String RAW_PATH = "/api/v1/repos/%s/%s/raw/%s";

  /** {@code commits/{ref}} returns a JSON array of one commit; we pick the first {@code sha}. */
  private static final String COMMITS_PATH = "/api/v1/repos/%s/%s/commits/%s";

  /** Adapter id surfaced in problem responses + logs. */
  public static final String NAME = "gitea";

  /**
   * Priority for {@link de.dlr.shepard.context.references.git.adapters.GitAdapterRegistry}
   * dispatch ordering. Lower = checked first; Gitea sits between GitHub
   * (most specific) and GitLab (broadest substring matcher). See ADR-0021.
   */
  public static final int PRIORITY = 200;

  @ConfigProperty(name = "shepard.git.adapter.timeout", defaultValue = "PT15S")
  Duration timeout;

  /**
   * Comma-separated extra hostnames whose traffic should be routed to this
   * adapter, on top of the default "any host containing 'gitea'" rule.
   * Useful for self-hosted Gitea / Forgejo behind a non-obvious DNS
   * name (e.g. {@code code.dlr.de} or {@code codeberg.org}).
   */
  @ConfigProperty(name = "shepard.git.adapter.gitea.hosts", defaultValue = "")
  Optional<String> extraHosts;

  @ConfigProperty(name = "shepard.git.preview.max-bytes", defaultValue = "1048576")
  long maxBytes;

  /**
   * Scheme used for built URLs. {@code https} in production; {@code http}
   * is a test-only knob.
   */
  String scheme = "https";

  private final ObjectMapper json = new ObjectMapper();

  /** Default constructor for CDI. */
  public GiteaRestClient() {}

  /** Test seam — build a fresh HttpClient honouring the configured timeout. */
  HttpClient httpClient() {
    return HttpClient.newBuilder()
      .connectTimeout(timeout != null ? timeout : Duration.ofSeconds(15))
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();
  }

  /** Package-visible setter used by tests to swap the timeout. */
  GiteaRestClient withTimeout(Duration t) {
    this.timeout = t;
    return this;
  }

  /** Package-visible setter used by tests to widen the host allow-list. */
  GiteaRestClient withExtraHosts(String csv) {
    this.extraHosts = Optional.ofNullable(csv);
    return this;
  }

  /** Package-visible setter used by tests to lower the byte cap. */
  GiteaRestClient withMaxBytes(long max) {
    this.maxBytes = max;
    return this;
  }

  /** Package-visible setter used by tests to point at an http test server. */
  GiteaRestClient withScheme(String s) {
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
    if (h.contains("gitea")) return true;
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
    String url = scheme + "://" + parsed.authority() +
      RAW_PATH.formatted(urlEncode(or.owner()), urlEncode(or.repo()), encodePath(path)) +
      "?ref=" + urlEncode(ref);

    HttpResponse<byte[]> response = sendWithRetry(
      buildGet(url, pat), HttpResponse.BodyHandlers.ofByteArray()
    );

    if (response.statusCode() == 404) {
      throw new GitAdapterException(404,
        "File not found at " + or.owner() + "/" + or.repo() + ":" + ref + "/" + path +
        " — verify the repoUrl / ref / path are correct."
      );
    }
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new GitAdapterException(response.statusCode(),
        "Repository not found or access denied — verify your PAT has the " +
        "`read:repository` scope (Gitea/Forgejo) for " + parsed.host() + "."
      );
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new GitAdapterException(response.statusCode(),
        "Gitea returned status " + response.statusCode() + " for raw — " +
        "retry later or contact the Gitea admin."
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

    // Gitea's raw endpoint surfaces the commit SHA in
    // `X-Gitea-Commit-Id` on recent releases; if absent, fall back
    // to the commits/{ref} endpoint just like GitLab's adapter.
    String sha = response.headers().firstValue("x-gitea-commit-id").orElse(null);
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
    OwnerRepo or = splitOwnerRepo(parsed);
    String url = scheme + "://" + parsed.authority() +
      COMMITS_PATH.formatted(urlEncode(or.owner()), urlEncode(or.repo()), urlEncode(ref));

    HttpResponse<byte[]> response = sendWithRetry(
      buildGet(url, pat), HttpResponse.BodyHandlers.ofByteArray()
    );
    if (response.statusCode() == 404) {
      throw new GitAdapterException(404,
        "Branch / tag " + ref + " not found on " + or.owner() + "/" + or.repo() +
        " — verify the ref exists."
      );
    }
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new GitAdapterException(response.statusCode(),
        "Repository not found or access denied — verify your PAT has the " +
        "`read:repository` scope (Gitea/Forgejo) for " + parsed.host() + "."
      );
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new GitAdapterException(response.statusCode(),
        "Gitea returned status " + response.statusCode() + " for commits/" + ref + " — " +
        "retry later or contact the Gitea admin."
      );
    }
    byte[] body = response.body();
    try {
      JsonNode tree = json.readTree(body == null ? new byte[0] : body);
      // Gitea's commits/{ref} response is an object with `sha`; older
      // builds returned an array — accept both.
      JsonNode shaNode;
      if (tree.isArray() && tree.size() > 0) {
        shaNode = tree.get(0).get("sha");
      } else {
        shaNode = tree.get("sha");
      }
      if (shaNode == null || !shaNode.isTextual() || shaNode.asText().isBlank()) {
        throw new GitAdapterException(502,
          "Gitea commits/" + ref + " response is missing the `sha` field"
        );
      }
      return shaNode.asText();
    } catch (IOException ioe) {
      throw new GitAdapterException(502, "Gitea commits/" + ref + " response is not JSON", ioe);
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
      b.header("Authorization", "token " + pat);
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
      throw new GitAdapterException(0, "Gitea request failed: " + e.getMessage(), e);
    }
    if (response.statusCode() >= 500 && response.statusCode() < 600) {
      Log.debugf("Gitea returned 5xx (%d); retrying once after 250ms", response.statusCode());
      try {
        Thread.sleep(250);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new GitAdapterException(0, "Gitea retry interrupted", ie);
      }
      try {
        response = client.send(request, handler);
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        throw new GitAdapterException(0, "Gitea retry failed: " + e.getMessage(), e);
      }
    }
    return response;
  }

  private static OwnerRepo splitOwnerRepo(ParsedRepoUrl parsed) {
    String pp = parsed.projectPath();
    int slash = pp.indexOf('/');
    if (slash <= 0 || slash >= pp.length() - 1) {
      throw new GitAdapterException(400,
        "Gitea repo URL must be of the form https://" + parsed.host() + "/{owner}/{repo} — got: "
        + pp
      );
    }
    String owner = pp.substring(0, slash);
    String repo = pp.substring(slash + 1);
    if (repo.contains("/")) {
      throw new GitAdapterException(400,
        "Gitea repo URL must be of the form https://" + parsed.host() + "/{owner}/{repo} (no subgroups) — got: "
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
   * are preserved (Gitea's raw endpoint interprets {@code %2F} as a
   * literal slash in the filename, which is wrong for nested paths).
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
