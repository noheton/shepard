package de.dlr.shepard.context.references.git.adapters;

/**
 * Per-host git-API adapter contract.
 *
 * <p>{@code aidocs/38 §5} chooses small per-host REST adapters over JGit —
 * we only need to fetch one file + the commit metadata, not clone. v1
 * (G1b) ships {@code GitLabRestClient}; G1d adds GitHub + Gitea on the
 * same interface. {@code GitAdapterRegistry} picks the right adapter
 * for a given host.
 *
 * <p>The {@code pat} parameter is the cleartext PAT — adapters add the
 * {@code Authorization: Bearer …} header themselves. Callers
 * (the cache layer, the service layer) are responsible for not
 * persisting the cleartext anywhere outside the encrypted Neo4j
 * column (G1-cred).
 */
public interface GitAdapter {
  /**
   * @return {@code true} when this adapter handles {@code host}. Matching
   *         is host-substring based (e.g. any host containing "gitlab"
   *         is a GitLab adapter). Self-hosted GitLab instances behind a
   *         non-obvious hostname (e.g. {@code code.dlr.de}) can be opted
   *         in via the {@code shepard.git.adapter.gitlab.hosts}
   *         configuration knob.
   */
  boolean supports(String host);

  /**
   * @return human-readable adapter name (used in RFC 7807 problem
   *         responses + logs).
   */
  String name();

  /**
   * Fetches a single file at {@code (ref, path)} of {@code repoUrl}.
   *
   * @throws GitAdapterException with operator-readable message on failure.
   */
  FileResolution getFile(String repoUrl, String ref, String path, String pat);

  /**
   * Resolves a branch / tag name to its current commit SHA.
   *
   * @throws GitAdapterException with operator-readable message on failure.
   */
  String resolveRef(String repoUrl, String ref, String pat);

  /**
   * Dispatch ordering hint for {@link GitAdapterRegistry}. Lower wins
   * (more-specific first). Default is {@code 1000} (least specific) so
   * the GitLab substring matcher keeps acting as the fallback for any
   * unrecognised "*gitlab*" host that an existing operator already
   * relies on (G1b shipped before G1d).
   *
   * <p>The G1d adapters use:
   * <ul>
   *   <li>{@code 100} — GitHub (exact host or {@code *.github.com}; very narrow).</li>
   *   <li>{@code 200} — Gitea (substring match on {@code gitea}; narrow).</li>
   *   <li>{@code 1000} — GitLab (substring match on {@code gitlab}; broadest).</li>
   * </ul>
   * Both new adapters also honour their own host-allowlist config keys
   * ({@code shepard.git.adapter.{github,gitea}.hosts}) — those win
   * because the adapter is checked before the GitLab default.
   */
  default int priority() {
    return 1000;
  }
}
