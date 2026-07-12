package de.dlr.shepard.v2.admin.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.migration.FileMigrationService;
import de.dlr.shepard.v2.admin.storage.io.FileMigrationRollbackResultIO;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FS1e3 — unit tests for the per-file rollback endpoint
 * {@code POST /v2/admin/files/migrate/rollback/{appId}}.
 *
 * <p>Targets the REST adapter contract — input validation, exception
 * mapping (IllegalArgumentException → 404, IllegalStateException →
 * 409, StorageException → 500). The migrate / status endpoints from
 * FS1e1 are still exercised through {@link de.dlr.shepard.storage.migration.FileMigrationServiceTest};
 * this file adds the rollback-specific coverage.
 */
class FileMigrationRestTest {

  private FileMigrationService migrationService;
  private FileMigrationRest rest;

  @BeforeEach
  void setUp() {
    migrationService = mock(FileMigrationService.class);
    rest = new FileMigrationRest();
    rest.migrationService = migrationService;
  }

  @Test
  void rollbackRejectsBlankAppId() {
    assertEquals(400, rest.rollback("").getStatus());
    assertEquals(400, rest.rollback(null).getStatus());
    assertEquals(400, rest.rollback("   ").getStatus());
  }

  @Test
  void rollbackHappyPathReturns200WithStatus() throws Exception {
    // migrationService.rollbackOne(appId) — no exception → success
    Response r = rest.rollback("APPID-deadbeef");
    assertEquals(200, r.getStatus());
    var body = (FileMigrationRollbackResultIO) r.getEntity();
    assertEquals("APPID-deadbeef", body.appId());
    assertEquals("ROLLED_BACK", body.status());
    verify(migrationService).rollbackOne("APPID-deadbeef");
  }

  @Test
  void rollbackMapsUnknownAppIdTo404() throws Exception {
    doThrow(new IllegalArgumentException("no :ShepardFile with appId=missing"))
      .when(migrationService).rollbackOne("missing");

    assertEquals(404, rest.rollback("missing").getStatus());
  }

  @Test
  void rollbackMapsNothingToRevertTo409Conflict() throws Exception {
    doThrow(new IllegalStateException("previousProviderId is null"))
      .when(migrationService).rollbackOne("never-migrated");

    assertEquals(409, rest.rollback("never-migrated").getStatus());
  }

  @Test
  void rollbackMapsStorageExceptionTo500() throws Exception {
    doThrow(new StorageException("S3 timeout"))
      .when(migrationService).rollbackOne("APPID-storage-fail");

    assertEquals(500, rest.rollback("APPID-storage-fail").getStatus());
  }

  @Test
  void rollbackDoesNotCallServiceWhenAppIdBlank() throws Exception {
    assertEquals(400, rest.rollback("").getStatus());
    verify(migrationService, never()).rollbackOne(anyString());
  }

  // small helper — avoids polluting the static-import zone with the
  // mockito matcher when only one call needs it
  private static String anyString() {
    return org.mockito.ArgumentMatchers.anyString();
  }
}
