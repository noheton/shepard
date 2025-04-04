package de.dlr.shepard.auth.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LastSeenCacheImpl extends LastSeenCache {

  LastSeenCacheImpl(int cacheExpirationInMs) {
    super(cacheExpirationInMs);
  }
}

public class LastSeenCacheTest {

  @Test
  public void isKeyCached_keyNotCached_false() {
    LastSeenCache util = new LastSeenCacheImpl(1000);
    assertFalse(util.isKeyCached("Test"));
  }

  @Test
  public void isKeyCached_keyCached_true() {
    LastSeenCache util = new LastSeenCacheImpl(1000);
    util.cacheKey("Test");
    assertTrue(util.isKeyCached("Test"));
  }

  @Test
  public void isKeyCached_keyCachedButExpired_false() throws InterruptedException {
    LastSeenCache util = new LastSeenCacheImpl(1);
    util.cacheKey("Test");
    Thread.sleep(2);
    assertFalse(util.isKeyCached("Test"));
  }
}
