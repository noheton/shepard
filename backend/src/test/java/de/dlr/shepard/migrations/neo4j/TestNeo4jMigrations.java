package de.dlr.shepard.migrations.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.neo4j.cypherdsl.core.Cypher.*;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvValidationException;
import de.dlr.shepard.common.neo4j.MigrationsRunner;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.data.timeseries.io.TimeseriesWithDataPoints;
import de.dlr.shepard.data.timeseries.model.MigratedTimeseries;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.validation.constraints.NotNull;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.ConfigProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;
import org.neo4j.cypherdsl.core.PatternElement;
import org.neo4j.cypherdsl.core.Statement;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class TestNeo4jMigrations {

  private static final String randomElement = RandomStringUtils.insecure().next(6, true, true);
  private static final Renderer cypherRenderer = Renderer.getDefaultRenderer();
  private static Session session;

  @BeforeAll
  public static void setUp() {
    var conn = NeoConnector.getInstance();
    conn.connect();
    session = conn.getNeo4jSession();
  }

  private static Result query(Statement statement) {
    var cypherQuery = cypherRenderer.render(statement);
    return session.query(cypherQuery, Collections.emptyMap());
  }

  private static void testNodeMigrated(Node old, Node migrated) {
    assertEquals(0, match(old).size());
    assertEquals(1, match(migrated).size());
  }

  private static void runMigrations(String targetVersion) {
    new MigrationsRunner(targetVersion).apply();
  }

  private static void create(PatternElement node) {
    var statement = Cypher.create(node).build();
    query(statement);
  }

  private static List<Object> match(Node node) {
    return match(node, Object.class);
  }

  private static <T> List<T> match(Node node, Class<T> type) {
    var statement = Cypher.match(node).returning(node).build();
    var result = query(statement);
    return StreamSupport.stream(result.spliterator(), false)
      .map(Map::values)
      .flatMap(java.util.Collection::stream)
      .map(type::cast)
      .toList();
  }

  private static <K, V> void assertEqualsMaps(Map<K, V> expected, Map<K, V> actual) {
    assertEquals(expected.keySet(), actual.keySet());
  }

  @Test
  public void testV09() {
    var collectionWithBadAttributes = node("Collection").withProperties(
      "name",
      Cypher.literalOf(randomElement),
      "attributes.a",
      Cypher.literalOf(0),
      "attributes.b.c",
      Cypher.literalOf(1)
    );
    create(collectionWithBadAttributes);

    runMigrations("V9");

    var collectionWithGoodAttributes = node("Collection").withProperties(
      "name",
      Cypher.literalOf(randomElement),
      "attributes||a",
      Cypher.literalOf(0),
      "attributes||b.c",
      Cypher.literalOf(1)
    );

    testNodeMigrated(collectionWithBadAttributes, collectionWithGoodAttributes);

    // test that the migration has not touched the attribute values
    var migratedCollection = match(collectionWithGoodAttributes, Collection.class).get(0);
    assertEqualsMaps(Map.of("a", "0", "b.c", "1"), migratedCollection.getAttributes());
  }

  @Test
  public void testV10() {
    var legacyAnnotation = node("SemanticAnnotation").withProperties(
      "name",
      Cypher.literalOf("prop-" + randomElement + "::" + "value-" + randomElement),
      "propertyIRI",
      Cypher.literalOf("piri"),
      "valueIRI",
      Cypher.literalOf("viri")
    );
    create(legacyAnnotation);

    runMigrations("V10");

    var migratedAnnotation = node("SemanticAnnotation").withProperties(
      "propertyName",
      Cypher.literalOf("prop-" + randomElement),
      "valueName",
      Cypher.literalOf("value-" + randomElement)
    );

    testNodeMigrated(legacyAnnotation, migratedAnnotation);
  }

  private static List<Long> testingTimeseriesIds;

  @Test
  public void testV11_01_timeseriesPresentInGraphDb()
    throws ClassNotFoundException, IOException, CsvValidationException {
    testingTimeseriesIds = prepareV11TimescaleData();
    prepareV11Neo4jData();

    runMigrations("V11");

    var ts_result_list = match(node("Timeseries"), MigratedTimeseries.class);
    assertEquals(8, ts_result_list.size());
    assertPresent(testingTimeseriesIds.get(0), 1, "motion", DataPointValueType.Boolean);
    assertPresent(testingTimeseriesIds.get(1), 2, "motion", DataPointValueType.Boolean);
    assertPresent(testingTimeseriesIds.get(2), 1, "motion", DataPointValueType.Double);
    assertPresent(testingTimeseriesIds.get(3), 2, "motion", DataPointValueType.Double);
    assertPresent(testingTimeseriesIds.get(4), 1, "status", DataPointValueType.String);
    assertPresent(testingTimeseriesIds.get(5), 2, "status", DataPointValueType.String);
    assertPresent(testingTimeseriesIds.get(6), 1, "int_level", DataPointValueType.Integer);
    assertPresent(testingTimeseriesIds.get(7), 2, "int_level", DataPointValueType.Integer);
  }

  @Test
  public void testV11_02_metadataDeletedInTimeseriesDb() throws SQLException, ClassNotFoundException {
    try (var connection = createTimeseriesConnection()) {
      var result = connection
        .prepareStatement("select tablename from pg_tables where tablename = 'timeseries'")
        .executeQuery();
      assertFalse(result.next());
    }
  }

  @Test
  public void testV11_03_timeseriesDatapointsIntact() throws SQLException, ClassNotFoundException {
    try (var connection = createTimeseriesConnection()) {
      var tsCount = connection
        .prepareStatement("select count(timeseries_id) from timeseries_data_points")
        .executeQuery();
      tsCount.next();
      assertEquals(15, tsCount.getInt("count"));
      var actualTsIds = connection
        .prepareStatement("select timeseries_id from timeseries_data_points group by timeseries_id")
        .executeQuery();
      while (actualTsIds.next()) assertTrue(testingTimeseriesIds.contains(actualTsIds.getLong("timeseries_id")));
    }
  }

  @Test
  public void testV12_AnnotatedTimeseriesMigrated() {
    fail();
  }

  private Connection createTimeseriesConnection() throws SQLException, ClassNotFoundException {
    Class.forName("org.postgresql.Driver");
    var url = ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class);
    var user = ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class);
    var pass = ConfigProvider.getConfig().getValue("quarkus.datasource.password", String.class);
    return DriverManager.getConnection(url, user, pass);
  }

  private void prepareV11Neo4jData() {
    if (testingTimeseriesIds == null) throw new RuntimeException(
      "TimescaleDB must be filled and timeseries IDs must exist!"
    );
    createAnnotatableTimeseries();
    createReferencedTimeseries();
  }

  private static void createReferencedTimeseries() {
    var tsNode = node("Timeseries").withProperties(
      "measurement",
      Cypher.literalOf("measurement-" + randomElement),
      "device",
      Cypher.literalOf("device" + randomElement),
      "location",
      Cypher.literalOf("location" + randomElement),
      "symbolicName",
      Cypher.literalOf("symbolicName"),
      "field",
      Cypher.literalOf("field")
    );
    var ref1 = node("TimeseriesReference", "VersionableEntity", "BasicReference", "BasicEntity").withProperties(
      "createdAt",
      Cypher.literalOf(100),
      "deleted",
      Cypher.literalOf(false),
      "end",
      Cypher.literalOf(2000),
      "name",
      Cypher.literalOf("ref-1-" + randomElement),
      "shepardId",
      Cypher.literalOf(5),
      "start",
      Cypher.literalOf(1000)
    );
    var ref2 = node("TimeseriesReference", "VersionableEntity", "BasicReference", "BasicEntity").withProperties(
      "createdAt",
      Cypher.literalOf(100),
      "deleted",
      Cypher.literalOf(false),
      "end",
      Cypher.literalOf(2000),
      "name",
      Cypher.literalOf("ref-2-" + randomElement),
      "shepardId",
      Cypher.literalOf(6),
      "start",
      Cypher.literalOf(1000)
    );
    var annotation = node("SemanticAnnotation").withProperties(
      "propertyName",
      Cypher.literalOf("prop-on-ts-ref-" + randomElement),
      "valueName",
      Cypher.literalOf("value-on-ts-ref-" + randomElement)
    );
    var container1 = node("TimeseriesContainer", "BasicEntity", "BasicContainer").withProperties(
      "createdAt",
      Cypher.literalOf(200),
      "deleted",
      Cypher.literalOf(false),
      "name",
      Cypher.literalOf("TimeseriesContainer-1-" + randomElement)
    );
    var container2 = node("TimeseriesContainer", "BasicEntity", "BasicContainer").withProperties(
      "createdAt",
      Cypher.literalOf(300),
      "deleted",
      Cypher.literalOf(false),
      "name",
      Cypher.literalOf("TimeseriesContainer-2-" + randomElement)
    );
    create(ref1.relationshipTo(tsNode, "has_payload"));
    create(ref2.relationshipTo(tsNode, "has_payload"));
    create(ref1.relationshipTo(container1, "is_in_container"));
    create(ref2.relationshipTo(container2, "is_in_container"));
    create(ref1.relationshipTo(annotation, "has_annotation"));
  }

  private static void createAnnotatableTimeseries() {
    var tsNode = node("AnnotatableTimeseries").withProperties(
      "containerId",
      Cypher.literalOf(1),
      "timeseriesId",
      Cypher.literalOf(testingTimeseriesIds.get(0))
    );
    var annotation = node("SemanticAnnotation").withProperties(
      "propertyName",
      Cypher.literalOf("prop-on-timeseries-" + randomElement),
      "valueName",
      Cypher.literalOf("value-on-timeseries-" + randomElement)
    );
    var relation = tsNode.relationshipTo(annotation, "has_annotation");
    create(relation);
  }

  /**
   Prepare the timeseries database for the migration of timeseries metadata towards the graph database.
   @return List of timeseries unique timeseries IDs. The insertion order cannot be guaranteed.
   */
  private List<Long> prepareV11TimescaleData() throws CsvValidationException, IOException, ClassNotFoundException {
    Class.forName("org.postgresql.Driver");
    var url = ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class);
    var user = ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class);
    var pass = ConfigProvider.getConfig().getValue("quarkus.datasource.password", String.class);
    Flyway flyway = Flyway.configure().dataSource(url, user, pass).load();
    flyway.migrate();

    var dbEntries = readCsvAsMapList("src/test/resources/timeseries_import_migration_test.csv");
    var ts_list = dbEntries.stream().map(TestNeo4jMigrations::csvEntryToTs);

    return ts_list
      .map(ts -> {
        try (var connection = createTimeseriesConnection()) {
          var point = ts.getPoints().get(0);
          var sql = Files.readString(Path.of("src/test/resources/insert_timeseries.sql"));
          var stmt = connection.prepareStatement(sql);
          stmt.setBigDecimal(1, BigDecimal.valueOf(ts.getContainerId()));
          stmt.setString(2, ts.getTimeseries().getMeasurement());
          stmt.setString(3, ts.getTimeseries().getField());
          stmt.setString(4, ts.getTimeseries().getSymbolicName());
          stmt.setString(5, ts.getTimeseries().getDevice());
          stmt.setString(6, ts.getTimeseries().getLocation());
          stmt.setString(7, valueToValueType(point.getValue()).toString());
          var resultSet = stmt.executeQuery();
          var ts_id = resultSet.next() ? Optional.of(resultSet.getInt(1)) : Optional.empty();

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
          connection.close();
          return ts_id;
        } catch (SQLException | IOException | ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      })
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(Object::toString)
      .map(Long::valueOf)
      .toList();
  }

  private void assertPresent(long timeseriesId, long containerId, String measurement, DataPointValueType valueType) {
    var tsExpected = create(containerId, measurement, valueType, timeseriesId);
    var tsListActual = match(
      node("Timeseries")
        .withProperties(
          "timeseriesId",
          Cypher.literalOf(timeseriesId),
          "measurement",
          Cypher.literalOf(measurement),
          "valueType",
          Cypher.literalOf(valueType.toString())
        )
        .relationshipTo(node("TimeseriesContainer").withProperties("containerId", Cypher.literalOf(containerId)))
        .getLeft()
    );
    assertEquals(1, tsListActual.size());
    assertEquals(tsExpected, tsListActual.get(0));
  }

  /**
   * Create a migrated timeseries from containerId, measurement and valueType.
   * device, location, symbolicName and field remain fixed.
   */
  private static MigratedTimeseries create(
    long containerId,
    String measurement,
    DataPointValueType valueType,
    long timeseriesId
  ) {
    return new MigratedTimeseries(
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

  /**
   * Helper class to represent a timeseries including its corresponding container id.
   */
  @EqualsAndHashCode(callSuper = true)
  @Data
  private static class ContaineredTs extends TimeseriesWithDataPoints {

    @NotNull
    private long containerId;

    public ContaineredTs(long containerId, Timeseries timeseries, List<TimeseriesDataPoint> points) {
      super(timeseries, points);
      this.containerId = containerId;
    }
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
