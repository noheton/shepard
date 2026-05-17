package de.dlr.shepard.v2.template.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.auth.permission.services.PermissionsService;
import de.dlr.shepard.common.util.AccessType;
import de.dlr.shepard.context.collection.daos.CollectionPropertiesDAO;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.v2.template.io.TemplateInstantiateRequestIO;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TemplateInstantiationRestTest {

  static final String COLL_APP_ID = "coll-aaa";
  static final long COLL_OGM_ID = 42L;
  static final String TMPL_APP_ID = "tmpl-bbb";
  static final String CALLER = "alice";

  @Mock
  ShepardTemplateDAO templateDAO;

  @Mock
  CollectionPropertiesDAO propsDAO;

  @Mock
  PermissionsService permissionsService;

  @Mock
  DataObjectService dataObjectService;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  TemplateInstantiationRest resource;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new TemplateInstantiationRest();
    resource.templateDAO = templateDAO;
    resource.collectionPropsDAO = propsDAO;
    resource.permissionsService = permissionsService;
    resource.dataObjectService = dataObjectService;
    resource.objectMapper = new ObjectMapper();

    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn(CALLER);
  }

  // ---------- Auth ----------

  @Test
  void returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);
    assertEquals(401, r.getStatus());
    verify(dataObjectService, never()).createDataObject(anyLong(), any());
  }

  // ---------- Collection lookup ----------

  @Test
  void returns404WhenCollectionMissing() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);
    assertEquals(404, r.getStatus());
    verify(dataObjectService, never()).createDataObject(anyLong(), any());
  }

  // ---------- Write permission ----------

  @Test
  void returns403WhenCallerLacksWrite() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Write, CALLER, 0L)).thenReturn(false);
    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);
    assertEquals(403, r.getStatus());
    verify(dataObjectService, never()).createDataObject(anyLong(), any());
  }

  // ---------- Template lookup ----------

  @Test
  void returns404WhenTemplateMissing() {
    allowWrite();
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);
    assertEquals(404, r.getStatus());
    verify(dataObjectService, never()).createDataObject(anyLong(), any());
  }

  // ---------- Retired template → 409 ----------

  @Test
  void returns409WhenTemplateRetired() {
    allowWrite();
    ShepardTemplate retired = liveTemplate();
    retired.setRetired(true);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(retired));
    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);
    assertEquals(409, r.getStatus());
    verify(dataObjectService, never()).createDataObject(anyLong(), any());
  }

  // ---------- Allow-list guard ----------

  @Test
  void returns403WhenTemplateNotInAllowList() {
    allowWrite();
    ShepardTemplate other = new ShepardTemplate("Other", "DATAOBJECT_RECIPE", "{}");
    other.setAppId("other-tmpl");
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(liveTemplate()));
    // Non-empty allow-list that does NOT contain TMPL_APP_ID
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of(other));

    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);
    assertEquals(403, r.getStatus());
    verify(dataObjectService, never()).createDataObject(anyLong(), any());
  }

  // ---------- Success paths ----------

  @Test
  void returns201WhenUnrestrictedCollection() {
    allowWrite();
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(liveTemplate()));
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of()); // unrestricted
    DataObject newDo = fakeDataObject(99L);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), any(DataObjectIO.class))).thenReturn(newDo);

    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);

    assertEquals(201, r.getStatus());
    DataObjectIO body = (DataObjectIO) r.getEntity();
    assertNotNull(body);
    verify(templateDAO).recordCreatedFrom(eq(99L), any(ShepardTemplate.class));
  }

  @Test
  void returns201WhenTemplateInAllowList() {
    allowWrite();
    ShepardTemplate tmpl = liveTemplate();
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(tmpl));
    // Allow-list includes the template
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of(tmpl));
    DataObject newDo = fakeDataObject(50L);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), any(DataObjectIO.class))).thenReturn(newDo);

    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);

    assertEquals(201, r.getStatus());
  }

  @Test
  void appliesAttributesFromTemplateBody() {
    allowWrite();
    ShepardTemplate tmpl = new ShepardTemplate("Recipe", "DATAOBJECT_RECIPE",
      "{\"dataobjects\":[{\"name\":\"MyName\",\"attributes\":{\"color\":\"red\",\"count\":\"3\"}}]}");
    tmpl.setAppId(TMPL_APP_ID);
    tmpl.setVersion(1);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(tmpl));
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of());
    DataObject newDo = fakeDataObject(77L);
    ArgumentCaptor<DataObjectIO> captor = ArgumentCaptor.forClass(DataObjectIO.class);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), captor.capture())).thenReturn(newDo);

    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);

    assertEquals(201, r.getStatus());
    DataObjectIO passedIO = captor.getValue();
    // Name from template body dataobjects[0].name
    assertEquals("MyName", passedIO.getName());
    assertNotNull(passedIO.getAttributes());
    assertEquals("red", passedIO.getAttributes().get("color"));
    assertEquals("3", passedIO.getAttributes().get("count"));
  }

  @Test
  void requestBodyNameOverridesTemplateBodyName() {
    allowWrite();
    ShepardTemplate tmpl = new ShepardTemplate("Recipe", "DATAOBJECT_RECIPE",
      "{\"dataobjects\":[{\"name\":\"TemplateName\"}]}");
    tmpl.setAppId(TMPL_APP_ID);
    tmpl.setVersion(1);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(tmpl));
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of());
    DataObject newDo = fakeDataObject(55L);
    ArgumentCaptor<DataObjectIO> captor = ArgumentCaptor.forClass(DataObjectIO.class);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), captor.capture())).thenReturn(newDo);

    TemplateInstantiateRequestIO req = new TemplateInstantiateRequestIO();
    req.setName("OverrideName");
    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, req, securityContext);

    assertEquals(201, r.getStatus());
    assertEquals("OverrideName", captor.getValue().getName());
  }

  @Test
  void returns201WithEmptyBodyAndNoAttributes() {
    allowWrite();
    ShepardTemplate tmpl = new ShepardTemplate("Recipe", "DATAOBJECT_RECIPE",
      "{\"dataobjects\":[{}]}");
    tmpl.setAppId(TMPL_APP_ID);
    tmpl.setVersion(1);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(tmpl));
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of());
    DataObject newDo = fakeDataObject(88L);
    ArgumentCaptor<DataObjectIO> captor = ArgumentCaptor.forClass(DataObjectIO.class);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), captor.capture())).thenReturn(newDo);

    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);

    assertEquals(201, r.getStatus());
    // No attributes key in body → null attributes passed
    assertTrue(captor.getValue().getAttributes() == null || captor.getValue().getAttributes().isEmpty());
  }

  // ---------- helpers ----------

  private void allowWrite() {
    when(propsDAO.findCollectionIdByAppId(COLL_APP_ID)).thenReturn(Optional.of(COLL_OGM_ID));
    when(permissionsService.isAccessTypeAllowedForUser(COLL_OGM_ID, AccessType.Write, CALLER, 0L)).thenReturn(true);
  }

  private ShepardTemplate liveTemplate() {
    ShepardTemplate t = new ShepardTemplate("Recipe", "DATAOBJECT_RECIPE", "{\"dataobjects\":[{}]}");
    t.setAppId(TMPL_APP_ID);
    t.setVersion(1);
    return t;
  }

  /**
   * Minimal {@link DataObject} with the given {@code shepardId}, wrapped in a
   * dummy {@link Collection} so that {@link DataObjectIO#DataObjectIO(DataObject)}
   * can access {@code dataObject.getCollection().getShepardId()}.
   */
  private DataObject fakeDataObject(long shepardId) {
    Collection col = new Collection();
    col.setShepardId(COLL_OGM_ID);

    DataObject d = new DataObject();
    d.setShepardId(shepardId);
    d.setName("generated");
    d.setCollection(col);
    return d;
  }
}
