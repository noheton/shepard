package de.dlr.shepard.v2.collection.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.io.PermissionsIO;
import de.dlr.shepard.auth.permission.model.Permissions;
import de.dlr.shepard.auth.permission.model.Roles;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.common.util.PermissionType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CollectionPermissionsRestTest {

  static final String APP_ID = "018f9c5a-7e26-7000-b000-000000000099";
  static final long OGM_ID = 77L;
  static final String CALLER = "alice";

  @Mock CollectionPropertiesDAO dao;
  @Mock PermissionsService permissionsService;
  @Mock SecurityContext sc;
  @Mock Principal principal;

  CollectionPermissionsRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionPermissionsRest();
    resource.collectionPropertiesDAO = dao;
    resource.permissionsService = permissionsService;
    when(sc.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  // --- getPermissions ---

  @Test
  void getPermissions_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.getPermissions(APP_ID, sc);
    assertEquals(401, r.getStatus());
    verify(dao, never()).findCollectionIdByAppId(any());
  }

  @Test
  void getPermissions_returns404WhenCollectionMissing() {
    when(dao.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.empty());
    Response r = resource.getPermissions(APP_ID, sc);
    assertEquals(404, r.getStatus());
  }

  @Test
  void getPermissions_returns403WhenCallerLacksManage() {
    when(dao.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_ID), eq(AccessType.Manage), eq(CALLER), anyLong()))
        .thenReturn(false);
    Response r = resource.getPermissions(APP_ID, sc);
    assertEquals(403, r.getStatus());
  }

  @Test
  void getPermissions_returns200WithPermissions() {
    Permissions perms = new Permissions();
    perms.setPermissionType(PermissionType.Private);
    perms.setEntities(List.of(new BasicEntity(OGM_ID)));
    when(dao.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_ID), eq(AccessType.Manage), eq(CALLER), anyLong()))
        .thenReturn(true);
    when(permissionsService.getPermissionsOfEntity(OGM_ID)).thenReturn(perms);
    Response r = resource.getPermissions(APP_ID, sc);
    assertEquals(200, r.getStatus());
    assertNotNull(r.getEntity());
  }

  // --- editPermissions (PATCH) ---

  @Test
  void editPermissions_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.editPermissions(APP_ID, new PermissionsIO(), sc);
    assertEquals(401, r.getStatus());
  }

  @Test
  void editPermissions_returns404WhenCollectionMissing() {
    when(dao.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.empty());
    Response r = resource.editPermissions(APP_ID, new PermissionsIO(), sc);
    assertEquals(404, r.getStatus());
  }

  @Test
  void editPermissions_returns403WhenCallerLacksManage() {
    when(dao.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_ID), eq(AccessType.Manage), eq(CALLER), anyLong()))
        .thenReturn(false);
    Response r = resource.editPermissions(APP_ID, new PermissionsIO(), sc);
    assertEquals(403, r.getStatus());
  }

  @Test
  void editPermissions_returns200WithUpdatedPermissions() {
    Permissions updated = new Permissions();
    updated.setPermissionType(PermissionType.Public);
    updated.setEntities(List.of(new BasicEntity(OGM_ID)));
    when(dao.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_ID), eq(AccessType.Manage), eq(CALLER), anyLong()))
        .thenReturn(true);
    when(permissionsService.updatePermissionsByNeo4jId(any(), eq(OGM_ID))).thenReturn(updated);
    Response r = resource.editPermissions(APP_ID, new PermissionsIO(), sc);
    assertEquals(200, r.getStatus());
    assertNotNull(r.getEntity());
  }

  // --- getRoles ---

  @Test
  void getRoles_returns401WhenUnauthenticated() {
    when(sc.getUserPrincipal()).thenReturn(null);
    Response r = resource.getRoles(APP_ID, sc);
    assertEquals(401, r.getStatus());
  }

  @Test
  void getRoles_returns404WhenCollectionMissing() {
    when(dao.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.empty());
    Response r = resource.getRoles(APP_ID, sc);
    assertEquals(404, r.getStatus());
  }

  @Test
  void getRoles_returns403WhenCallerLacksRead() {
    when(dao.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
        .thenReturn(false);
    Response r = resource.getRoles(APP_ID, sc);
    assertEquals(403, r.getStatus());
  }

  @Test
  void getRoles_returns200WithRoles() {
    when(dao.findCollectionIdByAppId(APP_ID)).thenReturn(Optional.of(OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
        .thenReturn(true);
    when(permissionsService.getUserRolesOnEntity(OGM_ID, CALLER)).thenReturn(new Roles());
    Response r = resource.getRoles(APP_ID, sc);
    assertEquals(200, r.getStatus());
  }
}
