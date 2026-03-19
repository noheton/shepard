package de.dlr.shepard.migrations.neo4j;

import static de.dlr.shepard.common.util.Constants.HAS_TIMESERIES_TUPLE;
import static de.dlr.shepard.common.util.Constants.IS_IN_CONTAINER;
import static org.junit.jupiter.api.Assertions.*;
import static org.neo4j.cypherdsl.core.Cypher.literalOf;
import static org.neo4j.cypherdsl.core.Cypher.node;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvValidationException;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesTuple;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.ConfigProvider;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.NonNull;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;

public class TestV12 extends MigrationTest {

  private List<Timeseries> allTimeseries;

  @Override
  public void setupPreMigrationData() throws CsvValidationException, IOException, ClassNotFoundException {
    clearDatabase();
    var containerIdMappings = preparePreexistingNeo4jData();
    var containeredTimeseries = prepareV12TimescaleData(containerIdMappings);
    allTimeseries = containeredTsToInternalRep(containeredTimeseries)
      .sorted(Comparator.comparing(Timeseries::getTimeseriesId))
      .toList();
  }

  @Override
  public String getTargetVersion() {
    return "V12";
  }

  private void clearDatabase() {
    q.deleteAll("Timeseries");
    q.deleteAll("TimeseriesTuple");
    q.deleteAll("TimeseriesContainer");
    q.deleteAll("TimeseriesReference");
  }

  /**
   * Assert that the timeseries from timescale are now present as timeseries nodes in the graph database.
   */
  public void assertTimeseriesPresentInGraphDb() {
    var ts_result_list = q.match(node("Timeseries"));
    assertEquals(8, ts_result_list.size());
    for (var ts : allTimeseries) {
      var tsListActual = q.match(
        node("Timeseries")
          .withProperties(
            "timeseriesId",
            Cypher.literalOf(ts.getTimeseriesId()),
            "valueType",
            Cypher.literalOf(ts.getValueType().toString())
          )
          .relationshipTo(
            node("TimeseriesContainer").withProperties("name", Cypher.literalOf(ts.getContainer().getName())),
            IS_IN_CONTAINER
          )
          .getLeft()
          .relationshipTo(
            node("TimeseriesTuple").withProperties(
              "measurement",
              Cypher.literalOf(ts.getTimeseriesTuple().getMeasurement())
            ),
            Constants.HAS_TIMESERIES_TUPLE
          )
          .getLeft()
      );
      assertEquals(1, tsListActual.size());
    }
  }

  /**
   * Assert that meta-information does not exist anymore in timescaledb.
   */
  public void assertMetadataDeletedInTimeseriesDb() throws SQLException, ClassNotFoundException {
    try (var connection = createTimeseriesConnection()) {
      var result = connection
        .prepareStatement("select tablename from pg_tables where tablename = 'timeseries'")
        .executeQuery();
      assertFalse(result.next());
    }
  }

  /**
   * Assert that the values of all timeseries are still intact in timescaledb.
   */
  public void assertTimeseriesDatapointsIntact() throws SQLException, ClassNotFoundException {
    try (var connection = createTimeseriesConnection()) {
      var tsCount = connection
        .prepareStatement("select count(timeseries_id) from timeseries_data_points")
        .executeQuery();
      tsCount.next();
      assertEquals(15, tsCount.getInt("count"));
      var actualTsIds = connection
        .prepareStatement("select timeseries_id from timeseries_data_points group by timeseries_id")
        .executeQuery();
      while (actualTsIds.next()) assertTrue(
        allTimeseries.stream().map(Timeseries::getTimeseriesId).toList().contains(actualTsIds.getLong("timeseries_id"))
      );
    }
  }

  private final Node intLevelTs = node("TimeseriesTuple")
    .withProperties(
      "device",
      literalOf("device"),
      "field",
      literalOf("field"),
      "location",
      literalOf("location"),
      "measurement",
      literalOf("int_level"),
      "symbolicName",
      literalOf("symbolicName")
    )
    .named("ts");

  private final Node tsc1 = sample.timeseriesContainer(1).named("tsc1");
  private final Node tsc2 = sample.timeseriesContainer(2).named("tsc2");

  /**
   * Prepare the preexisting data in Neo4j.
   * Those are one already existing timeseries tuple and two timeseries containers.
   *
   * @return The mapping of the "testing" container id which is originally assigned to timeseries to the actual
   * container ids which are generated by neo4j.
   * This is a crutch around not being able to predetermine the container id for the test case since we rely on the
   * internalId of Neo4j.
   */
  private Map<Long, Long> preparePreexistingNeo4jData() {
    q.create(intLevelTs);
    var objectTsc1 = q.create(tsc1, TimeseriesContainer.class);
    var objectTsc2 = q.create(tsc2, TimeseriesContainer.class);
    return Map.of(1L, objectTsc1.getId(), 2L, objectTsc2.getId());
  }

  public void assertNewTimeseriesMergedWithPreexisting() {
    // assert node was not duplicated
    assertEquals(1, q.match(intLevelTs).size());
    // assert node is referenced by two Timeseries
    var ts1 = node("Timeseries")
      .withProperties("valueType", Cypher.literalOf(DataPointValueType.Integer.name()))
      .named("ts1");
    var ts2 = node("Timeseries")
      .withProperties("valueType", Cypher.literalOf(DataPointValueType.Integer.name()))
      .named("ts2");
    var st = Cypher.match(
      ts1.relationshipTo(tsc1, IS_IN_CONTAINER),
      ts2.relationshipTo(tsc2, IS_IN_CONTAINER),
      ts1.relationshipTo(intLevelTs, HAS_TIMESERIES_TUPLE),
      ts2.relationshipTo(intLevelTs, HAS_TIMESERIES_TUPLE)
    )
      .returning(ts1, ts2)
      .build();
    var resList = q
      .queryResults(st, Timeseries.class)
      .stream()
      .map(Timeseries::getId)
      .map(id -> q.loadSingle(id, Timeseries.class))
      .toList();
    assertTrue(allTimeseries.containsAll(resList));
    assertNotEquals(resList.get(0).getTimeseriesId(), resList.get(1).getTimeseriesId());
  }

  private Connection createTimeseriesConnection() throws SQLException, ClassNotFoundException {
    Class.forName("org.postgresql.Driver");
    var url = ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class);
    var user = ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class);
    var pass = ConfigProvider.getConfig().getValue("quarkus.datasource.password", String.class);
    return DriverManager.getConnection(url, user, pass);
  }

  /**
   * Prepare the timeseries database for the migration of timeseries metadata towards the graph database.
   */
  private Stream<ExtendedTimeseries> prepareV12TimescaleData(Map<Long, Long> containerIdMappings)
    throws CsvValidationException, IOException, ClassNotFoundException {
    Class.forName("org.postgresql.Driver");
    var url = ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class);
    var user = ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class);
    var pass = ConfigProvider.getConfig().getValue("quarkus.datasource.password", String.class);
    Flyway flyway = Flyway.configure().target("1.7.0").dataSource(url, user, pass).load();
    flyway.migrate();

    var dbEntries = readCsvAsMapList("src/test/resources/timeseries_import_migration_test.csv");

    return dbEntries
      .stream()
      .map(TestV12::csvEntryToTs)
      // replace the dummy container ids 1 and 2 with the actual ids from Neo4j
      .map(t -> new DatapointCsvEntry(containerIdMappings.get(t.containerId()), t.timestamp(), t.value(), t.quintuple())
      )
      .map(this::addTimeseriesToTimescale)
      .filter(Optional::isPresent)
      .map(Optional::get);
  }

  private record DatapointCsvEntry(long containerId, long timestamp, Object value, TimeseriesTuple quintuple) {}

  private record ExtendedTimeseries(
    long containerId,
    long timeseriesId,
    TimeseriesTuple quintuple,
    DataPointValueType valueType
  ) {}

  /**
   * Add the first point of the Timeseries to the timescale table timeseries_with_data_points.
   * If the timeseries does not exist yet create it and return its id including the Timeseries.
   *
   * @param ts Timeseries that must have only one data point
   * @return Empty optional if timeseries already existed, otherwise timeseries id and Timeseries.
   */
  private Optional<ExtendedTimeseries> addTimeseriesToTimescale(DatapointCsvEntry ts) {
    try (var connection = createTimeseriesConnection()) {
      Optional<Long> ts_id = insertTimeseries(ts, connection);
      insertTimeseriesDataPoint(connection, ts);
      var valueType = valueToValueType(ts.value());
      return ts_id.map(tsIdValue -> new ExtendedTimeseries(ts.containerId, tsIdValue, ts.quintuple(), valueType));
    } catch (SQLException | IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private static @NonNull Optional<Long> insertTimeseries(DatapointCsvEntry ts, Connection connection)
    throws IOException, SQLException {
    var sql = Files.readString(Path.of("src/test/resources/insert_timeseries.sql"));
    var stmt = connection.prepareStatement(sql);
    var valueType = valueToValueType(ts.value());
    stmt.setBigDecimal(1, BigDecimal.valueOf(ts.containerId()));
    stmt.setString(2, ts.quintuple().getMeasurement());
    stmt.setString(3, ts.quintuple().getField());
    stmt.setString(4, ts.quintuple().getSymbolicName());
    stmt.setString(5, ts.quintuple().getDevice());
    stmt.setString(6, ts.quintuple().getLocation());
    stmt.setString(7, valueType.toString());
    var resultSet = stmt.executeQuery();
    return resultSet.next() ? Optional.of(resultSet.getLong(1)) : Optional.empty();
  }

  private void insertTimeseriesDataPoint(Connection connection, DatapointCsvEntry ts) throws IOException, SQLException {
    var sql2 = Files.readString(Path.of("src/test/resources/insert_timeseries_data_point.sql"));
    sql2 = sql2.replace(":column", getDatapointColumn(ts.value()));
    var stmt2 = connection.prepareStatement(sql2);
    stmt2.setBigDecimal(1, BigDecimal.valueOf(ts.containerId()));
    stmt2.setString(2, ts.quintuple().getMeasurement());
    stmt2.setString(3, ts.quintuple().getField());
    stmt2.setString(4, ts.quintuple().getSymbolicName());
    stmt2.setString(5, ts.quintuple().getDevice());
    stmt2.setString(6, ts.quintuple().getLocation());
    stmt2.setBigDecimal(7, BigDecimal.valueOf(ts.timestamp()));
    stmt2.setObject(8, ts.value());
    stmt2.executeUpdate();
  }

  private Stream<Timeseries> containeredTsToInternalRep(Stream<ExtendedTimeseries> tsList) {
    return tsList.map(ts -> {
      var container = q.loadSingle(ts.containerId(), TimeseriesContainer.class);
      return new Timeseries(container, ts.quintuple(), ts.valueType(), ts.timeseriesId());
    });
  }

  /**
   * Get the column name into which a timeseries data point should be saved in timescale.
   */
  private static String getDatapointColumn(Object value) {
    if (value instanceof Double) return "double_value";
    else if (value instanceof String) return "string_value";
    else if (value instanceof Boolean) return "boolean_value";
    else if (value instanceof Integer) return "int_value";
    throw new RuntimeException("Data point " + value + " is of unfitting value!");
  }

  private static DatapointCsvEntry csvEntryToTs(Map<String, String> entry) {
    return new DatapointCsvEntry(
      Long.parseLong(entry.get("CONTAINERID")),
      Long.parseLong(entry.get("TIMESTAMP")),
      strValueToObject(entry.get("VALUE")),
      new TimeseriesTuple(
        entry.get("MEASUREMENT"),
        entry.get("DEVICE"),
        entry.get("LOCATION"),
        entry.get("SYMBOLICNAME"),
        entry.get("FIELD")
      )
    );
  }

  /**
   * Convert a string value of a timeseries to an Object of type Integer, Double, Boolean or String.
   */
  private static Object strValueToObject(String strValue) {
    try {
      return Integer.valueOf(strValue);
    } catch (NumberFormatException e1) {
      try {
        return Double.valueOf(strValue);
      } catch (NumberFormatException e2) {
        if ("true".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) return Boolean.valueOf(strValue);
        else return strValue;
      }
    }
  }

  /**
   * Determine the value type of a timeseries value
   *
   * @param value Object that contains the value of the timeseries data point
   * @return The type corresponding to the value
   */
  private static DataPointValueType valueToValueType(Object value) {
    var strValue = value.toString();
    try {
      Integer.valueOf(strValue);
      return DataPointValueType.Integer;
    } catch (NumberFormatException e1) {
      try {
        Double.valueOf(strValue);
        return DataPointValueType.Double;
      } catch (NumberFormatException e2) {
        if ("true".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) return DataPointValueType.Boolean;
        else return DataPointValueType.String;
      }
    }
  }

  /**
   * Read a csv file into a list of maps each mapping header to corresponding value.
   */
  private static List<Map<String, String>> readCsvAsMapList(String csvFilePath)
    throws IOException, CsvValidationException {
    CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(csvFilePath));

    List<Map<String, String>> rows = new ArrayList<>();
    Map<String, String> rowMap;

    while ((rowMap = reader.readMap()) != null) {
      rows.add(rowMap);
    }

    return rows;
  }
}
