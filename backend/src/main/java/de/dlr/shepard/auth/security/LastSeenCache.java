package de.dlr.shepard.auth.security;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This cache allows to store keys along with the date of storage.
 * This can be used to check if e.g. an api key has been used in a recent period of time.
 *
 * To use this, extend this class, annotate the extending class with @ApplicationScoped and set the cacheExpiration.
 */
public abstract class LastSeenCache {

  private final Map<String, Date> cache;
  private final int cacheExpirationPeriodInMs;

  /**
   * @param cacheExpirationPeriodInMs period in ms when to expire cached keys
   */
  public LastSeenCache(int cacheExpirationPeriodInMs) {
    this.cacheExpirationPeriodInMs = cacheExpirationPeriodInMs;
    cache = new HashMap<>();
  }

  /**
   * @return true if the key has a cache entry that is not expired
   */
  public boolean isKeyCached(String key) {
    if (!cache.containsKey(key)) return false;

    var threshold = new Date(System.currentTimeMillis() - cacheExpirationPeriodInMs);
    return cache.get(key).after(threshold);
  }

  public void cacheKey(String key) {
    cache.put(key, new Date());
  }
}
