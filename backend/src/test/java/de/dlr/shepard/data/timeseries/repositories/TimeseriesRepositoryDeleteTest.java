package de.dlr.shepard.data.timeseries.repositories;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Unit tests for the TSDB-DDL-2 chunk-aware container delete path in
 * {@link TimeseriesRepository}.
 *
 * <p>No live database required — the {@link EntityManager} is mocked so the tests
 * verify that:
 * <ol>
 *   <li>The native DELETE on {@code timeseries_data_points} is issued BEFORE the
 *       JPQL DELETE on {@code timeseries} (ordering invariant).</li>
 *   <li>The native DELETE SQL targets the right subquery shape.</li>
 *   <li>Both deletes use the correct {@code containerId} parameter.</li>
 *   <li>A container with zero data points completes without throwing.</li>
 * </ol>
 *
 * <p>TSDB-DDL-2 in {@code aidocs/16-dispatcher-backlog.md}.
 */
public class TimeseriesRepositoryDeleteTest {

  private TimeseriesRepository repository;
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    entityManager = mock(EntityManager.class);
    repository = new TimeseriesRepository();
    repository.entityManager = entityManager;
  }

  // ── deleteDataPointsByContainerId: SQL shape and parameter ────────────────

  @Test
  void deleteDataPointsByContainerId_issuedWithSubqueryAndContainerId() {
    Query nativeQuery = mock(Query.class);
    when(entityManager.createNativeQuery(
      contains("timeseries_data_points")
    )).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
    when(nativeQuery.executeUpdate()).thenReturn(0);

    repository.deleteDataPointsByContainerId(42L);

    // Verify the native query scopes to the container via a subquery.
    verify(entityManager).createNativeQuery(
      contains("WHERE timeseries_id IN (SELECT id FROM timeseries WHERE container_id = :containerId)")
    );
    verify(nativeQuery).setParameter("containerId", 42L);
  }

  @Test
  void deleteDataPointsByContainerId_zeroRowsDeleted_doesNotThrow() {
    Query nativeQuery = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
    when(nativeQuery.executeUpdate()).thenReturn(0);

    assertDoesNotThrow(() -> repository.deleteDataPointsByContainerId(99L));
  }

  @Test
  void deleteDataPointsByContainerId_manyRowsDeleted_doesNotThrow() {
    Query nativeQuery = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
    // Simulate a large compressed container.
    when(nativeQuery.executeUpdate()).thenReturn(500_000);

    assertDoesNotThrow(() -> repository.deleteDataPointsByContainerId(7L));
  }

  // ── deleteByContainerId: ordering invariant ───────────────────────────────

  /**
   * TSDB-DDL-2 correctness invariant: the native DELETE on
   * {@code timeseries_data_points} MUST be issued before the JPQL DELETE on
   * {@code timeseries}. If the order is reversed, the ON DELETE CASCADE fires on
   * compressed chunks and causes the OOM failure that TSDB-DDL-2 was designed to fix.
   */
  @Test
  void deleteByContainerId_nativeDeleteIssuedBeforeJpqlDelete() {
    // Native query mock (data points)
    Query nativeQuery = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
    when(nativeQuery.executeUpdate()).thenReturn(10);

    // JPQL query mock (timeseries rows)
    Query jpqlQuery = mock(Query.class);
    when(entityManager.createQuery(anyString())).thenReturn(jpqlQuery);
    when(jpqlQuery.setParameter(anyString(), any())).thenReturn(jpqlQuery);
    when(jpqlQuery.executeUpdate()).thenReturn(2);

    InOrder order = inOrder(entityManager);

    repository.deleteByContainerId(5L);

    // Native DELETE (createNativeQuery) must precede JPQL DELETE (createQuery).
    order.verify(entityManager).createNativeQuery(anyString());
    order.verify(entityManager).createQuery(anyString());
  }

  @Test
  void deleteByContainerId_bothDeletesUseContainerId() {
    Query nativeQuery = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
    when(nativeQuery.executeUpdate()).thenReturn(0);

    Query jpqlQuery = mock(Query.class);
    when(entityManager.createQuery(anyString())).thenReturn(jpqlQuery);
    when(jpqlQuery.setParameter(anyString(), any())).thenReturn(jpqlQuery);
    when(jpqlQuery.executeUpdate()).thenReturn(0);

    repository.deleteByContainerId(77L);

    // Both the native and JPQL queries must receive containerId = 77.
    verify(nativeQuery).setParameter("containerId", 77L);
    verify(jpqlQuery).setParameter("containerId", 77L);
  }

  @Test
  void deleteByContainerId_noDataPoints_completes() {
    // Simulates a container that has no data yet (empty timeseries or new container).
    Query nativeQuery = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
    when(nativeQuery.executeUpdate()).thenReturn(0);

    Query jpqlQuery = mock(Query.class);
    when(entityManager.createQuery(anyString())).thenReturn(jpqlQuery);
    when(jpqlQuery.setParameter(anyString(), any())).thenReturn(jpqlQuery);
    when(jpqlQuery.executeUpdate()).thenReturn(0);

    assertDoesNotThrow(() -> repository.deleteByContainerId(13L));
  }

  @Test
  void deleteByContainerId_largeContainer_completes() {
    // Simulates a large container with compressed chunks (the TSDB-DDL-2 scenario).
    Query nativeQuery = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
    when(nativeQuery.executeUpdate()).thenReturn(1_000_000);

    Query jpqlQuery = mock(Query.class);
    when(entityManager.createQuery(anyString())).thenReturn(jpqlQuery);
    when(jpqlQuery.setParameter(anyString(), any())).thenReturn(jpqlQuery);
    when(jpqlQuery.executeUpdate()).thenReturn(50);

    assertDoesNotThrow(() -> repository.deleteByContainerId(1L));

    // Verify native DELETE ran first (times(1) on createNativeQuery, times(1) on createQuery).
    verify(entityManager, times(1)).createNativeQuery(anyString());
    verify(entityManager, times(1)).createQuery(anyString());
  }
}
