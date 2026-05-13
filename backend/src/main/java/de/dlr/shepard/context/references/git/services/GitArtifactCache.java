package de.dlr.shepard.context.references.git.services;

import de.dlr.shepard.context.references.git.adapters.FileResolution;
import de.dlr.shepard.context.references.git.adapters.GitAdapter;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * {@code PT5M}-cached file fetch keyed by {@code (userSub, repoUrl, ref, path)}.
 * The {@code userSub} axis is mandatory — different users may have different
 * PATs that resolve the same {@code (repoUrl, ref, path)} differently in the
 * presence of private repositories.
 *
 * <p>TTL + max-size are wired in {@code application.properties}:
 * <ul>
 *   <li>{@code shepard.git.cache.ttl} (default {@code PT5M}).</li>
 *   <li>{@code shepard.git.cache.max-size} (default {@code 1000}).</li>
 * </ul>
 *
 * <p>The {@code pat} argument is intentionally **not** part of the cache key
 * — Quarkus' {@code @CacheResult} uses the method-parameter signature as
 * the key, and we use {@code @io.quarkus.cache.CacheKey} on the four key
 * fields so {@code pat} is delegated to the adapter without participating
 * in cache invalidation. PAT rotation is handled by the user deleting
 * the credential and re-adding it (the new sub-cache entry will
 * naturally miss); the old entries age out within {@code PT5M}.
 */
@ApplicationScoped
public class GitArtifactCache {

  /**
   * Cache name: {@code git-artifacts}. Tunable in
   * {@code application.properties} via the
   * {@code quarkus.cache.caffeine."git-artifacts".*} keys.
   *
   * @param adapter   per-host adapter to delegate to. Not part of the key —
   *                  for v1 (GitLab-only) the adapter is constant per host.
   * @param userSub   the caller's username (sub claim) — partitions the
   *                  cache so user A's PAT-result never leaks to user B.
   * @param repoUrl   git repository URL.
   * @param ref       branch / tag / "HEAD".
   * @param path      file path inside the repo.
   * @param pat       cleartext PAT — not part of the key.
   * @return the (cached) file resolution.
   */
  @CacheResult(cacheName = "git-artifacts")
  public FileResolution getFile(
    @io.quarkus.cache.CacheKey String userSub,
    @io.quarkus.cache.CacheKey String repoUrl,
    @io.quarkus.cache.CacheKey String ref,
    @io.quarkus.cache.CacheKey String path,
    GitAdapter adapter,
    String pat
  ) {
    return adapter.getFile(repoUrl, ref, path, pat);
  }
}
