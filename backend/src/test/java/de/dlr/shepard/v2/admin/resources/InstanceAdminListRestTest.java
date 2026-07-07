package de.dlr.shepard.v2.admin.resources;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.v2.admin.io.InstanceAdminGrantIO;
import de.dlr.shepard.v2.admin.io.PermissionAuditEntryIO;
import de.dlr.shepard.v2.admin.services.InstanceAdminService;
import de.dlr.shepard.v2.admin.services.NukeService;
import de.dlr.shepard.v2.admin.services.PermissionAuditLogQueryService;
import de.dlr.shepard.v2.admin.services.PermissionAuditService;
import de.dlr.shepard.v2.common.io.PagedResponseIO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-INSTANCE-ADMINS-BARE-LIST — verifies that {@code listInstanceAdmins}
 * returns a {@link PagedResponseIO} envelope. {@code permissionAudit} still returns
 * a bare list (APISIMP-PERMISSION-AUDIT-BARE-LIST, needs DB-side pagination, deferred).
 */
class InstanceAdminListRestTest {

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
  @Mock
  SecurityContext securityContext;

  @InjectMocks
  InstanceAdminRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(securityContext.isUserInRole("instance-admin")).thenReturn(true);
  }

  @Test
  void listInstanceAdmins_returnsPagedEnvelope() {
    when(instanceAdminService.listInstanceAdmins()).thenReturn(List.of());

    Response r = resource.listInstanceAdmins(securityContext);

    assertEquals(200, r.getStatus());
    Object entity = r.getEntity();
    assertInstanceOf(PagedResponseIO.class, entity,
        "listInstanceAdmins must return PagedResponseIO (APISIMP-INSTANCE-ADMINS-BARE-LIST)");
  }

  @Test
  void permissionAudit_returnsBareList() {
    // APISIMP-PERMISSION-AUDIT-BARE-LIST: still bare — needs DB-side pagination (deferred S-size row).
    when(permissionAuditService.listOrphans()).thenReturn(List.of());

    Response r = resource.permissionAudit(securityContext);

    assertEquals(200, r.getStatus());
    Object entity = r.getEntity();
    assertInstanceOf(List.class, entity,
        "permissionAudit still returns a plain List (APISIMP-PERMISSION-AUDIT-BARE-LIST pending)");
  }

  @Test
  void listInstanceAdmins_returnsAllItemsInEnvelope() {
    InstanceAdminGrantIO grant = new InstanceAdminGrantIO("alice", "Neo4j", "bob", null);
    when(instanceAdminService.listInstanceAdmins()).thenReturn(List.of(grant));

    Response r = resource.listInstanceAdmins(securityContext);

    @SuppressWarnings("unchecked")
    PagedResponseIO<InstanceAdminGrantIO> body = (PagedResponseIO<InstanceAdminGrantIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(1, body.total());
    assertEquals("alice", body.items().get(0).getUsername());
  }
}
