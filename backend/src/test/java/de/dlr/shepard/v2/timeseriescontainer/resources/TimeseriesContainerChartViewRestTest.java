package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.v2.containers.services.ContainersV2Service;
import de.dlr.shepard.v2.containers.spi.ContainerKindHandler;
import de.dlr.shepard.v2.timeseriescontainer.entities.TimeseriesContainerChartView;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesContainerChartViewIO;
import de.dlr.shepard.v2.timeseriescontainer.services.TimeseriesContainerChartViewService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link TimeseriesContainerChartViewRest} after APISIMP-CONT-NS-COLLAPSE-7 —
 * path migrated from {@code /v2/timeseries-containers/{containerAppId}/chart-view} to
 * {@code /v2/containers/{containerAppId}/chart-view}; kind-guard + explicit auth added.
 */
class TimeseriesContainerChartViewRestTest {

  static final long CONTAINER_ID = 42L;
  static final String CONTAINER_APP_ID = "01928eaa-0000-7000-8000-000000000042";
  static final String CALLER = "test-user";

  @Mock ContainersV2Service containersV2Service;
  @Mock PermissionsService permissionsService;
  @Mock TimeseriesContainerChartViewService chartViewService;
  @Mock SecurityContext sc;

  ContainerKindHandler tsHandler;
  TimeseriesContainer container;
  TimeseriesContainerChartViewRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new TimeseriesContainerChartViewRest();
    resource.containersV2Service = containersV2Service;
    resource.permissionsService  = permissionsService;
    resource.chartViewService    = chartViewService;

    tsHandler = mock(ContainerKindHandler.class);
    when(tsHandler.kind()).thenReturn("timeseries");

    container = new TimeseriesContainer();
    container.setId(CONTAINER_ID);

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn(CALLER);
    when(sc.getUserPrincipal()).thenReturn(principal);

    when(containersV2Service.resolveByAppId(CONTAINER_APP_ID))
      .thenReturn(Optional.of(new ContainersV2Service.ResolvedContainer(tsHandler, container)));
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(true);
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(true);
  }

  // ── auth ─────────────────────────────────────────────────────────────────

  @Test
  void get_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.get(CONTAINER_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(401);
    verify(containersV2Service, never()).resolveByAppId(any());
  }

  @Test
  void patch_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.patch(CONTAINER_APP_ID, new TimeseriesContainerChartViewIO(List.of(), null, null), sc);
    assertThat(r.getStatus()).isEqualTo(401);
    verify(containersV2Service, never()).resolveByAppId(any());
  }

  // ── resolution ───────────────────────────────────────────────────────────

  @Test
  void get_returns404WhenContainerNotFound() {
    when(containersV2Service.resolveByAppId(CONTAINER_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.get(CONTAINER_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  @Test
  void patch_returns404WhenContainerNotFound() {
    when(containersV2Service.resolveByAppId(CONTAINER_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.patch(CONTAINER_APP_ID, new TimeseriesContainerChartViewIO(List.of(), null, null), sc);
    assertThat(r.getStatus()).isEqualTo(404);
  }

  // ── kind guard ───────────────────────────────────────────────────────────

  @Test
  void get_returns415WhenNonTimeseriesContainer() {
    ContainerKindHandler fileHandler = mock(ContainerKindHandler.class);
    when(fileHandler.kind()).thenReturn("file");
    when(containersV2Service.resolveByAppId(CONTAINER_APP_ID))
      .thenReturn(Optional.of(new ContainersV2Service.ResolvedContainer(fileHandler, container)));

    Response r = resource.get(CONTAINER_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(415);
    verify(chartViewService, never()).find(CONTAINER_ID);
  }

  @Test
  void patch_returns415WhenNonTimeseriesContainer() {
    ContainerKindHandler fileHandler = mock(ContainerKindHandler.class);
    when(fileHandler.kind()).thenReturn("structured-data");
    when(containersV2Service.resolveByAppId(CONTAINER_APP_ID))
      .thenReturn(Optional.of(new ContainersV2Service.ResolvedContainer(fileHandler, container)));

    Response r = resource.patch(CONTAINER_APP_ID, new TimeseriesContainerChartViewIO(List.of(), null, null), sc);

    assertThat(r.getStatus()).isEqualTo(415);
    verify(chartViewService, never()).patch(eq(CONTAINER_ID), any());
  }

  // ── permission ───────────────────────────────────────────────────────────

  @Test
  void get_returns403WhenReadPermissionDenied() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_ID), eq(AccessType.Read), eq(CALLER)))
      .thenReturn(false);
    Response r = resource.get(CONTAINER_APP_ID, sc);
    assertThat(r.getStatus()).isEqualTo(403);
    verify(chartViewService, never()).find(CONTAINER_ID);
  }

  @Test
  void patch_returns403WhenWritePermissionDenied() {
    when(permissionsService.isAccessTypeAllowedForUser(eq(CONTAINER_ID), eq(AccessType.Write), eq(CALLER)))
      .thenReturn(false);
    Response r = resource.patch(CONTAINER_APP_ID, new TimeseriesContainerChartViewIO(List.of(), null, null), sc);
    assertThat(r.getStatus()).isEqualTo(403);
    verify(chartViewService, never()).patch(eq(CONTAINER_ID), any());
  }

  // ── GET happy path ────────────────────────────────────────────────────────

  @Test
  void get_returns200WithChartView() {
    TimeseriesContainerChartView view = new TimeseriesContainerChartView();
    view.setSelectedChannels(List.of("m|d|l|s|f"));
    when(chartViewService.find(CONTAINER_ID)).thenReturn(view);

    Response r = resource.get(CONTAINER_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    TimeseriesContainerChartViewIO body = (TimeseriesContainerChartViewIO) r.getEntity();
    assertThat(body.selectedChannels()).containsExactly("m|d|l|s|f");
    verify(containersV2Service).resolveByAppId(CONTAINER_APP_ID);
    verify(chartViewService).find(CONTAINER_ID);
  }

  @Test
  void get_returns200WithEmptyWhenNoView() {
    when(chartViewService.find(CONTAINER_ID)).thenReturn(null);

    Response r = resource.get(CONTAINER_APP_ID, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    TimeseriesContainerChartViewIO body = (TimeseriesContainerChartViewIO) r.getEntity();
    assertThat(body.selectedChannels()).isEmpty();
  }

  // ── PATCH happy path ──────────────────────────────────────────────────────

  @Test
  void patch_returns200WithUpdatedView() {
    TimeseriesContainerChartView updated = new TimeseriesContainerChartView();
    updated.setSelectedChannels(List.of("a|b|c|d|e"));
    when(chartViewService.patch(eq(CONTAINER_ID), any(TimeseriesContainerChartViewIO.class)))
      .thenReturn(updated);

    TimeseriesContainerChartViewIO patchBody =
      new TimeseriesContainerChartViewIO(List.of("a|b|c|d|e"), null, null);
    Response r = resource.patch(CONTAINER_APP_ID, patchBody, sc);

    assertThat(r.getStatus()).isEqualTo(200);
    TimeseriesContainerChartViewIO body = (TimeseriesContainerChartViewIO) r.getEntity();
    assertThat(body.selectedChannels()).containsExactly("a|b|c|d|e");
  }

  @Test
  void patch_checksWritePermissionViaPermissionsService() {
    TimeseriesContainerChartView updated = new TimeseriesContainerChartView();
    when(chartViewService.patch(eq(CONTAINER_ID), any())).thenReturn(updated);

    TimeseriesContainerChartViewIO patchBody =
      new TimeseriesContainerChartViewIO(List.of(), null, null);
    resource.patch(CONTAINER_APP_ID, patchBody, sc);

    verify(permissionsService).isAccessTypeAllowedForUser(CONTAINER_ID, AccessType.Write, CALLER);
  }
}
