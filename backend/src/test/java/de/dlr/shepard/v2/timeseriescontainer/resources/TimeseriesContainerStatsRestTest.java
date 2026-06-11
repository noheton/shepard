package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for APISIMP-TSCONT-APPID-KEY-1:
 * {@code GET /v2/timeseries-containers/{containerAppId}/stats}
 * is now keyed on appId (UUID string) rather than the numeric Neo4j OGM id.
 */
public class TimeseriesContainerStatsRestTest {

  private TimeseriesContainerStatsRest resource;
  private TimeseriesContainerService containerServiceMock;
  private EntityManager entityManagerMock;

  private static final String CONTAINER_APP_ID = "01234567-abcd-7000-8000-000000000001";
  private static final long   CONTAINER_OGM_ID = 99L;

  @BeforeEach
  void setUp() throws Exception {
    resource             = new TimeseriesContainerStatsRest();
    containerServiceMock = mock(TimeseriesContainerService.class);
    entityManagerMock    = mock(EntityManager.class);

    inject(resource, "timeseriesContainerService", containerServiceMock);
    inject(resource, "entityManager",              entityManagerMock);
  }

  @Test
  void getStats_unknownAppId_propagates404() {
    when(containerServiceMock.getContainerByAppId(CONTAINER_APP_ID))
      .thenThrow(new InvalidPathException("not found"));

    try {
      resource.getStats(CONTAINER_APP_ID);
    } catch (InvalidPathException e) {
      assertEquals("not found", e.getMessage());
    }
    verify(entityManagerMock, never()).createNativeQuery(anyString());
  }

  @Test
  void getStats_knownAppId_resolvesToOgmIdForSql() {
    TimeseriesContainer container = new TimeseriesContainer();
    container.setId(CONTAINER_OGM_ID);
    when(containerServiceMock.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container);

    // Stub the two SQL native queries (point-count + recent-count)
    Query pointsQuery  = mock(Query.class);
    Query recentQuery  = mock(Query.class);
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

    Response resp = resource.getStats(CONTAINER_APP_ID);

    assertEquals(200, resp.getStatus());
    verify(containerServiceMock).getContainerByAppId(CONTAINER_APP_ID);
    verify(pointsQuery).setParameter("cid", CONTAINER_OGM_ID);
  }

  // ── reflection helper ─────────────────────────────────────────────────────

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
