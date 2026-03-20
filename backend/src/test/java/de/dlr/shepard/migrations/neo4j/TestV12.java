package de.dlr.shepard.migrations.neo4j;

import static de.dlr.shepard.migrations.neo4j.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.cypherdsl.core.Cypher.literalOf;
import static org.neo4j.cypherdsl.core.Cypher.node;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvValidationException;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
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
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;

public class TestV12 extends MigrationTest {

  private List<PostV12Timeseries> allTimeseries;

  @Override
  public void setupPreMigrationData() throws CsvValidationException, IOException, ClassNotFoundException {
    clearDatabase();
    var containerIdMappings = preparePreexistingNeo4jData();
    var containeredTimeseries = prepareV12TimescaleData(containerIdMappings);
    allTimeseries = containeredTsToInternalRep(containeredTimeseries)
      .sorted(Comparator.comparing(PostV12Timeseries::getTimeseriesId))
      .toList();
  }

  @Override
  public String getTargetVersion() {
    return "V12";
  }

  private void clearDatabase() {
    q.deleteAll("Timeseries");
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
            "measurement",
            Cypher.literalOf(ts.getMeasurement()),
            "valueType",
            Cypher.literalOf(ts.getValueType().toString())
          )
          .relationshipTo(
            node("TimeseriesContainer").withProperties("name", Cypher.literalOf(ts.getContainer().getName()))
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
        allTimeseries
          .stream()
          .map(PostV12Timeseries::getTimeseriesId)
          .toList()
          .contains(actualTsIds.getLong("timeseries_id"))
      );
    }
  }

  private final Node intLevelTs = node("Timeseries").withProperties(
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
  );

  private final SampleNodeCreator sampleCreatorV12 = sample.instance("V12");
  private final Node tsc1 = sampleCreatorV12.timeseriesContainer(1).named("tsc1");
  private final Node tsc2 = sampleCreatorV12.timeseriesContainer(2).named("tsc2");

  /**
   * Prepare the preexisting data in Neo4j.
   * Those are two already existing timeseries and their respective containers.
   * @return The mapping of the "testing" container id which is originally assigned to timeseries to the actual \
   * container ids which are generated by neo4j. \
   * This is a crutch around not being able to predetermine the container id for the test case since we rely on the internalId of Neo4j.
   */
  private Map<Long, Long> preparePreexistingNeo4jData() {
    var insertTsc1 = Cypher.create(intLevelTs.relationshipTo(tsc1, IS_IN_CONTAINER))
      .returning(tsc1.internalId())
      .build();
    var insertTsc2 = Cypher.create(intLevelTs.relationshipTo(tsc2, IS_IN_CONTAINER))
      .returning(tsc2.internalId())
      .build();
    return Map.of(
      1L,
      q.queryResults(insertTsc1, Long.class).getFirst(),
      2L,
      q.queryResults(insertTsc2, Long.class).getFirst()
    );
  }

  public void assertNewTimeseriesMergedWithPreexisting() {
    var c = sample.instance("V12");
    var nodes1 = q.match(intLevelTs.relationshipTo(c.timeseriesContainer("V12-1")).getLeft());
    assertEquals(1, nodes1.size());
    var actualNode1 = nodes1.getFirst();
    var expectedNode1 = intLevelTs.withProperties(
      "timeseriesId",
      Cypher.literalOf(allTimeseries.get(13).getTimeseriesId()),
      "valueType",
      Cypher.literalOf("int_value")
    );
    assertEquals(expectedNode1, actualNode1);

    var nodes2 = q.match(intLevelTs.relationshipTo(c.timeseriesContainer("V12-2")).getLeft());
    assertEquals(1, nodes2.size());
    var actualNode2 = nodes2.getFirst();
    var expectedNode2 = intLevelTs.withProperties(
      "timeseriesId",
      Cypher.literalOf(allTimeseries.get(14).getTimeseriesId()),
      "valueType",
      Cypher.literalOf("int_value")
    );
    assertEquals(expectedNode2, actualNode2);
  }

  private Connection createTimeseriesConnection() throws SQLException, ClassNotFoundException {
    Class.forName("org.postgresql.Driver");
    var url = ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class);
    var user = ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class);
    var pass = ConfigProvider.getConfig().getValue("quarkus.datasource.password", String.class);
    return DriverManager.getConnection(url, user, pass);
  }

  private record Triple<T, U, V>(T x, U y, V z) {}

  /**
   Prepare the timeseries database for the migration of timeseries metadata towards the graph database.
   */
  private Stream<Triple<Long, DataPointValueType, ContaineredTs>> prepareV12TimescaleData(
    Map<Long, Long> containerIdMappings
  ) throws CsvValidationException, IOException, ClassNotFoundException {
    Class.forName("org.postgresql.Driver");
    var url = ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class);
    var user = ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class);
    var pass = ConfigProvider.getConfig().getValue("quarkus.datasource.password", String.class);
    Flyway flyway = Flyway.configure()
      .target("1.7.0")
      .dataSource(url, user, pass)
      .locations("db/migration", "classpath:de/dlr/shepard/data/timeseries/migrations")
      .load();
    flyway.migrate();

    var dbEntries = readCsvAsMapList("src/test/resources/timeseries_import_migration_test.csv");

    var tsList = dbEntries.stream().map(TestV12::csvEntryToTs).toList();

    // replace the dummy container ids 1 and 2 with the actual ids from Neo4j
    tsList.forEach(t -> t.setContainerId(containerIdMappings.get(t.getContainerId())));

    return tsList.stream().map(this::addTimeseriesToTimescale).filter(Optional::isPresent).map(Optional::get);
  }

  /**
   * Add the first point of the ContaineredTs to the timescale table timeseries_with_data_points.
   * If the timeseries does not exist yet create it and return its id including the ContaineredTs.
   * @param ts ContaineredTs that must have only one data point
   * @return Empty optional if timeseries already existed, otherwise timeseries id and ContaineredTs.
   */
  private Optional<Triple<Long, DataPointValueType, ContaineredTs>> addTimeseriesToTimescale(ContaineredTs ts) {
    try (var connection = createTimeseriesConnection()) {
      var point = ts.getPoints().getFirst();
      var sql = Files.readString(Path.of("src/test/resources/insert_timeseries.sql"));
      var stmt = connection.prepareStatement(sql);
      var valueType = valueToValueType(point.getValue());
      stmt.setBigDecimal(1, BigDecimal.valueOf(ts.getContainerId()));
      stmt.setString(2, ts.getTimeseries().getMeasurement());
      stmt.setString(3, ts.getTimeseries().getField());
      stmt.setString(4, ts.getTimeseries().getSymbolicName());
      stmt.setString(5, ts.getTimeseries().getDevice());
      stmt.setString(6, ts.getTimeseries().getLocation());
      stmt.setString(7, valueType.toString());
      var resultSet = stmt.executeQuery();
      Optional<Long> ts_id = resultSet.next() ? Optional.of(resultSet.getLong(1)) : Optional.empty();

      var sql2 = Files.readString(Path.of("src/test/resources/insert_timeseries_data_point.sql"));
      sql2 = sql2.replace(":column", getDatapointColumn(point));
      var stmt2 = connection.prepareStatement(sql2);
      stmt2.setBigDecimal(1, BigDecimal.valueOf(ts.getContainerId()));
      stmt2.setString(2, ts.getTimeseries().getMeasurement());
      stmt2.setString(3, ts.getTimeseries().getField());
      stmt2.setString(4, ts.getTimeseries().getSymbolicName());
      stmt2.setString(5, ts.getTimeseries().getDevice());
      stmt2.setString(6, ts.getTimeseries().getLocation());
      stmt2.setBigDecimal(7, BigDecimal.valueOf(point.getTimestamp()));
      stmt2.setObject(8, point.getValue());
      stmt2.executeUpdate();

      return ts_id.map(tsIdValue -> new Triple<>(tsIdValue, valueType, ts));
    } catch (SQLException | IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private Stream<PostV12Timeseries> containeredTsToInternalRep(
    Stream<Triple<Long, DataPointValueType, ContaineredTs>> tsList
  ) {
    return tsList.map(p -> {
      var tsId = p.x;
      var vtype = p.y;
      var cts = p.z;
      var containerList = cts.getContainerId() == 1
        ? q.match(tsc1, TimeseriesContainer.class)
        : q.match(tsc2, TimeseriesContainer.class);
      assert containerList.size() == 1;
      var container = containerList.getFirst();
      return new PostV12Timeseries(
        cts.getTimeseries().getMeasurement(),
        cts.getTimeseries().getDevice(),
        cts.getTimeseries().getLocation(),
        cts.getTimeseries().getSymbolicName(),
        cts.getTimeseries().getField(),
        vtype,
        tsId,
        container
      );
    });
  }

  /**
   * Create a migrated timeseries from containerId, measurement and valueType.
   * device, location, symbolicName and field remain fixed.
   */
  private static PostV12Timeseries createMigratedTimeseries(
    long containerId,
    String measurement,
    DataPointValueType valueType,
    long timeseriesId
  ) {
    return new PostV12Timeseries(
      measurement,
      "device",
      "location",
      "symbolicName",
      "field",
      valueType,
      timeseriesId,
      new TimeseriesContainer(containerId)
    );
  }

  /**
   * Get the column name into which a timeseries data point should be saved in timescale.
   */
  private static String getDatapointColumn(TimeseriesDataPoint p) {
    if (p.getValue() instanceof Double) return "double_value";
    else if (p.getValue() instanceof String) return "string_value";
    else if (p.getValue() instanceof Boolean) return "boolean_value";
    else if (p.getValue() instanceof Integer) return "int_value";
    throw new RuntimeException("Data point " + p + " is of unfitting value!");
  }

  private static ContaineredTs csvEntryToTs(Map<String, String> entry) {
    return new ContaineredTs(
      Long.parseLong(entry.get("CONTAINERID")),
      new Timeseries(
        entry.get("MEASUREMENT"),
        entry.get("DEVICE"),
        entry.get("LOCATION"),
        entry.get("SYMBOLICNAME"),
        entry.get("FIELD")
      ),
      List.of(new TimeseriesDataPoint(Long.parseLong(entry.get("TIMESTAMP")), strValueToObject(entry.get("VALUE"))))
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
