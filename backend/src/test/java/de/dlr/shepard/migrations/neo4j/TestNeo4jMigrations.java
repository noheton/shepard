package de.dlr.shepard.migrations.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.sql.PreparedStatement;
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

  private static void create(Node node) {
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

  @Test
  public void testV11() throws ClassNotFoundException, SQLException, IOException, CsvValidationException {
    Class.forName("org.postgresql.Driver");
    var url = ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class);
    var user = ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class);
    var pass = ConfigProvider.getConfig().getValue("quarkus.datasource.password", String.class);
    Flyway flyway = Flyway.configure().dataSource(url, user, pass).load();
    flyway.migrate();

    var connection = DriverManager.getConnection(url, user, pass);
    var dbEntries = readCsvAsMapList("src/test/resources/timeseries_import_migration_test.csv");
    var ts_list = dbEntries.stream().map(TestNeo4jMigrations::csvEntryToTs);

    var tsIds = ts_list
      .map(ts -> {
        try {
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
          return ts_id;
        } catch (SQLException | IOException e) {
          throw new RuntimeException(e);
        }
      })
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(Object::toString)
      .map(Long::valueOf)
      .toList();

    runMigrations("V11");

    var ts_result_list = match(node("Timeseries"), MigratedTimeseries.class);
    assertEquals(4, ts_result_list.size());
    assertPresent(tsIds.get(0), 1, "motion", DataPointValueType.Boolean);
    assertPresent(tsIds.get(1), 2, "motion", DataPointValueType.Boolean);
    assertPresent(tsIds.get(2), 1, "motion", DataPointValueType.Double);
    assertPresent(tsIds.get(3), 2, "motion", DataPointValueType.Double);
    assertPresent(tsIds.get(4), 1, "status", DataPointValueType.String);
    assertPresent(tsIds.get(5), 2, "status", DataPointValueType.String);
    assertPresent(tsIds.get(6), 1, "int_level", DataPointValueType.Integer);
    assertPresent(tsIds.get(7), 2, "int_level", DataPointValueType.Integer);
  }

  private void assertPresent(long timeseriesId, long containerId, String measurement, DataPointValueType valueType) {
    var tsExpected = create(containerId, measurement, valueType);
    var tsListActual = match(
      node("Timeseries").withProperties(
        "timeseriesId",
        Cypher.literalOf(timeseriesId),
        "containerId",
        Cypher.literalOf(containerId),
        "measurement",
        Cypher.literalOf(measurement),
        "valueType",
        Cypher.literalOf(valueType.toString())
      )
    );
    assertEquals(1, tsListActual.size());
    assertEquals(tsExpected, tsListActual.get(0));
  }

  private static MigratedTimeseries create(long containerId, String measurement, DataPointValueType valueType) {
    return new MigratedTimeseries(
      measurement,
      "device",
      "location",
      "symbolicName",
      "field",
      valueType,
      //      timeseriesId,
      0,
      new TimeseriesContainer(containerId)
    );
  }

  private static class SQLBuilder {

    private String sql;

    public SQLBuilder(String sql) {
      this.sql = sql;
    }

    private void internalSet(String key, String s) {
      this.sql = sql.replace(":" + key, s);
    }

    public SQLBuilder set(String key, String s) {
      this.internalSet(key, "'" + s + "'");
      return this;
    }

    public SQLBuilder set(String key, String s, boolean escape) {
      if (escape) this.set(key, s);
      else this.internalSet(key, s);
      return this;
    }

    public SQLBuilder set(String key, Long l) {
      this.internalSet(key, String.valueOf(l));
      return this;
    }

    public SQLBuilder set(String key, Double d) {
      this.internalSet(key, String.valueOf(d));
      return this;
    }

    public SQLBuilder set(String key, Object o) {
      this.internalSet(key, String.valueOf(o));
      return this;
    }

    public String build() {
      return this.sql;
    }
  }

  private static PreparedStatement buildTimeseriesInsert(Connection con, ContaineredTs ts)
    throws IOException, SQLException {
    var point = ts.getPoints().get(0);
    String sql = Files.readString(Path.of("src/test/resources/insert_timeseries.sql"));
    var builder = new SQLBuilder(sql)
      .set("container_id", ts.getContainerId())
      .set("measurement", ts.getTimeseries().getMeasurement())
      .set("field", ts.getTimeseries().getField())
      .set("symbolic_name", ts.getTimeseries().getSymbolicName())
      .set("device", ts.getTimeseries().getDevice())
      .set("location", ts.getTimeseries().getLocation())
      .set("value_type", valueToValueType(point.getValue()).toString())
      .set("timestamp", point.getTimestamp())
      .set("dp_column", getDatapointColumn(point), false);

    var valType = valueToValueType(point.getValue());
    switch (valType) {
      case Double -> builder.set("value", (Double) point.getValue());
      case Integer -> builder.set("value", (Integer) point.getValue());
      case Boolean -> builder.set("value", (Boolean) point.getValue());
      case String -> builder.set("value", (String) point.getValue());
    }

    PreparedStatement st = con.prepareStatement(builder.build());
    //    System.out.println();
    //    System.out.println(st.toString());
    return st;
  }

  private static String getDatapointColumn(TimeseriesDataPoint p) {
    if (p.getValue() instanceof Double) return "double_value";
    else if (p.getValue() instanceof String) return "string_value";
    else if (p.getValue() instanceof Boolean) return "boolean_value";
    else if (p.getValue() instanceof Integer) return "int_value";
    throw new RuntimeException("Data point " + p + " is of unfitting value!");
  }

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
