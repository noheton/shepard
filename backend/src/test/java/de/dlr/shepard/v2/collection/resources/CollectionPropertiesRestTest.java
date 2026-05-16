package de.dlr.shepard.v2.collection.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.collection.entities.CollectionProperties;
import de.dlr.shepard.v2.collection.io.CollectionPropertiesIO;
import de.dlr.shepard.v2.collection.io.PatchCollectionPropertiesIO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CollectionPropertiesRestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000010";
  static final long COLL_OGM_ID = 42L;
  static final String CALLER = "alice";

  @Mock
  CollectionPropertiesDAO propertiesDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  CollectionPropertiesRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionPropertiesRest();
    resource.propertiesDAO = propertiesDAO;
    resource.permissionsService = permissionsService;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  @Test
  void readReturns401IfUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.read(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void readReturns404WhenCollectionMissing() {
    when(propertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.read(COLL_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessTypeAllowedForUser(anyLong(), any(), any());
  }

  @Test
  void readReturns403WhenCallerLacksReadPermission() {
    when(propertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER, anyLong())).thenReturn(false);
    Response r = resource.read(COLL_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
    verify(propertiesDAO, never()).ensureFor(any());
  }

  @Test
  void readReturns200WithProperties() {
    var entity = new CollectionProperties("props-app-id");
    entity.setWebdavVisible(false);
    when(propertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER, anyLong())).thenReturn(true);
    when(propertiesDAO.ensureFor(COLL_APP_ID)).thenReturn(entity);

    Response r = resource.read(COLL_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    var io = (CollectionPropertiesIO) r.getEntity();
    assertNotNull(io);
    assertEquals("props-app-id", io.getAppId());
    assertEquals(false, io.isWebdavVisible());
  }

  @Test
  void patchReturns403WhenCallerLacksManagePermission() {
    when(propertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Manage, CALLER, anyLong())).thenReturn(false);
    var body = new PatchCollectionPropertiesIO(false, null, null);
    Response r = resource.patch(COLL_APP_ID, body, securityContext);
    assertEquals(403, r.getStatus());
    verify(propertiesDAO, never()).createOrUpdate(any());
  }

  @Test
  void patchUpdatesOnlySuppliedFields() {
    var entity = new CollectionProperties("props-app-id");
    entity.setWebdavVisible(true);
    entity.setDefaultOntologyUri("old-uri");
    entity.setUiDefaultsJson("{\"old\":true}");
    when(propertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Manage, CALLER, anyLong())).thenReturn(true);
    when(propertiesDAO.ensureFor(COLL_APP_ID)).thenReturn(entity);
    when(propertiesDAO.createOrUpdate(any(CollectionProperties.class))).thenAnswer(inv -> inv.getArgument(0));

    // Only flip webdavVisible; the other two stay.
    var body = new PatchCollectionPropertiesIO(false, null, null);
    Response r = resource.patch(COLL_APP_ID, body, securityContext);

    assertEquals(200, r.getStatus());
    var io = (CollectionPropertiesIO) r.getEntity();
    assertEquals(false, io.isWebdavVisible());
    assertEquals("old-uri", io.getDefaultOntologyUri());
    assertEquals("{\"old\":true}", io.getUiDefaultsJson());
  }

  @Test
  void patchHandlesNullBody() {
    var entity = new CollectionProperties("props-app-id");
    when(propertiesDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Manage), eq(CALLER), anyLong())).thenReturn(true);
    when(propertiesDAO.ensureFor(COLL_APP_ID)).thenReturn(entity);
    when(propertiesDAO.createOrUpdate(any(CollectionProperties.class))).thenAnswer(inv -> inv.getArgument(0));

    Response r = resource.patch(COLL_APP_ID, null, securityContext);
    assertEquals(200, r.getStatus());
  }
}
