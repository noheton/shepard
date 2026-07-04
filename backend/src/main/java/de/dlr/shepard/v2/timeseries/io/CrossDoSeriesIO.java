package de.dlr.shepard.v2.timeseries.io;

import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS-CROSS-DO-VIEW-1 — one resolved DataObject's series in the cross-DO response.
 *
 * <p>One entry per DataObject the caller requested AND has Read permission on.
 * DataObjects without a matching channel still appear with an empty
 * {@code points} list — the consumer can render an "empty cell" placeholder
 * without re-querying.
 *
 * <p>Point timestamps are absolute UTC nanoseconds; the consumer normalises
 * to within-DO relative time at render (e.g. subtract the series' first
 * timestamp). Keeping absolute timestamps server-side preserves the caller's
 * ability to align series on wall clock when desired.
 */
@Schema(
  name = "CrossDoSeries",
  description = "One DataObject's series in the cross-DO bulk response."
)
public record CrossDoSeriesIO(

  @Schema(description = "DataObject appId (UUID v7 string).")
  String dataObjectAppId,

  @Schema(description = "DataObject name (cached at response time).")
  String dataObjectName,

  @Schema(
    description = "Canonical channel-key predicate IRI that matched, echoed for client convenience."
  )
  String channelKey,

  @Schema(description = "Symbolic name of the resolved channel, or null when no channel matched.")
  String channelSymbolicName,

  @Schema(description = "LTTB-downsampled data points; absolute UTC nanoseconds.")
  List<TimeseriesDataPoint> points
) {}
