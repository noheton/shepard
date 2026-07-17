package de.dlr.shepard.common.neo4j.migrations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * TIFF-PREVIEW-SUPPORT — static-file validation for the V120 image-fileKind
 * backfill migration and its rollback twin. Mirrors {@link V119MigrationFileTest}
 * (the MP4-PROMOTE-VIDEO precedent) for the {@code "image"} kind.
 *
 * <p>Does NOT spin up Neo4j — it loads the classpath migration resource + its
 * rollback and asserts they are well-formed: the forward sets
 * {@code fileKind = 'image'} on :SingletonFileReference nodes guarded by
 * {@code fileKind IS NULL} (idempotent, non-clobbering) with a case-insensitive
 * extension test ({@code toLower}), including the TIFF extensions that
 * motivated this feature; the rollback removes the tag. The testcontainer
 * integration assertion is deferred to the CI IT layer (tracked in aidocs/34).
 */
class V120MigrationFileTest {

  private static final String MIGRATION_RESOURCE =
    "/neo4j/migrations/V120__promote_image_filekind.cypher";
  private static final String ROLLBACK_RESOURCE =
    "/neo4j/migrations/V120_R__promote_image_filekind.cypher";

  private static String readResource(String path) throws IOException {
    try (InputStream in = V120MigrationFileTest.class.getResourceAsStream(path)) {
      assertNotNull(in, "missing classpath resource: " + path);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void migrationFileExists() throws IOException {
    assertFalse(readResource(MIGRATION_RESOURCE).isBlank(), "V120 migration must not be empty");
  }

  @Test
  void rollbackFileExists() throws IOException {
    assertFalse(readResource(ROLLBACK_RESOURCE).isBlank(), "V120_R rollback must not be empty");
  }

  @Test
  void forwardTagsImageOnlyWhereNull() throws IOException {
    String body = readResource(MIGRATION_RESOURCE);
    assertTrue(body.contains("MATCH (r:SingletonFileReference)"),
      "V120 must match :SingletonFileReference nodes");
    assertTrue(body.contains("r.fileKind IS NULL"),
      "V120 must guard on fileKind IS NULL (idempotent, non-clobbering)");
    assertTrue(body.contains("SET r.fileKind = 'image'"),
      "V120 must set fileKind = 'image'");
    assertTrue(body.contains("toLower(r.name)"),
      "V120 must lower-case the name so uppercase .TIFF is caught");
    assertTrue(body.contains("'.tif'") && body.contains("'.tiff'"),
      "V120 must claim both TIFF spellings");
    assertTrue(body.contains("'.png'") && body.contains("'.webp'"),
      "V120 must claim the other common raster-image extensions");
  }

  @Test
  void rollbackRemovesImageTag() throws IOException {
    String body = readResource(ROLLBACK_RESOURCE);
    assertTrue(body.contains("REMOVE r.fileKind"),
      "V120_R must remove the fileKind tag");
    assertTrue(body.contains("r.fileKind = 'image'"),
      "V120_R must scope the removal to image-tagged references");
  }
}
