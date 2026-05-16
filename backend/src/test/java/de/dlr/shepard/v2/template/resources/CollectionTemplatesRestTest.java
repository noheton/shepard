package de.dlr.shepard.v2.template.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.template.io.AllowedTemplatesIO;
import de.dlr.shepard.v2.template.io.ShepardTemplateIO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CollectionTemplatesRestTest {

  static final String COLL_APP_ID = "coll-app-id";
  static final long COLL_OGM_ID = 100L;
  static final String CALLER = "alice";

  @Mock
  ShepardTemplateDAO templateDAO;

  @Mock
  CollectionPropertiesDAO propsDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  CollectionTemplatesRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new CollectionTemplatesRest();
    resource.templateDAO = templateDAO;
    resource.collectionPropsDAO = propsDAO;
    resource.permissionsService = permissionsService;
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  @Test
  void listAllowedReturns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.listAllowed(COLL_APP_ID, securityContext);
    assertEquals(401, r.getStatus());
    verify(templateDAO, never()).listAllowedForCollection(any());
  }

  @Test
  void listAllowedReturns404WhenCollectionMissing() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.listAllowed(COLL_APP_ID, securityContext);
    assertEquals(404, r.getStatus());
    verify(permissionsService, never()).isAccessTypeAllowedForUser(anyLong(), any(), any());
  }

  @Test
  void listAllowedReturns403WhenCallerLacksRead() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER, anyLong())).thenReturn(false);
    Response r = resource.listAllowed(COLL_APP_ID, securityContext);
    assertEquals(403, r.getStatus());
  }

  @Test
  void listAllowedReturnsTemplates() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER, anyLong())).thenReturn(true);
    var t = new ShepardTemplate("recipe", "EXPERIMENT_RECIPE", "{}");
    t.setAppId("tmpl-1");
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of(t));

    Response r = resource.listAllowed(COLL_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<ShepardTemplateIO> rows = (List<ShepardTemplateIO>) r.getEntity();
    assertEquals(1, rows.size());
    assertEquals("tmpl-1", rows.get(0).getAppId());
  }

  @Test
  void listUsedReturnsTemplates() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Read, CALLER, anyLong())).thenReturn(true);
    var t = new ShepardTemplate("recipe", "EXPERIMENT_RECIPE", "{}");
    t.setAppId("tmpl-used");
    when(templateDAO.listUsedByCollection(COLL_APP_ID)).thenReturn(List.of(t));

    Response r = resource.listUsed(COLL_APP_ID, securityContext);

    assertEquals(200, r.getStatus());
    @SuppressWarnings("unchecked")
    List<ShepardTemplateIO> rows = (List<ShepardTemplateIO>) r.getEntity();
    assertEquals(1, rows.size());
    assertEquals("tmpl-used", rows.get(0).getAppId());
  }

  @Test
  void setAllowedRequiresManage() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Manage, CALLER, anyLong())).thenReturn(false);
    Response r = resource.setAllowed(COLL_APP_ID, new AllowedTemplatesIO(List.of("t1")), securityContext);
    assertEquals(403, r.getStatus());
    verify(templateDAO, never()).setAllowedForCollection(any(), any());
  }

  @Test
  void setAllowedReplacesAndReturnsNewSet() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Manage, CALLER, anyLong())).thenReturn(true);
    var t = new ShepardTemplate("recipe", "EXPERIMENT_RECIPE", "{}");
    t.setAppId("tmpl-1");
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of(t));

    Response r = resource.setAllowed(COLL_APP_ID, new AllowedTemplatesIO(List.of("tmpl-1")), securityContext);

    assertEquals(200, r.getStatus());
    verify(templateDAO).setAllowedForCollection(COLL_APP_ID, List.of("tmpl-1"));
    @SuppressWarnings("unchecked")
    List<ShepardTemplateIO> rows = (List<ShepardTemplateIO>) r.getEntity();
    assertEquals(1, rows.size());
  }

  @Test
  void setAllowedNullBodyClearsTheSet() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Manage, CALLER, anyLong())).thenReturn(true);
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of());

    Response r = resource.setAllowed(COLL_APP_ID, null, securityContext);

    assertEquals(200, r.getStatus());
    verify(templateDAO).setAllowedForCollection(COLL_APP_ID, List.of());
  }

  @Test
  void setAllowedEmptyListClearsTheSet() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Manage, CALLER, anyLong())).thenReturn(true);
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of());

    Response r = resource.setAllowed(COLL_APP_ID, new AllowedTemplatesIO(List.of()), securityContext);

    assertEquals(200, r.getStatus());
    verify(templateDAO).setAllowedForCollection(COLL_APP_ID, List.of());
  }

  @Test
  void instantiateRequires401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.instantiate(COLL_APP_ID, "tmpl-1", securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void instantiateReturns403WhenCallerLacksWrite() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, de.dlr.shepard.common.util.AccessType.Write, CALLER, anyLong())).thenReturn(false);
    Response r = resource.instantiate(COLL_APP_ID, "tmpl-1", securityContext);
    assertEquals(403, r.getStatus());
    verify(templateDAO, never()).recordUsageReportingCreation(any(), any());
  }

  @Test
  void instantiateReturns404WhenTemplateMissing() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, de.dlr.shepard.common.util.AccessType.Write, CALLER, anyLong())).thenReturn(true);
    when(templateDAO.findByAppId("ghost")).thenReturn(Optional.empty());

    Response r = resource.instantiate(COLL_APP_ID, "ghost", securityContext);
    assertEquals(404, r.getStatus());
    verify(templateDAO, never()).recordUsageReportingCreation(any(), any());
  }

  @Test
  void instantiateReturns404WhenTemplateRetired() {
    var t = new de.dlr.shepard.template.entities.ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{}");
    t.setAppId("retired-tmpl");
    t.setRetired(true);
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, de.dlr.shepard.common.util.AccessType.Write, CALLER, anyLong())).thenReturn(true);
    when(templateDAO.findByAppId("retired-tmpl")).thenReturn(Optional.of(t));

    Response r = resource.instantiate(COLL_APP_ID, "retired-tmpl", securityContext);
    assertEquals(404, r.getStatus());
    verify(templateDAO, never()).recordUsageReportingCreation(any(), any());
  }

  @Test
  void instantiateStampsEdgeAndReturnsBody() {
    var t = new de.dlr.shepard.template.entities.ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
    t.setAppId("tmpl-1");
    t.setVersion(3);
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, de.dlr.shepard.common.util.AccessType.Write, CALLER, anyLong())).thenReturn(true);
    when(templateDAO.findByAppId("tmpl-1")).thenReturn(Optional.of(t));
    when(templateDAO.recordUsageReportingCreation(COLL_APP_ID, "tmpl-1")).thenReturn(true);

    Response r = resource.instantiate(COLL_APP_ID, "tmpl-1", securityContext);
    assertEquals(200, r.getStatus());
    var io = (de.dlr.shepard.v2.template.io.TemplateInstantiationIO) r.getEntity();
    assertEquals(COLL_APP_ID, io.getCollectionAppId());
    assertEquals("tmpl-1", io.getTemplateAppId());
    assertEquals("EXPERIMENT_RECIPE", io.getTemplateKind());
    assertEquals(Integer.valueOf(3), io.getTemplateVersion());
    assertEquals("{\"experiment\":{}}", io.getBody());
    assertTrue(io.isEdgeCreated());
  }

  @Test
  void instantiateReportsExistingEdgeOnRepeatCall() {
    var t = new de.dlr.shepard.template.entities.ShepardTemplate("Recipe", "EXPERIMENT_RECIPE", "{\"experiment\":{}}");
    t.setAppId("tmpl-1");
    t.setVersion(3);
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, de.dlr.shepard.common.util.AccessType.Write, CALLER, anyLong())).thenReturn(true);
    when(templateDAO.findByAppId("tmpl-1")).thenReturn(Optional.of(t));
    when(templateDAO.recordUsageReportingCreation(COLL_APP_ID, "tmpl-1")).thenReturn(false);

    Response r = resource.instantiate(COLL_APP_ID, "tmpl-1", securityContext);
    var io = (de.dlr.shepard.v2.template.io.TemplateInstantiationIO) r.getEntity();
    assertFalse(io.isEdgeCreated());
  }
}
