package de.dlr.shepard.v2.structureddatacontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataContainer;
import de.dlr.shepard.data.structureddata.services.StructuredDataContainerService;
import de.dlr.shepard.v2.structureddatacontainer.io.StructuredDataContainerStatsIO;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-STATS-NUMERIC-ID — unit tests for {@link StructuredDataContainerStatsRest}.
 *
 * <p>Verifies that the REST layer delegates to the service, resolves the appId
 * to an OGM id via {@link EntityIdResolver}, and returns the correct entry count.
 */
class StructuredDataContainerStatsRestTest {

  private static final String APP_ID = "01928eaa-2222-7000-9000-bbbbbbbbbbbb";
  private static final long OGM_ID = 77L;

  private StructuredDataContainerService service;
  private EntityIdResolver resolver;
  private StructuredDataContainerStatsRest rest;

  @BeforeEach
  void setUp() {
    service = mock(StructuredDataContainerService.class);
    resolver = new EntityIdResolver();
    resolver.primeForTesting(OGM_ID, APP_ID);

    rest = new StructuredDataContainerStatsRest();
    rest.structuredDataContainerService = service;
    rest.entityIdResolver = resolver;
  }

  @Test
  void returnsEntryCountForContainer() {
    StructuredDataContainer container = mock(StructuredDataContainer.class);
    when(container.getStructuredDatas()).thenReturn(List.of(
        mock(StructuredData.class),
        mock(StructuredData.class)));
    when(service.getContainer(OGM_ID)).thenReturn(container);

    Response r = rest.getStats(APP_ID);

    assertEquals(200, r.getStatus());
    StructuredDataContainerStatsIO body = (StructuredDataContainerStatsIO) r.getEntity();
    assertEquals(2L, body.entryCount());
    verify(service).getContainer(OGM_ID);
  }

  @Test
  void returnsZeroWhenEntriesListIsEmpty() {
    StructuredDataContainer container = mock(StructuredDataContainer.class);
    when(container.getStructuredDatas()).thenReturn(List.of());
    when(service.getContainer(OGM_ID)).thenReturn(container);

    Response r = rest.getStats(APP_ID);

    assertEquals(200, r.getStatus());
    StructuredDataContainerStatsIO body = (StructuredDataContainerStatsIO) r.getEntity();
    assertEquals(0L, body.entryCount());
  }

  @Test
  void returnsZeroWhenEntriesListIsNull() {
    StructuredDataContainer container = mock(StructuredDataContainer.class);
    when(container.getStructuredDatas()).thenReturn(null);
    when(service.getContainer(OGM_ID)).thenReturn(container);

    Response r = rest.getStats(APP_ID);

    assertEquals(200, r.getStatus());
    StructuredDataContainerStatsIO body = (StructuredDataContainerStatsIO) r.getEntity();
    assertEquals(0L, body.entryCount());
  }
}
