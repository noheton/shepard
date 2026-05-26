package de.dlr.shepard.auth.users.services;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PROV-USER-ENRICH — short-lived in-process cache for resolved
 * {@code :MirroredUser} {@code appId} values keyed by
 * {@code (sourceInstance, sourceUsername)}.
 *
 * <p>Exists to prevent the N-per-importer-pass Neo4j MERGE storms that
 * would occur if {@code ProvenanceCaptureFilter} performed a live upsert
 * on every single write request. The MFFD v16 importer sends hundreds of
 * requests per second; each carrying the same {@code X-Source-User-*}
 * headers. Without caching the first upsert result, each request would
 * trigger an independent OGM session round-trip.
 *
 * <p><b>TTL:</b> 5 minutes. Entries expire on access (lazy eviction):
 * the first access after expiry triggers a live upsert and refreshes
 * the entry. Entries that are never re-accessed accumulate at most
 * until the singleton is restarted, which is acceptable given the small
 * key cardinality in practice (one entry per distinct source user per
 * run).
 *
 * <p>Thread-safety: {@link ConcurrentHashMap} provides the necessary
 * visibility guarantees. No additional locking is needed because the
 * upsert at miss time is idempotent — two concurrent threads racing on
 * the same key will both upsert and the second write is a no-op at the
 * graph layer (the DAO's MERGE pattern preserves the existing appId).
 */
@ApplicationScoped
public class MirroredUserEnrichmentCache {

  private static final long TTL_MS = 5L * 60 * 1000;

  /**
   * Internal entry — pairs the resolved {@code appId} with the wall-clock
   * milliseconds at which it was stored.
   */
  private record Entry(String mirroredUserAppId, long cachedAtMs) {}

  private final Map<String, Entry> cache = new ConcurrentHashMap<>();

  /**
   * Look up the cached {@code appId} for the given composite key.
   *
   * @param sourceInstance base URL of the source Shepard instance
   * @param sourceUsername username on the source instance
   * @return the cached {@code appId}, or {@link Optional#empty()} if absent
   *         or expired
   */
  public Optional<String> get(String sourceInstance, String sourceUsername) {
    String key = cacheKey(sourceInstance, sourceUsername);
    Entry entry = cache.get(key);
    if (entry == null) return Optional.empty();
    if (System.currentTimeMillis() - entry.cachedAtMs() > TTL_MS) {
      cache.remove(key);
      return Optional.empty();
    }
    return Optional.of(entry.mirroredUserAppId());
  }

  /**
   * Store a resolved {@code appId} for the given composite key.
   *
   * @param sourceInstance    base URL of the source Shepard instance
   * @param sourceUsername    username on the source instance
   * @param mirroredUserAppId the {@code appId} to cache
   */
  public void put(String sourceInstance, String sourceUsername, String mirroredUserAppId) {
    cache.put(cacheKey(sourceInstance, sourceUsername), new Entry(mirroredUserAppId, System.currentTimeMillis()));
  }

  /** Composite cache key — tab-separated to avoid collisions. */
  private static String cacheKey(String sourceInstance, String sourceUsername) {
    return sourceInstance + "\t" + sourceUsername;
  }

  /** Visible for testing — returns the current cache size. */
  int size() {
    return cache.size();
  }

  /** Visible for testing — wipes all entries. */
  void clear() {
    cache.clear();
  }
}
