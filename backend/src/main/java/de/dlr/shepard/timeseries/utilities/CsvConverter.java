package de.dlr.shepard.timeseries.utilities;

import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvException;
import de.dlr.shepard.exceptions.InvalidRequestException;
import de.dlr.shepard.influxDB.TimeseriesCsv;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPoint;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RequestScoped
public class CsvConverter {

  public InputStream convertToCsv(ExperimentalTimeseries timeseries, List<ExperimentalTimeseriesDataPoint> dataPoints) {
    var timeseriesDataAsMap = new HashMap<ExperimentalTimeseries, List<ExperimentalTimeseriesDataPoint>>();
    timeseriesDataAsMap.put(timeseries, dataPoints);
    return convertToCsv(timeseriesDataAsMap);
  }

  public InputStream convertToCsv(
    HashMap<ExperimentalTimeseries, List<ExperimentalTimeseriesDataPoint>> timeseriesDataMap
  ) {
    Path tmpfile = null;
    try {
      tmpfile = Files.createTempFile("shepard", ".csv");
      try (var stream = Files.newOutputStream(tmpfile); var streamWriter = new OutputStreamWriter(stream)) {
        StatefulBeanToCsv<TimeseriesCsv> writer = new StatefulBeanToCsvBuilder<TimeseriesCsv>(streamWriter)
          .withApplyQuotesToAll(false)
          .build();

        for (var timeseriesData : timeseriesDataMap.entrySet()) {
          try {
            writer.write(convertPayloadToCsv(timeseriesData.getKey(), timeseriesData.getValue()));
          } catch (CsvException e) {
            Log.error("CsvException while writing stream", e);
            throw new InvalidRequestException();
          }
        }
      }
    } catch (IOException e) {
      Log.error("IOException while creating or writing to the temp file", e);
      throw new InvalidRequestException();
    }

    InputStream result = null;
    if (tmpfile != null) {
      try {
        result = Files.newInputStream(tmpfile);
      } catch (IOException e) {
        Log.error("IOException while opening the temp file for reading", e);
        throw new InvalidRequestException();
      }
    }
    return result;
  }

  private List<TimeseriesCsv> convertPayloadToCsv(
    ExperimentalTimeseries timeseries,
    List<ExperimentalTimeseriesDataPoint> dataPoints
  ) {
    var result = new ArrayList<TimeseriesCsv>(dataPoints.size());
    for (var dataPoint : dataPoints) {
      var tsc = new TimeseriesCsv(
        dataPoint.getTimestamp(),
        timeseries.getMeasurement(),
        timeseries.getDevice(),
        timeseries.getLocation(),
        timeseries.getSymbolicName(),
        timeseries.getField(),
        dataPoint.getValue()
      );
      result.add(tsc);
    }
    return result;
  }
}
