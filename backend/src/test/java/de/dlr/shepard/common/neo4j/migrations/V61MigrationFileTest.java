package de.dlr.shepard.common.neo4j.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Static-file validation for the V61 v15-prov-predicates Cypher migration.
 *
 * <p>This test does NOT spin up Neo4j — it loads the classpath migration
 * resource and asserts it is well-formed and carries the 10 expected
 * IRIs called out in {@code aidocs/agent-findings/data-ontologist-prov-o-v15.md
 * §"New shepard: predicates proposed"}.
 *
 * <p>Pairs with {@code de.dlr.shepard.migrations.neo4j.TestNeo4jMigrations#testV61_v15ProvPredicates}
 * (the Neo4j-bound integration assertion). This unit-level companion
 * catches drift between the design doc and the migration file in CI
 * configurations where the testcontainer Neo4j is unavailable.
 */
class V61MigrationFileTest {

  private static final String MIGRATION_RESOURCE =
    "/neo4j/migrations/V61__v15_prov_predicates.cypher";
  private static final String ROLLBACK_RESOURCE =
    "/neo4j/migrations/V61_R__rollback.cypher";

  /** 8 predicates + 2 role individuals = 10 IRIs the migration must touch. */
  private static final List<String> EXPECTED_IRIS = List.of(
    "http://semantics.dlr.de/shepard-upper#targetCollection",
    "http://semantics.dlr.de/shepard-upper#filesUploaded",
    "http://semantics.dlr.de/shepard-upper#timeseriesImported",
    "http://semantics.dlr.de/shepard-upper#structuredPayloads",
    "http://semantics.dlr.de/shepard-upper#batchSequence",
    "http://semantics.dlr.de/shepard-upper#throughputBytesPerSec",
    "http://semantics.dlr.de/shepard-upper#retryCount",
    "http://semantics.dlr.de/shepard-upper#sourceInstance",
    "http://semantics.dlr.de/shepard-upper#role-executor",
    "http://semantics.dlr.de/shepard-upper#role-operator"
  );

  private static String readResource(String path) throws IOException {
    try (InputStream in = V61MigrationFileTest.class.getResourceAsStream(path)) {
      assertNotNull(in, "missing classpath resource: " + path);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void migrationFileExists() throws IOException {
    String body = readResource(MIGRATION_RESOURCE);
    assertFalse(body.isBlank(), "V61 migration must not be empty");
  }

  @Test
  void rollbackFileExists() throws IOException {
    String body = readResource(ROLLBACK_RESOURCE);
    assertFalse(body.isBlank(), "V61_R rollback must not be empty");
  }

  @Test
  void migrationContainsAllExpectedIris() throws IOException {
    String body = readResource(MIGRATION_RESOURCE);
    for (String iri : EXPECTED_IRIS) {
      assertTrue(body.contains(iri),
        "V61 migration must MERGE the IRI " + iri + " — design doc drift?");
    }
  }

  @Test
  void migrationTagsEveryNodeWithProvenance() throws IOException {
    String body = readResource(MIGRATION_RESOURCE);
    // The rollback identifies V61's nodes by this exact tag — drift = orphans.
    int occurrences = countOccurrences(body, "'V61__v15_prov_predicates'");
    // 10 ON CREATE SET blocks reference the tag, plus one filter clause in
    // the smoke probe at the end = at least 11 occurrences. The rollback's
    // contract is "removes the exact set V61 created", so any drift here
    // means the rollback is unsafe.
    assertTrue(occurrences >= 11,
      "Every MERGE in V61 must carry shepard__addedBy = 'V61__v15_prov_predicates' (found "
        + occurrences + " uses)");
  }

  @Test
  void migrationIsIdempotent() throws IOException {
    String body = readResource(MIGRATION_RESOURCE);
    // Every IRI must be MERGE'd, never CREATE'd (otherwise re-run violates
    // the constraint AND duplicates the node). Count MERGE vs CREATE for the
    // (r:Resource clauses.
    int mergeResource = countOccurrences(body, "MERGE (");
    int createResource = countOccurrencesIgnoringCase(body, "CREATE (r:Resource")
      + countOccurrencesIgnoringCase(body, "CREATE (p:Resource");
    assertEquals(0, createResource,
      "V61 must use MERGE not CREATE for :Resource nodes — re-running fails otherwise");
    assertTrue(mergeResource >= 10,
      "V61 must MERGE at least 10 :Resource nodes (got " + mergeResource + ")");
  }

  @Test
  void migrationLabelsPredicatesAndRolesDistinctly() throws IOException {
    String body = readResource(MIGRATION_RESOURCE);
    // Predicates get :Property; role individuals get :NamedIndividual.
    // This labelling is what downstream SPARQL queries filter on.
    assertTrue(body.contains("p:Property"),
      "V61 must label predicate Resources with :Property");
    assertTrue(body.contains("r:NamedIndividual"),
      "V61 must label role-individual Resources with :NamedIndividual");
  }

  @Test
  void migrationCarriesEnglishLabels() throws IOException {
    String body = readResource(MIGRATION_RESOURCE);
    // The n10s `rdfs__label@en` property is what InternalSemanticConnector.getTerm
    // surfaces back to SemanticAnnotation IO. Without it the annotation picker
    // shows a bare IRI.
    int englishLabels = countOccurrences(body, "`rdfs__label@en`");
    assertTrue(englishLabels >= 10,
      "V61 must set rdfs__label@en on every Resource (found " + englishLabels + " uses)");
  }

  @Test
  void rollbackTargetsOnlyV61Nodes() throws IOException {
    String body = readResource(ROLLBACK_RESOURCE);
    // The rollback must NOT do a blanket "DELETE all Resources" — it must
    // scope by the provenance tag so other n10s-managed Resources survive.
    assertTrue(body.contains("shepard__addedBy = 'V61__v15_prov_predicates'"),
      "V61 rollback must scope deletes by shepard__addedBy tag");
    assertTrue(body.contains("DETACH DELETE"),
      "V61 rollback must use DETACH DELETE (Resources may have inbound rels via n10s)");
  }

  // ---------------------------------------------------------------

  private static int countOccurrences(String haystack, String needle) {
    int count = 0, idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) >= 0) {
      count++;
      idx += needle.length();
    }
    return count;
  }

  private static int countOccurrencesIgnoringCase(String haystack, String needle) {
    return countOccurrences(haystack.toLowerCase(), needle.toLowerCase());
  }
}
