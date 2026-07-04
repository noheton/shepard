package de.dlr.shepard.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * STORAGE-NO-MAGIC-ROUTES-1 — prevents direct GridFS byte access from
 * growing outside the sanctioned storage adapter zone.
 *
 * <p>After STORAGE-SPI-UNIFY-1 (2026-06-24) all binary content routes
 * through {@link de.dlr.shepard.storage.FileStorage} via
 * {@link de.dlr.shepard.storage.FileStorageService}. This test is the
 * compile-time fence that keeps it that way: a new class that skips the
 * SPI and reaches {@code com.mongodb.client.gridfs.*} directly will fail
 * the build immediately.
 *
 * <p>Sanctioned zones (allowed to use {@code com.mongodb.client.gridfs}):
 * <ol>
 *   <li><b>{@code de.dlr.shepard.storage.gridfs..*}</b> — the GridFS
 *       {@link de.dlr.shepard.storage.FileStorage} adapter (FS1a). This
 *       is the one place gridfs is permitted to live.</li>
 *   <li><b>{@code de.dlr.shepard.data.file.services..*}</b> — the legacy
 *       {@code FileService} that the GridFS adapter wraps. It predates
 *       the SPI and is grandfathered; the adapter delegates into it rather
 *       than duplicating its GridFS logic.</li>
 *   <li><b>{@code de.dlr.shepard.common.neo4j.migrations..*}</b> —
 *       Flyway-style migrations that need direct MongoDB access to migrate
 *       byte blobs between storage shapes (e.g. V23 singleton-bundle
 *       split). Migrations run once at startup, before the SPI is up.</li>
 * </ol>
 *
 * <p>Everything else — v2 REST resources, services, DAOs, plugins (which
 * live in separate Maven modules and are not in scope of this test) — must
 * route file bytes through {@code FileStorageService.storeFile()},
 * {@code .getPayload()}, or {@code .deleteFile()}.
 */
class StorageAdapterIsolationTest {

  private static final String GRIDFS_ADAPTER_PACKAGE = "de.dlr.shepard.storage.gridfs";
  private static final String LEGACY_FILE_SERVICE_PACKAGE = "de.dlr.shepard.data.file.services";
  private static final String MIGRATIONS_PACKAGE = "de.dlr.shepard.common.neo4j.migrations";

  private static JavaClasses shepardClasses;

  @BeforeAll
  static void importClasses() {
    shepardClasses =
      new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("de.dlr.shepard");
  }

  /**
   * No class outside the three sanctioned zones may depend on
   * {@code com.mongodb.client.gridfs.*}.
   *
   * <p>A violation here means a new piece of code is bypassing the
   * {@link de.dlr.shepard.storage.FileStorage} SPI and writing/reading
   * bytes directly against GridFS — the failure mode STORAGE-NO-MAGIC-ROUTES-1
   * was designed to prevent.
   *
   * <p>{@code allowEmptyShould(true)} keeps the rule green when no violating
   * classes exist (the desired steady state after STORAGE-SPI-UNIFY-1).
   */
  @Test
  void gridFsByteAccessMustStayInsideAdapterZone() {
    ArchRuleDefinition.noClasses()
      .that()
      .resideOutsideOfPackage(GRIDFS_ADAPTER_PACKAGE + "..")
      .and()
      .resideOutsideOfPackage(LEGACY_FILE_SERVICE_PACKAGE + "..")
      .and()
      .resideOutsideOfPackage(MIGRATIONS_PACKAGE + "..")
      .should()
      .dependOnClassesThat()
      .resideInAPackage("com.mongodb.client.gridfs..")
      .allowEmptyShould(true)
      .check(shepardClasses);
  }
}
