package de.dlr.shepard.data.timeseries.utilities;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.TimeseriesFiveTuple;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A {@link CsvLineProvider} that provides CSV rows in a column based format, that is each
 * row of the resulting CSV file represents one timestamp, and each timeseries is put in its
 * own column.
 * <p>
 * It should be noted that this provider may generate very sparse CSV files if the timestamps
 * of several timeseries do not exactly match
 */
public class CsvColumnLineProvider implements CsvLineProvider {

  private final StringWriter csvStringWriter;
  private final ICSVWriter csvWriter;

  // Number of timeseries
  private final int size;

  // True if the header has been written
  private boolean firstLineWritten = false;
  private final TimeseriesFiveTuple[] timeseriesArray;

  // The TreeMap containing all data points is sorted according to the order of the keys (timestamps)
  private final TreeMap<Long, Object[]> dataPoints = new TreeMap<>();

  public CsvColumnLineProvider(@Nonnull List<TimeseriesWithDataPoints> timeseriesWithDataPoints) {
    Objects.requireNonNull(timeseriesWithDataPoints);
    size = timeseriesWithDataPoints.size();
    timeseriesArray = new TimeseriesFiveTuple[size];

    // Sort the data points according to their timestamp
    for (int i = 0; i < size; i++) {
      // timeseriesArray is necessary for header generation
      timeseriesArray[i] = timeseriesWithDataPoints.get(i).getTimeseries();
      for (var point : timeseriesWithDataPoints.get(i).getPoints()) {
        // For each individual timestamp, an Object[] is generated and populated
        dataPoints.computeIfAbsent(point.getTimestamp(), k -> new Object[size])[i] = point.getValue();
      }
    }

    csvStringWriter = new StringWriter();
    csvWriter = new CSVWriterBuilder(csvStringWriter).build();
  }

  @Override
  public String readCsvLine() throws IOException {
    // Generate header as first line
    if (!firstLineWritten) {
      return generateHeader();
    }

    var entry = dataPoints.pollFirstEntry();
    // If no entry can be polled from map, no more data is available
    if (entry == null) return "";

    String[] line = new String[size + 1];
    // First column is always the timestamp
    line[0] = entry.getKey().toString();

    for (int i = 0; i < size; i++) {
      line[i + 1] = Objects.toString(entry.getValue()[i], "");
    }

    csvWriter.writeNext(line, false);
    var lineBuffer = csvStringWriter.toString();
    csvStringWriter.getBuffer().setLength(0);
    return lineBuffer;
  }

  /**
   * Generate the header row
   * @return One CSV row with the header information.
   */
  private String generateHeader() {
    String[] header = new String[size + 1];
    header[0] = "timestamp";
    for (int i = 0; i < size; i++) {
      header[i + 1] = String.join(
        "-",
        timeseriesArray[i].getMeasurement(),
        timeseriesArray[i].getDevice(),
        timeseriesArray[i].getLocation(),
        timeseriesArray[i].getSymbolicName(),
        timeseriesArray[i].getField()
      );
    }
    csvWriter.writeNext(header, false);
    firstLineWritten = true;
    var lineBuffer = csvStringWriter.toString();
    csvStringWriter.getBuffer().setLength(0);
    return lineBuffer;
  }
}
