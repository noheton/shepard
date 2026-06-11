package de.dlr.shepard.v2.timeseriescontainer.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.timeseriescontainer.entities.TimeseriesContainerChartView;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesContainerChartViewIO;
import de.dlr.shepard.v2.timeseriescontainer.services.TimeseriesContainerChartViewService;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link TimeseriesContainerChartViewRest} — TS_CHART_VIEW1.
 *
 * <p>Verifies that the path param is resolved via {@code getContainerByAppId}
 * and that Read / Write permission gates are applied correctly.
 */
class TimeseriesContainerChartViewRestTest {

  static final long CONTAINER_ID = 42L;
  static final String CONTAINER_APP_ID = "01928eaa-0000-7000-8000-000000000042";

  @Mock
  TimeseriesContainerService containerService;

  @Mock
  TimeseriesContainerChartViewService chartViewService;

  @Mock
  TimeseriesContainer container;

  TimeseriesContainerChartViewRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new TimeseriesContainerChartViewRest();
    resource.containerService = containerService;
    resource.chartViewService = chartViewService;

    when(container.getId()).thenReturn(CONTAINER_ID);
    when(containerService.getContainerByAppId(CONTAINER_APP_ID)).thenReturn(container);
  }

  // ── GET ───────────────────────────────────────────────────────────────────

  @Test
  void get_returns200WithChartView() {
    TimeseriesContainerChartView view = new TimeseriesContainerChartView();
    view.setSelectedChannels(List.of("m|d|l|s|f"));
    when(chartViewService.find(CONTAINER_ID)).thenReturn(view);

    Response r = resource.get(CONTAINER_APP_ID);

    assertThat(r.getStatus()).isEqualTo(200);
    TimeseriesContainerChartViewIO body = (TimeseriesContainerChartViewIO) r.getEntity();
    assertThat(body.selectedChannels()).containsExactly("m|d|l|s|f");
    verify(containerService).getContainerByAppId(CONTAINER_APP_ID);
    verify(chartViewService).find(CONTAINER_ID);
  }

  @Test
  void get_returns200WithEmptyWhenNoView() {
    when(chartViewService.find(CONTAINER_ID)).thenReturn(null);

    Response r = resource.get(CONTAINER_APP_ID);

    assertThat(r.getStatus()).isEqualTo(200);
    TimeseriesContainerChartViewIO body = (TimeseriesContainerChartViewIO) r.getEntity();
    assertThat(body.selectedChannels()).isEmpty();
  }

  // ── PATCH ─────────────────────────────────────────────────────────────────

  @Test
  void patch_returns200WithUpdatedView() {
    TimeseriesContainerChartView updated = new TimeseriesContainerChartView();
    updated.setSelectedChannels(List.of("a|b|c|d|e"));
    when(chartViewService.patch(eq(CONTAINER_ID), any(TimeseriesContainerChartViewIO.class)))
      .thenReturn(updated);

    TimeseriesContainerChartViewIO patchBody =
      new TimeseriesContainerChartViewIO(List.of("a|b|c|d|e"), null, null);
    Response r = resource.patch(CONTAINER_APP_ID, patchBody);

    assertThat(r.getStatus()).isEqualTo(200);
    TimeseriesContainerChartViewIO body = (TimeseriesContainerChartViewIO) r.getEntity();
    assertThat(body.selectedChannels()).containsExactly("a|b|c|d|e");
  }

  @Test
  void patch_calls_assertIsAllowedToEditContainer() {
    TimeseriesContainerChartView updated = new TimeseriesContainerChartView();
    when(chartViewService.patch(eq(CONTAINER_ID), any())).thenReturn(updated);

    TimeseriesContainerChartViewIO patchBody =
      new TimeseriesContainerChartViewIO(List.of(), null, null);
    resource.patch(CONTAINER_APP_ID, patchBody);

    verify(containerService).assertIsAllowedToEditContainer(CONTAINER_ID);
  }
}
