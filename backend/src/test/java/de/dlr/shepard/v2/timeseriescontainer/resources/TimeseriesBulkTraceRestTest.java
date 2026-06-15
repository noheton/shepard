package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.v2.containers.handlers.TimeseriesContainerKindHandler;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkChannelDataRequestIO;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the TS-OPT2 bulk channel data endpoint (APISIMP-CONT-NS-COLLAPSE-2):
 * {@code POST /v2/containers/{containerAppId}/channels/data/bulk}.
 *
 * <p>Migrated from TimeseriesContainerChannelsRest (deleted in APISIMP-CONT-NS-COLLAPSE-2)
 * to {@link TimeseriesContainerKindHandler}.
 *
 * <p>Verifies: container permission check, UUID resolution, empty-result
 * on unknown channel, and result forwarding.
 */
class TimeseriesBulkTraceRestTest {

  private TimeseriesContainerKindHandler handler;
  private TimeseriesService serviceMock;
  private TimeseriesContainerService containerServiceMock;
  private TsChannelResolver resolverMock;

  private static final String CONTAINER_APP_ID = "00000000-0000-7000-8000-00000000002a";
  private static final long CONTAINER_ID = 42L;
  private static final long START_NS     = 1_000_000_000L;
  private static final long END_NS       = 2_000_000_000L;

  @BeforeEach
  void setUp() throws Exception {
    handler              = new TimeseriesContainerKindHandler();
    serviceMock          = mock(TimeseriesService.class);
    containerServiceMock = mock(TimeseriesContainerService.class);
    resolverMock         = mock(TsChannelResolver.class);
    inject(handler, "timeseriesService",  serviceMock);
    inject(handler, "service",            containerServiceMock);
    inject(handler, "tsChannelResolver",  resolverMock);
    when(resolverMock.bulkFindByShepardIds(any())).thenReturn(List.of());
    when(serviceMock.getManyDataPointsByEntities(anyLong(), any(), any())).thenReturn(List.of());

    var mockContainer = mock(TimeseriesContainer.class);
    when(mockContainer.getId()).thenReturn(CONTAINER_ID);
    when(containerServiceMock.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(mockContainer);
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  // ── Container permission check ─────────────────────────────────────────────

  @Test
  void alwaysChecksContainerPermission() {
    handler.getBulkChannelData(CONTAINER_APP_ID,
      new BulkChannelDataRequestIO(List.of(UUID.randomUUID()), START_NS, END_NS));

    verify(containerServiceMock).getContainerByAppId(CONTAINER_APP_ID);
  }

  // ── Empty result when no channel resolves ────────────────────────────────

  @Test
  void unknownShepardId_returnsEmptyList() {
    UUID unknown = UUID.randomUUID();
    when(resolverMock.bulkFindByShepardIds(List.of(unknown))).thenReturn(List.of());
    when(serviceMock.getManyDataPointsByEntities(anyLong(), any(), any())).thenReturn(List.of());

    Optional<List<TimeseriesWithDataPoints>> result = handler.getBulkChannelData(CONTAINER_APP_ID,
      new BulkChannelDataRequestIO(List.of(unknown), START_NS, END_NS));

    assertThat(result).isPresent();
    assertThat(result.get()).isEmpty();
  }

  // ── Results forwarded verbatim ────────────────────────────────────────────

  @Test
  void resolvedChannels_returnedInResponse() {
    TimeseriesWithDataPoints resultItem = mock(TimeseriesWithDataPoints.class);
    when(serviceMock.getManyDataPointsByEntities(anyLong(), any(), any()))
      .thenReturn(List.of(resultItem));

    Optional<List<TimeseriesWithDataPoints>> result = handler.getBulkChannelData(CONTAINER_APP_ID,
      new BulkChannelDataRequestIO(List.of(UUID.randomUUID()), START_NS, END_NS));

    assertThat(result).isPresent();
    assertThat(result.get()).hasSize(1).contains(resultItem);
  }

  // ── Multi-channel: all channels queried ────────────────────────────────────

  @Test
  void multiChannel_delegatesWithAllIds() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();

    handler.getBulkChannelData(CONTAINER_APP_ID,
      new BulkChannelDataRequestIO(List.of(id1, id2, id3), START_NS, END_NS));

    verify(resolverMock).bulkFindByShepardIds(List.of(id1, id2, id3));
  }

  // ── Delegates to getManyDataPointsByEntities ────────────────────────────────

  @Test
  void delegatesToManyPointsService() {
    handler.getBulkChannelData(CONTAINER_APP_ID,
      new BulkChannelDataRequestIO(List.of(UUID.randomUUID()), START_NS, END_NS));

    verify(serviceMock).getManyDataPointsByEntities(anyLong(), any(), any());
  }
}
