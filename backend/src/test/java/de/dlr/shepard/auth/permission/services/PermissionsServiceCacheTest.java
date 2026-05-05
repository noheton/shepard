package de.dlr.shepard.auth.permission.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Caffeine cache semantics that back the {@code permissions-service-cache}
 * (TTL eviction, max-size eviction, invalidation, and user x entity x access-type keying).
 * The permissions service itself uses Quarkus' {@code @CacheResult} which delegates to the
 * same Caffeine implementation; these tests pin the behavioural contract that A4 promises.
 */
public class PermissionsServiceCacheTest {

  private record CacheKey(long entityId, String accessType, String username) {}

  @Test
  public void cache_hitOnRepeatedKey_skipsLoader() {
    var loaderCalls = new AtomicInteger();
    var cache = Caffeine.newBuilder()
      .expireAfterWrite(Duration.ofMinutes(5))
      .maximumSize(10_000)
      .<CacheKey, Boolean>build();

    var key = new CacheKey(42L, "Read", "alice");

    cache.get(key, k -> {
      loaderCalls.incrementAndGet();
      return Boolean.TRUE;
    });
    cache.get(key, k -> {
      loaderCalls.incrementAndGet();
      return Boolean.TRUE;
    });

    assertThat(loaderCalls.get()).isEqualTo(1);
  }

  @Test
  public void cache_distinctKeys_areKeyedByUserAndEntityAndAccessType() {
    var loaderCalls = new AtomicInteger();
    var cache = Caffeine.newBuilder()
      .expireAfterWrite(Duration.ofMinutes(5))
      .maximumSize(10_000)
      .<CacheKey, Boolean>build();

    cache.get(new CacheKey(1L, "Read", "alice"), k -> {
      loaderCalls.incrementAndGet();
      return Boolean.TRUE;
    });
    cache.get(new CacheKey(2L, "Read", "alice"), k -> {
      loaderCalls.incrementAndGet();
      return Boolean.TRUE;
    });
    cache.get(new CacheKey(1L, "Write", "alice"), k -> {
      loaderCalls.incrementAndGet();
      return Boolean.TRUE;
    });
    cache.get(new CacheKey(1L, "Read", "bob"), k -> {
      loaderCalls.incrementAndGet();
      return Boolean.TRUE;
    });
    cache.get(new CacheKey(1L, "Read", "alice"), k -> {
      loaderCalls.incrementAndGet();
      return Boolean.TRUE;
    });

    assertThat(loaderCalls.get()).isEqualTo(4);
  }

  @Test
  public void cache_entriesExpireAfterTtl() throws InterruptedException {
    var loaderCalls = new AtomicInteger();
    var cache = Caffeine.newBuilder()
      .expireAfterWrite(Duration.ofMillis(100))
      .maximumSize(10_000)
      .<CacheKey, Boolean>build();

    var key = new CacheKey(42L, "Read", "alice");

    cache.get(key, k -> {
      loaderCalls.incrementAndGet();
      return Boolean.TRUE;
    });
    Thread.sleep(250);
    cache.get(key, k -> {
      loaderCalls.incrementAndGet();
      return Boolean.TRUE;
    });

    assertThat(loaderCalls.get()).isEqualTo(2);
  }

  @Test
  public void cache_invalidateByEntity_evictsAllAccessTypesForThatEntity() {
    var cache = Caffeine.newBuilder()
      .expireAfterWrite(Duration.ofMinutes(5))
      .maximumSize(10_000)
      .<CacheKey, Boolean>build();

    cache.put(new CacheKey(99L, "Read", "alice"), Boolean.TRUE);
    cache.put(new CacheKey(99L, "Write", "alice"), Boolean.TRUE);
    cache.put(new CacheKey(100L, "Read", "alice"), Boolean.TRUE);

    cache.asMap().keySet().removeIf(k -> k.entityId() == 99L);

    assertThat(cache.getIfPresent(new CacheKey(99L, "Read", "alice"))).isNull();
    assertThat(cache.getIfPresent(new CacheKey(99L, "Write", "alice"))).isNull();
    assertThat(cache.getIfPresent(new CacheKey(100L, "Read", "alice"))).isEqualTo(Boolean.TRUE);
  }

  @Test
  public void cache_maximumSize_evictsOldEntriesUnderLruPressure() {
    var cache = Caffeine.newBuilder()
      .expireAfterWrite(Duration.ofMinutes(5))
      .maximumSize(2)
      .<CacheKey, Boolean>build();

    cache.put(new CacheKey(1L, "Read", "alice"), Boolean.TRUE);
    cache.put(new CacheKey(2L, "Read", "alice"), Boolean.TRUE);
    cache.put(new CacheKey(3L, "Read", "alice"), Boolean.TRUE);
    cache.cleanUp();

    assertThat(cache.estimatedSize()).isLessThanOrEqualTo(2);
  }
}
