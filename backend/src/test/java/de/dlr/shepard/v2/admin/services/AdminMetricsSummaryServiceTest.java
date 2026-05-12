package de.dlr.shepard.v2.admin.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminMetricsSummaryServiceTest {

  MeterRegistry registry;
  AdminMetricsSummaryService service;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    service = new AdminMetricsSummaryService();
    service.registry = registry;
  }

  @Test
  void snapshotIncludesJvmAndUptime() {
    var snap = service.snapshot();
    assertTrue(snap.getJvmHeapUsedBytes() > 0);
    assertTrue(snap.getJvmHeapMaxBytes() >= snap.getJvmHeapUsedBytes());
    assertTrue(snap.getUptimeMillis() >= 0);
  }

  @Test
  void snapshotReturnsZerosWhenNoMetersRegistered() {
    var snap = service.snapshot();
    assertEquals(0L, snap.getHttpRequestsTotal());
    assertNull(snap.getHttpMeanRequestMillis());
    assertEquals(0L, snap.getPermissionsCacheHits());
    assertEquals(0L, snap.getPermissionsCacheMisses());
    assertNull(snap.getPermissionsCacheHitRatio());
  }

  @Test
  void snapshotAggregatesHttpTimersAcrossTags() {
    Timer t1 = Timer.builder("http.server.requests").tags(Tags.of("uri", "/foo")).register(registry);
    Timer t2 = Timer.builder("http.server.requests").tags(Tags.of("uri", "/bar")).register(registry);
    t1.record(java.time.Duration.ofMillis(100));
    t1.record(java.time.Duration.ofMillis(200));
    t2.record(java.time.Duration.ofMillis(300));

    var snap = service.snapshot();
    assertEquals(3L, snap.getHttpRequestsTotal());
    assertNotNull(snap.getHttpMeanRequestMillis());
    assertTrue(snap.getHttpMeanRequestMillis() > 150 && snap.getHttpMeanRequestMillis() < 250);
  }

  @Test
  void snapshotComputesPermissionsCacheHitRatio() {
    Counter hits = Counter.builder("cache.gets").tags("cache", "permissions-service-cache", "result", "hit").register(registry);
    Counter misses = Counter.builder("cache.gets").tags("cache", "permissions-service-cache", "result", "miss").register(registry);
    hits.increment(8);
    misses.increment(2);

    var snap = service.snapshot();
    assertEquals(8L, snap.getPermissionsCacheHits());
    assertEquals(2L, snap.getPermissionsCacheMisses());
    assertNotNull(snap.getPermissionsCacheHitRatio());
    assertEquals(0.8, snap.getPermissionsCacheHitRatio(), 1e-9);
  }

  @Test
  void snapshotIgnoresOtherCachesInRatio() {
    Counter otherHits = Counter.builder("cache.gets").tags("cache", "other-cache", "result", "hit").register(registry);
    otherHits.increment(100);
    var snap = service.snapshot();
    // No permissions-cache counters → no ratio.
    assertNull(snap.getPermissionsCacheHitRatio());
  }
}
