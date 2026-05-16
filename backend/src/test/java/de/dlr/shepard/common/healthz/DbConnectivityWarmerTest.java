package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DbConnectivityWarmer}.
 *
 * <p>All tests use manual dependency injection (no CDI container) so they run
 * fast in CI.  The parallel-timing test inserts artificial latency in each
 * pinger and asserts that total elapsed time is closer to {@code max(latencies)}
 * than {@code sum(latencies)}.
 */
public class DbConnectivityWarmerTest {

  @SuppressWarnings("unchecked")
  private static Instance<DbPinger> instanceOf(DbPinger... ps) {
    Instance<DbPinger> inst = mock(Instance.class);
    when(inst.iterator()).thenAnswer(inv -> List.of(ps).iterator());
    return inst;
  }

  private static RequestContextController noOpController() {
    RequestContextController ctrl = mock(RequestContextController.class);
    when(ctrl.activate()).thenReturn(true);
    return ctrl;
  }

  private static DbConnectivityWarmer warmer(
    Instance<DbPinger> pingers,
    RequestContextController ctrl
  ) {
    DbConnectivityWarmer w = new DbConnectivityWarmer();
    w.pingers = pingers;
    w.requestContextController = ctrl;
    return w;
  }

  // ── basic functional tests ───────────────────────────────────────────────

  @Test
  public void noPingers_completesImmediately() throws Exception {
    DbConnectivityWarmer w = warmer(instanceOf(), noOpController());
    w.onStart(new StartupEvent());
    w.warmingFuture().get(2, TimeUnit.SECONDS);
  }

  @Test
  public void requiredPingers_arePinged() throws Exception {
    DbHealthState state = new DbHealthState();
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("mongodb");
    when(p.isRequired()).thenReturn(true);
    when(p.state()).thenReturn(state);
    when(p.ping()).thenAnswer(inv -> {
      state.recordSuccess(2L);
      return true;
    });

    DbConnectivityWarmer w = warmer(instanceOf(p), noOpController());
    w.onStart(new StartupEvent());
    w.warmingFuture().get(5, TimeUnit.SECONDS);

    verify(p).ping();
    assertTrue(state.hasEverBeenUp());
  }

  @Test
  public void notRequiredPinger_isSkipped() throws Exception {
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("postgis");
    when(p.isRequired()).thenReturn(false);

    DbConnectivityWarmer w = warmer(instanceOf(p), noOpController());
    w.onStart(new StartupEvent());
    w.warmingFuture().get(2, TimeUnit.SECONDS);

    verify(p, never()).ping();
  }

  @Test
  public void pingThatThrows_doesNotBlockSiblings() throws Exception {
    // bad pinger throws on ping()
    DbPinger bad = mock(DbPinger.class);
    when(bad.name()).thenReturn("broken");
    when(bad.isRequired()).thenReturn(true);
    when(bad.state()).thenReturn(new DbHealthState());
    when(bad.ping()).thenThrow(new RuntimeException("db totally gone"));

    // good pinger should still be probed
    DbHealthState goodState = new DbHealthState();
    DbPinger good = mock(DbPinger.class);
    when(good.name()).thenReturn("timescaledb");
    when(good.isRequired()).thenReturn(true);
    when(good.state()).thenReturn(goodState);
    when(good.ping()).thenAnswer(inv -> {
      goodState.recordSuccess(1L);
      return true;
    });

    DbConnectivityWarmer w = warmer(instanceOf(bad, good), noOpController());
    w.onStart(new StartupEvent());
    w.warmingFuture().get(5, TimeUnit.SECONDS);

    verify(good).ping();
    assertTrue(goodState.hasEverBeenUp());
  }

  @Test
  public void downPinger_doesNotMarkRegistryDown() throws Exception {
    // a DOWN result from ping() only affects the DbHealthState of that pinger
    DbHealthState state = new DbHealthState();
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("neo4j");
    when(p.isRequired()).thenReturn(true);
    when(p.state()).thenReturn(state);
    when(p.ping()).thenAnswer(inv -> {
      state.recordFailure(10L, new RuntimeException("connection refused"));
      return false;
    });

    DbConnectivityWarmer w = warmer(instanceOf(p), noOpController());
    w.onStart(new StartupEvent());
    w.warmingFuture().get(5, TimeUnit.SECONDS);

    assertFalse(state.hasEverBeenUp(), "DbHealthState should remain DOWN when ping fails");
  }

  // ── parallelism timing test ──────────────────────────────────────────────

  /**
   * Three pingers each sleeping ~300 ms.  If sequential, total ≥ 900 ms.
   * If truly parallel, total ≤ ~600 ms (generous margin for CI load).
   */
  @Test
  public void multipleSlowPingers_runInParallel() throws Exception {
    final long delayMs = 300L;
    final int pingerCount = 3;
    // latch so all virtual threads start their sleep before any finishes
    CountDownLatch allStarted = new CountDownLatch(pingerCount);
    AtomicBoolean latchTimedOut = new AtomicBoolean(false);

    DbPinger[] ps = new DbPinger[pingerCount];
    for (int i = 0; i < pingerCount; i++) {
      final String dbName = "db-" + i;
      DbHealthState st = new DbHealthState();
      DbPinger p = mock(DbPinger.class);
      when(p.name()).thenReturn(dbName);
      when(p.isRequired()).thenReturn(true);
      when(p.state()).thenReturn(st);
      when(p.ping()).thenAnswer(inv -> {
        allStarted.countDown();
        // wait for all threads to start (proves concurrency), then sleep
        boolean ok = allStarted.await(5, TimeUnit.SECONDS);
        if (!ok) latchTimedOut.set(true);
        Thread.sleep(delayMs);
        st.recordSuccess(delayMs);
        return true;
      });
      ps[i] = p;
    }

    DbConnectivityWarmer w = warmer(instanceOf(ps), noOpController());
    long t0 = System.currentTimeMillis();
    w.onStart(new StartupEvent());
    w.warmingFuture().get(10, TimeUnit.SECONDS);
    long elapsed = System.currentTimeMillis() - t0;

    assertFalse(latchTimedOut.get(), "All virtual threads should have started before the latch timed out");

    long sumMs = delayMs * pingerCount;
    long maxMs = delayMs;
    // Generous upper bound: allow up to 1.5× the max single-probe latency for CI overhead
    long upperBoundMs = maxMs * 3 / 2 + 500;
    assertTrue(
      elapsed < upperBoundMs,
      "Parallel warm should complete in < " + upperBoundMs + " ms (sum would be " + sumMs + " ms), but took " + elapsed + " ms"
    );
  }

  // ── request-context activation ───────────────────────────────────────────

  @Test
  public void requestContextIsActivatedAndDeactivated() throws Exception {
    DbHealthState st = new DbHealthState();
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("timescaledb");
    when(p.isRequired()).thenReturn(true);
    when(p.state()).thenReturn(st);
    when(p.ping()).thenAnswer(inv -> {
      st.recordSuccess(1L);
      return true;
    });

    RequestContextController ctrl = mock(RequestContextController.class);
    when(ctrl.activate()).thenReturn(true);

    DbConnectivityWarmer w = warmer(instanceOf(p), ctrl);
    w.onStart(new StartupEvent());
    w.warmingFuture().get(5, TimeUnit.SECONDS);

    verify(ctrl).activate();
    verify(ctrl).deactivate();
  }
}
