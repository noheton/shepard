package de.dlr.shepard.influxDB;

import de.dlr.shepard.util.Constants;
import de.dlr.shepard.util.IConnector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBException;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

/**
 * Connector for read and write access to the Influx timeseries database. The
 * class represents the lowest level of data access to the Influx database. The
 * Influx database is accessed directly by using query strings.
 */
@Slf4j
public class InfluxDBConnector implements IConnector {

  private InfluxDB influxDB;
  private static InfluxDBConnector instance = null;

  /**
   * Private constructor
   */
  private InfluxDBConnector() {}

  /**
   * For development reasons, there should always be just one InfluxConnector
   * instance.
   *
   * @return The one and only InfluxConnector instance.
   */
  public static InfluxDBConnector getInstance() {
    if (instance == null) {
      instance = new InfluxDBConnector();
    }
    return instance;
  }

  /**
   * Establishes a connection to the Influx server by using the URL saved in the
   * config.properties file returned by the DatabaseHelper. In addition the
   * logging is being configured.
   *
   */
  @Override
  public boolean connect() {
    String host = ConfigProvider.getConfig().getValue("influx.host", String.class);
    String username = ConfigProvider.getConfig().getValue("influx.username", String.class);
    String password = ConfigProvider.getConfig().getValue("influx.password", String.class);

    influxDB = InfluxDBFactory.connect(String.format("http://%s", host), username, password);
    influxDB.enableBatch(
      BatchOptions.DEFAULTS.exceptionHandler((failedPoints, throwable) ->
        log.error("Exception while writing the following points: {}, Exception: {}", failedPoints, throwable)
      )
    );
    return true;
  }

  @Override
  public boolean disconnect() {
    if (influxDB != null) influxDB.close();
    return true;
  }

  /**
   * Tests whether a connection exists.
   *
   * @return True if connection exists, otherwise false.
   */
  @Override
  public boolean alive() {
    Pong response;
    try {
      response = influxDB.ping();
    } catch (InfluxDBException ex) {
      return false;
    }
    return response != null && !response.getVersion().equalsIgnoreCase("unknown");
  }

  /**
   * Creates a new database.
   *
   * @param databaseName Name of the new database to be created.
   */
  public void createDatabase(String databaseName) {
    String query = String.format("CREATE DATABASE \"%s\"", databaseName);
    influxDB.query(new Query(query));
  }

  /**
   * Deletes a database.
   *
   * @param databaseName Name of the database to be deleted.
   */
  public void deleteDatabase(String databaseName) {
    String query = String.format("DROP DATABASE \"%s\"", databaseName);
    influxDB.query(new Query(query));
  }

  /**
   * Writes all InfluxPoint objects, saved in the TimeseriesPayload object, into
   * the influx database. Therefore the method uses the name of the database, the
   * name of the measurement, the location, the device, the symbolic name and the
   * name of the field provided by the given TimeseriesPayload object. The actual
   * write operation is done by using the unix timestamp in nanoseconds and the
   * value of every InfluxPoint object in the TimeseriesPayload object. All these
   * variables have to be defined in the given Timeseries object for a successful
   * write operation.
   *
   * @param database The database to store the payload in
   * @param payload  Combines the required attributes in a structured way.
   * @return An error if there was a problem, empty string if all went well
   */
  public String saveTimeseriesPayload(String database, TimeseriesPayload payload) {
    var timeseries = payload.getTimeseries();
    var expectedType = getExpectedDatatype(database, timeseries.getMeasurement(), timeseries.getField());
    var batchPoints = InfluxUtil.createBatch(database, payload, expectedType);
    try {
      influxDB.write(batchPoints);
    } catch (InfluxDBException e) {
      log.error("InfluxdbException while writing payload {}: {}", payload.getTimeseries(), e.getMessage());
      return e.getMessage();
    }
    return "";
  }

  /**
   * Returns a Timeseries object containing all the influx data points which match
   * the given conditions consigned to the method. The returned InfluxPoint
   * objects are in the given measurement of the specific database between the
   * start and the end timestamp.
   *
   * @param startTimeStamp Start timestamp from which values should be returned
   * @param endTimeStamp   End timestamp to which values should be returned
   * @param database       Name of the database
   * @param timeseries     Specifies the data to load from database
   * @param function       The aggregate function
   * @param groupBy        The time interval measurements get grouped by
   * @param fillOption     The fill option for missing values
   * @return A Timeseries object containing a list of all InfluxPoints in the
   *         given measurement of the specific database between the two given
   *         timestamps matching the "device"-tag, the "location"-tag and the
   *         "symbolic_name"-tag.
   */
  public TimeseriesPayload getTimeseriesPayload(
    long startTimeStamp,
    long endTimeStamp,
    String database,
    Timeseries timeseries,
    SingleValuedUnaryFunction function,
    Long groupBy,
    FillOption fillOption
  ) {
    Query query = InfluxUtil.buildQuery(
      startTimeStamp,
      endTimeStamp,
      database,
      timeseries,
      function,
      groupBy,
      fillOption
    );
    log.debug("Influx Query: {}", query.getCommand());
    QueryResult queryResult;

    try {
      queryResult = influxDB.query(query);
    } catch (InfluxDBException e) {
      queryResult = null;
      log.error("Could not parse query: {}", query.getCommand());
    }
    if (InfluxUtil.isQueryResultValid(queryResult)) {
      return InfluxUtil.extractPayload(queryResult, timeseries);
    }
    return new TimeseriesPayload(timeseries, Collections.emptyList());
  }

  /**
   * Returns a list of timeseries objects that are in the given database.
   *
   * @param database the given database
   * @return a list of timeseries objects
   */
  public List<Timeseries> getTimeseriesAvailable(String database) {
    Query query = new Query(String.format("SHOW SERIES ON \"%s\"", database));
    QueryResult queryResult = influxDB.query(query);
    if (!InfluxUtil.isQueryResultValid(queryResult)) {
      log.warn("There was an error while querying the Influxdb for available timeseries");
      return Collections.emptyList();
    }
    var values = queryResult.getResults().get(0).getSeries().get(0).getValues();
    var fields = getFields(database);
    var result = new ArrayList<Timeseries>(values.size());
    for (var value : values) {
      var series = ((String) value.get(0)).split(",");
      // The first entry is the measurement
      var meas = series[0];
      var tags = extractTags(series);
      var dev = tags.getOrDefault(Constants.DEVICE, "");
      var loc = tags.getOrDefault(Constants.LOCATION, "");
      var symName = tags.getOrDefault(Constants.SYMBOLICNAME, "");
      for (var field : fields.getOrDefault(meas, Collections.emptyList())) {
        result.add(new Timeseries(meas, dev, loc, symName, field));
      }
    }
    return result;
  }

  private Map<String, String> extractTags(String[] series) {
    var result = new HashMap<String, String>();
    for (var tagString : series) {
      var tags = tagString.split("=", 2);

      // Skip entries with less than 2 tuples (most likely the measurement)
      if (tags.length < 2) continue;

      result.put(tags[0], tags[1]);
    }
    return result;
  }

  private Map<String, List<String>> getFields(String database) {
    Query query = new Query(String.format("SHOW FIELD KEYS ON \"%s\"", database));
    QueryResult queryResult = influxDB.query(query);
    if (!InfluxUtil.isQueryResultValid(queryResult)) {
      log.warn("There was an error while querying the Influxdb for available fields");
      return Collections.emptyMap();
    }
    var series = queryResult.getResults().get(0).getSeries();
    var result = new HashMap<String, List<String>>();
    for (var s : series) {
      var fields = new ArrayList<String>();
      for (var value : s.getValues()) {
        fields.add((String) value.get(0));
      }
      result.put(s.getName(), fields);
    }
    return result;
  }

  public boolean databaseExist(String database) {
    QueryResult queryResult = influxDB.query(new Query("SHOW DATABASES"));
    if (!InfluxUtil.isQueryResultValid(queryResult)) {
      log.warn("There was an error while querying the Influxdb for databases");
      return false;
    }

    var values = queryResult.getResults().get(0).getSeries().get(0).getValues();
    for (var databaseName : values) {
      if (databaseName.get(0).toString().trim().equals(database)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns the expected data type of a field or an empty string according to
   * https://docs.influxdata.com/influxdb/v1.8/write_protocols/line_protocol_reference/#data-types
   */
  private String getExpectedDatatype(String database, String measurement, String field) {
    String queryString = String.format("SHOW FIELD KEYS ON \"%s\" FROM %s", database, measurement);
    QueryResult result = influxDB.query(new Query(queryString));
    if (!InfluxUtil.isQueryResultValid(result)) {
      log.info("Could not get expected datatype query string \"{}\"", queryString);
      return "";
    }

    var values = result.getResults().get(0).getSeries().get(0).getValues();
    for (var value : values) {
      if (value.get(0).equals(field)) return (String) value.get(1);
    }

    return "";
  }
}
