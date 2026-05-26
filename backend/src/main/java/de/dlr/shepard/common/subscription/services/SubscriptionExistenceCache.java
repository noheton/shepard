package de.dlr.shepard.common.subscription.services;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * NEO-AUDIT-009 — short-circuit cache for the subscription hot-path.
 *
 * <p>{@link SubscriptionService} is {@code @RequestScoped} and therefore cannot
 * hold application-lifetime state itself. This {@code @ApplicationScoped}
 * companion bean holds the single volatile flag + timestamp so that
 * {@code SubscriptionFilter} (which fires on every authenticated write) can
 * skip the Neo4j index walk when there are no subscriptions at all.
 *
 * <p>Thread-safety: the two fields are {@code volatile} so visibility is
 * guaranteed across threads without a lock. A brief window exists between
 * {@code lastCheckAt} and {@code hasSubscriptions} writes, but the only
 * consequence is an extra DB round-trip, not a correctness failure.
 *
 * <p>Invalidation: {@link SubscriptionService} calls {@link #invalidate()} on
 * every {@code createSubscription} and {@code deleteSubscription} so the next
 * call rebuilds the flag immediately.
 *
 * <p>TTL: the cache is considered stale after {@link #TTL_MS} (60 s) even
 * without explicit invalidation, guarding against out-of-band DB mutations
 * (e.g. a direct Cypher admin fix) that do not go through the service layer.
 */
@ApplicationScoped
public class SubscriptionExistenceCache {

  /** Time-to-live in milliseconds (60 seconds). */
  static final long TTL_MS = 60_000L;

  /**
   * Epoch-ms timestamp of the last check, or {@code 0} if the cache has never
   * been populated or has been explicitly invalidated.
   */
  volatile long lastCheckAt = 0L;

  /**
   * Whether at least one subscription existed at the time of the last check.
   * Meaningful only when {@link #lastCheckAt} is non-zero and not stale.
   */
  volatile boolean hasSubscriptions = false;

  /**
   * Returns {@code true} when the cached value is still within the TTL window
   * and has not been explicitly invalidated.
   */
  public boolean isValid() {
    return lastCheckAt != 0L && (System.currentTimeMillis() - lastCheckAt) < TTL_MS;
  }

  /**
   * Returns the last-known {@code hasSubscriptions} flag.
   * Callers must check {@link #isValid()} first.
   */
  public boolean hasSubscriptions() {
    return hasSubscriptions;
  }

  /**
   * Store a fresh observation. Called by {@link SubscriptionService} after a
   * real DB query.
   *
   * @param hasAny whether the query returned at least one subscription
   */
  public void update(boolean hasAny) {
    hasSubscriptions = hasAny;
    lastCheckAt = System.currentTimeMillis();
  }

  /**
   * Force a re-check on the next call. Called by {@link SubscriptionService}
   * on every subscription create or delete.
   */
  public void invalidate() {
    lastCheckAt = 0L;
  }
}
