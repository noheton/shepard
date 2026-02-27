package de.dlr.shepard.migrations.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.cypherdsl.core.Cypher.*;

import de.dlr.shepard.common.neo4j.MigrationsRunner;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.context.collection.entities.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.apache.commons.lang3.RandomStringUtils;
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
      Cypher.literalOf("prop-" + randomElement),
      "valueName",
      Cypher.literalOf("value-" + randomElement)
    );

    testNodeMigrated(legacyAnnotation, migratedAnnotation);
  }

  /**
   * Assert that all referenced timeseries migrated correctly towards individual timeseries.
   */
  @Test
  public void testV11_1_ReferencedTimeseriesMigrated() {
    createReferencedTimeseries();
    runMigrations("V11");
    assertReferencedTimeseriesMigrated();
  }

  /**
   * Assert that now each timeseries has a relationship to exactly one container.
   */
  @Test
  public void testV11_2_EachTimeseriesHasOneContainer() {
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

  private static void assertReferencedTimeseriesMigrated() {
    var tsNode1 = node("Timeseries")
      .withProperties(
        "measurement",
        Cypher.literalOf("measurement-" + randomElement),
        "device",
        Cypher.literalOf("device-" + randomElement),
        "location",
        Cypher.literalOf("location-" + randomElement),
        "symbolicName",
        Cypher.literalOf("symbolicName-" + randomElement),
        "field",
        Cypher.literalOf("field-" + randomElement)
      )
      .named("tsNode");
    var tsNode2 = node("Timeseries")
      .withProperties(
        "measurement",
        Cypher.literalOf("measurement-" + randomElement),
        "device",
        Cypher.literalOf("device-" + randomElement),
        "location",
        Cypher.literalOf("location-" + randomElement),
        "symbolicName",
        Cypher.literalOf("symbolicName-" + randomElement),
        "field",
        Cypher.literalOf("field-" + randomElement)
      )
      .named("tsNode2");
    var ref1 = node("TimeseriesReference", "VersionableEntity", "BasicReference", "BasicEntity")
      .withProperties(
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
      )
      .named("ref1");
    var ref2 = node("TimeseriesReference", "VersionableEntity", "BasicReference", "BasicEntity")
      .withProperties(
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
      )
      .named("ref2");
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
    var result = Cypher.match(
      ref1.relationshipTo(tsNode1, "has_payload"),
      ref2.relationshipTo(tsNode2, "has_payload"),
      ref1.relationshipTo(container1, "is_in_container"),
      ref2.relationshipTo(container2, "is_in_container"),
      ref1.relationshipTo(annotation, "has_annotation"),
      tsNode1.relationshipTo(container1, "is_in_container"),
      tsNode2.relationshipTo(container2, "is_in_container")
    )
      .returning(Cypher.asterisk())
      .build();
    var results = queryResults(result, Object.class);
    assertEquals(1, results.size());
  }

  private static void createReferencedTimeseries() {
    var tsNode = node("Timeseries")
      .withProperties(
        "measurement",
        Cypher.literalOf("measurement-" + randomElement),
        "device",
        Cypher.literalOf("device-" + randomElement),
        "location",
        Cypher.literalOf("location-" + randomElement),
        "symbolicName",
        Cypher.literalOf("symbolicName-" + randomElement),
        "field",
        Cypher.literalOf("field-" + randomElement)
      )
      .named("tsNode");
    var ref1 = node("TimeseriesReference", "VersionableEntity", "BasicReference", "BasicEntity")
      .withProperties(
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
      )
      .named("ref1");
    var ref2 = node("TimeseriesReference", "VersionableEntity", "BasicReference", "BasicEntity")
      .withProperties(
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
      )
      .named("ref2");
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
    create(
      ref1.relationshipTo(tsNode, "has_payload"),
      ref2.relationshipTo(tsNode, "has_payload"),
      ref1.relationshipTo(container1, "is_in_container"),
      ref2.relationshipTo(container2, "is_in_container"),
      ref1.relationshipTo(annotation, "has_annotation")
    );
  }
}
