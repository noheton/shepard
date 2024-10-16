package de.dlr.shepard.timeseries.io;

import de.dlr.shepard.timeseries.entities.ExperimentalTimeseriesDataPoint;
import java.util.List;

public class TimeseriesPayloadIOMapper {

  public static List<ExperimentalTimeseriesDataPoint> map(
    int timeseriesId,
    String dataPointType,
    List<TimeseriesPayloadDataPointIO> points
  ) {
    return points
      .stream()
      .map(dataPoint -> {
        var newDataPoint = new ExperimentalTimeseriesDataPoint();
        newDataPoint.setTimestamp(dataPoint.getTimestamp());
        newDataPoint.setTimeseriesId(timeseriesId);
        switch (dataPointType) {
          case "double":
            newDataPoint.setDoubleValue(Double.parseDouble(dataPoint.getValue().toString()));
            break;
          default:
        }

        return newDataPoint;
      })
      .toList();
  }
}
