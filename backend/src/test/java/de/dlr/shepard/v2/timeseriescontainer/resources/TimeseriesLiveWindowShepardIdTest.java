package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesDataPointRepository;
import de.dlr.shepard.data.timeseries.repositories.TsChannelResolver;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TS-IDc — unit coverage for the {@code shepardId} acceptance path on the
 * live-window endpoint.
 *
 * <p>Verifies the four decision branches:
 * <ul>
 *   <li>{@code shepardId} present + known + correct container → 200 with data</li>
 *   <li>{@code shepardId} present + unknown → 404 (no fallthrough to 5-tuple)</li>
 *   <li>{@code shepardId} present + wrong container → 404 (cross-container leak guard)</li>
 *   <li>{@code shepardId} present alongside 5-tuple → shepardId wins; 5-tuple path NOT called</li>
 *   <li>{@code shepardId} absent → 5-tuple path taken (legacy bridge)</li>
 * </ul>
 *
 * <p>Sister to {@link TimeseriesChannelDataRestTest} which covers the
 * sibling {@code /channels/{shepardId}/data} endpoint (path-param shape).
 */
public class TimeseriesLiveWindowShepardIdTest {

  private TimeseriesLiveWindowRest resource;
  private TsChannelResolver resolverMock;
  private TimeseriesContainerService containerServiceMock;
  private TimeseriesDataPointRepository dataPointRepositoryMock;

  private static final String CONTAINER_APP_ID = "01930000-0000-7000-8000-000000000abc";
  private static final long CONTAINER_NUMERIC_ID = 1772L;
  private static final UUID KNOWN_SHEPARD_ID =
      UUID.fromString("a2c0f1dd-4dce-4400-92e4-445cd18826e6");
  private static final UUID UNKNOWN_SHEPARD_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000099");

  @BeforeEach
  void setUp() throws Exception {
    resource = new TimeseriesLiveWindowRest();
    resolverMock = mock(TsChannelResolver.class);
    containerServiceMock = mock(TimeseriesContainerService.class);
    dataPointRepositoryMock = mock(TimeseriesDataPointRepository.class);

    inject(resource, "channelResolver", resolverMock);
    inject(resource, "containerService", containerServiceMock);
    inject(resource, "dataPointRepository", dataPointRepositoryMock);

    TimeseriesContainer container = mock(TimeseriesContainer.class);
    when(container.getId()).thenReturn(CONTAINER_NUMERIC_ID);
    when(containerServiceMock.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container);
  }

  private static void inject(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static TimeseriesEntity row(long containerId, UUID shepardId) {
    TimeseriesEntity row = new TimeseriesEntity(
        containerId,
        "mffd",
        "consolidation_force_N",
        "AFP-AFPT-MTLH-S1",
        "ZLP-Augsburg",
        "afp-s1_consolidation_force_N",
        DataPointValueType.Double);
    row.setShepardId(shepardId);
    return row;
  }

  // ── shepardId + known + correct container → 200 ─────────────────────────

  @Test
  void shepardId_knownInContainer_returns200_andNoTupleLookup() {
    TimeseriesEntity entity = row(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID);
    when(resolverMock.findByContainerAndShepardId(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID))
        .thenReturn(Optional.of(entity));
    when(dataPointRepositoryMock.queryDataPoints(eq(entity.getId()), any(), any()))
        .thenReturn(List.of());

    Response resp = resource.getLiveWindow(
        CONTAINER_APP_ID, KNOWN_SHEPARD_ID,
        null, null, null, null, null,
        300, true);

    assertEquals(Response.Status.OK.getStatusCode(), resp.getStatus());
    verify(resolverMock).findByContainerAndShepardId(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID);
    // 5-tuple resolution must NOT be called when shepardId is present.
    verify(resolverMock, never()).findByContainerAndPartialTuple(
        eq(CONTAINER_NUMERIC_ID), any(), any(), any(), any(), any());
  }

  // ── shepardId + unknown → 404 ────────────────────────────────────────────

  @Test
  void shepardId_unknown_returns404() {
    when(resolverMock.findByContainerAndShepardId(CONTAINER_NUMERIC_ID, UNKNOWN_SHEPARD_ID))
        .thenReturn(Optional.empty());

    Response resp = resource.getLiveWindow(
        CONTAINER_APP_ID, UNKNOWN_SHEPARD_ID,
        null, null, null, null, null,
        300, true);

    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), resp.getStatus());
    // Must not fall through to the 5-tuple path on shepardId-miss.
    verify(resolverMock, never()).findByContainerAndPartialTuple(
        eq(CONTAINER_NUMERIC_ID), any(), any(), any(), any(), any());
  }

  // ── shepardId + wrong container → 404 (cross-container leak guard) ──────

  @Test
  void shepardId_inWrongContainer_returns404_notCrossContainerLeak() {
    // Resolver's container-scoped wrapper returns empty when the underlying
    // row exists but is in a different container — same observable as unknown.
    when(resolverMock.findByContainerAndShepardId(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID))
        .thenReturn(Optional.empty());

    Response resp = resource.getLiveWindow(
        CONTAINER_APP_ID, KNOWN_SHEPARD_ID,
        null, null, null, null, null,
        300, true);

    assertEquals(Response.Status.NOT_FOUND.getStatusCode(), resp.getStatus());
  }

  // ── shepardId + 5-tuple both supplied → shepardId wins ──────────────────

  @Test
  void bothShepardIdAndTuple_shepardIdWins_tupleIgnored() {
    TimeseriesEntity entity = row(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID);
    when(resolverMock.findByContainerAndShepardId(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID))
        .thenReturn(Optional.of(entity));
    when(dataPointRepositoryMock.queryDataPoints(eq(entity.getId()), any(), any()))
        .thenReturn(List.of());

    Response resp = resource.getLiveWindow(
        CONTAINER_APP_ID, KNOWN_SHEPARD_ID,
        "wrong-measurement", "wrong-device", "wrong-location", "wrong-sym", "wrong-field",
        300, true);

    assertEquals(Response.Status.OK.getStatusCode(), resp.getStatus());
    verify(resolverMock).findByContainerAndShepardId(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID);
    // 5-tuple path MUST NOT be touched.
    verify(resolverMock, never()).findByContainerAndPartialTuple(
        eq(CONTAINER_NUMERIC_ID), any(), any(), any(), any(), any());
  }

  // ── shepardId absent → 5-tuple path taken (legacy bridge stays green) ───

  @Test
  void shepardIdAbsent_fallsThroughTo5Tuple() {
    TimeseriesEntity entity = row(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID);
    when(resolverMock.findByContainerAndPartialTuple(
            eq(CONTAINER_NUMERIC_ID), eq("mffd"), any(), any(), any(), any()))
        .thenReturn(List.of(entity));
    when(dataPointRepositoryMock.queryDataPoints(eq(entity.getId()), any(), any()))
        .thenReturn(List.of());

    Response resp = resource.getLiveWindow(
        CONTAINER_APP_ID, null,
        "mffd", null, null, null, null,
        300, true);

    assertEquals(Response.Status.OK.getStatusCode(), resp.getStatus());
    // shepardId path must NOT be called when shepardId is null.
    verify(resolverMock, never()).findByContainerAndShepardId(
        eq(CONTAINER_NUMERIC_ID), any());
    verify(resolverMock).findByContainerAndPartialTuple(
        eq(CONTAINER_NUMERIC_ID), eq("mffd"), any(), any(), any(), any());
  }

  // ── container permission check is always called first ──────────────────

  @Test
  void alwaysChecksContainerPermissionFirst() {
    when(resolverMock.findByContainerAndShepardId(CONTAINER_NUMERIC_ID, KNOWN_SHEPARD_ID))
        .thenReturn(Optional.empty());

    resource.getLiveWindow(
        CONTAINER_APP_ID, KNOWN_SHEPARD_ID,
        null, null, null, null, null,
        300, true);

    verify(containerServiceMock).getContainerByAppId(CONTAINER_APP_ID);
  }
}
