package de.dlr.shepard.common.neo4j.migrations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * V2CONV-B5 — static-file validation for the V112 KRL-dissolution migration.
 *
 * <p>Does NOT spin up Neo4j — it loads the classpath migration resource + its
 * rollback twin and asserts the forward + reverse are well-formed and form a
 * lossless label rename ({@code :KrlInterpretActivity} ⇄
 * {@code :KrlTrajectoryActivity}). The integration assertion (apply against a
 * testcontainer Neo4j) is deferred to the CI IT layer; this unit-level companion
 * catches drift between the design and the migration file in CI configurations
 * where the testcontainer Neo4j is unavailable.
 */
class V112MigrationFileTest {

  private static final String MIGRATION_RESOURCE =
    "/neo4j/migrations/V112__B5_dissolve_krl.cypher";
  private static final String ROLLBACK_RESOURCE =
    "/neo4j/migrations/V112_R__B5_dissolve_krl.cypher";

  private static String readResource(String path) throws IOException {
    try (InputStream in = V112MigrationFileTest.class.getResourceAsStream(path)) {
      assertNotNull(in, "missing classpath resource: " + path);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void migrationFileExists() throws IOException {
    assertFalse(readResource(MIGRATION_RESOURCE).isBlank(), "V112 migration must not be empty");
  }

  @Test
  void rollbackFileExists() throws IOException {
    assertFalse(readResource(ROLLBACK_RESOURCE).isBlank(), "V112_R rollback must not be empty");
  }

  @Test
  void forwardRelabelsInterpretToTrajectory() throws IOException {
    String body = readResource(MIGRATION_RESOURCE);
    assertTrue(body.contains("MATCH (a:KrlInterpretActivity)"),
      "V112 must match the legacy :KrlInterpretActivity nodes");
    assertTrue(body.contains("SET a:KrlTrajectoryActivity"),
      "V112 must add the converged :KrlTrajectoryActivity label");
    assertTrue(body.contains("REMOVE a:KrlInterpretActivity"),
      "V112 must remove the legacy :KrlInterpretActivity label");
  }

  @Test
  void rollbackIsTheInverseRelabel() throws IOException {
    String body = readResource(ROLLBACK_RESOURCE);
    assertTrue(body.contains("MATCH (a:KrlTrajectoryActivity)"),
      "V112_R must match the converged :KrlTrajectoryActivity nodes");
    assertTrue(body.contains("SET a:KrlInterpretActivity"),
      "V112_R must restore the legacy :KrlInterpretActivity label");
    assertTrue(body.contains("REMOVE a:KrlTrajectoryActivity"),
      "V112_R must remove the converged :KrlTrajectoryActivity label");
  }

  @Test
  void forwardNeverDeletesActivityData() throws IOException {
    String body = readResource(MIGRATION_RESOURCE);
    // Referenced-data infinite-retention: the audit trail is a pure label
    // rename — no DELETE / DETACH DELETE Cypher statement on any Activity node.
    // Scan only executable lines (strip // comment lines, which legitimately
    // discuss "deletion" in prose).
    String executable = body.lines()
      .filter(line -> !line.stripLeading().startsWith("//"))
      .reduce("", (a, b) -> a + "\n" + b)
      .toUpperCase();
    assertFalse(executable.contains("DELETE"),
      "V112 must never DELETE Activity nodes — the audit trail is referenced data");
  }
}
