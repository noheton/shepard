package de.dlr.shepard.influxDB;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.util.Constants;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBException;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.dto.QueryResult.Result;
import org.influxdb.dto.QueryResult.Series;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class InfluxDBConnectorTest {

  @InjectMock
  InfluxDB influxDB;

  @Inject
  InfluxDBConnector connector;

  private final String database = "my_database";
  private final String measurement = "my_measurement";
  private final String location = "my_location";
  private final String device = "my_device";
  private final String sym_name = "my_sym_name";
  private final String field = "my_field";
  private final long timestamp = System.currentTimeMillis() * 1000000;
  private final long start = 12345L;
  private final long end = 67890L;
  private final long groupBy = 10L;

  private final Timeseries expectedTimeseries = new Timeseries(measurement, location, device, sym_name, field);
  private final TimeseriesPayload expectedTimeseriesPayload = new TimeseriesPayload(
    expectedTimeseries,
    new ArrayList<InfluxPoint>()
  );

  @BeforeEach
  void setUp() {
    this.connector = new InfluxDBConnector(influxDB);
  }

  @Test
  public void createInstance_notNull() {
    assertNotNull(connector);
  }

  @Test
  public void testAlivePositive() {
    Pong pong = new Pong();
    pong.setVersion("MyVersion");
    when(influxDB.ping()).thenReturn(pong);
    assertTrue(connector.alive());
  }

  @Test
  public void testAliveNegative() {
    Pong pong = new Pong();
    pong.setVersion("unknown");
    when(influxDB.ping()).thenReturn(pong);
    assertFalse(connector.alive());
  }

  @Test
  public void testAliveNull() {
    when(influxDB.ping()).thenReturn(null);
    assertFalse(connector.alive());
  }

  @Test
  public void testAliveException() {
    when(influxDB.ping()).thenThrow(new InfluxDBException("Exception"));
    assertFalse(connector.alive());
  }

  @Test
  public void testCreateDatabase() {
    connector.createDatabase(database);
    verify(influxDB).query(new Query(String.format("CREATE DATABASE \"%s\"", database)));
  }

  @Test
  public void testDeleteDatabase() {
    connector.deleteDatabase(database);
    verify(influxDB).query(new Query(String.format("DROP DATABASE \"%s\"", database)));
  }

  @Test
  public void testSaveTimeseries() {
    String queryString = String.format("SHOW FIELD KEYS ON \"%s\" FROM %s", database, measurement);
    var value = 10.0;
    TimeseriesPayload timeseries = configureTimeseries(value);
    BatchPoints points = BatchPoints.database(database).build();
    Builder pointBuilder = configurePointBuilder(timeseries);
    pointBuilder.time(timestamp, TimeUnit.NANOSECONDS).addField(timeseries.getTimeseries().getField(), value);
    points.point(pointBuilder.build());

    when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey("float"));
    when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

    var actual = connector.saveTimeseriesPayload(database, timeseries);
    assertEquals("", actual);
    verify(influxDB).write(points);
  }

  @Test
  public void testSaveTimeseriesWithNoExpectedDatatype() {
    String queryString = String.format("SHOW FIELD KEYS ON \"%s\" FROM %s", database, measurement);
    int value = 10;
    TimeseriesPayload timeseries = configureTimeseries(value);

    QueryResult queryResult = new QueryResult() {
      List<Result> results;

      @Override
      public List<Result> getResults() {
        results = new ArrayList<>();
        Result result = new Result();
        ArrayList<Series> seriesList = new ArrayList<>();
        Series series = new Series();
        List<List<Object>> valueList = new ArrayList<>();
        List<Object> value = new ArrayList<>();
        value.add("AnotherField");
        value.add("string");
        valueList.add(value);
        series.setValues(valueList);
        seriesList.add(series);
        result.setSeries(seriesList);
        results.add(result);
        return results;
      }
    };

    when(influxDB.query(new Query(queryString))).thenReturn(queryResult);
    when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

    var actual = connector.saveTimeseriesPayload(database, timeseries);
    assertEquals("", actual);
  }

  @Test
  public void testSaveTimeseriesWithDatabase() {
    String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
    int value = 10;
    TimeseriesPayload timeseries = configureTimeseries(value);

    when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
    when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));

    var actual = connector.saveTimeseriesPayload(database, timeseries);
    assertEquals("", actual);
    verify(influxDB, never()).query(new Query("CREATE DATABASE " + database));
  }

  @Test
  public void testSaveTimeseriesException() {
    String queryString = String.format("SHOW FIELD KEYS ON %s FROM %s", database, measurement);
    Object value = new Object();
    TimeseriesPayload timeseries = configureTimeseries(value);
    BatchPoints points = BatchPoints.database(database).build();
    Builder pointBuilder = configurePointBuilder(timeseries);
    pointBuilder
      .time(timestamp, TimeUnit.NANOSECONDS)
      .addField(timeseries.getTimeseries().getField(), value.toString());
    points.point(pointBuilder.build());

    when(influxDB.query(new Query(queryString))).thenReturn(getFieldKey(""));
    when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));
    doThrow(new InfluxDBException("My Exception")).when(influxDB).write(points);
    var actual = connector.saveTimeseriesPayload(database, timeseries);

    assertEquals("My Exception", actual);
  }

  @Test
  public void testGetTimeseriesWithValidRequestBody() {
    List<List<Object>> values = new ArrayList<>();
    List<Object> value = new ArrayList<>();
    value.add(Instant.ofEpochMilli(System.currentTimeMillis()).toString());
    value.add(5);
    values.add(value);

    Series series = new Series();
    series.setValues(values);

    Result result = new Result();
    List<Series> seriesList = new ArrayList<>();
    seriesList.add(series);
    result.setSeries(seriesList);

    List<Result> resultList = new ArrayList<>();
    resultList.add(result);

    QueryResult queryResult = new QueryResult();
    queryResult.setResults(resultList);

    when(influxDB.query(any(Query.class))).thenReturn(queryResult);

    List<InfluxPoint> exp1 = connector
      .getTimeseriesPayload(
        start,
        end,
        database,
        expectedTimeseries,
        SingleValuedUnaryFunction.MEAN,
        groupBy,
        FillOption.LINEAR
      )
      .getPoints();
    assertEquals(5, (int) exp1.get(0).getValue());
  }

  @Test
  public void testGetTimeseriesWithQueryResultHasErrors() {
    QueryResult queryResult = new QueryResult();
    queryResult.setError("Some Error");

    when(influxDB.query(any(Query.class))).thenReturn(queryResult);

    TimeseriesPayload actualTimeseries = connector.getTimeseriesPayload(
      start,
      end,
      database,
      expectedTimeseries,
      SingleValuedUnaryFunction.MEAN,
      groupBy,
      FillOption.LINEAR
    );
    assertThat(actualTimeseries)
      .usingRecursiveComparison()
      .ignoringFields("influxPoints")
      .isEqualTo(expectedTimeseriesPayload);
    assertTrue(actualTimeseries.getPoints().isEmpty());
  }

  @Test
  public void testGetTimeseriesWithInfluxDBException() {
    doThrow(InfluxDBException.class).when(influxDB).query(any(Query.class));

    TimeseriesPayload actualTimeseries = connector.getTimeseriesPayload(
      start,
      end,
      database,
      expectedTimeseries,
      SingleValuedUnaryFunction.MEAN,
      groupBy,
      FillOption.LINEAR
    );
    assertThat(actualTimeseries)
      .usingRecursiveComparison()
      .ignoringFields("influxPoints")
      .isEqualTo(expectedTimeseriesPayload);
    assertTrue(actualTimeseries.getPoints().isEmpty());
  }

  @Test
  public void testGetTimeseriesAvailable() {
    String seriesQueryString = "SHOW SERIES ON \"database\"";
    String fieldQueryString = "SHOW FIELD KEYS ON \"database\"";
    var series = List.of(
      new String[] { "meas", "dev", "loc", "sym" },
      new String[] { "different", "first", "bla", "badum" },
      new String[] { "different", "second", "bla", "badum" }
    );
    when(influxDB.query(new Query(seriesQueryString))).thenReturn(getShowSeries(series));
    var fields = Map.of("meas", List.of("field"), "different", List.of("field", "value"));
    when(influxDB.query(new Query(fieldQueryString))).thenReturn(getShowFields(fields));

    var expected = List.of(
      new Timeseries("meas", "dev", "loc", "sym", "field"),
      new Timeseries("different", "first", "bla", "badum", "field"),
      new Timeseries("different", "first", "bla", "badum", "value"),
      new Timeseries("different", "second", "bla", "badum", "field"),
      new Timeseries("different", "second", "bla", "badum", "value")
    );
    var actual = connector.getTimeseriesAvailable("database");

    assertEquals(expected, actual);
  }

  @Test
  public void testGetTimeseriesAvailable_showFieldError() {
    String seriesQueryString = "SHOW SERIES ON \"database\"";
    String fieldQueryString = "SHOW FIELD KEYS ON \"database\"";
    var series = List.of(
      new String[] { "meas", "dev", "loc", "sym" },
      new String[] { "different", "first", "bla", "badum" },
      new String[] { "different", "second", "bla", "badum" }
    );
    when(influxDB.query(new Query(seriesQueryString))).thenReturn(getShowSeries(series));

    QueryResult queryResult = new QueryResult();
    queryResult.setError("Some Error");
    when(influxDB.query(new Query(fieldQueryString))).thenReturn(queryResult);

    var actual = connector.getTimeseriesAvailable("database");

    assertEquals(0, actual.size());
  }

  @Test
  public void testGetTimeseriesAvailable_showSeriesError() {
    String queryString = "SHOW SERIES ON \"database\"";
    QueryResult queryResult = new QueryResult();
    queryResult.setError("Some Error");

    when(influxDB.query(new Query(queryString))).thenReturn(queryResult);

    var actual = connector.getTimeseriesAvailable("database");

    assertEquals(0, actual.size());
  }

  @Test
  public void testDatabaseDoesNotExist() {
    when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));
    boolean exists = connector.databaseExist("db");
    assertFalse(exists);
  }

  @Test
  public void testDatabaseExists() {
    when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(getShowDatabases(database));
    boolean exists = connector.databaseExist(database);
    assertTrue(exists);
  }

  @Test
  public void testDatabaseExistQueryResultNotValid() {
    when(influxDB.query(new Query("SHOW DATABASES"))).thenReturn(null);
    boolean exists = connector.databaseExist(database);
    assertFalse(exists);
  }

  private TimeseriesPayload configureTimeseries(Object value) {
    InfluxPoint influxPoint = new InfluxPoint(timestamp, value);
    List<InfluxPoint> influxPoints = new ArrayList<>();
    influxPoints.add(influxPoint);
    var timeseries = new Timeseries(measurement, device, location, sym_name, field);
    var payload = new TimeseriesPayload(timeseries, influxPoints);
    return payload;
  }

  private Builder configurePointBuilder(TimeseriesPayload ts) {
    Builder pointBuilder = Point.measurement(ts.getTimeseries().getMeasurement())
      .tag(Constants.LOCATION, ts.getTimeseries().getLocation())
      .tag(Constants.DEVICE, ts.getTimeseries().getDevice())
      .tag(Constants.SYMBOLICNAME, ts.getTimeseries().getSymbolicName());
    return pointBuilder;
  }

  private QueryResult getShowDatabases(String databaseName) {
    QueryResult queryResult = new QueryResult() {
      List<Result> results;

      @Override
      public List<Result> getResults() {
        results = new ArrayList<>();
        Result result = new Result();
        ArrayList<Series> seriesList = new ArrayList<>();
        Series series = new Series();
        List<List<Object>> valueList = new ArrayList<>();
        List<Object> value = new ArrayList<>();
        value.add(databaseName);
        valueList.add(value);
        series.setValues(valueList);
        seriesList.add(series);
        result.setSeries(seriesList);
        results.add(result);
        return results;
      }
    };
    return queryResult;
  }

  private QueryResult getShowSeries(List<String[]> timeseries) {
    QueryResult queryResult = new QueryResult() {
      List<Result> results;

      @Override
      public List<Result> getResults() {
        results = new ArrayList<>();
        Result result = new Result();
        ArrayList<Series> seriesList = new ArrayList<>();
        Series series = new Series();
        List<List<Object>> valueList = timeseries
          .stream()
          .map(ts -> {
            List<Object> value = List.of(
              String.format("%s,device=%s,location=%s,symbolic_name=%s,bla=blub", ts[0], ts[1], ts[2], ts[3])
            );
            return value;
          })
          .toList();
        series.setValues(valueList);
        seriesList.add(series);
        result.setSeries(seriesList);
        results.add(result);
        return results;
      }
    };
    return queryResult;
  }

  private QueryResult getShowFields(Map<String, List<String>> fields) {
    QueryResult queryResult = new QueryResult() {
      List<Result> results;

      @Override
      public List<Result> getResults() {
        results = new ArrayList<>();
        Result result = new Result();
        ArrayList<Series> seriesList = new ArrayList<>();
        for (var entry : fields.entrySet()) {
          Series series = new Series();
          series.setName(entry.getKey());
          List<List<Object>> valueList = entry
            .getValue()
            .stream()
            .map(f -> {
              List<Object> value = List.of(f);
              return value;
            })
            .toList();
          series.setValues(valueList);
          seriesList.add(series);
        }
        result.setSeries(seriesList);
        results.add(result);
        return results;
      }
    };
    return queryResult;
  }

  private QueryResult getFieldKey(String fieldKey) {
    QueryResult queryResult = new QueryResult() {
      List<Result> results;

      @Override
      public List<Result> getResults() {
        results = new ArrayList<>();
        Result result = new Result();
        ArrayList<Series> seriesList = new ArrayList<>();
        Series series = new Series();
        List<List<Object>> valueList = new ArrayList<>();
        List<Object> value = new ArrayList<>();
        value.add(field);
        value.add(fieldKey);
        valueList.add(value);
        series.setValues(valueList);
        seriesList.add(series);
        result.setSeries(seriesList);
        results.add(result);
        return results;
      }
    };
    return queryResult;
  }
}
