package de.dlr.shepard.v2.containers.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.timeseriescontainer.entities.TimeseriesContainerChartView;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesContainerChartViewIO;
import de.dlr.shepard.v2.timeseriescontainer.services.TimeseriesContainerChartViewService;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-CONT-NS-COLLAPSE-3 — unit tests for the chart-view handler methods
 * on {@link TimeseriesContainerKindHandler}.
 */
class TimeseriesContainerKindHandlerChartViewTest {

  static final long CONTAINER_ID = 42L;
  static final String APP_ID = "01928eaa-0000-7000-8000-000000000042";

  @Mock
  TimeseriesContainerService service;

  @Mock
  TimeseriesContainerDAO dao;

  @Mock
  TimeseriesContainerChartViewService chartViewService;

  @Mock
  TimeseriesContainer container;

  TimeseriesContainerKindHandler handler;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    handler = new TimeseriesContainerKindHandler();
    handler.service = service;
    handler.dao = dao;
    handler.chartViewService = chartViewService;

    when(container.getId()).thenReturn(CONTAINER_ID);
    when(container.isDeleted()).thenReturn(false);
    when(dao.findByAppId(APP_ID)).thenReturn(Optional.of(container));
  }

  // ── getChartView ─────────────────────────────────────────────────────────

  @Test
  void getChartView_returnsViewWithChannels() {
    TimeseriesContainerChartView view = new TimeseriesContainerChartView();
    view.setSelectedChannels(List.of("m|d|l|s|f"));
    when(chartViewService.find(CONTAINER_ID)).thenReturn(view);

    Optional<TimeseriesContainerChartViewIO> result = handler.getChartView(APP_ID);

    assertThat(result).isPresent();
    assertThat(result.get().selectedChannels()).containsExactly("m|d|l|s|f");
    verify(chartViewService).find(CONTAINER_ID);
  }

  @Test
  void getChartView_returnsEmptyChannelsWhenNoViewPersisted() {
    when(chartViewService.find(CONTAINER_ID)).thenReturn(null);

    Optional<TimeseriesContainerChartViewIO> result = handler.getChartView(APP_ID);

    assertThat(result).isPresent();
    assertThat(result.get().selectedChannels()).isEmpty();
  }

  @Test
  void getChartView_throwsNotFoundForUnknownAppId() {
    when(dao.findByAppId("unknown-id")).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> handler.getChartView("unknown-id"));
  }

  // ── patchChartView ───────────────────────────────────────────────────────

  @Test
  void patchChartView_returnsUpdatedView() {
    TimeseriesContainerChartView updated = new TimeseriesContainerChartView();
    updated.setSelectedChannels(List.of("a|b|c|d|e"));
    when(chartViewService.patch(eq(CONTAINER_ID), any(TimeseriesContainerChartViewIO.class)))
      .thenReturn(updated);

    TimeseriesContainerChartViewIO patch =
      new TimeseriesContainerChartViewIO(List.of("a|b|c|d|e"), null, null);
    Optional<TimeseriesContainerChartViewIO> result = handler.patchChartView(APP_ID, patch);

    assertThat(result).isPresent();
    assertThat(result.get().selectedChannels()).containsExactly("a|b|c|d|e");
    verify(chartViewService).patch(CONTAINER_ID, patch);
  }

  @Test
  void patchChartView_throwsNotFoundForUnknownAppId() {
    when(dao.findByAppId("bad-id")).thenReturn(Optional.empty());

    TimeseriesContainerChartViewIO patch =
      new TimeseriesContainerChartViewIO(List.of(), null, null);
    assertThrows(NotFoundException.class, () -> handler.patchChartView("bad-id", patch));
  }
}
