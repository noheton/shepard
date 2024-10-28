package de.dlr.shepard.timeseries.utilities;

import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvException;
import de.dlr.shepard.influxDB.TimeseriesCsv;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesData;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@RequestScoped
public class CsvConverter {

  public InputStream convertToCsv(List<ExperimentalTimeseriesData> timeseriesDataList) throws IOException {
    var tmpfile = Files.createTempFile("shepard", ".csv");
    var stream = Files.newOutputStream(tmpfile);
    var streamWriter = new OutputStreamWriter(stream);
    var writer = new StatefulBeanToCsvBuilder<TimeseriesCsv>(streamWriter).withApplyQuotesToAll(false).build();
    Log.debugf("Write temp file to: %s", tmpfile.toAbsolutePath().toString());

    for (var timeseriesData : timeseriesDataList) {
      try {
        writer.write(convertPayloadToCsv(timeseriesData));
      } catch (CsvException e) {
        Log.error("CsvException while writing stream");
      }
    }

    streamWriter.close();
    var result = Files.newInputStream(tmpfile);
    return result;
  }

  private List<TimeseriesCsv> convertPayloadToCsv(ExperimentalTimeseriesData timeseriesData) {
    var timeseries = timeseriesData.getTimeseries();
    var result = new ArrayList<TimeseriesCsv>(timeseriesData.getDataPoints().size());
    for (var dataPoint : timeseriesData.getDataPoints()) {
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
