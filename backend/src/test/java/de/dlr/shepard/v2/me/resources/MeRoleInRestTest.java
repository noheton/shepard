package de.dlr.shepard.v2.me.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.v2.me.io.MeRoleInIO;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class MeRoleInRestTest {

  static final String CALLER = "alice";
  static final String APP_ID = "appid-42";

  @Mock
  CollectionPropertiesDAO collectionDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  MeRoleInRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new MeRoleInRest();
    resource.collectionDAO = collectionDAO;
    resource.permissionsService = permissionsService;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  @Test
  void returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    var r = resource.roleIn(APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void returns404WhenCollectionDoesNotExist() {
    when(collectionDAO.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.empty());
    var r = resource.roleIn(APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void ownerMapsToAllThreeCapabilitiesPlusOmitsAdminFlag() {
    when(collectionDAO.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(42L));
    when(permissionsService.getUserRolesOnEntity(42L, CALLER)).thenReturn(new Roles(true, false, false, false));
    when(securityContext.isUserInRole("instance-admin")).thenReturn(false);

    var r = resource.roleIn(APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    var io = (MeRoleInIO) r.getEntity();
    assertEquals(APP_ID, io.getCollectionAppId());
    assertTrue(io.isRead());
    assertTrue(io.isWrite());
    assertTrue(io.isManage());
    assertFalse(io.isInstanceAdmin());
  }

  @Test
  void managerMapsToReadWriteManage() {
    when(collectionDAO.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(42L));
    when(permissionsService.getUserRolesOnEntity(42L, CALLER)).thenReturn(new Roles(false, true, false, false));
    var r = resource.roleIn(APP_ID, securityContext);
    var io = (MeRoleInIO) r.getEntity();
    assertTrue(io.isRead());
    assertTrue(io.isWrite());
    assertTrue(io.isManage());
  }

  @Test
  void writerMapsToReadWriteWithoutManage() {
    when(collectionDAO.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(42L));
    when(permissionsService.getUserRolesOnEntity(42L, CALLER)).thenReturn(new Roles(false, false, true, false));
    var io = (MeRoleInIO) resource.roleIn(APP_ID, securityContext).getEntity();
    assertTrue(io.isRead());
    assertTrue(io.isWrite());
    assertFalse(io.isManage());
  }

  @Test
  void readerMapsToReadOnly() {
    when(collectionDAO.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(42L));
    when(permissionsService.getUserRolesOnEntity(42L, CALLER)).thenReturn(new Roles(false, false, false, true));
    var io = (MeRoleInIO) resource.roleIn(APP_ID, securityContext).getEntity();
    assertTrue(io.isRead());
    assertFalse(io.isWrite());
    assertFalse(io.isManage());
  }

  @Test
  void returns403WhenNoRolesAndNotAdmin() {
    // Existence-protection: a no-access caller can't probe which
    // Collections exist (the 403 mirrors the 404-or-403 pattern of
    // CollectionPropertiesRest).
    when(collectionDAO.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(42L));
    when(permissionsService.getUserRolesOnEntity(42L, CALLER)).thenReturn(new Roles(false, false, false, false));
    when(securityContext.isUserInRole("instance-admin")).thenReturn(false);

    var r = resource.roleIn(APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void instanceAdminWithNoRolesStillGets200() {
    // Admin override: even without per-Collection roles, the admin chip
    // must render; the UI uses this to surface the Instance-Admin badge.
    when(collectionDAO.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(42L));
    when(permissionsService.getUserRolesOnEntity(42L, CALLER)).thenReturn(new Roles(false, false, false, false));
    when(securityContext.isUserInRole("instance-admin")).thenReturn(true);

    var r = resource.roleIn(APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    var io = (MeRoleInIO) r.getEntity();
    assertFalse(io.isRead());
    assertFalse(io.isWrite());
    assertFalse(io.isManage());
    assertTrue(io.isInstanceAdmin());
  }

  @Test
  void instanceAdminFlagOrthogonalToCollectionRoles() {
    // Admin and writer simultaneously: both chips render.
    when(collectionDAO.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(42L));
    when(permissionsService.getUserRolesOnEntity(42L, CALLER)).thenReturn(new Roles(false, false, true, false));
    when(securityContext.isUserInRole("instance-admin")).thenReturn(true);

    var io = (MeRoleInIO) resource.roleIn(APP_ID, securityContext).getEntity();
    assertTrue(io.isWrite());
    assertTrue(io.isInstanceAdmin());
  }
}
