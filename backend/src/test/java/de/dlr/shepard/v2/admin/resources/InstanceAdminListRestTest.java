package de.dlr.shepard.v2.admin.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.v2.admin.io.InstanceAdminGrantIO;
import de.dlr.shepard.v2.admin.io.PermissionAuditEntryIO;
import de.dlr.shepard.v2.admin.services.InstanceAdminService;
import de.dlr.shepard.v2.admin.services.NukeService;
import de.dlr.shepard.v2.admin.services.PermissionAuditLogQueryService;
import de.dlr.shepard.v2.admin.services.PermissionAuditService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * APISIMP-INSTANCE-ADMIN-FAKE-PAGED — verifies that {@code listInstanceAdmins}
 * and {@code permissionAudit} return a plain {@code List} and not a
 * {@code PagedResponseIO} wrapper.
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
  void listInstanceAdmins_returnsBareList() {
    when(instanceAdminService.listInstanceAdmins()).thenReturn(List.of());

    Response r = resource.listInstanceAdmins(securityContext);

    assertEquals(200, r.getStatus());
    Object entity = r.getEntity();
    assertEquals(List.class, entity.getClass(),
        "listInstanceAdmins must return a plain List, not PagedResponseIO");
  }

  @Test
  void permissionAudit_returnsBareList() {
    when(permissionAuditService.listOrphans()).thenReturn(List.of());

    Response r = resource.permissionAudit(securityContext);

    assertEquals(200, r.getStatus());
    Object entity = r.getEntity();
    assertEquals(List.class, entity.getClass(),
        "permissionAudit must return a plain List, not PagedResponseIO");
  }

  @Test
  void listInstanceAdmins_returnsAllItems() {
    InstanceAdminGrantIO grant = new InstanceAdminGrantIO("alice", "Neo4j", "bob", null);
    when(instanceAdminService.listInstanceAdmins()).thenReturn(List.of(grant));

    Response r = resource.listInstanceAdmins(securityContext);

    @SuppressWarnings("unchecked")
    List<InstanceAdminGrantIO> body = (List<InstanceAdminGrantIO>) r.getEntity();
    assertEquals(1, body.size());
    assertEquals("alice", body.get(0).getUsername());
  }
}
