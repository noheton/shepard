package de.dlr.shepard.data.timeseries.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.data.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

class CsvColumnLineProviderTest {

  @Test
  void testMultipleTimeseries() throws IOException {
    var timeseries1 = TimeseriesTestDataGenerator.generateTimeseriesWithDataPoints(
      "temperature",
      List.of(
        new TimeseriesDataPoint(88551122, 22.1),
        new TimeseriesDataPoint(88551123, 4),
        new TimeseriesDataPoint(88551124, 22.2)
      )
    );
    var timeseries2 = TimeseriesTestDataGenerator.generateTimeseriesWithDataPoints(
      "pressure",
      List.of(
        new TimeseriesDataPoint(88551125, 21.2),
        new TimeseriesDataPoint(88551122, 23),
        new TimeseriesDataPoint(88551123, 51)
      )
    );

    String expected =
      """
      timestamp,temperature-device-location-symbolicName-field,pressure-device-location-symbolicName-field
      88551122,22.1,23
      88551123,4,51
      88551124,22.2,
      88551125,,21.2
      """;

    List<TimeseriesWithDataPoints> timeseriesWithDataPoints = List.of(timeseries1, timeseries2);

    CsvColumnLineProvider provider = new CsvColumnLineProvider(timeseriesWithDataPoints);

    String actual = IOUtils.toString(new CsvInputStream(provider), StandardCharsets.UTF_8);

    assertEquals(expected, actual);
  }

  @Test
  void testNoTimeseries() throws IOException {
    List<TimeseriesWithDataPoints> timeseriesWithDataPoints = Collections.emptyList();

    CsvColumnLineProvider provider = new CsvColumnLineProvider(timeseriesWithDataPoints);

    String actual = IOUtils.toString(new CsvInputStream(provider), StandardCharsets.UTF_8);

    // We always expect a header line, without any timeseries this will only contain
    //  timestamp and no further rows of data
    assertEquals("timestamp\n", actual);
  }

  @Test
  void testEmptyTimeseries() throws IOException {
    List<TimeseriesWithDataPoints> timeseriesWithDataPoints = List.of(
      TimeseriesTestDataGenerator.generateTimeseriesWithDataPoints("pressure", Collections.emptyList())
    );

    CsvColumnLineProvider provider = new CsvColumnLineProvider(timeseriesWithDataPoints);

    String actual = IOUtils.toString(new CsvInputStream(provider), StandardCharsets.UTF_8);

    // Even if no data is available, the header should still be generated for all timeseries
    String expected =
      """
      timestamp,pressure-device-location-symbolicName-field
      """;

    assertEquals(expected, actual);
  }

  @Test
  void testPartiallyEmptyTimeseries() throws IOException {
    List<TimeseriesWithDataPoints> timeseriesWithDataPoints = List.of(
      TimeseriesTestDataGenerator.generateTimeseriesWithDataPoints("pressure", Collections.emptyList()),
      TimeseriesTestDataGenerator.generateTimeseriesWithDataPoints(
        "temperature",
        List.of(
          new TimeseriesDataPoint(88551122, 22.1),
          new TimeseriesDataPoint(88551123, 4),
          new TimeseriesDataPoint(88551124, 22.2)
        )
      )
    );

    CsvColumnLineProvider provider = new CsvColumnLineProvider(timeseriesWithDataPoints);

    String actual = IOUtils.toString(new CsvInputStream(provider), StandardCharsets.UTF_8);

    // If any timeseries does not contain any data, its header should still be generated and a
    // completely empty column should be contained in the output
    String expected =
      """
      timestamp,pressure-device-location-symbolicName-field,temperature-device-location-symbolicName-field
      88551122,,22.1
      88551123,,4
      88551124,,22.2
      """;

    assertEquals(expected, actual);
  }
}
