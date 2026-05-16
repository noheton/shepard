package de.dlr.shepard.v2.admin.hdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.data.hdf.daos.HdfContainerDAO;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.hsds.HsdsClient;
import de.dlr.shepard.v2.admin.hdf.io.HdfRebuildAclsResultIO;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HdfAdminRestTest {

  private HdfContainerDAO dao;
  private PermissionsService permissionsService;
  private HsdsClient hsdsClient;
  @SuppressWarnings("unchecked")
  private Instance<HsdsClient> hsdsInstance = (Instance<HsdsClient>) mock(Instance.class);
  private AuthenticationContext authCtx;
  private SecurityContext securityContext;

  private HdfAdminRest rest;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    dao = mock(HdfContainerDAO.class);
    permissionsService = mock(PermissionsService.class);
    hsdsClient = mock(HsdsClient.class);
    hsdsInstance = (Instance<HsdsClient>) mock(Instance.class);
    when(hsdsInstance.isUnsatisfied()).thenReturn(false);
    when(hsdsInstance.get()).thenReturn(hsdsClient);
    authCtx = mock(AuthenticationContext.class);
    securityContext = mock(SecurityContext.class);
    when(securityContext.getUserPrincipal()).thenReturn(() -> "admin");
    when(securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(true);

    rest = new HdfAdminRest();
    rest.hdfContainerDAO = dao;
    rest.permissionsService = permissionsService;
    rest.hsdsClientInstance = hsdsInstance;
    rest.authenticationContext = authCtx;
  }

  private HdfContainer container(long id, String appId, String domain) {
    var c = new HdfContainer(id);
    c.setAppId(appId);
    c.setHsdsDomain(domain);
    return c;
  }

  private Permissions perms(String owner, List<String> readers) {
    return new Permissions(
      owner == null ? null : new User(owner),
      readers.stream().map(User::new).toList(),
      List.of(),
      List.of(),
      List.of(),
      List.of(),
      PermissionType.Private
    );
  }

  // ─── auth / role gate ───────────────────────────────────────────────────

  @Test
  void unauthorizedNoPrincipalReturns401ProblemJson() {
    when(securityContext.getUserPrincipal()).thenReturn(null);

    Response resp = rest.rebuildAcls(securityContext);
    assertEquals(401, resp.getStatus());
    assertEquals("application/problem+json", resp.getMediaType().toString());
    ProblemJson body = (ProblemJson) resp.getEntity();
    assertEquals(401, body.status());
    assertTrue(body.type().contains("auth"));
  }

  @Test
  void forbiddenWhenRoleMissingReturns403ProblemJson() {
    when(securityContext.isUserInRole(Constants.INSTANCE_ADMIN_ROLE)).thenReturn(false);

    Response resp = rest.rebuildAcls(securityContext);
    assertEquals(403, resp.getStatus());
    ProblemJson body = (ProblemJson) resp.getEntity();
    assertEquals(403, body.status());
  }

  // ─── feature toggle ─────────────────────────────────────────────────────

  @Test
  void featureOffReturns503HdfDisabledProblem() {
    when(hsdsInstance.isUnsatisfied()).thenReturn(true);

    Response resp = rest.rebuildAcls(securityContext);
    assertEquals(503, resp.getStatus());
    ProblemJson body = (ProblemJson) resp.getEntity();
    assertEquals(HdfAdminRest.PROBLEM_TYPE_DISABLED, body.type());
    assertTrue(body.detail().contains("shepard.hdf.enabled"));
  }

  // ─── happy path ─────────────────────────────────────────────────────────

  @Test
  void rebuildAclsIteratesContainersAndCallsSetDomainAcl() {
    var c1 = container(1L, "app-1", "/shepard/app-1/");
    var c2 = container(2L, "app-2", "/shepard/app-2/");
    when(dao.findAll()).thenReturn(List.of(c1, c2));
    when(permissionsService.getPermissionsOfEntityOptional(1L)).thenReturn(Optional.of(perms("alice", List.of("bob"))));
    when(permissionsService.getPermissionsOfEntityOptional(2L)).thenReturn(Optional.of(perms("carol", List.of())));

    Response resp = rest.rebuildAcls(securityContext);
    assertEquals(200, resp.getStatus());
    HdfRebuildAclsResultIO result = (HdfRebuildAclsResultIO) resp.getEntity();
    assertNotNull(result);
    assertEquals(2, result.getContainersProcessed());
    assertEquals(2, result.getContainersSynced());
    assertTrue(result.getErrors().isEmpty());
    verify(hsdsClient).setDomainAcl(eq("/shepard/app-1/"), eq("alice"), any(), any(), any());
    verify(hsdsClient).setDomainAcl(eq("/shepard/app-2/"), eq("carol"), any(), any(), any());
  }

  @Test
  void skipsDeletedContainers() {
    var c = container(1L, "app-1", "/shepard/app-1/");
    c.setDeleted(true);
    when(dao.findAll()).thenReturn(List.of(c));

    Response resp = rest.rebuildAcls(securityContext);
    HdfRebuildAclsResultIO result = (HdfRebuildAclsResultIO) resp.getEntity();
    assertEquals(0, result.getContainersProcessed());
    verify(hsdsClient, times(0)).setDomainAcl(anyString(), anyString(), any(), any(), any());
  }

  @Test
  void containerWithoutHsdsDomainBecomesErrorEntry() {
    var c = container(1L, "app-1", null);
    when(dao.findAll()).thenReturn(List.of(c));

    Response resp = rest.rebuildAcls(securityContext);
    HdfRebuildAclsResultIO result = (HdfRebuildAclsResultIO) resp.getEntity();
    assertEquals(1, result.getContainersProcessed());
    assertEquals(0, result.getContainersSynced());
    assertEquals(1, result.getErrors().size());
    assertEquals("app-1", result.getErrors().get(0).getContainerAppId());
    assertTrue(result.getErrors().get(0).getReason().contains("hsdsDomain"));
  }

  @Test
  void containerWithoutPermissionsTriggersClearDomainAcl() {
    var c = container(1L, "app-1", "/shepard/app-1/");
    when(dao.findAll()).thenReturn(List.of(c));
    when(permissionsService.getPermissionsOfEntityOptional(1L)).thenReturn(Optional.empty());

    Response resp = rest.rebuildAcls(securityContext);
    HdfRebuildAclsResultIO result = (HdfRebuildAclsResultIO) resp.getEntity();
    assertEquals(1, result.getContainersProcessed());
    assertEquals(1, result.getContainersSynced());
    verify(hsdsClient).clearDomainAcl("/shepard/app-1/");
  }

  @Test
  void hsdsSetAclFailureBecomesErrorEntryButOverallStays200() {
    var c1 = container(1L, "app-1", "/shepard/app-1/");
    var c2 = container(2L, "app-2", "/shepard/app-2/");
    when(dao.findAll()).thenReturn(List.of(c1, c2));
    when(permissionsService.getPermissionsOfEntityOptional(1L)).thenReturn(Optional.of(perms("alice", List.of())));
    when(permissionsService.getPermissionsOfEntityOptional(2L)).thenReturn(Optional.of(perms("carol", List.of())));
    doThrow(new HsdsClient.HsdsException("boom"))
      .when(hsdsClient)
      .setDomainAcl(eq("/shepard/app-1/"), anyString(), any(), any(), any());

    Response resp = rest.rebuildAcls(securityContext);
    assertEquals(200, resp.getStatus());
    HdfRebuildAclsResultIO result = (HdfRebuildAclsResultIO) resp.getEntity();
    assertEquals(2, result.getContainersProcessed());
    assertEquals(1, result.getContainersSynced());
    assertEquals(1, result.getErrors().size());
    assertEquals("app-1", result.getErrors().get(0).getContainerAppId());
    assertTrue(result.getErrors().get(0).getReason().contains("setDomainAcl failed"));
  }

  @Test
  void daoEnumerationFailureReturns500ProblemJson() {
    when(dao.findAll()).thenThrow(new RuntimeException("neo4j down"));

    Response resp = rest.rebuildAcls(securityContext);
    assertEquals(500, resp.getStatus());
    ProblemJson body = (ProblemJson) resp.getEntity();
    assertEquals(HdfAdminRest.PROBLEM_TYPE_PARTIAL_FAILURE, body.type());
  }
}
