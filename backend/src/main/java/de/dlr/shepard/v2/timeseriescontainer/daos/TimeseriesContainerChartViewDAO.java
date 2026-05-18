package de.dlr.shepard.v2.timeseriescontainer.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.v2.timeseriescontainer.entities.TimeseriesContainerChartView;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/**
 * TS_CHART_VIEW1 — DAO for {@link TimeseriesContainerChartView}.
 *
 * <p>Not a global singleton (unlike {@code InstanceRorConfigDAO}) —
 * one node per container. Looked up by {@code containerAppId} rather
 * than the view's own {@code appId} because the calling code always
 * starts from a container.
 */
@ApplicationScoped
public class TimeseriesContainerChartViewDAO extends GenericDAO<TimeseriesContainerChartView> {

  @Override
  public Class<TimeseriesContainerChartView> getEntityType() {
    return TimeseriesContainerChartView.class;
  }

  /**
   * Look up the chart-view for a TimeseriesContainer by the container's
   * appId. Returns {@code null} if no view has been configured yet
   * (frontend falls back to "show all channels").
   */
  public TimeseriesContainerChartView findByContainerAppId(String containerAppId) {
    String query =
      "MATCH (v:TimeseriesContainerChartView) " +
      "WHERE v.containerAppId = $containerAppId " +
      "RETURN v " +
      "LIMIT 1";
    var iter = findByQuery(query, Map.of("containerAppId", containerAppId)).iterator();
    return iter.hasNext() ? iter.next() : null;
  }
}
