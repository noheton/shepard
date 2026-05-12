package de.dlr.shepard.data.hdf.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.auth.security.AuthenticationContext;
import de.dlr.shepard.auth.users.entities.User;
import de.dlr.shepard.auth.users.services.UserService;
import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.data.hdf.daos.HdfContainerDAO;
import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.data.hdf.hsds.HsdsClient;
import de.dlr.shepard.data.hdf.io.HdfContainerIO;
import jakarta.enterprise.inject.Instance;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HdfContainerServiceTest {

  private static final User USER = new User("alice");

  private HdfContainerDAO dao;
  private PermissionsService permissionsService;
  private UserService userService;
  private DateHelper dateHelper;
  private AuthenticationContext authenticationContext;
  private HsdsClient hsdsClient;
  @SuppressWarnings("unchecked")
  private Instance<HsdsClient> hsdsInstance = (Instance<HsdsClient>) mock(Instance.class);

  private HdfContainerService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    dao = mock(HdfContainerDAO.class);
    permissionsService = mock(PermissionsService.class);
    userService = mock(UserService.class);
    dateHelper = mock(DateHelper.class);
    authenticationContext = mock(AuthenticationContext.class);
    hsdsClient = mock(HsdsClient.class);
    hsdsInstance = (Instance<HsdsClient>) mock(Instance.class);
    when(hsdsInstance.isUnsatisfied()).thenReturn(false);
    when(hsdsInstance.get()).thenReturn(hsdsClient);

    service = new HdfContainerService();
    service.hdfContainerDAO = dao;
    service.permissionsService = permissionsService;
    service.userService = userService;
    service.dateHelper = dateHelper;
    service.hsdsClientInstance = hsdsInstance;
    // Inject the AbstractContainerService fields too — package-private access.
    setSuperField("permissionsService", permissionsService);
    setSuperField("authenticationContext", authenticationContext);

    when(userService.getCurrentUser()).thenReturn(USER);
    when(authenticationContext.getCurrentUserName()).thenReturn(USER.getUsername());
    when(dateHelper.getDate()).thenReturn(new Date(1_700_000_000_000L));
    // Permission grant by default — tests can override.
    when(permissionsService.isAccessTypeAllowedForUser(anyLong(), any(AccessType.class), any())).thenReturn(true);
    when(permissionsService.isCurrentUserOwner(anyLong())).thenReturn(true);
    // DAO save returns the same object back so we can observe state.
    when(dao.createOrUpdate(any(HdfContainer.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private void setSuperField(String name, Object value) {
    try {
      var f = de.dlr.shepard.data.AbstractContainerService.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(service, value);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Test scaffold broken: " + e.getMessage(), e);
    }
  }

  // ─── create ────────────────────────────────────────────────────────────

  @Test
  void createContainerProvisionsHsdsAndPersistsRow() {
    var body = new HdfContainerIO();
    body.setName("primary");
    body.setDescription("hot fire run");
    body.setAttributes(Map.of("project", "rocket"));

    HdfContainer created = service.createContainer(body);

    // appId minted before HSDS call; HSDS provisioning of /shepard/<appId>/ path.
    assertNotNull(created.getAppId(), "appId minted before save");
    String expectedDomain = "/shepard/" + created.getAppId() + "/";
    assertEquals(expectedDomain, created.getHsdsDomain());
    verify(hsdsClient).createDomain(expectedDomain);
    verify(dao).createOrUpdate(any(HdfContainer.class));
    verify(permissionsService).createPermissions(created, USER, PermissionType.Private);
  }

  @Test
  void createContainerSurfacesHsdsFailureAndSkipsNeo4j() {
    var body = new HdfContainerIO();
    body.setName("primary");
    doThrow(new HsdsClient.HsdsException("boom")).when(hsdsClient).createDomain(any());

    assertThrows(HsdsClient.HsdsException.class, () -> service.createContainer(body));
    verify(dao, never()).createOrUpdate(any(HdfContainer.class));
    verify(permissionsService, never()).createPermissions(any(), any(), any());
  }

  @Test
  void createContainerRollsBackHsdsWhenNeo4jWriteFails() {
    var body = new HdfContainerIO();
    body.setName("primary");
    when(dao.createOrUpdate(any(HdfContainer.class))).thenThrow(new RuntimeException("neo4j down"));

    assertThrows(RuntimeException.class, () -> service.createContainer(body));
    // Compensation: HSDS createDomain was called, then deleteDomain was called.
    ArgumentCaptor<String> domains = ArgumentCaptor.forClass(String.class);
    verify(hsdsClient).createDomain(domains.capture());
    verify(hsdsClient).deleteDomain(domains.getValue());
    verify(permissionsService, never()).createPermissions(any(), any(), any());
  }

  @Test
  void createContainerFailsCleanlyWhenFeatureOff() {
    when(hsdsInstance.isUnsatisfied()).thenReturn(true);
    var body = new HdfContainerIO();
    body.setName("primary");
    var ex = assertThrows(IllegalStateException.class, () -> service.createContainer(body));
    assertTrue(ex.getMessage().contains("shepard.hdf.enabled=false"));
    verify(hsdsClient, never()).createDomain(any());
  }

  // ─── read ──────────────────────────────────────────────────────────────

  @Test
  void getContainerByAppIdReturnsRowWhenReadable() {
    var c = new HdfContainer(11L);
    c.setAppId("app-11");
    c.setName("X");
    when(dao.findByAppId("app-11")).thenReturn(c);
    var got = service.getContainerByAppId("app-11");
    assertEquals(c, got);
  }

  @Test
  void getContainerByAppIdRaisesWhenMissing() {
    when(dao.findByAppId("ghost")).thenReturn(null);
    assertThrows(InvalidPathException.class, () -> service.getContainerByAppId("ghost"));
  }

  @Test
  void getContainerByAppIdRaisesWhenDeleted() {
    var c = new HdfContainer(11L);
    c.setAppId("app-11");
    c.setDeleted(true);
    when(dao.findByAppId("app-11")).thenReturn(c);
    assertThrows(InvalidPathException.class, () -> service.getContainerByAppId("app-11"));
  }

  // ─── delete ────────────────────────────────────────────────────────────

  @Test
  void deleteContainerSoftDeletesAndDropsHsdsDomain() {
    var c = new HdfContainer(11L);
    c.setAppId("app-11");
    c.setHsdsDomain("/shepard/app-11/");
    when(dao.findByNeo4jId(11L)).thenReturn(c);

    service.deleteContainer(11L);

    assertTrue(c.isDeleted(), "soft-deleted");
    verify(dao, times(1)).createOrUpdate(c);
    verify(hsdsClient).deleteDomain("/shepard/app-11/");
  }

  @Test
  void deleteContainerByAppIdResolvesAndDelegates() {
    var c = new HdfContainer(11L);
    c.setAppId("app-11");
    c.setHsdsDomain("/shepard/app-11/");
    when(dao.findByAppId("app-11")).thenReturn(c);
    when(dao.findByNeo4jId(11L)).thenReturn(c);

    service.deleteContainerByAppId("app-11");
    assertTrue(c.isDeleted());
    verify(hsdsClient).deleteDomain("/shepard/app-11/");
  }

  @Test
  void deleteContainerByAppIdRaisesWhenMissing() {
    when(dao.findByAppId("ghost")).thenReturn(null);
    assertThrows(InvalidPathException.class, () -> service.deleteContainerByAppId("ghost"));
    verify(hsdsClient, never()).deleteDomain(any());
  }

  // ─── feature toggle ────────────────────────────────────────────────────

  @Test
  void requireHsdsAvailableYieldsErrorWhenInstanceUnsatisfied() {
    when(hsdsInstance.isUnsatisfied()).thenReturn(true);
    assertThrows(IllegalStateException.class, () -> service.requireHsdsAvailable());
  }

  @Test
  void requireHsdsAvailableReturnsClientWhenWired() {
    assertEquals(hsdsClient, service.requireHsdsAvailable());
  }
}
