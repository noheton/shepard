package de.dlr.shepard.data.timeseries.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.data.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesTuple;
import de.dlr.shepard.data.timeseries.model.enums.CsvFormat;
import de.dlr.shepard.data.timeseries.services.TimeseriesCsvService;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

public class CsvConverterTest {

  @Inject
  TimeseriesCsvService timeseriesService;

  @Test
  void testConvertToCsv_multipleTypes_success() throws IOException {
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        new TimeseriesDataPoint(88551122, 22.1),
        new TimeseriesDataPoint(88551123, 4),
        new TimeseriesDataPoint(88551124, 22.2),
        new TimeseriesDataPoint(88551125, true),
        new TimeseriesDataPoint(88551126, "Hello World")
      )
    );

    String expectedCsvString =
      """
      DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE
      device,field,location,temperature,symbolicName,88551122,22.1
      device,field,location,temperature,symbolicName,88551123,4
      device,field,location,temperature,symbolicName,88551124,22.2
      device,field,location,temperature,symbolicName,88551125,true
      device,field,location,temperature,symbolicName,88551126,Hello World
      """;

    String actualCsvString = IOUtils.toString(
      CsvConverter.convertToCsv(timeseries, dataPoints, CsvFormat.ROW),
      StandardCharsets.UTF_8
    );

    assertEquals(expectedCsvString, actualCsvString);
  }

  @Test
  void testConvertToCsv_emptyData_success() throws IOException {
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<TimeseriesDataPoint> dataPoints = new ArrayList<>();

    String expectedCsvString = "";

    String actualCsvString = IOUtils.toString(
      CsvConverter.convertToCsv(timeseries, dataPoints, CsvFormat.ROW),
      StandardCharsets.UTF_8
    );

    assertEquals(expectedCsvString, actualCsvString);
  }

  @Test
  void testConvertToTimeseriesWithData_multipleTypes_success() throws IOException {
    String actualCsvString =
      """
      DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE
      device,field,location,double,symbolicName,88551122,22.1
      device,field,location,integer,symbolicName,88551123,4
      device,field,location,double,symbolicName,88551124,22.2
      device,field,location,boolean,symbolicName,88551125,true
      device,field,location,integer,symbolicName,88551126,5
      device,field,location,string,symbolicName,88551127,Hello World
      device,field,location,boolean,symbolicName,88551128,false
      """;

    var expectedTimeseriesDouble = new TimeseriesTuple("double", "device", "location", "symbolicName", "field");
    var expectedTimeseriesInteger = new TimeseriesTuple("integer", "device", "location", "symbolicName", "field");
    var expectedTimeseriesBoolean = new TimeseriesTuple("boolean", "device", "location", "symbolicName", "field");
    var expectedTimeseriesString = new TimeseriesTuple("string", "device", "location", "symbolicName", "field");

    List<TimeseriesDataPoint> expectedDataPointsIODouble = new ArrayList<>(
      List.of(new TimeseriesDataPoint(88551122, 22.1), new TimeseriesDataPoint(88551124, 22.2))
    );

    List<TimeseriesDataPoint> expectedDataPointsIOInteger = new ArrayList<>(
      List.of(new TimeseriesDataPoint(88551123, 4), new TimeseriesDataPoint(88551126, 5))
    );

    List<TimeseriesDataPoint> expectedDataPointsIOBoolean = new ArrayList<>(
      List.of(new TimeseriesDataPoint(88551125, true), new TimeseriesDataPoint(88551128, false))
    );

    List<TimeseriesDataPoint> expectedDataPointsIOString = new ArrayList<>(
      List.of(new TimeseriesDataPoint(88551127, "Hello World"))
    );

    InputStream timeseriesDataStream = new ByteArrayInputStream(actualCsvString.getBytes(StandardCharsets.UTF_8));

    List<TimeseriesWithDataPoints> actualTimeseriesWithDataPointsList = CsvConverter.convertToTimeseriesWithData(
      timeseriesDataStream
    );
    List<TimeseriesWithDataPoints> expectedTimeseriesWithDataPointsList = List.of(
      new TimeseriesWithDataPoints(expectedTimeseriesBoolean, expectedDataPointsIOBoolean),
      new TimeseriesWithDataPoints(expectedTimeseriesInteger, expectedDataPointsIOInteger),
      new TimeseriesWithDataPoints(expectedTimeseriesString, expectedDataPointsIOString),
      new TimeseriesWithDataPoints(expectedTimeseriesDouble, expectedDataPointsIODouble)
    );

    assertEquals(4, actualTimeseriesWithDataPointsList.size());
    assertTrue(actualTimeseriesWithDataPointsList.containsAll(expectedTimeseriesWithDataPointsList));
    assertTrue(expectedTimeseriesWithDataPointsList.containsAll(actualTimeseriesWithDataPointsList));
  }

  @Test
  void testConvertToTimeseriesWithData_emptyData() throws IOException {
    String actualCsvString = "DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE";
    InputStream timeseriesDataStream = new ByteArrayInputStream(actualCsvString.getBytes(StandardCharsets.UTF_8));
    var actualTimeseriesDataIOMap = CsvConverter.convertToTimeseriesWithData(timeseriesDataStream);
    assertEquals(0, actualTimeseriesDataIOMap.size());
  }

  @Test
  void testConvertToTimeseriesWithData_csvFormatError_failure() throws IOException {
    String wrongFormatCSV =
      """
      DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE
      device,field,location,double,symbolicName,88551122,22.1,,
      device,field,location,integer,symbolicName,88551123,4
      device,field,location,boolean,symbolicName,88551128,false
      """;

    InputStream timeseriesDataStream = new ByteArrayInputStream(wrongFormatCSV.getBytes(StandardCharsets.UTF_8));

    // this RuntimeException is a InvalidBodyException wrapped into a runtime exception
    RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
      CsvConverter.convertToTimeseriesWithData(timeseriesDataStream);
    });

    Throwable cause = thrown.getCause();
    assertEquals(InvalidBodyException.class, cause.getClass());
    assertEquals("Number of data fields does not match number of headers.", cause.getMessage());
  }
}
