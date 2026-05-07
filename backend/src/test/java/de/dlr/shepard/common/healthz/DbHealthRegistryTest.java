package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.inject.Instance;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DbHealthRegistryTest {

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

  private static DbHealthRegistry registry(Instance<DbPinger> pingers, ReadinessConfig cfg) {
    DbHealthRegistry r = new DbHealthRegistry();
    r.pingers = pingers;
    r.readinessConfig = cfg;
    return r;
  }

  @Test
  public void kindOf_mapsPingerNamesToEnum() {
    assertEquals(DatabaseKind.NEO4J, DbHealthRegistry.kindOf("neo4j"));
    assertEquals(DatabaseKind.MONGO, DbHealthRegistry.kindOf("mongodb"));
    assertEquals(DatabaseKind.TIMESCALE, DbHealthRegistry.kindOf("timescaledb"));
    assertEquals(DatabaseKind.SPATIAL, DbHealthRegistry.kindOf("postgis"));
    assertNull(DbHealthRegistry.kindOf("unknown"));
    assertNull(DbHealthRegistry.kindOf(null));
  }

  @Test
  public void pingerFor_returnsMatchingPingerByKind() {
    DbPinger postgis = mock(DbPinger.class);
    when(postgis.name()).thenReturn("postgis");
    DbPinger neo = mock(DbPinger.class);
    when(neo.name()).thenReturn("neo4j");

    DbHealthRegistry r = registry(instanceOf(postgis, neo), configWithStaleness(30_000L));
    assertSame(postgis, r.pingerFor(DatabaseKind.SPATIAL));
    assertSame(neo, r.pingerFor(DatabaseKind.NEO4J));
    assertNull(r.pingerFor(DatabaseKind.MONGO));
  }

  @Test
  public void isCurrentlyDown_falseForFreshRequiredPinger() {
    DbHealthState state = new DbHealthState();
    state.recordSuccess(2L);
    DbPinger neo = mock(DbPinger.class);
    when(neo.name()).thenReturn("neo4j");
    when(neo.isRequired()).thenReturn(true);
    when(neo.state()).thenReturn(state);

    DbHealthRegistry r = registry(instanceOf(neo), configWithStaleness(60_000L));
    assertFalse(r.isCurrentlyDown(DatabaseKind.NEO4J));
  }

  @Test
  public void isCurrentlyDown_trueForStaleRequiredPinger() {
    DbHealthState state = new DbHealthState();
    DbPinger neo = mock(DbPinger.class);
    when(neo.name()).thenReturn("neo4j");
    when(neo.isRequired()).thenReturn(true);
    when(neo.state()).thenReturn(state);

    DbHealthRegistry r = registry(instanceOf(neo), configWithStaleness(30_000L));
    assertTrue(r.isCurrentlyDown(DatabaseKind.NEO4J));
  }

  @Test
  public void isCurrentlyDown_falseWhenNotRequired() {
    DbPinger postgis = mock(DbPinger.class);
    when(postgis.name()).thenReturn("postgis");
    when(postgis.isRequired()).thenReturn(false);

    DbHealthRegistry r = registry(instanceOf(postgis), configWithStaleness(30_000L));
    assertFalse(r.isCurrentlyDown(DatabaseKind.SPATIAL));
  }

  @Test
  public void isCurrentlyDown_falseForUnknownKind() {
    DbHealthRegistry r = registry(instanceOf(), configWithStaleness(30_000L));
    assertFalse(r.isCurrentlyDown(DatabaseKind.MONGO));
  }
}
