package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import java.util.List;
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

  private static DbRecoveryScheduler scheduler(Instance<DbPinger> pingers, ReadinessConfig cfg) {
    DbRecoveryScheduler s = new DbRecoveryScheduler();
    s.pingers = pingers;
    s.readinessConfig = cfg;
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
}
