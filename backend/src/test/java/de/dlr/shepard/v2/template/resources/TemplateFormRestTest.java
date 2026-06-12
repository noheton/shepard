package de.dlr.shepard.v2.template.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.exceptions.ProblemJson;
import de.dlr.shepard.template.daos.ShepardTemplateDAO;
import de.dlr.shepard.template.entities.ShepardTemplate;
import de.dlr.shepard.template.services.TemplateInheritanceResolver;
import de.dlr.shepard.v2.shapes.builder.FormHintSpec;
import de.dlr.shepard.v2.shapes.builder.GroupSpec;
import de.dlr.shepard.v2.shapes.builder.PropertyShapeSpec;
import de.dlr.shepard.v2.shapes.builder.ShaclShapeBuilder;
import de.dlr.shepard.v2.shapes.builder.ShapeSpec;
import de.dlr.shepard.v2.template.io.TemplateFormDescriptorIO;
import de.dlr.shepard.v2.template.services.FormDescriptorCompiler;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * {@code GET /v2/templates/{templateAppId}/form} — status-code contract +
 * happy-path descriptor + ETag (FORM-DESCRIPTOR-1, doc 125 §5.1).
 */
class TemplateFormRestTest {

  static final String TMPL_APP_ID = "tmpl-docket-general";
  static final String ATTR = "urn:shepard:attribute:";
  static final String DASH = "http://datashapes.org/dash#";

  @Mock
  ShepardTemplateDAO templateDAO;

  @Mock
  SecurityContext securityContext;

  @Mock
  Principal principal;

  TemplateFormRest resource;

  final ShaclShapeBuilder builder = new ShaclShapeBuilder();

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    resource = new TemplateFormRest();
    resource.templateDAO = templateDAO;
    resource.inheritanceResolver = new TemplateInheritanceResolver(templateDAO);
    resource.compiler = new FormDescriptorCompiler();
    resource.objectMapper = new ObjectMapper();
    when(securityContext.getUserPrincipal()).thenReturn(principal);
    when(principal.getName()).thenReturn("alice");
  }

  String docketShapeTurtle() {
    var spec = new ShapeSpec(
      "urn:btkvs:shape:docket-general",
      null,
      false,
      List.of(
        new PropertyShapeSpec(
          ATTR + "docket_id",
          "http://www.w3.org/2001/XMLSchema#string",
          1,
          1,
          null,
          null,
          "^[A-Z][0-9]{3}$",
          new FormHintSpec(
            "Docket ID",
            null,
            1.0,
            "urn:btkvs:group:identity",
            null,
            DASH + "TextFieldEditor",
            null,
            "I123",
            null,
            new FormHintSpec.CellMappingSpec("Laufzettel C-C bzw C-C-SiC", "K1")
          )
        )
      ),
      List.of(new GroupSpec("urn:btkvs:group:identity", "Identity", 1.0)),
      "urn:shepard:instance:candidate"
    );
    return builder.toTurtle(spec);
  }

  ShepardTemplate templateWithShape() {
    String body;
    try {
      var node = new ObjectMapper().createObjectNode();
      node.set("structuredData", new ObjectMapper().createObjectNode());
      node.put("shapeGraph", docketShapeTurtle());
      body = node.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    ShepardTemplate t = new ShepardTemplate("Docket — general section", "STRUCTURED_RECIPE", body);
    t.setAppId(TMPL_APP_ID);
    t.setVersion(1);
    return t;
  }

  @Test
  void returns401WhenUnauthenticated() {
    when(securityContext.getUserPrincipal()).thenReturn(null);
    Response r = resource.getForm(TMPL_APP_ID, null, securityContext);
    assertEquals(401, r.getStatus());
  }

  @Test
  void returns404WhenTemplateMissing() {
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.empty());
    Response r = resource.getForm(TMPL_APP_ID, null, securityContext);
    assertEquals(404, r.getStatus());
    ProblemJson p = (ProblemJson) r.getEntity();
    assertTrue(p.detail().contains(TMPL_APP_ID));
  }

  @Test
  void returns409WhenTemplateRetired() {
    ShepardTemplate t = templateWithShape();
    t.setRetired(true);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(t));
    Response r = resource.getForm(TMPL_APP_ID, null, securityContext);
    assertEquals(409, r.getStatus());
  }

  @Test
  void returns422WhenTemplateKindIsNotADataKind() {
    ShepardTemplate t = new ShepardTemplate("Trace3D", "VIEW_RECIPE", "{\"view\":{}}");
    t.setAppId(TMPL_APP_ID);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(t));
    Response r = resource.getForm(TMPL_APP_ID, null, securityContext);
    assertEquals(422, r.getStatus());
    ProblemJson p = (ProblemJson) r.getEntity();
    assertTrue(p.detail().contains("VIEW_RECIPE"));
  }

  @Test
  void returns422WhenNoShapeGraph() {
    ShepardTemplate t = new ShepardTemplate("Legacy", "DATAOBJECT_RECIPE", "{\"dataobjects\":[{}]}");
    t.setAppId(TMPL_APP_ID);
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(t));
    Response r = resource.getForm(TMPL_APP_ID, null, securityContext);
    assertEquals(422, r.getStatus());
    ProblemJson p = (ProblemJson) r.getEntity();
    assertTrue(p.detail().contains("shapeGraph"));
  }

  @Test
  void returns200WithCompiledDescriptor() {
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(templateWithShape()));
    Response r = resource.getForm(TMPL_APP_ID, null, securityContext);
    assertEquals(200, r.getStatus());
    TemplateFormDescriptorIO d = (TemplateFormDescriptorIO) r.getEntity();
    assertEquals(TMPL_APP_ID, d.templateAppId());
    assertEquals("STRUCTURED_RECIPE", d.templateKind());
    assertEquals("urn:btkvs:shape:docket-general", d.shapeIri());
    assertEquals(1, d.fields().size());
    var f = d.fields().get(0);
    assertEquals(ATTR + "docket_id", f.path());
    assertEquals("docket_id", f.attributeKey());
    assertEquals("^[A-Z][0-9]{3}$", f.pattern());
    assertEquals(DASH + "TextFieldEditor", f.editor());
    assertNotNull(f.cellMapping());
    assertEquals("K1", f.cellMapping().cell());
    assertEquals("POST", d.submit().method());
    assertTrue(d.submit().href().endsWith("/data-objects/from-template/" + TMPL_APP_ID));
    assertNotNull(r.getHeaderString("ETag"));
  }

  @Test
  void returns304WhenIfNoneMatchMatches() {
    when(templateDAO.findByAppId(TMPL_APP_ID)).thenReturn(Optional.of(templateWithShape()));
    Response first = resource.getForm(TMPL_APP_ID, null, securityContext);
    String etag = first.getHeaderString("ETag");
    assertNotNull(etag);
    Response second = resource.getForm(TMPL_APP_ID, etag, securityContext);
    assertEquals(304, second.getStatus());
    assertNull(second.getEntity());
  }

  @Test
  void extractShapeGraph_handlesAbsentAndMalformedBodies() {
    assertNull(resource.extractShapeGraph(null));
    assertNull(resource.extractShapeGraph("{\"structuredData\":{}}"));
    assertNull(resource.extractShapeGraph("not json"));
  }

  @Test
  void etagIsStablePerInput() {
    assertEquals(TemplateFormRest.etagFor("a", "b"), TemplateFormRest.etagFor("a", "b"));
    assertTrue(TemplateFormRest.etagFor("a", "b").startsWith("W/\""));
  }
}
