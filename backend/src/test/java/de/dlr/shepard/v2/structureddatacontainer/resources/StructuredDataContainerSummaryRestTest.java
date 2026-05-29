package de.dlr.shepard.v2.structureddatacontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.v2.containers.io.ContainerCardinalitySummaryIO;
import jakarta.ws.rs.core.Response;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * UI21-SIZEBAR-DATA — plain-Mockito unit tests for {@link StructuredDataContainerSummaryRest}.
 * Wires the resource by hand; no CDI or databases required.
 */
class StructuredDataContainerSummaryRestTest {

  private static final long CONTAINER_ID = 99L;

  @Mock
  StructuredDataContainerService structuredDataContainerService;

  StructuredDataContainerSummaryRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new StructuredDataContainerSummaryRest();
    resource.structuredDataContainerService = structuredDataContainerService;
  }

  private StructuredDataContainer containerWithPayloads(int count) {
    StructuredDataContainer c = new StructuredDataContainer(CONTAINER_ID);
    for (int i = 0; i < count; i++) {
      c.addStructuredData(new StructuredData("payload-" + i, new Date()));
    }
    return c;
  }

  @Test
  void getSummary_returns200WithZeroPayloadsWhenContainerIsEmpty() {
    when(structuredDataContainerService.getContainer(CONTAINER_ID))
      .thenReturn(containerWithPayloads(0));

    Response resp = resource.getSummary(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    ContainerCardinalitySummaryIO body = (ContainerCardinalitySummaryIO) resp.getEntity();
    assertEquals(0L, body.cardinality());
    assertNotNull(body.lastUpdated());
  }

  @Test
  void getSummary_returns200WithCorrectPayloadCount() {
    when(structuredDataContainerService.getContainer(CONTAINER_ID))
      .thenReturn(containerWithPayloads(3));

    Response resp = resource.getSummary(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    ContainerCardinalitySummaryIO body = (ContainerCardinalitySummaryIO) resp.getEntity();
    assertEquals(3L, body.cardinality());
  }

  @Test
  void getSummary_returns200WithLargeCount() {
    when(structuredDataContainerService.getContainer(CONTAINER_ID))
      .thenReturn(containerWithPayloads(42));

    Response resp = resource.getSummary(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    ContainerCardinalitySummaryIO body = (ContainerCardinalitySummaryIO) resp.getEntity();
    assertEquals(42L, body.cardinality());
  }

  @Test
  void getSummary_handlesNullStructuredDatasListGracefully() {
    StructuredDataContainer c = new StructuredDataContainer(CONTAINER_ID);
    c.setStructuredDatas(null);
    when(structuredDataContainerService.getContainer(CONTAINER_ID)).thenReturn(c);

    Response resp = resource.getSummary(CONTAINER_ID);

    assertEquals(200, resp.getStatus());
    ContainerCardinalitySummaryIO body = (ContainerCardinalitySummaryIO) resp.getEntity();
    assertEquals(0L, body.cardinality());
  }

  @Test
  void getSummary_callsPermissionCheckViaGetContainer() {
    when(structuredDataContainerService.getContainer(CONTAINER_ID))
      .thenReturn(containerWithPayloads(0));

    resource.getSummary(CONTAINER_ID);

    verify(structuredDataContainerService).getContainer(CONTAINER_ID);
  }
}
