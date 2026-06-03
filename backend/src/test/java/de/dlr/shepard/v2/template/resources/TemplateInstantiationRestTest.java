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
import de.dlr.shepard.template.services.TemplateInheritanceResolver;
import de.dlr.shepard.v2.shapes.validator.JenaShaclValidator;
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

  /**
   * A SHACL shape that requires the candidate node to carry at least one
   * {@code urn:shepard:attribute:propellant} triple.
   */
  static final String SHAPE_REQUIRES_PROPELLANT =
    "@prefix sh: <http://www.w3.org/ns/shacl#> .\n" +
    "@prefix attr: <urn:shepard:attribute:> .\n" +
    "<urn:shepard:shape:testShape>\n" +
    "  a sh:NodeShape ;\n" +
    "  sh:targetNode <" + TemplateInstantiationRest.INSTANCE_URI + "> ;\n" +
    "  sh:property [\n" +
    "    sh:path attr:propellant ;\n" +
    "    sh:minCount 1 ;\n" +
    "    sh:message \"propellant is required\" ;\n" +
    "  ] .\n";

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
    resource.inheritanceResolver = new TemplateInheritanceResolver(templateDAO);
    resource.shaclValidator = new JenaShaclValidator();

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

  // ---------- SHACL shape validation ----------

  @Test
  void returns201WhenShapeIsPresent_andInstanceConforms() {
    allowWrite();
    // Template body has a shapeGraph requiring 'propellant', and the attributes include it
    String body =
      "{\"shapeGraph\":" + escapeJson(SHAPE_REQUIRES_PROPELLANT) + "," +
      "\"dataobjects\":[{\"attributes\":{\"propellant\":\"LOX/LH2\"}}]}";
    ShepardTemplate tmpl = new ShepardTemplate("Recipe", "DATAOBJECT_RECIPE", body);
    tmpl.setAppId(TMPL_APP_ID);
    tmpl.setVersion(1);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(tmpl));
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of());
    DataObject newDo = fakeDataObject(200L);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), any(DataObjectIO.class))).thenReturn(newDo);

    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);

    assertEquals(201, r.getStatus());
    verify(dataObjectService).createDataObject(eq(COLL_OGM_ID), any());
  }

  @Test
  void returns422WhenShapeIsPresent_andInstanceViolates() {
    allowWrite();
    // Template body has a shapeGraph requiring 'propellant'; the attributes provided
    // do NOT include 'propellant' so the shape constraint is violated.
    String body =
      "{\"shapeGraph\":" + escapeJson(SHAPE_REQUIRES_PROPELLANT) + "," +
      "\"dataobjects\":[{\"attributes\":{\"color\":\"red\"}}]}";
    ShepardTemplate tmpl = new ShepardTemplate("Recipe", "DATAOBJECT_RECIPE", body);
    tmpl.setAppId(TMPL_APP_ID);
    tmpl.setVersion(1);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(tmpl));
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of());

    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);

    assertEquals(422, r.getStatus());
    String entity = (String) r.getEntity();
    assertTrue(entity.contains("violates"), "Response body should mention violation: " + entity);
    // DataObject must NOT be created on validation failure
    verify(dataObjectService, never()).createDataObject(anyLong(), any());
  }

  @Test
  void returns201WhenNoShapeGraph() {
    allowWrite();
    // Template body has NO shapeGraph field — validation is skipped regardless of attributes
    ShepardTemplate tmpl = new ShepardTemplate("Recipe", "DATAOBJECT_RECIPE",
      "{\"dataobjects\":[{\"attributes\":{\"propellant\":\"LOX\"}}]}");
    tmpl.setAppId(TMPL_APP_ID);
    tmpl.setVersion(1);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(tmpl));
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of());
    DataObject newDo = fakeDataObject(300L);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), any(DataObjectIO.class))).thenReturn(newDo);

    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);

    assertEquals(201, r.getStatus());
  }

  @Test
  void returns201WhenShapeGraphIsMalformedTurtle() {
    allowWrite();
    // Malformed Turtle → fail-soft: validation is skipped, DataObject is created anyway
    String body =
      "{\"shapeGraph\":\"this is not valid turtle @#$%\"," +
      "\"dataobjects\":[{\"attributes\":{\"color\":\"blue\"}}]}";
    ShepardTemplate tmpl = new ShepardTemplate("Recipe", "DATAOBJECT_RECIPE", body);
    tmpl.setAppId(TMPL_APP_ID);
    tmpl.setVersion(1);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(tmpl));
    when(templateDAO.listAllowedForCollection(COLL_APP_ID)).thenReturn(List.of());
    DataObject newDo = fakeDataObject(400L);
    when(dataObjectService.createDataObject(eq(COLL_OGM_ID), any(DataObjectIO.class))).thenReturn(newDo);

    Response r = resource.instantiateDataObject(COLL_APP_ID, TMPL_APP_ID, null, securityContext);

    assertEquals(201, r.getStatus());
  }

  // ---------- extractShapeGraph unit tests ----------

  @Test
  void extractShapeGraph_returnsNullWhenBodyIsNull() {
    assertEquals(null, resource.extractShapeGraph(null));
  }

  @Test
  void extractShapeGraph_returnsNullWhenFieldAbsent() {
    assertEquals(null, resource.extractShapeGraph("{\"dataobjects\":[]}"));
  }

  @Test
  void extractShapeGraph_returnsTurtleWhenPresent() {
    String turtle = "@prefix sh: <http://www.w3.org/ns/shacl#> . <S> a sh:NodeShape .";
    String body = "{\"shapeGraph\":" + escapeJson(turtle) + "}";
    assertEquals(turtle, resource.extractShapeGraph(body));
  }

  // ---------- buildDataTurtle unit tests ----------

  @Test
  void buildDataTurtle_emptyAttributes_producesValidTurtle() {
    String turtle = resource.buildDataTurtle(Map.of());
    assertTrue(turtle.startsWith("<" + TemplateInstantiationRest.INSTANCE_URI + ">"),
      "Turtle must start with instance URI");
    assertTrue(turtle.contains("."), "Turtle must end with a period");
  }

  @Test
  void buildDataTurtle_withAttributes_containsPredicatesAndValues() {
    String turtle = resource.buildDataTurtle(Map.of("color", "red"));
    assertTrue(turtle.contains(TemplateInstantiationRest.ATTR_NS + "color"),
      "Turtle must contain attribute predicate");
    assertTrue(turtle.contains("\"red\""), "Turtle must contain quoted value");
  }

  @Test
  void buildDataTurtle_escapesDoubleQuotesInValues() {
    String turtle = resource.buildDataTurtle(Map.of("notes", "say \"hello\""));
    assertTrue(turtle.contains("\\\"hello\\\""), "Double quotes must be escaped in Turtle literals");
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

  /**
   * Escape a string for embedding as a JSON string value (surrounded by
   * double-quotes in the caller). Escapes backslashes and double-quotes.
   */
  private static String escapeJson(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }
}
