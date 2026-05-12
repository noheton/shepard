package de.dlr.shepard.data.timeseries.migration.endpoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.migration.io.MigrationProgressIO;
import de.dlr.shepard.data.timeseries.migration.model.MigrationProgress;
import de.dlr.shepard.data.timeseries.migration.services.MigrationProgressService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * P3c regression: ensure {@link MigrationProgressRest} stays gated on
 * {@code @RolesAllowed("instance-admin")}. The endpoint used to be
 * reachable by any authenticated user via the
 * {@code PermissionsService.isAllowed} "temp/migrations" always-true
 * carve-out — locking it down is the security fix.
 */
class MigrationProgressRestTest {

  @Mock
  MigrationProgressService migrationProgressService;

  MigrationProgressRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new MigrationProgressRest();
    resource.migrationProgressService = migrationProgressService;
  }

  @Test
  void classCarriesInstanceAdminGate() {
    // Annotation-presence regression: if someone strips @RolesAllowed
    // from MigrationProgressRest in a future refactor, this fence fires.
    RolesAllowed gate = MigrationProgressRest.class.getAnnotation(RolesAllowed.class);
    assertNotNull(gate, "MigrationProgressRest must be @RolesAllowed-gated");
    assertEquals(1, gate.value().length);
    assertEquals("instance-admin", gate.value()[0]);
  }

  @Test
  void getAllReturns200WithList() {
    when(migrationProgressService.listAll()).thenReturn(List.of(new MigrationProgress(), new MigrationProgress()));
    var r = resource.getAll();
    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<MigrationProgressIO> rows = (List<MigrationProgressIO>) r.getEntity();
    assertEquals(2, rows.size());
  }

  @Test
  void getByContainerReturns200WhenFound() {
    when(migrationProgressService.getProgress(7L)).thenReturn(Optional.of(new MigrationProgress()));
    var r = resource.getByContainer(7L);
    assertEquals(200, r.getStatus());
    assertTrue(r.getEntity() instanceof MigrationProgressIO);
  }

  @Test
  void getByContainerThrows404WhenMissing() {
    when(migrationProgressService.getProgress(9999L)).thenReturn(Optional.empty());
    org.junit.jupiter.api.Assertions.assertThrows(NotFoundException.class, () -> resource.getByContainer(9999L));
  }
}
