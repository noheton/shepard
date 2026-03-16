package de.dlr.shepard.migrations.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.cypherdsl.core.Cypher.*;

import com.opencsv.exceptions.CsvValidationException;
import de.dlr.shepard.common.neo4j.MigrationsRunner;
import de.dlr.shepard.context.collection.entities.Collection;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;

/**
 * Test the database migrations with a focus on the Neo4j migrations, utilizing the neo4j-migrations package.
 * This includes migrations which additionally rely on timescale data.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class TestNeo4jMigrations {

  private static final String randomElement = RandomStringUtils.insecure().next(6, true, true);
  private static final QueryHelper q = new QueryHelper();
  private static final TestV12 testV12 = new TestV12();

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

  @Test
  public void testV11() {
    var legacyReferencedTimeseries = node("Timeseries").withProperties("device", Cypher.literalOf("V11"));
    q.create(legacyReferencedTimeseries);
    runMigrations("V11");
    var migratedTimeseries = node("TimeseriesTuple");
    testNodeMigrated(legacyReferencedTimeseries, migratedTimeseries);
  }

  @Test
  public void testV12_0_NoException() throws CsvValidationException, IOException, ClassNotFoundException {
    testV12.setupPreMigrationData();
    testV12.runMigration();
  }

  @Test
  public void testV12_TimeseriesPresentInGraphDb() {
    testV12.assertTimeseriesPresentInGraphDb();
  }

  @Test
  public void testV12_MetadataDeletedInTimeseriesDb() throws SQLException, ClassNotFoundException {
    testV12.assertMetadataDeletedInTimeseriesDb();
  }

  @Test
  public void testV12_TimeseriesDatapointsIntact() throws SQLException, ClassNotFoundException {
    testV12.assertTimeseriesDatapointsIntact();
  }

  @Test
  public void testV12_NewTimeseriesMergedWithPreexisting() {
    testV12.assertNewTimeseriesMergedWithPreexisting();
  }
}
