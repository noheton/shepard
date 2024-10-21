package de.dlr.shepard.timeseries.io;

import de.dlr.shepard.timeseries.model.ExperimentalDataPointValueTypes;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointEntity;
import de.dlr.shepard.timeseries.utilities.LocalDateTimeHelper;
import java.util.List;

public class TimeseriesPayloadIOMapper {

  public static List<ExperimentalTimeseriesDataPointEntity> map(
    int timeseriesId,
    ExperimentalDataPointValueTypes dataPointType,
    List<ExperimentalTimeseriesPayloadDataPointIO> points
  ) {
    return points
      .stream()
      .map(dataPoint -> {
        var newDataPoint = new ExperimentalTimeseriesDataPointEntity();
        newDataPoint.setTime(LocalDateTimeHelper.fromMilliseconds(dataPoint.getTimestamp()));
        newDataPoint.setTimeseriesId(timeseriesId);
        switch (dataPointType) {
          case Double:
            newDataPoint.setDoubleValue(Double.parseDouble(dataPoint.getValue().toString()));
            break;
          // Todo: add other types here
          default:
        }

        return newDataPoint;
      })
      .toList();
  }
}
