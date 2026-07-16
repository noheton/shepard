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
 * APISIMP-INSTANCE-ADMINS-BARE-LIST + APISIMP-PERMISSION-AUDIT-BARE-LIST —
 * verifies that both list endpoints return a {@link PagedResponseIO} envelope
 * with DB-side pagination params forwarded to the service.
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
    when(instanceAdminService.countInstanceAdmins()).thenReturn(0L);
    when(instanceAdminService.listInstanceAdmins(0L, 50)).thenReturn(List.of());

    Response r = resource.listInstanceAdmins(securityContext, 0, 50);

    assertEquals(200, r.getStatus());
    Object entity = r.getEntity();
    assertInstanceOf(PagedResponseIO.class, entity,
        "listInstanceAdmins must return PagedResponseIO (APISIMP-ADMIN-INMEM-PAGING)");
  }

  @Test
  void permissionAudit_returnsPagedEnvelope() {
    when(permissionAuditService.countOrphans()).thenReturn(0L);
    when(permissionAuditService.listOrphans(0L, 50)).thenReturn(List.of());

    Response r = resource.permissionAudit(securityContext, 0, 50);

    assertEquals(200, r.getStatus());
    assertInstanceOf(PagedResponseIO.class, r.getEntity(),
        "permissionAudit must return PagedResponseIO (APISIMP-PERMISSION-AUDIT-BARE-LIST)");
  }

  @Test
  void permissionAudit_paginationParamsForwardedToService() {
    PermissionAuditEntryIO entry = new PermissionAuditEntryIO(42L, "test-app-id", List.of("BasicEntity"), "thing");
    when(permissionAuditService.countOrphans()).thenReturn(1L);
    when(permissionAuditService.listOrphans(100L, 50)).thenReturn(List.of(entry));

    Response r = resource.permissionAudit(securityContext, 2, 50);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    PagedResponseIO<PermissionAuditEntryIO> body = (PagedResponseIO<PermissionAuditEntryIO>) r.getEntity();
    assertEquals(1L, body.total());
    assertEquals(1, body.items().size());
    assertEquals("test-app-id", body.items().get(0).getAppId());
  }

  @Test
  void listInstanceAdmins_returnsAllItemsInEnvelope() {
    InstanceAdminGrantIO grant = new InstanceAdminGrantIO("alice", "Neo4j", "bob", null);
    when(instanceAdminService.countInstanceAdmins()).thenReturn(1L);
    when(instanceAdminService.listInstanceAdmins(0L, 50)).thenReturn(List.of(grant));

    Response r = resource.listInstanceAdmins(securityContext, 0, 50);

    @SuppressWarnings("unchecked")
    PagedResponseIO<InstanceAdminGrantIO> body = (PagedResponseIO<InstanceAdminGrantIO>) r.getEntity();
    assertEquals(1, body.items().size());
    assertEquals(1L, body.total());
    assertEquals("alice", body.items().get(0).getUsername());
  }

  @Test
  void listInstanceAdmins_paginationParamsForwardedToService() {
    InstanceAdminGrantIO grant = new InstanceAdminGrantIO("charlie", "Neo4j", "admin", null);
    when(instanceAdminService.countInstanceAdmins()).thenReturn(75L);
    when(instanceAdminService.listInstanceAdmins(50L, 50)).thenReturn(List.of(grant));

    Response r = resource.listInstanceAdmins(securityContext, 1, 50);

    @SuppressWarnings("unchecked")
    PagedResponseIO<InstanceAdminGrantIO> body = (PagedResponseIO<InstanceAdminGrantIO>) r.getEntity();
    assertEquals(75L, body.total());
    assertEquals(1, body.items().size());
    assertEquals("charlie", body.items().get(0).getUsername());
    assertEquals(75L, Long.parseLong(r.getHeaderString("X-Total-Count")));
  }
}
