package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class DbRecoverySchedulerTest {

  @SuppressWarnings("unchecked")
  private static Instance<DbPinger> instanceOf(DbPinger... pingers) {
    Instance<DbPinger> instance = mock(Instance.class);
    when(instance.iterator()).thenAnswer(inv -> List.of(pingers).iterator());
    return instance;
  }

  private static ReadinessConfig configWithStaleness(long ms) {
    ReadinessConfig cfg = mock(ReadinessConfig.class);
    when(cfg.maxStalenessMs()).thenReturn(ms);
    return cfg;
  }

  private static RequestContextController noOpController() {
    RequestContextController ctrl = mock(RequestContextController.class);
    when(ctrl.activate()).thenReturn(true);
    return ctrl;
  }

  private static DbRecoveryScheduler scheduler(Instance<DbPinger> pingers, ReadinessConfig cfg) {
    DbRecoveryScheduler s = new DbRecoveryScheduler();
    s.pingers = pingers;
    s.readinessConfig = cfg;
    s.requestContextController = noOpController();
    return s;
  }

  @Test
  public void stalePinger_isPinged() {
    DbHealthState state = new DbHealthState();
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("mongodb");
    when(p.isRequired()).thenReturn(true);
    when(p.state()).thenReturn(state);
    when(p.ping())
      .thenAnswer(inv -> {
        state.recordSuccess(3L);
        return true;
      });

    scheduler(instanceOf(p), configWithStaleness(30_000L)).attemptRecovery();

    verify(p).ping();
    assertTrue(state.hasEverBeenUp());
  }

  @Test
  public void freshPinger_isNotPinged() {
    DbHealthState state = new DbHealthState();
    state.recordSuccess(2L);
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("neo4j");
    when(p.isRequired()).thenReturn(true);
    when(p.state()).thenReturn(state);

    scheduler(instanceOf(p), configWithStaleness(60_000L)).attemptRecovery();

    verify(p, never()).ping();
  }

  @Test
  public void notRequiredPinger_isSkipped() {
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("postgis");
    when(p.isRequired()).thenReturn(false);

    scheduler(instanceOf(p), configWithStaleness(30_000L)).attemptRecovery();

    verify(p, never()).ping();
    verify(p, never()).state();
  }

  @Test
  public void pingerThatThrows_doesNotBlockSiblings() {
    DbPinger bad = mock(DbPinger.class);
    when(bad.name()).thenReturn("bad");
    when(bad.isRequired()).thenThrow(new RuntimeException("boom"));

    DbHealthState goodState = new DbHealthState();
    DbPinger good = mock(DbPinger.class);
    when(good.name()).thenReturn("good");
    when(good.isRequired()).thenReturn(true);
    when(good.state()).thenReturn(goodState);
    when(good.ping())
      .thenAnswer(inv -> {
        goodState.recordSuccess(1L);
        return true;
      });

    scheduler(instanceOf(bad, good), configWithStaleness(30_000L)).attemptRecovery();

    verify(good, times(1)).ping();
    assertTrue(goodState.hasEverBeenUp());
  }

  /**
   * Two stale pingers each sleeping ~300 ms.  Sequential execution would take
   * ≥ 600 ms; parallel execution should finish in < 600 ms (with margin).
   */
  @Test
  public void multipleStaleSlowPingers_runInParallel() throws Exception {
    final long delayMs = 300L;
    final int pingerCount = 2;
    CountDownLatch allStarted = new CountDownLatch(pingerCount);
    AtomicBoolean latchTimedOut = new AtomicBoolean(false);

    DbPinger[] ps = new DbPinger[pingerCount];
    for (int i = 0; i < pingerCount; i++) {
      final String dbName = "db-" + i;
      DbHealthState st = new DbHealthState(); // never had a success → stale
      DbPinger p = mock(DbPinger.class);
      when(p.name()).thenReturn(dbName);
      when(p.isRequired()).thenReturn(true);
      when(p.state()).thenReturn(st);
      when(p.ping()).thenAnswer(inv -> {
        allStarted.countDown();
        boolean ok = allStarted.await(5, TimeUnit.SECONDS);
        if (!ok) latchTimedOut.set(true);
        Thread.sleep(delayMs);
        st.recordSuccess(delayMs);
        return true;
      });
      ps[i] = p;
    }

    long t0 = System.currentTimeMillis();
    scheduler(instanceOf(ps), configWithStaleness(1L)).attemptRecovery(); // maxStaleness=1 ms → all stale
    long elapsed = System.currentTimeMillis() - t0;

    assertFalse(latchTimedOut.get(), "All virtual threads should have started before the latch timed out");

    long sumMs = delayMs * pingerCount;
    // Allow up to 1.5× single probe + overhead — well under the sequential sum
    long upperBoundMs = delayMs * 3 / 2 + 500;
    assertTrue(
      elapsed < upperBoundMs,
      "Parallel recovery should complete in < " + upperBoundMs + " ms (sum would be " + sumMs + " ms), but took " + elapsed + " ms"
    );
  }

  @Test
  public void requestContextIsActivatedAndDeactivatedPerPinger() {
    DbHealthState state = new DbHealthState();
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("timescaledb");
    when(p.isRequired()).thenReturn(true);
    when(p.state()).thenReturn(state);
    when(p.ping()).thenAnswer(inv -> {
      state.recordSuccess(1L);
      return true;
    });

    RequestContextController ctrl = mock(RequestContextController.class);
    when(ctrl.activate()).thenReturn(true);

    DbRecoveryScheduler s = new DbRecoveryScheduler();
    s.pingers = instanceOf(p);
    s.readinessConfig = configWithStaleness(1L); // 1 ms → pinger is stale
    s.requestContextController = ctrl;

    s.attemptRecovery();

    verify(ctrl).activate();
    verify(ctrl).deactivate();
  }
}
