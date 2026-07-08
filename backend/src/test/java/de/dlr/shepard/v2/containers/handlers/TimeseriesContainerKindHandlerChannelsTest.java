package de.dlr.shepard.v2.containers.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.context.semantic.daos.AnnotatableTimeseriesDAO;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import de.dlr.shepard.v2.timeseriescontainer.io.BulkChannelDataRequestIO;
import de.dlr.shepard.v2.timeseriescontainer.io.CopyIngestRequestIO;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesChannelV2IO;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-CONT-NS-COLLAPSE-2 — unit tests for the 5 channel-endpoint overrides
 * on {@link TimeseriesContainerKindHandler}. All per-kind services and DAOs are
 * mocked; no DB or CDI container needed.
 */
class TimeseriesContainerKindHandlerChannelsTest {

  @Mock TimeseriesContainerService tsService;
  @Mock TimeseriesContainerDAO tsDao;
  @Mock TsChannelResolver tsChannelResolver;
  @Mock TimeseriesService timeseriesService;
  @Mock AnnotatableTimeseriesDAO annotatableTimeseriesDAO;

  TimeseriesContainerKindHandler handler;

  private static final String CONTAINER_APP_ID = "01913a4f-ffff-7000-8000-000000000001";
  private static final long   CONTAINER_ID     = 42L;
  private static final UUID   CHANNEL_UUID     = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    handler = new TimeseriesContainerKindHandler();
    handler.service                 = tsService;
    handler.dao                     = tsDao;
    handler.tsChannelResolver       = tsChannelResolver;
    handler.timeseriesService       = timeseriesService;
    handler.annotatableTimeseriesDAO = annotatableTimeseriesDAO;
    // userService and dateHelper not exercised by channel methods — left null.

    // Container stub
    TimeseriesContainer container = new TimeseriesContainer(CONTAINER_ID);
    container.setAppId(CONTAINER_APP_ID);
    container.setName("telemetry");
    when(tsService.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private TimeseriesEntity channelEntity(UUID shepardId) {
    TimeseriesEntity e = new TimeseriesEntity(
        CONTAINER_ID, "pressure", "bar", "sensor-1", "nozzle", "PT-01",
        DataPointValueType.Double);
    e.setShepardId(shepardId);
    return e;
  }

  // ── listChannels ──────────────────────────────────────────────────────────────

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — listChannels delegates to TsChannelResolver
   * and projects the row to TimeseriesChannelV2IO.
   */
  @Test
  void listChannels_delegatesToTsChannelResolver() {
    TimeseriesEntity entity = channelEntity(CHANNEL_UUID);
    when(tsChannelResolver.listPaged(eq(CONTAINER_ID), eq(0), eq(200)))
        .thenReturn(List.of(entity));
    when(tsChannelResolver.countByContainerId(CONTAINER_ID)).thenReturn(1L);

    Optional<PagedResponseIO<TimeseriesChannelV2IO>> result = handler.listChannels(CONTAINER_APP_ID, 0, 200);

    assertTrue(result.isPresent(), "Should return a present Optional");
    PagedResponseIO<TimeseriesChannelV2IO> page = result.get();
    assertEquals(1, page.items().size());
    assertEquals(1L, page.total());
    assertEquals(0, page.page());
    assertEquals(200, page.pageSize());
    assertEquals(CHANNEL_UUID, page.items().get(0).shepardId());
    verify(tsChannelResolver).listPaged(CONTAINER_ID, 0, 200);
    verify(tsChannelResolver).countByContainerId(CONTAINER_ID);
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — listChannels clamps an over-limit pageSize
   * to MAX_PAGE_SIZE (1000) before passing it to the resolver.
   */
  @Test
  void listChannels_pageSize_clamped() {
    when(tsChannelResolver.listPaged(eq(CONTAINER_ID), eq(0), eq(1000)))
        .thenReturn(List.of());
    when(tsChannelResolver.countByContainerId(CONTAINER_ID)).thenReturn(0L);

    var result = handler.listChannels(CONTAINER_APP_ID, 0, 9999);

    assertTrue(result.isPresent());
    assertEquals(1000, result.get().pageSize(), "pageSize must be clamped to 1000");
    // The resolver must have been called with 1000, not 9999
    verify(tsChannelResolver).listPaged(CONTAINER_ID, 0, 1000);
  }

  // ── ingestChannelData ─────────────────────────────────────────────────────────

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — ingestChannelData returns true after a
   * successful COPY ingest.
   */
  @Test
  void ingestChannelData_returnsTrue() {
    TimeseriesEntity entity = channelEntity(CHANNEL_UUID);
    when(tsChannelResolver.findByShepardId(CHANNEL_UUID)).thenReturn(Optional.of(entity));

    var body = new CopyIngestRequestIO(List.of());
    boolean handled = handler.ingestChannelData(CONTAINER_APP_ID, CHANNEL_UUID, body);

    assertTrue(handled, "ingestChannelData should return true when channel exists");
    verify(timeseriesService).ingestDataPointsCopy(eq(CONTAINER_ID), eq(entity), any());
  }

  /**
   * APISIMP-CONT-NS-COLLAPSE-2 — ingestChannelData throws NotFoundException
   * when the channel is not found in the container.
   */
  @Test
  void ingestChannelData_channelNotFound_throwsNotFound() {
    when(tsChannelResolver.findByShepardId(CHANNEL_UUID)).thenReturn(Optional.empty());

    var body = new CopyIngestRequestIO(List.of());
    assertThrows(NotFoundException.class,
        () -> handler.ingestChannelData(CONTAINER_APP_ID, CHANNEL_UUID, body));
  }
}
