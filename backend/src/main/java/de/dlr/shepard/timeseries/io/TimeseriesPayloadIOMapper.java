package de.dlr.shepard.timeseries.io;

import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.timeseries.model.ExperimentalDataPointValueTypes;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointEntity;
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
        newDataPoint.setTime(dataPoint.getTimestamp());
        newDataPoint.setTimeseriesId(timeseriesId);
        switch (dataPointType) {
          case Double:
            newDataPoint.setDoubleValue(Double.parseDouble(dataPoint.getValue().toString()));
            break;
          case Boolean:
            newDataPoint.setBooleanValue(Boolean.parseBoolean(dataPoint.getValue().toString()));
            break;
          case Integer:
            newDataPoint.setIntValue(Integer.parseInt(dataPoint.getValue().toString()));
            break;
          case String:
            newDataPoint.setStringValue(dataPoint.getValue().toString());
            break;
          default:
            throw new InvalidRequestException("DataPoint has an unsupported data type.");
        }

        return newDataPoint;
      })
      .toList();
  }
}
