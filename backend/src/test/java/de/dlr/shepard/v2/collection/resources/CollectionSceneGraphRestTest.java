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
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.collection.daos.CollectionHeroViewLinkDAO;
import de.dlr.shepard.v2.collection.io.CollectionHeroViewLinkIO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * V2CONV-B4 — unit tests for the Collection ↔ hero-view link surface.
 *
 * <p>Replaces the pre-V2CONV-B4 tests that used {@code CollectionSceneGraphLinkDAO},
 * {@code SceneGraphService}, and {@code SceneGraphPermissionService}. The bespoke
 * scene-graph subsystem dissolved into the generic MAPPING_RECIPE mechanism; this
 * resource now stores and returns a MAPPING_RECIPE {@code ShepardTemplate} appId.
 */
class CollectionSceneGraphRestTest {

  static final String COLL_APP_ID = "018f9c5a-7e26-7000-a000-000000000020";
  static final long COLL_OGM_ID = 77L;
  static final String TEMPLATE_APP_ID = "018f9c5a-7e26-7000-a000-000000000021";
  static final String CALLER = "alice";

  @Mock CollectionHeroViewLinkDAO linkDAO;
  @Mock PermissionsService permissionsService;
  @Mock ShepardTemplateDAO templateDAO;
  @Mock SecurityContext securityContext;
  @Mock Principal principal;

  CollectionSceneGraphRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionSceneGraphRest();
    resource.linkDAO = linkDAO;
    resource.permissionsService = permissionsService;
    resource.templateDAO = templateDAO;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  // ── GET ────────────────────────────────────────────────────────────────────

  @Test
  void getReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void getReturns404WhenCollectionMissing() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessTypeAllowedForUser(anyLong(), any(), any(), anyLong());
  }

  @Test
  void getReturns403WhenCallerLacksReadOnCollection() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(false);
    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
    verify(linkDAO, never()).findLinkedTemplateAppId(any());
  }

  @Test
  void getReturns404WhenNoTemplateLinked() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(linkDAO.findLinkedTemplateAppId(COLL_APP_ID)).thenReturn(Optional.empty());

    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
  }

  @Test
  void getReturns200WithTemplateIdentityWhenLinked() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(linkDAO.findLinkedTemplateAppId(COLL_APP_ID)).thenReturn(Optional.of(TEMPLATE_APP_ID));

    ShepardTemplate template = new ShepardTemplate();
    template.setName("MFFD hero view");
    template.setDescription("3D MFFD robot cell mapping");
    template.setTemplateKind("MAPPING_RECIPE");
    when(templateDAO.findByAppId(TEMPLATE_APP_ID)).thenReturn(Optional.of(template));

    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    var io = (CollectionHeroViewLinkIO) r.getEntity();
    assertNotNull(io);
    assertEquals(TEMPLATE_APP_ID, io.getSceneGraphAppId());
    assertEquals("MFFD hero view", io.getTemplateName());
    assertEquals("3D MFFD robot cell mapping", io.getTemplateDescription());
    assertEquals("MAPPING_RECIPE", io.getTemplateKind());
  }

  @Test
  void getReturns200WhenLinkedTemplateEntityIsMissing() {
    // Dangling pointer: the template was hard-deleted. Resource still returns
    // the appId so the UI can render EntityNotFound for that band instead of 500.
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(linkDAO.findLinkedTemplateAppId(COLL_APP_ID)).thenReturn(Optional.of(TEMPLATE_APP_ID));
    when(templateDAO.findByAppId(TEMPLATE_APP_ID)).thenReturn(Optional.empty());

    Response r = resource.get(COLL_APP_ID, securityContext);
    assertEquals(200, r.getStatus());
    var io = (CollectionHeroViewLinkIO) r.getEntity();
    assertEquals(TEMPLATE_APP_ID, io.getSceneGraphAppId());
  }

  // ── PUT ────────────────────────────────────────────────────────────────────

  @Test
  void putReturns400WhenBodyMissingTemplateId() {
    var body = new CollectionHeroViewLinkIO();
    // sceneGraphAppId is null by default
    Response r = resource.link(COLL_APP_ID, body, securityContext);
    assertEquals(400, r.getStatus());
    verify(linkDAO, never()).link(any(), any());
  }

  @Test
  void putReturns400WhenBodyNull() {
    Response r = resource.link(COLL_APP_ID, null, securityContext);
    assertEquals(400, r.getStatus());
  }

  @Test
  void putReturns404WhenCollectionMissing() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());
    var body = new CollectionHeroViewLinkIO();
    body.setSceneGraphAppId(TEMPLATE_APP_ID);
    Response r = resource.link(COLL_APP_ID, body, securityContext);
    assertEquals(404, r.getStatus());
    verify(linkDAO, never()).link(any(), any());
  }

  @Test
  void putReturns403WhenCallerLacksWriteOnCollection() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(false);
    var body = new CollectionHeroViewLinkIO();
    body.setSceneGraphAppId(TEMPLATE_APP_ID);

    Response r = resource.link(COLL_APP_ID, body, securityContext);
    assertEquals(403, r.getStatus());
    verify(templateDAO, never()).findByAppId(any());
  }

  @Test
  void putReturns404WhenTargetTemplateMissing() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(templateDAO.findByAppId(TEMPLATE_APP_ID)).thenReturn(Optional.empty());
    var body = new CollectionHeroViewLinkIO();
    body.setSceneGraphAppId(TEMPLATE_APP_ID);

    Response r = resource.link(COLL_APP_ID, body, securityContext);
    assertEquals(404, r.getStatus());
    verify(linkDAO, never()).link(any(), any());
  }

  @Test
  void putReturns422WhenTemplateNotMappingRecipeKind() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    ShepardTemplate template = new ShepardTemplate();
    template.setAppId(TEMPLATE_APP_ID);
    template.setTemplateKind("DATAOBJECT_RECIPE");
    when(templateDAO.findByAppId(TEMPLATE_APP_ID)).thenReturn(Optional.of(template));
    var body = new CollectionHeroViewLinkIO();
    body.setSceneGraphAppId(TEMPLATE_APP_ID);

    Response r = resource.link(COLL_APP_ID, body, securityContext);
    assertEquals(422, r.getStatus());
    verify(linkDAO, never()).link(any(), any());
  }

  @Test
  void putReturns200OnHappyPath() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    // GET re-entry also needs Read permission.
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Read), eq(CALLER), anyLong()))
      .thenReturn(true);
    ShepardTemplate template = new ShepardTemplate();
    template.setName("MFFD hero view");
    template.setTemplateKind("MAPPING_RECIPE");
    when(templateDAO.findByAppId(TEMPLATE_APP_ID)).thenReturn(Optional.of(template));
    when(linkDAO.link(COLL_APP_ID, TEMPLATE_APP_ID)).thenReturn(true);
    when(linkDAO.findLinkedTemplateAppId(COLL_APP_ID)).thenReturn(Optional.of(TEMPLATE_APP_ID));

    var body = new CollectionHeroViewLinkIO();
    body.setSceneGraphAppId(TEMPLATE_APP_ID);
    Response r = resource.link(COLL_APP_ID, body, securityContext);

    assertEquals(200, r.getStatus());
    var io = (CollectionHeroViewLinkIO) r.getEntity();
    assertNotNull(io);
    assertEquals(TEMPLATE_APP_ID, io.getSceneGraphAppId());
    assertEquals("MFFD hero view", io.getTemplateName());
    verify(linkDAO).link(COLL_APP_ID, TEMPLATE_APP_ID);
  }

  // ── DELETE ─────────────────────────────────────────────────────────────────

  @Test
  void deleteReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.unlink(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void deleteReturns404WhenCollectionMissing() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.unlink(COLL_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(linkDAO, never()).unlink(any());
  }

  @Test
  void deleteReturns403WhenCallerLacksWriteOnCollection() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(false);
    Response r = resource.unlink(COLL_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
    verify(linkDAO, never()).unlink(any());
  }

  @Test
  void deleteReturns204OnHappyPath() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(linkDAO.unlink(COLL_APP_ID)).thenReturn(true);
    Response r = resource.unlink(COLL_APP_ID, securityContext);
    assertEquals(204, r.getStatus());
    verify(linkDAO).unlink(COLL_APP_ID);
  }

  @Test
  void deleteIsIdempotentWhenAlreadyUnlinked() {
    when(linkDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(eq(COLL_OGM_ID), eq(AccessType.Write), eq(CALLER), anyLong()))
      .thenReturn(true);
    when(linkDAO.unlink(COLL_APP_ID)).thenReturn(true);
    Response r = resource.unlink(COLL_APP_ID, securityContext);
    assertEquals(204, r.getStatus());
  }
}
