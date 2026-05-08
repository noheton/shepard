package de.dlr.shepard.migrations.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.cypherdsl.core.Cypher.*;

import de.dlr.shepard.common.neo4j.MigrationsRunner;
import de.dlr.shepard.common.neo4j.NeoConnector;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.apache.commons.lang3.RandomStringUtils;
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
    var statement = Cypher.match(node).returning(node).build();
    var result = query(statement);
    return StreamSupport.stream(result.spliterator(), false).map(Map::values).flatMap(Collection::stream).toList();
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
   * V12 backfills `appId` on every pre-L2a node across the 28 labels listed in
   * V11__Add_appId_unique_constraints.cypher. The exit criterion in
   * aidocs/25 §4 Phase 2 is that every targeted label has zero rows with
   * `appId IS NULL` after the migration runs, and that no two backfilled
   * rows collide (the V11 unique constraint guards the latter, but a
   * positive count assertion documents the contract).
   *
   * <p>This test seeds one row per in-scope label with `appId = null` (mimicking
   * pre-L2a state) — created via {@link Cypher#create} so the
   * `GenericDAO#createOrUpdate` write seam never fires. The seeded nodes get
   * a unique marker property so the assertions only count the seeded rows
   * and not unrelated state from earlier tests in the suite.
   */
  @Test
  public void testV12() {
    // The 28 in-scope labels — same list and order as V11__Add_appId_unique_constraints.cypher.
    // Re-derived from V11 (NOT from the Java sources) per aidocs/25 §4 Phase 2.
    String[] labels = {
      // auth
      "User",
      "UserGroup",
      "ApiKey",
      "Permissions",
      // subscriptions
      "Subscription",
      // data containers and payloads
      "FileContainer",
      "ShepardFile",
      "StructuredDataContainer",
      "StructuredData",
      "SpatialDataContainer",
      "TimeseriesContainer",
      "Timeseries",
      // references
      "BasicReference",
      "FileReference",
      "URIReference",
      "StructuredDataReference",
      "SpatialDataReference",
      "TimeseriesReference",
      "CollectionReference",
      "DataObjectReference",
      // collections / data objects / lab journal
      "Collection",
      "DataObject",
      "LabJournalEntry",
      // semantic
      "SemanticRepository",
      "SemanticAnnotation",
      "AnnotatableTimeseries",
      // versioning
      "VersionableEntity",
      "Version",
    };

    // Make sure V11 (the unique-appId constraint) is in place before we seed,
    // so any seeded row that already had a non-null appId would fail loudly.
    runMigrations("V11");

    // Use a per-test marker so that the assertions ignore unrelated nodes left
    // behind by prior tests in this suite (the migration test runs against a
    // shared neo4j instance).
    String marker = "L2b-test-" + randomElement;

    // Seed: create one node per label with appId deliberately NULL.
    // `Cypher.create` bypasses GenericDAO so the L2a write-seam doesn't fire;
    // we additionally REMOVE n.appId after creation to defend against any
    // future seam (e.g. an OGM hook) that mints on construction.
    for (String label : labels) {
      session.query(
        "CREATE (n:`" + label + "` {testMarker: $marker}) REMOVE n.appId",
        Map.of("marker", marker)
      );
    }

    // Pre-condition: every seeded node has a NULL appId.
    var preCount = (Number) session
      .query(
        "MATCH (n) WHERE n.testMarker = $marker AND n.appId IS NULL RETURN count(n) AS c",
        Map.of("marker", marker)
      )
      .iterator()
      .next()
      .get("c");
    assertEquals(labels.length, preCount.intValue(), "every seeded node should start with a NULL appId");

    // Apply V12 — the backfill.
    runMigrations("V12");

    // Exit criterion 1: zero seeded nodes have a NULL appId post-migration.
    var nullCount = (Number) session
      .query(
        "MATCH (n) WHERE n.testMarker = $marker AND n.appId IS NULL RETURN count(n) AS c",
        Map.of("marker", marker)
      )
      .iterator()
      .next()
      .get("c");
    assertEquals(0, nullCount.intValue(), "V12 must populate appId on every seeded node");

    // Per-label exit criterion: every label in scope has its row backfilled.
    Set<String> seenLabels = new HashSet<>();
    var perLabelResult = session.query(
      "MATCH (n) WHERE n.testMarker = $marker AND n.appId IS NOT NULL " +
      "RETURN labels(n) AS labels, n.appId AS appId",
      Map.of("marker", marker)
    );
    Set<String> appIds = new HashSet<>();
    for (var row : perLabelResult) {
      @SuppressWarnings("unchecked")
      var rowLabels = (List<String>) row.get("labels");
      var appId = (String) row.get("appId");
      assertNotNull(appId, "every seeded row should now have a non-null appId");
      // 36-char canonical UUID form (Cypher randomUUID() emits v4).
      assertEquals(36, appId.length(), "appId should be a canonical 36-char UUID");
      appIds.add(appId);
      seenLabels.addAll(rowLabels);
    }

    for (String label : labels) {
      assertTrue(seenLabels.contains(label), "label " + label + " should have been backfilled");
    }

    // Exit criterion 2: no two seeded rows share an appId. The V11 unique
    // constraint enforces this anyway (a collision would have aborted the
    // chunk and propagated as MigrationsException), but a positive
    // assertion documents the contract from aidocs/25 §4 Phase 2.
    assertEquals(labels.length, appIds.size(), "every backfilled appId must be unique");

    // Idempotency: re-running V12 must leave the graph unchanged.
    runMigrations("V12");
    var nullCountAfterRerun = (Number) session
      .query(
        "MATCH (n) WHERE n.testMarker = $marker AND n.appId IS NULL RETURN count(n) AS c",
        Map.of("marker", marker)
      )
      .iterator()
      .next()
      .get("c");
    assertEquals(0, nullCountAfterRerun.intValue(), "V12 must be idempotent — re-run should not introduce NULL appIds");
  }
}
