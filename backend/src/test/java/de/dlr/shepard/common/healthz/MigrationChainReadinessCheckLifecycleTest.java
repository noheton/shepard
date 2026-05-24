package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@code @PostConstruct} / {@code @PreDestroy} surfaces
 * of {@link MigrationChainReadinessCheck} that the live-extractor
 * test in {@link MigrationChainReadinessCheckTest} can't exercise
 * without a real Neo4j.
 *
 * <p>{@code init()} reads MicroProfile config and attempts a driver
 * handshake — when the keys are absent (as in a unit-test JVM that
 * doesn't load {@code application.properties}), the {@code Exception}
 * branch should fire and leave {@code migrations} null without
 * propagating. {@code close()} should be a no-op when the driver was
 * never created.
 *
 * <p>The full live-CDI integration (`@QuarkusTest @QuarkusTestResource
 * (ShepardTestStack.class)`) for this bean is covered transitively by
 * the smoke suite that hits {@code /shepard/api/healthz/ready} on
 * every backend image build — a green smoke is the end-to-end proof.
 */
public class MigrationChainReadinessCheckLifecycleTest {

  @Test
  public void init_swallowsConfigMissingError_leavesMigrationsNull() {
    MigrationChainReadinessCheck check = new MigrationChainReadinessCheck();
    // Without MicroProfile config loaded for neo4j.username/password/host,
    // init() must catch the resulting NoSuchElementException and return
    // cleanly. A propagated exception here would block backend startup.
    assertDoesNotThrow(check::init);
  }

  @Test
  public void close_isSafeWhenDriverNeverCreated() {
    MigrationChainReadinessCheck check = new MigrationChainReadinessCheck();
    // No init() was called → driver is null → close() must not NPE.
    assertDoesNotThrow(check::close);
  }

  @Test
  public void call_afterFailedInit_returnsDownWithCheckFailed() {
    // Simulate a backend startup where init() failed silently
    // (config missing, driver creation threw). The readiness probe
    // should DOWN with CHECK_FAILED, not crash.
    MigrationChainReadinessCheck check = new MigrationChainReadinessCheck();
    check.init(); // no config → migrations stays null
    // Plug in just the ReadinessConfig the call() path needs.
    ReadinessConfig cfg = mock(ReadinessConfig.class);
    when(cfg.maxStalenessMs()).thenReturn(30_000L);
    check.readinessConfig = cfg;

    HealthCheckResponse resp = check.call();

    assertEquals(HealthCheckResponse.Status.DOWN, resp.getStatus());
    assertEquals("CHECK_FAILED", resp.getData().orElseThrow().get("outcome"));
    assertNotNull(resp.getData().orElseThrow().get("errorMessage"));
  }
}
