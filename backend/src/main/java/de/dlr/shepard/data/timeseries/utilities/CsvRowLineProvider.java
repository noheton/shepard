package de.dlr.shepard.data.timeseries.utilities;

import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvException;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;

public class CsvRowLineProvider implements CsvLineProvider {

  private final List<TimeseriesWithDataPoints> timeseriesWithDataPoints;

  private final StringWriter csvStringWriter;
  private final StatefulBeanToCsv<CsvTimeseriesDataPoint> csvWriter;

  private int timeseriesIndex = -1;
  private int datapointIndex = -1;

  public CsvRowLineProvider(@Nonnull List<TimeseriesWithDataPoints> timeseriesWithDataPoints) {
    this.timeseriesWithDataPoints = Objects.requireNonNull(timeseriesWithDataPoints);
    csvStringWriter = new StringWriter();
    csvWriter = new StatefulBeanToCsvBuilder<CsvTimeseriesDataPoint>(csvStringWriter)
      .withApplyQuotesToAll(false)
      .build();
  }

  @Override
  public String readCsvLine() throws IOException {
    while (timeseriesIndex < timeseriesWithDataPoints.size()) {
      if (timeseriesIndex >= 0) {
        if (datapointIndex < timeseriesWithDataPoints.get(timeseriesIndex).getPoints().size() - 1) {
          datapointIndex++;
          break;
        } else {
          datapointIndex = -1;
          timeseriesIndex++;
        }
      } else {
        timeseriesIndex++;
      }
    }

    if (timeseriesIndex >= timeseriesWithDataPoints.size() || datapointIndex < 0) return "";

    var timeseries = timeseriesWithDataPoints.get(timeseriesIndex).getTimeseries();
    var dataPoint = timeseriesWithDataPoints.get(timeseriesIndex).getPoints().get(datapointIndex);

    try {
      csvWriter.write(
        new CsvTimeseriesDataPoint(
          dataPoint.getTimestamp(),
          timeseries.measurement(),
          timeseries.device(),
          timeseries.location(),
          timeseries.symbolicName(),
          timeseries.field(),
          dataPoint.getValue()
        )
      );

      var lineBuffer = csvStringWriter.toString();
      csvStringWriter.getBuffer().setLength(0);
      return lineBuffer;
    } catch (CsvException ex) {
      throw new IOException(ex);
    }
  }
}
