package de.dlr.shepard.influxDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidBodyException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

@QuarkusComponentTest
public class TimeseriesServiceTest extends BaseTestCase {

  @InjectMock
  private InfluxDBConnector connector;

  @InjectMock
  private CsvConverter converter;

  @Inject
  TimeseriesService service;

  @Captor
  private ArgumentCaptor<String> databaseName;

  private Timeseries ts = new Timeseries("meas", "dev", "loc", "symName", "field");
  private TimeseriesPayload payload = new TimeseriesPayload(ts, List.of(new InfluxPoint(123L, "value")));

  @Test
  public void createTimeseriesTest() throws InvalidBodyException {
    when(connector.saveTimeseriesPayload("database", payload)).thenReturn("");
    when(connector.databaseExist("database")).thenReturn(true);
    var actual = service.createTimeseries("database", payload);
    assertEquals("", actual);
  }

  @Test
  public void createTimeseriesTestException() {
    Timeseries buggyts = new Timeseries("meas", "de/v", "loc", "symName", "field");
    TimeseriesPayload buggypayload = new TimeseriesPayload(buggyts, List.of(new InfluxPoint(123L, "value")));
    Exception exception = assertThrows(InvalidBodyException.class, () ->
      service.createTimeseries("database", buggypayload)
    );
    assertEquals("device should not contain whitespaces or dots or slashes or commas: de/", exception.getMessage());
  }

  @Test
  public void createDatabaseTest() {
    var actual = service.createDatabase();
    verify(connector).createDatabase(databaseName.capture());
    assertEquals(databaseName.getValue(), actual);
  }

  @Test
  public void getTimeseriesTest() {
    when(
      connector.getTimeseriesPayload(1, 2, "db", ts, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR)
    ).thenReturn(payload);
    var actual = service.getTimeseriesPayload(1, 2, "db", ts, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR);
    assertEquals(payload, actual);
  }

  @Test
  public void getTimeseriesListTest_noFilter() {
    when(
      connector.getTimeseriesPayload(1, 2, "db", ts, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR)
    ).thenReturn(payload);
    var actual = service.getTimeseriesPayloadList(
      1,
      2,
      "db",
      List.of(ts),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Collections.emptySet(),
      Collections.emptySet(),
      Collections.emptySet()
    );
    assertEquals(List.of(payload), actual);
  }

  @Test
  public void getTimeseriesListTest_allFilters() {
    when(
      connector.getTimeseriesPayload(1, 2, "db", ts, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR)
    ).thenReturn(payload);
    var actual = service.getTimeseriesPayloadList(
      1,
      2,
      "db",
      List.of(ts),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Set.of("loc"),
      Set.of("symName")
    );
    assertEquals(List.of(payload), actual);
  }

  @Test
  public void getTimeseriesListTest_filterLoc() {
    when(
      connector.getTimeseriesPayload(1, 2, "db", ts, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR)
    ).thenReturn(payload);
    var actual = service.getTimeseriesPayloadList(
      1,
      2,
      "db",
      List.of(ts),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Collections.emptySet(),
      Set.of("loc"),
      Collections.emptySet()
    );
    assertEquals(List.of(payload), actual);
  }

  @Test
  public void getTimeseriesListTest_filterDev() {
    when(
      connector.getTimeseriesPayload(1, 2, "db", ts, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR)
    ).thenReturn(payload);
    var actual = service.getTimeseriesPayloadList(
      1,
      2,
      "db",
      List.of(ts),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("dev"),
      Collections.emptySet(),
      Collections.emptySet()
    );
    assertEquals(List.of(payload), actual);
  }

  @Test
  public void getTimeseriesListTest_filterName() {
    when(
      connector.getTimeseriesPayload(1, 2, "db", ts, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR)
    ).thenReturn(payload);
    var actual = service.getTimeseriesPayloadList(
      1,
      2,
      "db",
      List.of(ts),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Collections.emptySet(),
      Collections.emptySet(),
      Set.of("symName")
    );
    assertEquals(List.of(payload), actual);
  }

  @Test
  public void getTimeseriesListTest_nonMatchingLoc() {
    when(
      connector.getTimeseriesPayload(1, 2, "db", ts, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR)
    ).thenReturn(payload);
    var actual = service.getTimeseriesPayloadList(
      1,
      2,
      "db",
      List.of(ts),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Collections.emptySet(),
      Set.of("wrong"),
      Collections.emptySet()
    );
    assertEquals(Collections.emptyList(), actual);
  }

  @Test
  public void getTimeseriesListTest_nonMatchingDev() {
    when(
      connector.getTimeseriesPayload(1, 2, "db", ts, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR)
    ).thenReturn(payload);
    var actual = service.getTimeseriesPayloadList(
      1,
      2,
      "db",
      List.of(ts),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Set.of("wrong"),
      Collections.emptySet(),
      Collections.emptySet()
    );
    assertEquals(Collections.emptyList(), actual);
  }

  @Test
  public void getTimeseriesListTest_nonMatchingName() {
    when(
      connector.getTimeseriesPayload(1, 2, "db", ts, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR)
    ).thenReturn(payload);
    var actual = service.getTimeseriesPayloadList(
      1,
      2,
      "db",
      List.of(ts),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Collections.emptySet(),
      Collections.emptySet(),
      Set.of("wrong")
    );
    assertEquals(Collections.emptyList(), actual);
  }

  @Test
  public void deleteDatabaseTest() {
    service.deleteDatabase("database");
    verify(connector).deleteDatabase("database");
  }

  @Test
  public void getTimeseriesAvailableTest() {
    var expected = List.of(new Timeseries("meas", "dev", "loc", "sym", "field"));
    when(connector.getTimeseriesAvailable("test")).thenReturn(expected);
    var actual = service.getTimeseriesAvailable("test");
    assertEquals(expected, actual);
  }

  @Test
  public void exportTimeseriesTest() throws IOException {
    var is = new ByteArrayInputStream("Hello World".getBytes());
    when(
      connector.getTimeseriesPayload(1, 2, "db", ts, SingleValuedUnaryFunction.MEAN, 10L, FillOption.LINEAR)
    ).thenReturn(payload);
    when(converter.convertToCsv(List.of(payload))).thenReturn(is);
    var actual = service.exportTimeseriesPayload(
      1,
      2,
      "db",
      List.of(ts),
      SingleValuedUnaryFunction.MEAN,
      10L,
      FillOption.LINEAR,
      Collections.emptySet(),
      Collections.emptySet(),
      Collections.emptySet()
    );
    assertEquals(is, actual);
  }

  @Test
  public void importTimeseriesNonexistingDatabaseTest() throws IOException, InvalidBodyException {
    var is = new ByteArrayInputStream("Hello World".getBytes());
    var errorPayload = new TimeseriesPayload(
      new Timeseries("meas", "dev", "loc", "sym", "field"),
      List.of(new InfluxPoint(123, "asdf"))
    );
    when(converter.convertToPayload(is)).thenReturn(List.of(errorPayload));
    when(connector.databaseExist("db")).thenReturn(false);
    var actual = service.importTimeseries("db", is);
    assertEquals("The database db does not exist", actual);
  }

  @Test
  public void importTimeseriesTest() throws IOException, InvalidBodyException {
    var is = new ByteArrayInputStream("Hello World".getBytes());
    when(converter.convertToPayload(is)).thenReturn(List.of(payload));
    when(connector.databaseExist("db")).thenReturn(true);
    when(connector.saveTimeseriesPayload("db", payload)).thenReturn("    ");
    var actual = service.importTimeseries("db", is);
    assertEquals("", actual);
  }
}
