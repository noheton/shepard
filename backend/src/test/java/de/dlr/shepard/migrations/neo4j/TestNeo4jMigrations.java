package de.dlr.shepard.migrations.neo4j;

import static de.dlr.shepard.migrations.neo4j.V12Helper.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.cypherdsl.core.Cypher.*;

import com.opencsv.exceptions.CsvValidationException;
import de.dlr.shepard.common.neo4j.MigrationsRunner;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.ConfigProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class TestNeo4jMigrations {

  /**
   * Random element that is specific for this test run.
   * This exists to identify data belonging to this test run to not pollute the namespaces of other test runs.
   */
  private static final String randomElement = RandomStringUtils.insecure().next(6, true, true);
  private static final QueryHelper q = new QueryHelper();
  private static final SampleNodeCreatorFactory sample = new SampleNodeCreatorFactory(randomElement);

  private static final String HAS_PAYLOAD = "has_payload";
  private static final String IS_IN_CONTAINER = "is_in_container";
  private static final String HAS_ANNOTATION = "has_annotation";

  private static void testNodeMigrated(Node old, Node migrated) {
    assertEquals(0, q.match(old).size());
    assertEquals(1, q.match(migrated).size());
  }

  private static void runMigrations(String targetVersion) {
    new MigrationsRunner(targetVersion).apply();
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
    q.create(collectionWithBadAttributes);

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
    var migratedCollection = q.match(collectionWithGoodAttributes, Collection.class).getFirst();
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
    q.create(legacyAnnotation);

    runMigrations("V10");

    var migratedAnnotation = node("SemanticAnnotation").withProperties(
      "propertyName",
      Cypher.literalOf("prop-" + randomElement),
      "valueName",
      Cypher.literalOf("value-" + randomElement)
    );

    testNodeMigrated(legacyAnnotation, migratedAnnotation);
  }

  /**
   * Prepare the test data, run the migrations and assert they run without aborting.
   */
  @Test
  public void testV11_0_NoException() {
    create2References1Timeseries2Containers();
    createSingleReferencedTimeseries();
    create2References1Timeseries1Container();
    create3References2Timeseries2Containers();
    create3References1Container();
    create2ReferencesOneContainer2Timeseries();
    create3References3Containers1Timeseries();
    createEmptyReference();
    runMigrations("V11");
    assertTrue(true);
  }

  /**
   * Assert that now each timeseries has a relationship to exactly one container.
   */
  @Test
  public void testV11_EachTimeseriesHasOneContainer() {
    var tsWithoutContainer = q.queryResults(
      "match(ts:Timeseries) where not exists((ts)-[]->(:TimeseriesContainer)) return ts"
    );
    assertEquals(0, tsWithoutContainer.size());
    var tsWithExactlyOneContainer = q.queryResults(
      "match(ts:Timeseries)-[r:is_in_container]->() with ts, count(r) as relcount where relcount = 1 return ts"
    );
    var numTs = q.queryResults("match(ts:Timeseries) with count(ts) as c return c", Long.class).getFirst();
    assertEquals(numTs, tsWithExactlyOneContainer.size());
    var tsWithSeveralContainers = q.queryResults(
      "match(ts:Timeseries)-[r:is_in_container]->() with ts, count(r) as relcount where relcount > 1 return ts"
    );
    assertEquals(0, tsWithSeveralContainers.size());
  }

  @Test
  public void testV11_EachReferenceHasTimeseriesExceptEmptyRef() {
    assertEquals(
      1,
      q
        .queryResults(
          "match(tsr:TimeseriesReference) where not exists " +
          "{ match(tsr)-[:has_payload]->(:Timeseries) } " +
          "return count(tsr)",
          Long.class
        )
        .getFirst()
    );
  }

  private static void createSingleReferencedTimeseries() {
    var c = sample.instance("SingleReferencedTimeseries");
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var annotation = c.annotation();
    var container = c.timeseriesContainer();
    q.create(
      ref1.relationshipTo(tsNode, HAS_PAYLOAD),
      ref1.relationshipTo(container, IS_IN_CONTAINER),
      ref1.relationshipTo(annotation, HAS_ANNOTATION)
    );
  }

  /**
   * Assert that a timeseries migrated correctly if it is referenced by a single reference.
   * In this case the timeseries should get a reference towards the container of its reference.
   */
  @Test
  public void testV11_SingleReferencedTimeseriesMigrated() {
    var c = sample.instance("SingleReferencedTimeseries");
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var annotation = c.annotation();
    var container = c.timeseriesContainer();
    var result = Cypher.match(
      ref1.relationshipTo(tsNode, HAS_PAYLOAD),
      ref1.relationshipTo(container, IS_IN_CONTAINER),
      ref1.relationshipTo(annotation, HAS_ANNOTATION),
      tsNode.relationshipTo(container, IS_IN_CONTAINER)
    )
      .returning(tsNode)
      .build();
    var results = q.queryResults(result, Object.class);
    assertEquals(1, results.size());
  }

  private static void create2References1Timeseries1Container() {
    var c = sample.instance("References1Timeseries1Container");
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var annotation = c.annotation();
    var container = c.timeseriesContainer().named("container");
    q.create(
      ref1.relationshipTo(tsNode, HAS_PAYLOAD),
      ref2.relationshipTo(tsNode, HAS_PAYLOAD),
      ref1.relationshipTo(container, IS_IN_CONTAINER),
      ref2.relationshipTo(container, IS_IN_CONTAINER),
      ref1.relationshipTo(annotation, HAS_ANNOTATION)
    );
  }

  /**
   * Assert that a timeseries migrated correctly if it is referenced by multiple references which however all lie within one container.
   * In this case the timeseries should get a reference towards this container.
   */
  @Test
  public void testV11_References1Timeseries1ContainerMigrated() {
    var c = sample.instance("References1Timeseries1Container");
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var annotation = c.annotation();
    var container = c.timeseriesContainer();
    var result = Cypher.match(
      ref1.relationshipTo(tsNode, HAS_PAYLOAD),
      ref1.relationshipTo(container, IS_IN_CONTAINER),
      ref1.relationshipTo(annotation, HAS_ANNOTATION),
      ref2.relationshipTo(container, IS_IN_CONTAINER),
      tsNode.relationshipTo(container, IS_IN_CONTAINER)
    )
      .returning(tsNode)
      .build();
    var results = q.queryResults(result, Object.class);
    assertEquals(1, results.size());
  }

  private static void create2References1Timeseries2Containers() {
    var c = sample.instance("2References1Timeseries2Containers");
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var annotation = c.annotation();
    var container1 = c.timeseriesContainer(1);
    var container2 = c.timeseriesContainer(2);
    q.create(
      ref1.relationshipTo(tsNode, HAS_PAYLOAD),
      ref2.relationshipTo(tsNode, HAS_PAYLOAD),
      ref1.relationshipTo(container1, IS_IN_CONTAINER),
      ref2.relationshipTo(container2, IS_IN_CONTAINER),
      ref1.relationshipTo(annotation, HAS_ANNOTATION)
    );
  }

  /**
   * Assert that a timeseries migrated correctly if it is referenced by multiple references and these references lie within different containers.
   * In that case multiple timeseries, one for each container, should be created and each timeseries should reference its container.
   */
  @Test
  public void testV11_2References1Timeseries2ContainersMigrated() {
    var c = sample.instance("2References1Timeseries2Containers");
    var tsNode1 = c.timeseries().named("tsNode1");
    var tsNode2 = c.timeseries().named("tsNode2");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var annotation = c.annotation();
    var container1 = c.timeseriesContainer(1);
    var container2 = c.timeseriesContainer(2);
    var result = Cypher.match(
      ref1.relationshipTo(tsNode1, HAS_PAYLOAD),
      ref2.relationshipTo(tsNode2, HAS_PAYLOAD),
      ref1.relationshipTo(container1, IS_IN_CONTAINER),
      ref2.relationshipTo(container2, IS_IN_CONTAINER),
      ref1.relationshipTo(annotation, HAS_ANNOTATION),
      tsNode1.relationshipTo(container1, IS_IN_CONTAINER),
      tsNode2.relationshipTo(container2, IS_IN_CONTAINER)
    )
      .returning(tsNode1, tsNode2)
      .build();
    var results = q.queryResults(result, Object.class);
    assertEquals(2, results.size());
  }

  private static void create3References2Timeseries2Containers() {
    var c = sample.instance("3References2Timeseries2Containers");
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var ref3 = c.timeseriesReference("ref3").named("ref3");
    var container1 = c.timeseriesContainer(1).named("c1");
    var container2 = c.timeseriesContainer(2).named("c2");
    q.create(
      ref1.relationshipTo(tsNode, HAS_PAYLOAD),
      ref2.relationshipTo(tsNode, HAS_PAYLOAD),
      ref3.relationshipTo(tsNode, HAS_PAYLOAD),
      ref1.relationshipTo(container1, IS_IN_CONTAINER),
      ref2.relationshipTo(container2, IS_IN_CONTAINER),
      ref3.relationshipTo(container2, IS_IN_CONTAINER)
    );
  }

  /**
   * Assert that a timeseries migrated correctly if it is referenced by multiple references by three references sharing two containers.
   * In this case the two timeseries, one for each container should be created.
   */
  @Test
  public void testV11_3References2Timeseries2ContainersMigrated() {
    var c = sample.instance("3References2Timeseries2Containers");
    var tsNode1 = c.timeseries().named("tsNode1");
    var tsNode2 = c.timeseries().named("tsNode2");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var ref3 = c.timeseriesReference("ref3").named("ref3");
    var container1 = c.timeseriesContainer(1).named("c1");
    var container2 = c.timeseriesContainer(2).named("c2");
    var queryTs1 = Cypher.match(
      ref1.relationshipTo(tsNode1, HAS_PAYLOAD),
      ref1.relationshipTo(container1, IS_IN_CONTAINER),
      tsNode1.relationshipTo(container1, IS_IN_CONTAINER)
    )
      .returning(tsNode1)
      .build();
    assertEquals(1, q.queryResults(queryTs1).size());
    var queryTs2 = Cypher.match(
      ref2.relationshipTo(tsNode2, HAS_PAYLOAD),
      ref3.relationshipTo(tsNode2, HAS_PAYLOAD),
      ref2.relationshipTo(container2, IS_IN_CONTAINER),
      ref3.relationshipTo(container2, IS_IN_CONTAINER),
      tsNode2.relationshipTo(container2, IS_IN_CONTAINER)
    )
      .returning(tsNode2)
      .build();
    assertEquals(1, q.queryResults(queryTs2).size());
  }

  private static void create3References1Container() {
    var c = sample.instance("3References1Container");
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var ref3 = c.timeseriesReference("ref3").named("ref3");
    var container = c.timeseriesContainer().named("c1");
    q.create(
      ref1.relationshipTo(tsNode, HAS_PAYLOAD),
      ref2.relationshipTo(tsNode, HAS_PAYLOAD),
      ref3.relationshipTo(tsNode, HAS_PAYLOAD),
      ref1.relationshipTo(container, IS_IN_CONTAINER),
      ref2.relationshipTo(container, IS_IN_CONTAINER),
      ref3.relationshipTo(container, IS_IN_CONTAINER)
    );
  }

  @Test
  public void testV11_3References1ContainerMigrated() {
    var c = sample.instance("3References1Container");
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var ref3 = c.timeseriesReference("ref3").named("ref3");
    var container = c.timeseriesContainer().named("c1");
    var query = Cypher.match(
      ref1.relationshipTo(tsNode, HAS_PAYLOAD),
      ref2.relationshipTo(tsNode, HAS_PAYLOAD),
      ref3.relationshipTo(tsNode, HAS_PAYLOAD),
      ref1.relationshipTo(container, IS_IN_CONTAINER),
      ref2.relationshipTo(container, IS_IN_CONTAINER),
      ref3.relationshipTo(container, IS_IN_CONTAINER),
      tsNode.relationshipTo(container, IS_IN_CONTAINER)
    )
      .returning(tsNode)
      .build();
    assertEquals(1, q.queryResults(query).size());
  }

  private static void create2ReferencesOneContainer2Timeseries() {
    var c = sample.instance("2ReferencesOneContainer2Timeseries");
    var tsNode1 = c.timeseries("ts1").named("tsNode1");
    var tsNode2 = c.timeseries("ts2").named("tsNode2");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var container = c.timeseriesContainer().named("c1");
    q.create(
      ref1.relationshipTo(tsNode1, HAS_PAYLOAD),
      ref2.relationshipTo(tsNode2, HAS_PAYLOAD),
      ref1.relationshipTo(container, IS_IN_CONTAINER),
      ref2.relationshipTo(container, IS_IN_CONTAINER)
    );
  }

  @Test
  public void testV11_2ReferencesOneContainer2TimeseriesMigrated() {
    var c = sample.instance("2ReferencesOneContainer2Timeseries");
    var tsNode1 = c.timeseries("ts1").named("tsNode1");
    var tsNode2 = c.timeseries("ts2").named("tsNode2");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var container = c.timeseriesContainer().named("c");
    var query = Cypher.match(
      ref1.relationshipTo(tsNode1, HAS_PAYLOAD),
      ref2.relationshipTo(tsNode2, HAS_PAYLOAD),
      ref1.relationshipTo(container, IS_IN_CONTAINER),
      ref2.relationshipTo(container, IS_IN_CONTAINER)
    )
      .returning(tsNode1, tsNode2)
      .build();
    assertEquals(2, q.queryResults(query).size());
  }

  private static void create3References3Containers1Timeseries() {
    var c = sample.instance("3References3Containers1Timeseries");
    var tsNode = c.timeseries().named("ts");
    var ref1 = c.timeseriesReference(1).named("r1");
    var ref2 = c.timeseriesReference(2).named("r2");
    var ref3 = c.timeseriesReference(3).named("r3");
    var c1 = c.timeseriesContainer(1).named("c1");
    var c2 = c.timeseriesContainer(2).named("c2");
    var c3 = c.timeseriesContainer(3).named("c3");
    q.create(
      ref1.relationshipTo(tsNode, HAS_PAYLOAD),
      ref2.relationshipTo(tsNode, HAS_PAYLOAD),
      ref3.relationshipTo(tsNode, HAS_PAYLOAD),
      ref1.relationshipTo(c1, IS_IN_CONTAINER),
      ref2.relationshipTo(c2, IS_IN_CONTAINER),
      ref3.relationshipTo(c3, IS_IN_CONTAINER)
    );
  }

  @Test
  public void testV11_3References3Containers1TimeseriesMigrated() {
    var c = sample.instance("3References3Containers1Timeseries");
    var tsNode1 = c.timeseries().named("ts1");
    var tsNode2 = c.timeseries().named("ts2");
    var tsNode3 = c.timeseries().named("ts3");
    var ref1 = c.timeseriesReference(1).named("r1");
    var ref2 = c.timeseriesReference(2).named("r2");
    var ref3 = c.timeseriesReference(3).named("r3");
    var c1 = c.timeseriesContainer(1).named("c1");
    var c2 = c.timeseriesContainer(2).named("c2");
    var c3 = c.timeseriesContainer(3).named("c3");
    var query = Cypher.match(
      ref1.relationshipTo(tsNode1, HAS_PAYLOAD),
      ref2.relationshipTo(tsNode2, HAS_PAYLOAD),
      ref3.relationshipTo(tsNode3, HAS_PAYLOAD),
      ref1.relationshipTo(c1, IS_IN_CONTAINER),
      ref2.relationshipTo(c2, IS_IN_CONTAINER),
      ref3.relationshipTo(c3, IS_IN_CONTAINER),
      tsNode1.relationshipTo(c1, IS_IN_CONTAINER),
      tsNode2.relationshipTo(c2, IS_IN_CONTAINER),
      tsNode3.relationshipTo(c3, IS_IN_CONTAINER)
    )
      .returning(tsNode1, tsNode2, tsNode3)
      .build();
    assertEquals(3, q.queryResults(query).size());
  }

  private static void createEmptyReference() {
    var c = sample.instance("EmptyReference");
    var ref = c.timeseriesReference();
    var container = c.timeseriesContainer();
    q.create(ref.relationshipTo(container, IS_IN_CONTAINER));
  }

  @Test
  public void testV11_EmptyReferenceUntouched() {
    var c = sample.instance("EmptyReference");
    var ref = c.timeseriesReference();
    var container = c.timeseriesContainer();
    var query = Cypher.match(ref.relationshipTo(container, IS_IN_CONTAINER)).returning(ref).build();
    assertEquals(1, q.queryResults(query).size());
  }

  List<Long> testingTimeseriesIds;

  @Test
  public void testV12_0_NoException() throws CsvValidationException, IOException, ClassNotFoundException {
    prepareV12TimescaleData();
    runMigrations("V12");
  }

  /**
   * Assert that the timeseries from timescale are now present as timeseries nodes in the graph database.
   */
  @Test
  public void testV12_TimeseriesPresentInGraphDb() {
    var ts_result_list = q.match(node("Timeseries"));
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

  /**
   * Assert that meta-information does not exist anymore in timescaledb.
   */
  @Test
  public void testV12_MetadataDeletedInTimeseriesDb() throws SQLException, ClassNotFoundException {
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
  @Test
  public void testV12_TimeseriesDatapointsIntact() throws SQLException, ClassNotFoundException {
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
  public void testV12_NewTimeseriesMergedWithPreexisting() {}

  private Connection createTimeseriesConnection() throws SQLException, ClassNotFoundException {
    Class.forName("org.postgresql.Driver");
    var url = ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class);
    var user = ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class);
    var pass = ConfigProvider.getConfig().getValue("quarkus.datasource.password", String.class);
    return DriverManager.getConnection(url, user, pass);
  }

  /**
   Prepare the timeseries database for the migration of timeseries metadata towards the graph database.
   */
  private void prepareV12TimescaleData() throws CsvValidationException, IOException, ClassNotFoundException {
    Class.forName("org.postgresql.Driver");
    var url = ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class);
    var user = ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class);
    var pass = ConfigProvider.getConfig().getValue("quarkus.datasource.password", String.class);
    Flyway flyway = Flyway.configure().dataSource(url, user, pass).load();
    flyway.migrate();

    var dbEntries = readCsvAsMapList("src/test/resources/timeseries_import_migration_test.csv");
    var ts_list = dbEntries.stream().map(V12Helper::csvEntryToTs);

    testingTimeseriesIds = ts_list
      .map(ts -> {
        try (var connection = createTimeseriesConnection()) {
          var point = ts.getPoints().getFirst();
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
    var tsExpected = createMigratedTimeseries(containerId, measurement, valueType, timeseriesId);
    var tsListActual = q.match(
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
    assertEquals(tsExpected, tsListActual.getFirst());
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
}
