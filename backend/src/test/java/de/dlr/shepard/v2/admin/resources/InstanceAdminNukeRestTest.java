package de.dlr.shepard.v2.admin.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.common.exceptions.InvalidAuthException;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.v2.admin.io.NukeRequestIO;
import de.dlr.shepard.v2.admin.io.NukeResultIO;
import de.dlr.shepard.v2.admin.services.InstanceAdminService;
import de.dlr.shepard.v2.admin.services.NukeService;
import de.dlr.shepard.v2.admin.services.PermissionAuditLogQueryService;
import de.dlr.shepard.v2.admin.services.PermissionAuditService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * REST-layer tests for {@code POST /v2/admin/instance/nuke}.
 *
 * <p>Covers the confirm-phrase gate, the role gate, and the happy-path
 * delegation to {@link NukeService}. The NukeService itself is mocked
 * to avoid Neo4j / MongoDB / Postgres infrastructure.
 */
class InstanceAdminNukeRestTest {

  @Mock
  InstanceAdminService instanceAdminService;

  @Mock
  PermissionAuditService permissionAuditService;

  @Mock
  PermissionAuditLogQueryService permissionAuditLogQueryService;

  @Mock
  AuthenticationContext authenticationContext;

  @Mock
  NukeService nukeService;

  @InjectMocks
  InstanceAdminRest resource;

  private SecurityContext adminCtx;
  private SecurityContext nonAdminCtx;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    adminCtx = mock(SecurityContext.class);
    when(adminCtx.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(true);

    nonAdminCtx = mock(SecurityContext.class);
    when(nonAdminCtx.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(false);
  }

  @Test
  void nonAdmin_throwsInvalidAuth() {
    NukeRequestIO body = new NukeRequestIO();
    body.setConfirmPhrase("yes drop everything");

    org.junit.jupiter.api.Assertions.assertThrows(
      InvalidAuthException.class,
      () -> resource.nuke(nonAdminCtx, body)
    );
    verifyNoInteractions(nukeService);
  }

  @Test
  void wrongPhrase_returns400() {
    NukeRequestIO body = new NukeRequestIO();
    body.setConfirmPhrase("wrong phrase");
    when(nukeService.confirmPhraseValid("wrong phrase")).thenReturn(false);

    Response r = resource.nuke(adminCtx, body);

    assertEquals(400, r.getStatus());
    verifyNoInteractions(instanceAdminService);
  }

  @Test
  void correctPhraseAndAdmin_delegatesToNukeService_returns200() {
    NukeRequestIO body = new NukeRequestIO();
    body.setConfirmPhrase("yes drop everything");
    when(nukeService.confirmPhraseValid("yes drop everything")).thenReturn(true);

    NukeResultIO result = new NukeResultIO(42L, 5, 1000L, 7L, "Instance data reset complete. Preserved: [...].");
    when(nukeService.nuke()).thenReturn(result);

    Response r = resource.nuke(adminCtx, body);

    assertEquals(200, r.getStatus());
    NukeResultIO entity = (NukeResultIO) r.getEntity();
    assertEquals(42L, entity.getDeletedNeo4jNodes());
    assertEquals(5, entity.getDeletedMongoCollections());
    assertEquals(1000L, entity.getDeletedTimeseriesRecords());
    verify(nukeService).nuke();
  }
}
