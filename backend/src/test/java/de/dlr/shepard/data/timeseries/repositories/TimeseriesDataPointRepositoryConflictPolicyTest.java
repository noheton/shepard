package de.dlr.shepard.data.timeseries.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the overwrite-policy branch in
 * {@link TimeseriesDataPointRepository#insertManyDataPoints(List, TimeseriesEntity, boolean)}.
 *
 * <p>No live database required — the {@link EntityManager} is mocked so the tests
 * verify SQL clause selection and conflict-count arithmetic without a TimescaleDB fixture.
 *
 * <p>TS-CONFLICT-POLICY-1.
 */
public class TimeseriesDataPointRepositoryConflictPolicyTest {

  private TimeseriesDataPointRepository repository;
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    entityManager = mock(EntityManager.class);
    repository = new TimeseriesDataPointRepository();
    repository.entityManager = entityManager;
  }

  private static TimeseriesEntity entity(DataPointValueType type) {
    TimeseriesEntity e = new TimeseriesEntity(1L, "m", "f", "d", "loc", "sym", type);
    return e;
  }

  private static TimeseriesDataPoint dp(long ts) {
    return new TimeseriesDataPoint(ts, 1.0);
  }

  // ── overwrite=true → DO UPDATE clause ────────────────────────────────────────

  @Test
  void overwrite_true_usesDoUpdateClause() {
    Query q = mock(Query.class);
    when(entityManager.createNativeQuery(contains("DO UPDATE"))).thenReturn(q);
    when(q.setParameter(anyString(), any())).thenReturn(q);
    when(q.executeUpdate()).thenReturn(1);

    repository.insertManyDataPoints(List.of(dp(1000L)), entity(DataPointValueType.Double), true);

    verify(entityManager).createNativeQuery(contains("DO UPDATE"));
  }

  @Test
  void overwrite_true_doesNotUseDoNothingClause() {
    Query q = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(q);
    when(q.setParameter(anyString(), any())).thenReturn(q);
    when(q.executeUpdate()).thenReturn(1);

    repository.insertManyDataPoints(List.of(dp(1000L)), entity(DataPointValueType.Double), true);

    // Should have been called with a query that does NOT contain DO NOTHING
    verify(entityManager).createNativeQuery(contains("DO UPDATE"));
  }

  // ── overwrite=false → DO NOTHING clause ──────────────────────────────────────

  @Test
  void overwrite_false_usesDoNothingClause() {
    Query q = mock(Query.class);
    when(entityManager.createNativeQuery(contains("DO NOTHING"))).thenReturn(q);
    when(q.setParameter(anyString(), any())).thenReturn(q);
    when(q.executeUpdate()).thenReturn(1);

    repository.insertManyDataPoints(List.of(dp(1000L)), entity(DataPointValueType.Double), false);

    verify(entityManager).createNativeQuery(contains("DO NOTHING"));
  }

  // ── conflict count arithmetic ─────────────────────────────────────────────────

  @Test
  void conflictCount_zeroWhenAllPointsInserted() {
    Query q = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(q);
    when(q.setParameter(anyString(), any())).thenReturn(q);
    when(q.executeUpdate()).thenReturn(3); // all 3 inserted

    int conflicts = repository.insertManyDataPoints(
      List.of(dp(1L), dp(2L), dp(3L)),
      entity(DataPointValueType.Double),
      false
    );

    assertEquals(0, conflicts);
  }

  @Test
  void conflictCount_correctWhenSomePointsSkipped() {
    Query q = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(q);
    when(q.setParameter(anyString(), any())).thenReturn(q);
    when(q.executeUpdate()).thenReturn(1); // 1 inserted out of 3 → 2 conflicts

    int conflicts = repository.insertManyDataPoints(
      List.of(dp(1L), dp(2L), dp(3L)),
      entity(DataPointValueType.Double),
      false
    );

    assertEquals(2, conflicts);
  }

  @Test
  void conflictCount_allPointsConflict() {
    Query q = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(q);
    when(q.setParameter(anyString(), any())).thenReturn(q);
    when(q.executeUpdate()).thenReturn(0); // none inserted → all conflict

    int conflicts = repository.insertManyDataPoints(
      List.of(dp(1L), dp(2L)),
      entity(DataPointValueType.Double),
      false
    );

    assertEquals(2, conflicts);
  }

  @Test
  void conflictCount_alwaysZeroWhenOverwriteTrue() {
    Query q = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(q);
    when(q.setParameter(anyString(), any())).thenReturn(q);
    when(q.executeUpdate()).thenReturn(0); // value doesn't matter for overwrite=true

    int conflicts = repository.insertManyDataPoints(
      List.of(dp(1L), dp(2L)),
      entity(DataPointValueType.Double),
      true
    );

    assertEquals(0, conflicts);
  }

  // ── backward-compat void overload still works ─────────────────────────────────

  @Test
  void voidOverload_stillCallsDoUpdate() {
    Query q = mock(Query.class);
    when(entityManager.createNativeQuery(contains("DO UPDATE"))).thenReturn(q);
    when(q.setParameter(anyString(), any())).thenReturn(q);
    when(q.executeUpdate()).thenReturn(1);

    // The two-arg void overload must delegate with overwrite=true
    repository.insertManyDataPoints(List.of(dp(1000L)), entity(DataPointValueType.Double));

    verify(entityManager).createNativeQuery(contains("DO UPDATE"));
  }
}
