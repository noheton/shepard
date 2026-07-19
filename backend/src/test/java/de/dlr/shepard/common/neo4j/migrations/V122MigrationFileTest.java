package de.dlr.shepard.common.neo4j.migrations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * APPID-CHILD-MINT-REGRESSION — static-file validation for the V122 child-node
 * appId re-backfill migration and its rollback twin. Mirrors
 * {@link V120MigrationFileTest} (and the V119/V112 precedents).
 *
 * <p>Does NOT spin up Neo4j — it loads the classpath migration resource + its
 * rollback and asserts they are well-formed: the forward mints an appId on
 * {@code :Timeseries} and {@code :ShepardFile} nodes guarded by
 * {@code appId IS NULL} (idempotent, non-clobbering), batched via
 * {@code CALL { … } IN TRANSACTIONS} so the ~505k-row :ShepardFile population is
 * safe at startup (the multi-block-per-file shape is the proven V12/V78
 * precedent); the rollback removes the appId. The testcontainer pre/post
 * NULL-count integration assertion is deferred to the CI IT layer (tracked in
 * aidocs/16 / aidocs/34).
 */
class V122MigrationFileTest {

  private static final String MIGRATION_RESOURCE =
    "/neo4j/migrations/V122__backfill_child_appids.cypher";
  private static final String ROLLBACK_RESOURCE =
    "/neo4j/migrations/V122_R__backfill_child_appids.cypher";

  private static String readResource(String path) throws IOException {
    try (InputStream in = V122MigrationFileTest.class.getResourceAsStream(path)) {
      assertNotNull(in, "missing classpath resource: " + path);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void migrationFileExists() throws IOException {
    assertFalse(readResource(MIGRATION_RESOURCE).isBlank(), "V122 migration must not be empty");
  }

  @Test
  void rollbackFileExists() throws IOException {
    assertFalse(readResource(ROLLBACK_RESOURCE).isBlank(), "V122_R rollback must not be empty");
  }

  @Test
  void forwardMintsBothLabelsOnlyWhereNull() throws IOException {
    String body = readResource(MIGRATION_RESOURCE);
    assertTrue(body.contains("MATCH (t:Timeseries)"),
      "V122 must backfill :Timeseries nodes");
    assertTrue(body.contains("MATCH (f:ShepardFile)"),
      "V122 must backfill :ShepardFile nodes");
    assertTrue(body.contains("t.appId IS NULL") && body.contains("f.appId IS NULL"),
      "V122 must guard on appId IS NULL for both labels (idempotent, non-clobbering)");
    assertTrue(body.contains("SET t.appId = randomUUID()") && body.contains("SET f.appId = randomUUID()"),
      "V122 must mint appId on both labels");
    assertTrue(body.contains("IN TRANSACTIONS OF 1000 ROWS"),
      "V122 must batch the ~505k-row backfill via CALL {} IN TRANSACTIONS");
  }

  @Test
  void rollbackRemovesAppIdOnBothLabels() throws IOException {
    String body = readResource(ROLLBACK_RESOURCE);
    assertTrue(body.contains("REMOVE t.appId") && body.contains("REMOVE f.appId"),
      "V122_R must remove the appId from both labels");
    assertTrue(body.contains("t.appId IS NOT NULL") && body.contains("f.appId IS NOT NULL"),
      "V122_R must guard on appId IS NOT NULL (idempotent)");
  }
}
