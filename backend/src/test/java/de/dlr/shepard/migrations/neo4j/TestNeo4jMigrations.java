package de.dlr.shepard.migrations.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.cypherdsl.core.Cypher.*;

import de.dlr.shepard.common.neo4j.MigrationsRunner;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.context.collection.entities.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Literal;
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

  private static void create(PatternElement... creatable) {
    var statement = Cypher.create(creatable).build();
    query(statement);
  }

  private static List<Object> match(Node node) {
    return match(node, Object.class);
  }

  private static <T> List<T> match(Node node, Class<T> type) {
    var statement = Cypher.match(node).returning(node).build();
    return queryResults(statement, type);
  }

  private static <T> List<T> queryResults(Statement statement, Class<T> type) {
    var cypherQuery = cypherRenderer.render(statement);
    return queryResults(cypherQuery, type);
  }

  private static List<Object> queryResults(String cypherQuery) {
    return queryResults(cypherQuery, Object.class);
  }

  private static <T> List<T> queryResults(String cypherQuery, Class<T> type) {
    var result = session.query(cypherQuery, Collections.emptyMap());
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
    var migratedCollection = (de.dlr.shepard.context.collection.entities.Collection) match(
      collectionWithGoodAttributes
    ).getFirst();
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
      literal("prop"),
      "valueName",
      literal("value")
    );

    testNodeMigrated(legacyAnnotation, migratedAnnotation);
  }

  /**
   * Prepare the test data, run the migrations and assert they run without aborting.
   */
  @Test
  public void testV11_0_NoException() {
    createMultiReferencedTimeseries("V11 1");
    createSingleReferencedTimeseries("V11 2");
    createMultiReferencedTimeseriesOneContainer("V11 3");
    //    fail();
    runMigrations("V11");
    assertTrue(true);
  }

  /**
   * Assert that a timeseries migrated correctly if it is referenced by multiple references and these references lie within different containers.
   * In that case multiple timeseries, one for each container, should be created and each timeseries should reference its container.
   */
  @Test
  public void testV11_1_MultiReferencedTimeseriesMigrated() {
    assertMultiReferencedTimeseriesMigrated("V11 1");
  }

  /**
   * Assert that a timeseries migrated correctly if it is referenced by a single reference.
   * In this case the timeseries should get a reference towards the container of its reference.
   */
  @Test
  public void testV11_2_SingleReferencedTimeseriesMigrated() {
    assertSingleReferencedTimeseriesMigrated("V11 2");
  }

  /**
   * Assert that a timeseries migrated correctly if it is referenced by multiple references which however all lie within one container.
   * In this case the timeseries should get a reference towards this container.
   */
  @Test
  public void testV11_3_MultiReferencedTimeseriesOneContainerMigrated() {
    assertMultiReferencedTimeseriesOneContainerMigrated("V11 3");
  }

  /**
   * Assert that now each timeseries has a relationship to exactly one container.
   */
  @Test
  public void testV11_4_EachTimeseriesHasOneContainer() {
    var tsWithoutContainer = queryResults(
      "match(ts:Timeseries) where not exists((ts)-[]->(:TimeseriesContainer)) return ts"
    );
    assertEquals(0, tsWithoutContainer.size());
    var tsWithExactlyOneContainer = queryResults(
      "match(ts:Timeseries)-[r:is_in_container]->() with ts, count(r) as relcount where relcount = 1 return ts"
    );
    assertEquals(2, tsWithExactlyOneContainer.size());
    var tsWithSeveralContainers = queryResults(
      "match(ts:Timeseries)-[r:is_in_container]->() with ts, count(r) as relcount where relcount > 1 return ts"
    );
    assertEquals(0, tsWithSeveralContainers.size());
  }

  private static void createSingleReferencedTimeseries(String suffix) {
    var c = new GraphDataCreator(suffix);
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var annotation = c.annotation();
    var container = c.timeseriesContainer();
    create(
      ref1.relationshipTo(tsNode, "has_payload"),
      ref1.relationshipTo(container, "is_in_container"),
      ref1.relationshipTo(annotation, "has_annotation")
    );
  }

  private static void assertSingleReferencedTimeseriesMigrated(String suffix) {
    var c = new GraphDataCreator(suffix);
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var annotation = c.annotation();
    var container = c.timeseriesContainer();
    var result = Cypher.match(
      ref1.relationshipTo(tsNode, "has_payload"),
      ref1.relationshipTo(container, "is_in_container"),
      ref1.relationshipTo(annotation, "has_annotation"),
      tsNode.relationshipTo(container, "is_in_container")
    )
      .returning(tsNode)
      .build();
    var results = queryResults(result, Object.class);
    assertEquals(1, results.size());
  }

  private static void createMultiReferencedTimeseriesOneContainer(String suffix) {
    var c = new GraphDataCreator(suffix);
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var annotation = c.annotation();
    var container = c.timeseriesContainer().named("container");
    create(
      ref1.relationshipTo(tsNode, "has_payload"),
      ref2.relationshipTo(tsNode, "has_payload"),
      ref1.relationshipTo(container, "is_in_container"),
      ref2.relationshipTo(container, "is_in_container"),
      ref1.relationshipTo(annotation, "has_annotation")
    );
  }

  private static void assertMultiReferencedTimeseriesOneContainerMigrated(String suffix) {
    var c = new GraphDataCreator(suffix);
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var annotation = c.annotation();
    var container = c.timeseriesContainer();
    var result = Cypher.match(
      ref1.relationshipTo(tsNode, "has_payload"),
      ref1.relationshipTo(container, "is_in_container"),
      ref1.relationshipTo(annotation, "has_annotation"),
      ref2.relationshipTo(container, "is_in_container"),
      tsNode.relationshipTo(container, "is_in_container")
    )
      .returning(tsNode)
      .build();
    var results = queryResults(result, Object.class);
    assertEquals(1, results.size());
  }

  private static void createMultiReferencedTimeseries(String suffix) {
    var c = new GraphDataCreator(suffix);
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var annotation = c.annotation();
    var container1 = c.timeseriesContainer(1);
    var container2 = c.timeseriesContainer(2);
    create(
      ref1.relationshipTo(tsNode, "has_payload"),
      ref2.relationshipTo(tsNode, "has_payload"),
      ref1.relationshipTo(container1, "is_in_container"),
      ref2.relationshipTo(container2, "is_in_container"),
      ref1.relationshipTo(annotation, "has_annotation")
    );
  }

  private static void assertMultiReferencedTimeseriesMigrated(String suffix) {
    var c = new GraphDataCreator(suffix);
    var tsNode1 = c.timeseries().named("tsNode1");
    var tsNode2 = c.timeseries().named("tsNode2");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var annotation = c.annotation();
    var container1 = c.timeseriesContainer(1);
    var container2 = c.timeseriesContainer(2);
    var result = Cypher.match(
      ref1.relationshipTo(tsNode1, "has_payload"),
      ref2.relationshipTo(tsNode2, "has_payload"),
      ref1.relationshipTo(container1, "is_in_container"),
      ref2.relationshipTo(container2, "is_in_container"),
      ref1.relationshipTo(annotation, "has_annotation"),
      tsNode1.relationshipTo(container1, "is_in_container"),
      tsNode2.relationshipTo(container2, "is_in_container")
    )
      .returning(tsNode1, tsNode2)
      .build();
    var results = queryResults(result, Object.class);
    assertEquals(2, results.size());
  }

  private static Literal<String> literal(String of) {
    return Cypher.literalOf(of + "-" + randomElement);
  }

  @AllArgsConstructor
  private static class GraphDataCreator {

    private String suffix;

    private String getSuffix() {
      return " " + suffix;
    }

    private Node timeseries() {
      return node("Timeseries").withProperties(
        "measurement",
        literal("measurement" + getSuffix()),
        "device",
        literal("device" + getSuffix()),
        "location",
        literal("location" + getSuffix()),
        "symbolicName",
        literal("symbolicName" + getSuffix()),
        "field",
        literal("field" + getSuffix())
      );
    }

    private Node timeseriesReference(String name) {
      return node("TimeseriesReference", "VersionableEntity", "BasicReference", "BasicEntity").withProperties(
        "createdAt",
        Cypher.literalOf(100),
        "deleted",
        Cypher.literalOf(false),
        "end",
        Cypher.literalOf(2000),
        "name",
        literal(name + getSuffix()),
        "shepardId",
        Cypher.literalOf(5),
        "start",
        Cypher.literalOf(1000)
      );
    }

    private Node annotation() {
      return node("SemanticAnnotation").withProperties(
        "propertyName",
        literal("prop" + getSuffix()),
        "valueName",
        literal("value" + getSuffix())
      );
    }

    private Node timeseriesContainer(int index) {
      return timeseriesContainer("TimeseriesContainer " + index);
    }

    private Node timeseriesContainer() {
      return timeseriesContainer("TimeseriesContainer");
    }

    private Node timeseriesContainer(String name) {
      return node("TimeseriesContainer", "BasicEntity", "BasicContainer").withProperties(
        "createdAt",
        Cypher.literalOf(200),
        "deleted",
        Cypher.literalOf(false),
        "name",
        literal(name + getSuffix())
      );
    }
  }
}
