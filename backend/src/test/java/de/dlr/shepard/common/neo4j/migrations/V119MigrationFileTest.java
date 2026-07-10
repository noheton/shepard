package de.dlr.shepard.common.neo4j.migrations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * MP4-PROMOTE-VIDEO — static-file validation for the V119 video-fileKind
 * backfill migration and its rollback twin.
 *
 * <p>Does NOT spin up Neo4j — it loads the classpath migration resource + its
 * rollback and asserts they are well-formed: the forward sets
 * {@code fileKind = 'video'} on :SingletonFileReference nodes guarded by
 * {@code fileKind IS NULL} (idempotent, non-clobbering) with a case-insensitive
 * extension test ({@code toLower}); the rollback removes the tag. Mirrors the
 * V112 static-companion pattern; the testcontainer integration assertion is
 * deferred to the CI IT layer (tracked in aidocs/34).
 */
class V119MigrationFileTest {

  private static final String MIGRATION_RESOURCE =
    "/neo4j/migrations/V119__promote_video_filekind.cypher";
  private static final String ROLLBACK_RESOURCE =
    "/neo4j/migrations/V119_R__promote_video_filekind.cypher";

  private static String readResource(String path) throws IOException {
    try (InputStream in = V119MigrationFileTest.class.getResourceAsStream(path)) {
      assertNotNull(in, "missing classpath resource: " + path);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void migrationFileExists() throws IOException {
    assertFalse(readResource(MIGRATION_RESOURCE).isBlank(), "V119 migration must not be empty");
  }

  @Test
  void rollbackFileExists() throws IOException {
    assertFalse(readResource(ROLLBACK_RESOURCE).isBlank(), "V119_R rollback must not be empty");
  }

  @Test
  void forwardTagsVideoOnlyWhereNull() throws IOException {
    String body = readResource(MIGRATION_RESOURCE);
    assertTrue(body.contains("MATCH (r:SingletonFileReference)"),
      "V119 must match :SingletonFileReference nodes");
    assertTrue(body.contains("r.fileKind IS NULL"),
      "V119 must guard on fileKind IS NULL (idempotent, non-clobbering)");
    assertTrue(body.contains("SET r.fileKind = 'video'"),
      "V119 must set fileKind = 'video'");
    assertTrue(body.contains("toLower(r.name)"),
      "V119 must lower-case the name so uppercase .MP4 is caught");
    assertTrue(body.contains("'.mp4'") && body.contains("'.webm'"),
      "V119 must claim the common video extensions");
  }

  @Test
  void rollbackRemovesVideoTag() throws IOException {
    String body = readResource(ROLLBACK_RESOURCE);
    assertTrue(body.contains("REMOVE r.fileKind"),
      "V119_R must remove the fileKind tag");
    assertTrue(body.contains("r.fileKind = 'video'"),
      "V119_R must scope the removal to video-tagged references");
  }
}
