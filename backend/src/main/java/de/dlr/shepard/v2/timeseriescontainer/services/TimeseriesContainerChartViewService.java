package de.dlr.shepard.v2.timeseriescontainer.services;

import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.v2.timeseriescontainer.daos.TimeseriesContainerChartViewDAO;
import de.dlr.shepard.v2.timeseriescontainer.entities.TimeseriesContainerChartView;
import de.dlr.shepard.v2.timeseriescontainer.io.TimeseriesContainerChartViewIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * TS_CHART_VIEW1 — service layer for the per-container chart-view.
 *
 * <p>Lazily creates the persisted view on first PATCH; reads return
 * the empty default when no view has been configured yet (frontend
 * interprets empty selection as "show all channels").
 */
@ApplicationScoped
public class TimeseriesContainerChartViewService {

  @Inject
  TimeseriesContainerChartViewDAO viewDAO;

  @Inject
  TimeseriesContainerService containerService;

  @Inject
  UserService userService;

  /**
   * Read the current chart-view for the given container. The caller is
   * responsible for the Read-permission check (we go through
   * {@link TimeseriesContainerService#getContainer(long)} which enforces
   * it).
   *
   * @return the persisted view, or {@code null} if none configured.
   */
  public TimeseriesContainerChartView find(long containerOgmId) {
    TimeseriesContainer container = containerService.getContainer(containerOgmId);
    String appId = container.getAppId();
    if (appId == null) return null;
    return viewDAO.findByContainerAppId(appId);
  }

  /**
   * RFC 7396 merge-patch the chart-view for a container. Caller must
   * have already enforced Write permission. Lazily creates the view
   * row on first call.
   *
   * @param containerOgmId the OGM id of the target container.
   * @param patch          the inbound patch — non-null {@code selectedChannels}
   *                       replaces the current list.
   * @return the updated (or newly-created) view.
   */
  public TimeseriesContainerChartView patch(
    long containerOgmId,
    TimeseriesContainerChartViewIO patch
  ) {
    TimeseriesContainer container = containerService.getContainer(containerOgmId);
    String containerAppId = container.getAppId();
    if (containerAppId == null) {
      throw new IllegalStateException(
        "TimeseriesContainer has no appId — cannot persist chart-view (pre-L2a row?)"
      );
    }

    TimeseriesContainerChartView view = viewDAO.findByContainerAppId(containerAppId);
    if (view == null) {
      view = new TimeseriesContainerChartView();
      view.setContainerAppId(containerAppId);
    }

    if (patch.selectedChannels() != null) {
      view.setSelectedChannels(new ArrayList<>(patch.selectedChannels()));
    }
    view.setUpdatedAt(System.currentTimeMillis());
    view.setUpdatedBy(userService.getCurrentUser().getUsername());

    TimeseriesContainerChartView saved = viewDAO.createOrUpdate(view);
    Log.infof(
      "TS_CHART_VIEW1: %s for container %s by %s (%d channels)",
      view.getAppId() == null ? "created" : "updated",
      containerAppId,
      view.getUpdatedBy(),
      view.getSelectedChannels().size()
    );
    return saved;
  }
}
