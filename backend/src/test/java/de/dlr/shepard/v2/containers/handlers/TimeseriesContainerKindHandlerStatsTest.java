package de.dlr.shepard.v2.containers.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.containers.io.ContainerStatsIO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for APISIMP-CONT-NS-COLLAPSE-1:
 * {@link TimeseriesContainerKindHandler#getStats(String)} — moved from
 * {@code GET /v2/timeseries-containers/{appId}/stats} to
 * {@code GET /v2/containers/{appId}/stats}.
 */
public class TimeseriesContainerKindHandlerStatsTest {

  private TimeseriesContainerKindHandler handler;
  private TimeseriesContainerService serviceMock;
  private EntityManager entityManagerMock;

  private static final String APP_ID = "01234567-abcd-7000-8000-000000000001";
  private static final long OGM_ID = 99L;

  @BeforeEach
  void setUp() throws Exception {
    handler           = new TimeseriesContainerKindHandler();
    serviceMock       = mock(TimeseriesContainerService.class);
    entityManagerMock = mock(EntityManager.class);

    inject(handler, "service",         serviceMock);
    inject(handler, "entityManager",   entityManagerMock);
    inject(handler, "dao",             mock(TimeseriesContainerDAO.class));
  }

  @Test
  void getStats_unknownAppId_propagates404() {
    when(serviceMock.getContainerByAppId(APP_ID))
      .thenThrow(new InvalidPathException("not found"));

    try {
      handler.getStats(APP_ID);
    } catch (InvalidPathException e) {
      assertEquals("not found", e.getMessage());
    }
    verify(entityManagerMock, never()).createNativeQuery(anyString());
  }

  @Test
  void getStats_knownAppId_resolvesToOgmIdForSql() {
    TimeseriesContainer container = new TimeseriesContainer();
    container.setId(OGM_ID);
    when(serviceMock.getContainerByAppId(APP_ID)).thenReturn(container);

    Query pointsQuery = mock(Query.class);
    Query recentQuery = mock(Query.class);
    when(entityManagerMock.createNativeQuery(
      org.mockito.ArgumentMatchers.contains("point_count"))).thenReturn(pointsQuery);
    when(entityManagerMock.createNativeQuery(
      org.mockito.ArgumentMatchers.contains("windowStart"))).thenReturn(recentQuery);
    when(pointsQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any()))
      .thenReturn(pointsQuery);
    when(recentQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any()))
      .thenReturn(recentQuery);
    when(pointsQuery.getSingleResult()).thenReturn(new Object[]{100L, 5L});
    when(recentQuery.getSingleResult()).thenReturn(10L);

    Optional<ContainerStatsIO> result = handler.getStats(APP_ID);

    assertTrue(result.isPresent());
    ContainerStatsIO stats = result.get();
    assertEquals(100L, stats.pointCount());
    assertEquals(5L,   stats.channelCount());
    assertEquals(100L * 28, stats.estimatedSizeBytes());
    assertEquals(10L,  stats.recentPointsLast10s());
    assertEquals(10L * 28 / 10, stats.ingestRateBytesPerSec());
    verify(serviceMock).getContainerByAppId(APP_ID);
    verify(pointsQuery).setParameter("cid", OGM_ID);
  }

  @Test
  void getStats_returnsNonEmpty() {
    TimeseriesContainer container = new TimeseriesContainer();
    container.setId(OGM_ID);
    when(serviceMock.getContainerByAppId(APP_ID)).thenReturn(container);

    Query pointsQuery = mock(Query.class);
    Query recentQuery = mock(Query.class);
    when(entityManagerMock.createNativeQuery(
      org.mockito.ArgumentMatchers.contains("point_count"))).thenReturn(pointsQuery);
    when(entityManagerMock.createNativeQuery(
      org.mockito.ArgumentMatchers.contains("windowStart"))).thenReturn(recentQuery);
    when(pointsQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any()))
      .thenReturn(pointsQuery);
    when(recentQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any()))
      .thenReturn(recentQuery);
    when(pointsQuery.getSingleResult()).thenReturn(new Object[]{0L, 0L});
    when(recentQuery.getSingleResult()).thenReturn(0L);

    var result = handler.getStats(APP_ID);
    assertTrue(result.isPresent(), "timeseries kind always returns non-empty stats");
  }

  // ── reflection helper ──────────────────────────────────────────────────

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Class<?> clazz = target.getClass();
    while (clazz != null) {
      try {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
        return;
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
      }
    }
    throw new NoSuchFieldException("No field '" + fieldName + "' on " + target.getClass());
  }
}
