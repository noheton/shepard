package de.dlr.shepard.data.timeseries.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.data.timeseries.TimeseriesTestDataGenerator;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
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

    var expectedTimeseriesDouble = new Timeseries("double", "device", "location", "symbolicName", "field");
    var expectedTimeseriesInteger = new Timeseries("integer", "device", "location", "symbolicName", "field");
    var expectedTimeseriesBoolean = new Timeseries("boolean", "device", "location", "symbolicName", "field");
    var expectedTimeseriesString = new Timeseries("string", "device", "location", "symbolicName", "field");

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

  // ── Binary channel detection (TS-BINARY1) ─────────────────────────────────

  @Test
  void isBinaryChannel_nameContainsDigital_returnsTrue() {
    var ts = new Timeseries("digital_valve", "device", "location", "sym", "field");
    var pts = List.of(new TimeseriesDataPoint(1L, 0.0), new TimeseriesDataPoint(2L, 1.0));
    assertTrue(CsvConverter.isBinaryChannel(ts, pts));
  }

  @Test
  void isBinaryChannel_fieldContainsStatus_returnsTrue() {
    var ts = new Timeseries("temperature", "device", "location", "sym", "valve_status");
    var pts = List.of(new TimeseriesDataPoint(1L, 0.0), new TimeseriesDataPoint(2L, 1.0));
    assertTrue(CsvConverter.isBinaryChannel(ts, pts));
  }

  @Test
  void isBinaryChannel_onlyZeroAndOne_returnsTrue() {
    var ts = new Timeseries("temperature", "device", "location", "sym", "field");
    var pts = List.of(
      new TimeseriesDataPoint(1L, 0.0),
      new TimeseriesDataPoint(2L, 1.0),
      new TimeseriesDataPoint(3L, 0.0)
    );
    assertTrue(CsvConverter.isBinaryChannel(ts, pts));
  }

  @Test
  void isBinaryChannel_mixedValues_returnsFalse() {
    var ts = new Timeseries("temperature", "device", "location", "sym", "field");
    var pts = List.of(
      new TimeseriesDataPoint(1L, 0.0),
      new TimeseriesDataPoint(2L, 22.5),
      new TimeseriesDataPoint(3L, 1.0)
    );
    assertFalse(CsvConverter.isBinaryChannel(ts, pts));
  }

  @Test
  void isBinaryChannel_ordinaryName_returnsFalse() {
    var ts = new Timeseries("temperature", "device", "location", "sym", "celsius");
    var pts = List.of(
      new TimeseriesDataPoint(1L, 20.5),
      new TimeseriesDataPoint(2L, 21.0)
    );
    assertFalse(CsvConverter.isBinaryChannel(ts, pts));
  }

  @Test
  void convertToTimeseriesWithData_binaryByName_coercedToBoolean() {
    String csv =
      """
      DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE
      device,field,location,digital_valve,sym,1000000,0.0
      device,field,location,digital_valve,sym,2000000,1.0
      device,field,location,digital_valve,sym,3000000,0.0
      """;
    InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    List<TimeseriesWithDataPoints> result = CsvConverter.convertToTimeseriesWithData(stream);
    assertEquals(1, result.size());
    var pts = result.get(0).getPoints();
    assertEquals(Boolean.FALSE, pts.get(0).getValue());
    assertEquals(Boolean.TRUE, pts.get(1).getValue());
    assertEquals(Boolean.FALSE, pts.get(2).getValue());
  }

  @Test
  void convertToTimeseriesWithData_binaryByValueSpread_coercedToBoolean() {
    String csv =
      """
      DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE
      device,field,location,sensor_x,sym,1000000,0.0
      device,field,location,sensor_x,sym,2000000,1.0
      device,field,location,sensor_x,sym,3000000,1.0
      """;
    InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    List<TimeseriesWithDataPoints> result = CsvConverter.convertToTimeseriesWithData(stream);
    assertEquals(1, result.size());
    for (var pt : result.get(0).getPoints()) {
      assertTrue(pt.getValue() instanceof Boolean, "coerced value must be Boolean");
    }
  }

  @Test
  void convertToTimeseriesWithData_nonBinaryChannel_remainsDouble() {
    String csv =
      """
      DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE
      device,field,location,temperature,sym,1000000,20.5
      device,field,location,temperature,sym,2000000,21.3
      """;
    InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    List<TimeseriesWithDataPoints> result = CsvConverter.convertToTimeseriesWithData(stream);
    assertEquals(1, result.size());
    for (var pt : result.get(0).getPoints()) {
      assertTrue(pt.getValue() instanceof Double, "non-binary value must remain Double");
    }
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
