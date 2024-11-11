package de.dlr.shepard.timeseries.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPoint;
import de.dlr.shepard.timeseries.services.ExperimentalTimeseriesCsvService;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

public class CsvConverterTest {

  @Inject
  ExperimentalTimeseriesCsvService timeseriesService;

  @Test
  void testConvertToCsv_multipleTypes_success() throws IOException {
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>(
      List.of(
        new ExperimentalTimeseriesDataPoint(88551122, 22.1),
        new ExperimentalTimeseriesDataPoint(88551123, 4),
        new ExperimentalTimeseriesDataPoint(88551124, 22.2),
        new ExperimentalTimeseriesDataPoint(88551125, true),
        new ExperimentalTimeseriesDataPoint(88551126, "Hello World")
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
      CsvConverter.convertToCsv(timeseries, dataPoints),
      StandardCharsets.UTF_8
    );

    assertEquals(expectedCsvString, actualCsvString);
  }

  @Test
  void testConvertToCsv_emptyData_success() throws IOException {
    var timeseries = TimeseriesTestDataGenerator.generateTimeseries("temperature");
    List<ExperimentalTimeseriesDataPoint> dataPoints = new ArrayList<>();

    String expectedCsvString = "";

    String actualCsvString = IOUtils.toString(
      CsvConverter.convertToCsv(timeseries, dataPoints),
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

    var expectedTimeseriesDouble = new ExperimentalTimeseries("double", "device", "location", "symbolicName", "field");
    var expectedTimeseriesInteger = new ExperimentalTimeseries(
      "integer",
      "device",
      "location",
      "symbolicName",
      "field"
    );
    var expectedTimeseriesBoolean = new ExperimentalTimeseries(
      "boolean",
      "device",
      "location",
      "symbolicName",
      "field"
    );
    var expectedTimeseriesString = new ExperimentalTimeseries("string", "device", "location", "symbolicName", "field");

    List<ExperimentalTimeseriesDataPoint> expectedDataPointsIODouble = new ArrayList<>(
      List.of(new ExperimentalTimeseriesDataPoint(88551122, 22.1), new ExperimentalTimeseriesDataPoint(88551124, 22.2))
    );

    List<ExperimentalTimeseriesDataPoint> expectedDataPointsIOInteger = new ArrayList<>(
      List.of(new ExperimentalTimeseriesDataPoint(88551123, 4), new ExperimentalTimeseriesDataPoint(88551126, 5))
    );

    List<ExperimentalTimeseriesDataPoint> expectedDataPointsIOBoolean = new ArrayList<>(
      List.of(new ExperimentalTimeseriesDataPoint(88551125, true), new ExperimentalTimeseriesDataPoint(88551128, false))
    );

    List<ExperimentalTimeseriesDataPoint> expectedDataPointsIOString = new ArrayList<>(
      List.of(new ExperimentalTimeseriesDataPoint(88551127, "Hello World"))
    );

    InputStream timeseriesDataStream = new ByteArrayInputStream(actualCsvString.getBytes(StandardCharsets.UTF_8));
    var actualTimeseriesDataIOMap = CsvConverter.convertToTimeseriesWithData(timeseriesDataStream);
    HashMap<ExperimentalTimeseries, List<ExperimentalTimeseriesDataPoint>> expectedTimeseriesDataIOMap =
      new HashMap<>();
    expectedTimeseriesDataIOMap.put(expectedTimeseriesBoolean, expectedDataPointsIOBoolean);
    expectedTimeseriesDataIOMap.put(expectedTimeseriesString, expectedDataPointsIOString);
    expectedTimeseriesDataIOMap.put(expectedTimeseriesInteger, expectedDataPointsIOInteger);
    expectedTimeseriesDataIOMap.put(expectedTimeseriesDouble, expectedDataPointsIODouble);

    assertEquals(4, actualTimeseriesDataIOMap.size());
    assertEquals(expectedTimeseriesDataIOMap, actualTimeseriesDataIOMap);
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
