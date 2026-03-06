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
  private static final SampleNodeCreatorFactory sample = new SampleNodeCreatorFactory(randomElement);

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

  private static List<Object> queryResults(Statement statement) {
    var cypherQuery = cypherRenderer.render(statement);
    return queryResults(cypherQuery, Object.class);
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
      Cypher.literalOf("prop"),
      "valueName",
      Cypher.literalOf("value")
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
    createEmptyReference();
    runMigrations("V11");
    assertTrue(true);
  }

  /**
   * Assert that now each timeseries has a relationship to exactly one container.
   */
  @Test
  public void testV11_EachTimeseriesHasOneContainer() {
    var tsWithoutContainer = queryResults(
      "match(ts:Timeseries) where not exists((ts)-[]->(:TimeseriesContainer)) return ts"
    );
    assertEquals(0, tsWithoutContainer.size());
    var tsWithExactlyOneContainer = queryResults(
      "match(ts:Timeseries)-[r:is_in_container]->() with ts, count(r) as relcount where relcount = 1 return ts"
    );
    var numTs = queryResults("match(ts:Timeseries) with count(ts) as c return c", Long.class).get(0);
    assertEquals(numTs, tsWithExactlyOneContainer.size());
    var tsWithSeveralContainers = queryResults(
      "match(ts:Timeseries)-[r:is_in_container]->() with ts, count(r) as relcount where relcount > 1 return ts"
    );
    assertEquals(0, tsWithSeveralContainers.size());
  }

  @Test
  public void testV11_EachReferenceHasTimeseriesExceptEmptyRef() {
    assertEquals(
      1,
      queryResults(
        "match(tsr:TimeseriesReference) where not exists " +
        "{ match(tsr)-[:has_payload]->(:Timeseries) } " +
        "return count(tsr)",
        Long.class
      ).get(0)
    );
  }

  private static void createSingleReferencedTimeseries() {
    var c = sample.instance("SingleReferencedTimeseries");
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

  private static void create2References1Timeseries1Container() {
    var c = sample.instance("References1Timeseries1Container");
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

  private static void create2References1Timeseries2Containers() {
    var c = sample.instance("2References1Timeseries2Containers");
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

  private static void create3References2Timeseries2Containers() {
    var c = sample.instance("3References2Timeseries2Containers");
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var ref3 = c.timeseriesReference("ref3").named("ref3");
    var container1 = c.timeseriesContainer(1).named("c1");
    var container2 = c.timeseriesContainer(2).named("c2");
    create(
      ref1.relationshipTo(tsNode, "has_payload"),
      ref2.relationshipTo(tsNode, "has_payload"),
      ref3.relationshipTo(tsNode, "has_payload"),
      ref1.relationshipTo(container1, "is_in_container"),
      ref2.relationshipTo(container2, "is_in_container"),
      ref3.relationshipTo(container2, "is_in_container")
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
      ref1.relationshipTo(tsNode1, "has_payload"),
      ref1.relationshipTo(container1, "is_in_container"),
      tsNode1.relationshipTo(container1, "is_in_container")
    )
      .returning(tsNode1)
      .build();
    assertEquals(1, queryResults(queryTs1).size());
    var queryTs2 = Cypher.match(
      ref2.relationshipTo(tsNode2, "has_payload"),
      ref3.relationshipTo(tsNode2, "has_payload"),
      ref2.relationshipTo(container2, "is_in_container"),
      ref3.relationshipTo(container2, "is_in_container"),
      tsNode2.relationshipTo(container2, "is_in_container")
    )
      .returning(tsNode2)
      .build();
    assertEquals(1, queryResults(queryTs2).size());
  }

  private static void create3References1Container() {
    var c = sample.instance("3References1Container");
    var tsNode = c.timeseries().named("tsNode");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var ref3 = c.timeseriesReference("ref3").named("ref3");
    var container = c.timeseriesContainer().named("c1");
    create(
      ref1.relationshipTo(tsNode, "has_payload"),
      ref2.relationshipTo(tsNode, "has_payload"),
      ref3.relationshipTo(tsNode, "has_payload"),
      ref1.relationshipTo(container, "is_in_container"),
      ref2.relationshipTo(container, "is_in_container"),
      ref3.relationshipTo(container, "is_in_container")
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
      ref1.relationshipTo(tsNode, "has_payload"),
      ref2.relationshipTo(tsNode, "has_payload"),
      ref3.relationshipTo(tsNode, "has_payload"),
      ref1.relationshipTo(container, "is_in_container"),
      ref2.relationshipTo(container, "is_in_container"),
      ref3.relationshipTo(container, "is_in_container"),
      tsNode.relationshipTo(container, "is_in_container")
    )
      .returning(tsNode)
      .build();
    assertEquals(1, queryResults(query).size());
  }

  private static void create2ReferencesOneContainer2Timeseries() {
    var c = sample.instance("2ReferencesOneContainer2Timeseries");
    var tsNode1 = c.timeseries("ts1").named("tsNode1");
    var tsNode2 = c.timeseries("ts2").named("tsNode2");
    var ref1 = c.timeseriesReference("ref1").named("ref1");
    var ref2 = c.timeseriesReference("ref2").named("ref2");
    var container = c.timeseriesContainer().named("c1");
    create(
      ref1.relationshipTo(tsNode1, "has_payload"),
      ref2.relationshipTo(tsNode2, "has_payload"),
      ref1.relationshipTo(container, "is_in_container"),
      ref2.relationshipTo(container, "is_in_container")
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
      ref1.relationshipTo(tsNode1, "has_payload"),
      ref2.relationshipTo(tsNode2, "has_payload"),
      ref1.relationshipTo(container, "is_in_container"),
      ref2.relationshipTo(container, "is_in_container")
    )
      .returning(tsNode1, tsNode2)
      .build();
    assertEquals(2, queryResults(query).size());
  }

  private static void createEmptyReference() {
    var c = sample.instance("EmptyReference");
    var ref = c.timeseriesReference();
    var container = c.timeseriesContainer();
    create(ref.relationshipTo(container, "is_in_container"));
  }

  @Test
  public void testV11_EmptyReferenceUntouched() {
    var c = sample.instance("EmptyReference");
    var ref = c.timeseriesReference();
    var container = c.timeseriesContainer();
    var query = Cypher.match(ref.relationshipTo(container, "is_in_container")).returning(ref).build();
    assertEquals(1, queryResults(query).size());
  }
}
