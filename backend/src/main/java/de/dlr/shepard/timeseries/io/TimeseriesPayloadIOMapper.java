package de.dlr.shepard.timeseries.io;

import de.dlr.shepard.timeseries.entities.ExperimentalTimeseriesDataPoint;
import de.dlr.shepard.timeseries.utilities.LocalDateTimeHelper;
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
        newDataPoint.setTime(LocalDateTimeHelper.fromMilliseconds(dataPoint.getTimestamp()));
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
