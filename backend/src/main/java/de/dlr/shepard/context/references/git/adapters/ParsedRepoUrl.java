package de.dlr.shepard.context.references.git.adapters;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Hostname + project path parsed out of a Git repo URL.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code https://gitlab.com/foo/bar} → {@code host=gitlab.com}, {@code projectPath=foo/bar}.</li>
 *   <li>{@code https://gitlab.example.dlr.de/group/sub/proj} →
 *       {@code host=gitlab.example.dlr.de}, {@code projectPath=group/sub/proj}.</li>
 *   <li>{@code https://gitlab.com/foo/bar.git} → trailing {@code .git} stripped.</li>
 * </ul>
 *
 * <p>Used by both the credential lookup (host-keyed PAT) and the GitLab API
 * URL builder (the {@code projectPath} is URL-encoded to form the
 * {@code /api/v4/projects/{projectId}/...} segment).
 */
public record ParsedRepoUrl(String host, int port, String projectPath) {
  /**
   * @throws GitAdapterException 400-class for malformed URLs.
   */
  public static ParsedRepoUrl parse(String repoUrl) {
    if (repoUrl == null || repoUrl.isBlank()) {
      throw new GitAdapterException(400, "repoUrl is required and must be non-blank");
    }
    URI uri;
    try {
      uri = new URI(repoUrl.trim());
    } catch (URISyntaxException e) {
      throw new GitAdapterException(400, "repoUrl is not a valid URI: " + e.getMessage(), e);
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new GitAdapterException(400, "repoUrl is missing a host component: " + repoUrl);
    }
    String rawPath = uri.getPath();
    if (rawPath == null || rawPath.isBlank() || rawPath.equals("/")) {
      throw new GitAdapterException(400, "repoUrl is missing a project path component: " + repoUrl);
    }
    // Strip leading slash and trailing ".git" — GitLab tolerates both forms.
    String projectPath = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
    if (projectPath.endsWith(".git")) {
      projectPath = projectPath.substring(0, projectPath.length() - ".git".length());
    }
    // Strip any trailing slash from "https://host/foo/bar/".
    while (projectPath.endsWith("/")) {
      projectPath = projectPath.substring(0, projectPath.length() - 1);
    }
    if (projectPath.isBlank()) {
      throw new GitAdapterException(400, "repoUrl project path is empty after stripping: " + repoUrl);
    }
    return new ParsedRepoUrl(host.toLowerCase(java.util.Locale.ROOT), uri.getPort(), projectPath);
  }

  /**
   * Authority for URL building: {@code host} on the default scheme port,
   * or {@code host:port} when a non-default port is set. Used by adapters
   * to preserve a custom port (mainly test servers; some self-hosted
   * GitLab instances also expose a non-443 port).
   */
  public String authority() {
    return port < 0 ? host : host + ":" + port;
  }
}
