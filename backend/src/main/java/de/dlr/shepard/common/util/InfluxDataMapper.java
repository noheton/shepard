package de.dlr.shepard.common.util;

import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxPoint;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseries;
import de.dlr.shepard.data.timeseries.migration.influxtimeseries.InfluxTimeseriesDataType;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import java.util.List;

public final class InfluxDataMapper {

  public static Timeseries mapToTimeseries(InfluxTimeseries timeseries) {
    return new Timeseries(
      timeseries.getMeasurement(),
      timeseries.getDevice(),
      timeseries.getLocation(),
      timeseries.getSymbolicName(),
      timeseries.getField()
    );
  }

  public static List<TimeseriesDataPoint> mapToTimeseriesDataPoints(List<InfluxPoint> influxPoints) {
    return influxPoints
      .stream()
      .map(point -> {
        return new TimeseriesDataPoint(point.getTimeInNanoseconds(), point.getValue());
      })
      .toList();
  }

  public static DataPointValueType mapToValueType(InfluxTimeseriesDataType dataType) {
    switch (dataType) {
      case BOOLEAN:
        return DataPointValueType.Boolean;
      case FLOAT:
        return DataPointValueType.Double;
      case INTEGER:
        return DataPointValueType.Integer;
      case STRING:
        return DataPointValueType.String;
      default:
        throw new IllegalArgumentException(String.format("Cannot map %s to DataPointValueType.", dataType));
    }
  }
}
