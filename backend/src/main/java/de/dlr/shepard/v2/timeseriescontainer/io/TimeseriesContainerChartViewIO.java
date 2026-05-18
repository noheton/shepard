package de.dlr.shepard.v2.timeseriescontainer.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.timeseriescontainer.entities.TimeseriesContainerChartView;
import java.util.ArrayList;
import java.util.List;

/**
 * Wire shape for {@code GET / PATCH /v2/timeseries-containers/{id}/chart-view}.
 *
 * <p>RFC 7396 merge-patch semantics on PATCH: absent field = leave alone,
 * {@code null} = clear, value = replace. {@link #selectedChannels} is a
 * list — PATCH replaces the whole list (RFC 7396 doesn't have per-element
 * patch semantics for arrays).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimeseriesContainerChartViewIO(
  /**
   * Curated channel selection. Each entry is the 5-tuple key
   * {@code measurement|device|location|symbolicName|field} (pipe-separated).
   * Empty list = "no curated view, show all channels" (frontend default).
   */
  List<String> selectedChannels,

  /** Millis-epoch of the last PATCH. Server-set; ignored on PATCH. */
  Long updatedAt,

  /** Username of the last PATCH author. Server-set; ignored on PATCH. */
  String updatedBy
) {
  public static TimeseriesContainerChartViewIO from(TimeseriesContainerChartView view) {
    if (view == null) {
      return new TimeseriesContainerChartViewIO(new ArrayList<>(), null, null);
    }
    return new TimeseriesContainerChartViewIO(
      view.getSelectedChannels() != null
        ? new ArrayList<>(view.getSelectedChannels())
        : new ArrayList<>(),
      view.getUpdatedAt(),
      view.getUpdatedBy()
    );
  }
}
