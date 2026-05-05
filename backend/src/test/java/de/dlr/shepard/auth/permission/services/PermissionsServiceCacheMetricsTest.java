package de.dlr.shepard.auth.permission.services;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Metrics;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.cache.CompositeCacheKey;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

// @QuarkusTest boots the full stack (Neo4j + MongoDB) and hangs in
// MigrationsRunner.awaitConnectivity when those aren't running.
// @QuarkusComponentTest can't observe Micrometer cache meter
// registration because it doesn't run the quarkus-cache lifecycle.
// Tracking conversion to a HealthzIT-style integration test (which
// runs against live DBs in CI) as backlog A4e.
@Disabled("A4e: convert to integration test (needs live Neo4j + MongoDB)")
@QuarkusTest
@TestProfile(PermissionsServiceCacheMetricsTest.MetricsProfile.class)
public class PermissionsServiceCacheMetricsTest {

  public static class MetricsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.ofEntries(
        // Test-only OIDC public key so the JWTFilter producer can resolve.
        Map.entry(
          "oidc.public",
          "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiAxFyffvM0oiga3h2E7XpHtJvu1vTodrn9Y426FOv80YJcMwPkaI5tXY5hnLjgOwsVNSBv9wAhLL4bUfP+TVhdg4dijD2H/3FamheQaPmduimytzQjlHIIfFuZidH12ZyUrOWfDxiHiRFQ3Dd8dlS7MbIsWt/qBIg16ZZazTJTiaSyP/qH305x9iRjrtGRmvE2VMOdc5EhujFMJnQWWgwOnv2C9U9KIchkPCz+TAL4kKJ79BUi4b0+jxL5Cbgyt0bMo27Zx0zQjU7f0ynFIllqZ6new3Q8HYbr4AIkca4pMjfKWrTHkrQBL2cEXHLIHt86C17goKteToqDjphkwImwIDAQAB"
        ),
        // Disable DB integrations that require live infra.
        Map.entry("quarkus.flyway.migrate-at-start", "false"),
        Map.entry("quarkus.flyway.spatial.active", "false"),
        Map.entry("quarkus.hibernate-orm.active", "false"),
        Map.entry("quarkus.hibernate-orm.spatial.active", "false"),
        Map.entry("quarkus.datasource.devservices.enabled", "false"),
        Map.entry("quarkus.datasource.spatial.devservices.enabled", "false"),
        Map.entry("quarkus.mongodb.devservices.enabled", "false"),
        Map.entry("shepard.spatial-data.enabled", "false")
      );
    }
  }


  @Inject
  @CacheName("permissions-service-cache")
  Cache cache;

  @Test
  public void permissionsCacheEmitsGetsMeter() {
    var key = new CompositeCacheKey(1L, "Read", "alice");

    // 1 miss (loader runs) + 2 hits (loader does NOT run)
    cache.get(key, k -> Boolean.TRUE).await().indefinitely();
    cache.get(key, k -> Boolean.TRUE).await().indefinitely();
    cache.get(key, k -> Boolean.TRUE).await().indefinitely();

    double gets = Metrics.globalRegistry
      .getMeters()
      .stream()
      .filter(m -> m.getId().getName().equals("cache.gets"))
      .filter(m -> "permissions-service-cache".equals(m.getId().getTag("cache")))
      .mapToDouble(m -> {
        double count = 0d;
        for (Measurement measurement : m.measure()) {
          count += measurement.getValue();
        }
        return count;
      })
      .sum();

    assertThat(gets).isGreaterThan(0d);
  }
}
