package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.containers.io.ContainerCardinalitySummaryIO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * UI21-SIZEBAR-DATA — plain-Mockito unit tests for {@link TimeseriesContainerSummaryRest}.
 * Wires the resource by hand; no CDI or databases required.
 */
class TimeseriesContainerSummaryRestTest {

  private static final long CONTAINER_ID = 42L;

  @Mock
  TimeseriesContainerService timeseriesContainerService;

  @Mock
  EntityManager entityManager;

  @Mock
  Query nativeQuery;

  TimeseriesContainerSummaryRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new TimeseriesContainerSummaryRest();
    resource.timeseriesContainerService = timeseriesContainerService;
    resource.entityManager = entityManager;
  }

  @Test
  void getSummary_returns200WithZeroChannelsWhenContainerIsEmpty() {
    when(timeseriesContainerService.getContainer(CONTAINER_ID))
      .thenReturn(new TimeseriesContainer(CONTAINER_ID));
    when(entityManager.createNativeQuery(
      "SELECT COUNT(*) FROM timeseries WHERE container_id = :cid"
    )).thenReturn(nativeQuery);
    when(nativeQuery.setParameter("cid", CONTAINER_ID)).thenReturn(nativeQuery);
    when(nativeQuery.getSingleResult()).thenReturn(0L);

    Response resp = resource.getSummary(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    ContainerCardinalitySummaryIO body = (ContainerCardinalitySummaryIO) resp.getEntity();
    assertEquals(0L, body.cardinality());
    assertNotNull(body.lastUpdated());
  }

  @Test
  void getSummary_returns200WithCorrectChannelCount() {
    when(timeseriesContainerService.getContainer(CONTAINER_ID))
      .thenReturn(new TimeseriesContainer(CONTAINER_ID));
    when(entityManager.createNativeQuery(
      "SELECT COUNT(*) FROM timeseries WHERE container_id = :cid"
    )).thenReturn(nativeQuery);
    when(nativeQuery.setParameter("cid", CONTAINER_ID)).thenReturn(nativeQuery);
    when(nativeQuery.getSingleResult()).thenReturn(7L);

    Response resp = resource.getSummary(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    ContainerCardinalitySummaryIO body = (ContainerCardinalitySummaryIO) resp.getEntity();
    assertEquals(7L, body.cardinality());
  }

  @Test
  void getSummary_handlesIntegerResultFromJdbc() {
    // JDBC drivers may return Integer rather than Long for COUNT(*).
    when(timeseriesContainerService.getContainer(CONTAINER_ID))
      .thenReturn(new TimeseriesContainer(CONTAINER_ID));
    when(entityManager.createNativeQuery(
      "SELECT COUNT(*) FROM timeseries WHERE container_id = :cid"
    )).thenReturn(nativeQuery);
    when(nativeQuery.setParameter("cid", CONTAINER_ID)).thenReturn(nativeQuery);
    when(nativeQuery.getSingleResult()).thenReturn(Integer.valueOf(3));

    Response resp = resource.getSummary(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    ContainerCardinalitySummaryIO body = (ContainerCardinalitySummaryIO) resp.getEntity();
    assertEquals(3L, body.cardinality());
  }

  @Test
  void getSummary_callsPermissionCheckViaGetContainer() {
    when(timeseriesContainerService.getContainer(CONTAINER_ID))
      .thenReturn(new TimeseriesContainer(CONTAINER_ID));
    when(entityManager.createNativeQuery(
      "SELECT COUNT(*) FROM timeseries WHERE container_id = :cid"
    )).thenReturn(nativeQuery);
    when(nativeQuery.setParameter("cid", CONTAINER_ID)).thenReturn(nativeQuery);
    when(nativeQuery.getSingleResult()).thenReturn(0L);

    resource.getSummary(CONTAINER_ID);

    verify(timeseriesContainerService).getContainer(CONTAINER_ID);
  }
}
